package me.vasir.jdaforge.internal.database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import me.vasir.jdaforge.api.Database;
import me.vasir.jdaforge.api.Log;
import me.vasir.jdaforge.util.Files;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

/**
 * Writes a compressed database snapshot into the local {@code backups/} folder. SQLite and H2 use a
 * native snapshot; every other JDBC database is exported as a logical SQL dump (schema + data), so
 * backups work regardless of the configured driver. Internal.
 */
public final class Backup {

    private Backup() {}

    public static void runBackupSequence(String jdbcUrl) {
        File rawFile = null;
        File zipFile = null;
        try {
            rawFile = createSnapshot(jdbcUrl);
            if (rawFile == null) return;

            if (!Files.ensureDirectoryExists("backups")) return;
            zipFile = new File("backups", "backup_" + System.currentTimeMillis() + ".zip");

            if (rawFile.getName().endsWith(".zip")) {
                if (!rawFile.renameTo(zipFile)) zipFile = rawFile;
            } else {
                Files.zip(rawFile, zipFile);
                Files.deleteSafely(rawFile);
            }
            Log.done("Database backup saved: backups/" + zipFile.getName());
        } catch (Exception e) {
            Log.error("Automatic backup failed: " + e.getMessage());
            if (rawFile != null && !rawFile.getName().endsWith(".zip")) Files.deleteSafely(rawFile);
            if (zipFile != null) Files.deleteSafely(zipFile);
        }
    }

    private static File createSnapshot(String jdbcUrl) throws Exception {
        File backupsDir = new File("backups");
        Files.ensureDirectoryExists("backups");

        if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            File dest = new File(backupsDir, "temp_db.db");
            java.nio.file.Files.copy(new File(jdbcUrl.substring(12)).toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dest;
        }
        if (jdbcUrl.startsWith("jdbc:h2:")) {
            Jdbi jdbi = Database.getJdbi();
            if (jdbi == null) {
                Log.warn("Cannot back up: the database is not connected.");
                return null;
            }
            File dest = new File(backupsDir, "temp_h2_backup.zip");
            Files.deleteSafely(dest);
            jdbi.useHandle(handle -> handle.execute("BACKUP TO '" + dest.getAbsolutePath() + "'"));
            return dest;
        }
        return dumpSql(backupsDir);
    }

    /** Exports the whole database as a portable SQL script using only JDBC (works on any driver). */
    private static File dumpSql(File backupsDir) throws Exception {
        Jdbi jdbi = Database.getJdbi();
        if (jdbi == null) {
            Log.warn("Cannot back up: the database is not connected.");
            return null;
        }

        File dest = new File(backupsDir, "temp_dump.sql");
        Files.deleteSafely(dest);

        try (Handle handle = jdbi.open();
             PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(dest, StandardCharsets.UTF_8)))) {

            Connection conn = handle.getConnection();
            DatabaseMetaData meta = conn.getMetaData();
            String product = meta.getDatabaseProductName() == null ? "" : meta.getDatabaseProductName().toLowerCase();
            boolean mysql = product.contains("mysql") || product.contains("maria");
            boolean postgres = product.contains("postgres");

            String catalog = conn.getCatalog();
            String schema = safeSchema(conn);
            String q = quote(meta);

            // Stream large tables instead of buffering them fully (esp. MySQL/PostgreSQL).
            boolean restoreAutoCommit = false;
            if (postgres && conn.getAutoCommit()) {
                conn.setAutoCommit(false);
                restoreAutoCommit = true;
            }

            try {
                out.println("-- JDA-Forge database backup");
                out.println("-- Generated: " + LocalDateTime.now());
                out.println();

                // Relax foreign-key checks so table order never breaks a restore.
                if (mysql) out.println("SET FOREIGN_KEY_CHECKS=0;");
                else if (postgres) out.println("SET session_replication_role = 'replica';");
                out.println();

                List<String> tables = new ArrayList<>();
                try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                    while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
                }

                for (String table : tables) {
                    writeCreateTable(out, meta, catalog, schema, table, q, mysql, postgres);
                    writeRows(out, conn, table, q, mysql);
                    out.println();
                }

                if (mysql) out.println("SET FOREIGN_KEY_CHECKS=1;");
                else if (postgres) out.println("SET session_replication_role = 'origin';");

                if (restoreAutoCommit) conn.commit();
            } finally {
                if (restoreAutoCommit) conn.setAutoCommit(true);
            }
        }
        return dest;
    }

    private static void writeCreateTable(PrintWriter out, DatabaseMetaData meta, String catalog, String schema,
                                         String table, String q, boolean mysql, boolean postgres) throws Exception {
        out.println("DROP TABLE IF EXISTS " + q + table + q + ";");

        List<String> defs = new ArrayList<>();
        try (ResultSet cols = meta.getColumns(catalog, schema, table, "%")) {
            while (cols.next()) {
                String name = cols.getString("COLUMN_NAME");
                String typeName = cols.getString("TYPE_NAME");
                int size = cols.getInt("COLUMN_SIZE");
                int digits = cols.getInt("DECIMAL_DIGITS");
                boolean digitsNull = cols.wasNull();
                boolean notNull = cols.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
                boolean autoInc = "YES".equalsIgnoreCase(cols.getString("IS_AUTOINCREMENT"));

                String def = q + name + q + " " + renderType(typeName, size, digits, digitsNull);
                if (notNull) def += " NOT NULL";
                if (autoInc) {
                    if (mysql) def += " AUTO_INCREMENT";
                    else if (postgres && !typeName.toLowerCase().contains("serial")) def += " GENERATED BY DEFAULT AS IDENTITY";
                }
                defs.add(def);
            }
        }

        List<String> pk = new ArrayList<>();
        try (ResultSet keys = meta.getPrimaryKeys(catalog, schema, table)) {
            while (keys.next()) pk.add(q + keys.getString("COLUMN_NAME") + q);
        }
        if (!pk.isEmpty()) defs.add("PRIMARY KEY (" + String.join(", ", pk) + ")");

        out.println("CREATE TABLE " + q + table + q + " (");
        out.println("    " + String.join(",\n    ", defs));
        out.println(");");
    }

    private static void writeRows(PrintWriter out, Connection conn, String table, String q, boolean mysql) throws Exception {
        Statement st = null;
        try {
            if (mysql) {
                // MySQL streams row-by-row only with a forward-only cursor and this magic fetch size.
                st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                st.setFetchSize(Integer.MIN_VALUE);
            } else {
                st = conn.createStatement();
                st.setFetchSize(1000);
            }

            try (ResultSet rs = st.executeQuery("SELECT * FROM " + q + table + q)) {
                ResultSetMetaData rmd = rs.getMetaData();
                int columnCount = rmd.getColumnCount();

                StringBuilder cols = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) cols.append(", ");
                    cols.append(q).append(rmd.getColumnName(i)).append(q);
                }

                while (rs.next()) {
                    StringBuilder values = new StringBuilder();
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) values.append(", ");
                        values.append(formatValue(rs, i, rmd.getColumnType(i)));
                    }
                    out.println("INSERT INTO " + q + table + q + " (" + cols + ") VALUES (" + values + ");");
                }
            }
        } finally {
            if (st != null) st.close();
        }
    }

    private static String formatValue(ResultSet rs, int col, int sqlType) throws Exception {
        rs.getObject(col);
        if (rs.wasNull()) return "NULL";

        return switch (sqlType) {
            case Types.BIT, Types.BOOLEAN -> rs.getBoolean(col) ? "TRUE" : "FALSE";
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> rs.getString(col);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] bytes = rs.getBytes(col);
                yield bytes == null ? "NULL" : "X'" + toHex(bytes) + "'";
            }
            default -> "'" + rs.getString(col).replace("'", "''") + "'";
        };
    }

    private static String renderType(String typeName, int size, int digits, boolean digitsNull) {
        if (typeName == null) return "TEXT";
        if (typeName.contains("(")) return typeName;
        String upper = typeName.toUpperCase();
        if (upper.contains("CHAR") && size > 0) return typeName + "(" + size + ")";
        if ((upper.equals("DECIMAL") || upper.equals("NUMERIC")) && size > 0) {
            return typeName + "(" + size + (digitsNull ? "" : ", " + digits) + ")";
        }
        return typeName;
    }

    private static String quote(DatabaseMetaData meta) {
        try {
            String q = meta.getIdentifierQuoteString();
            return (q == null || q.trim().isEmpty()) ? "\"" : q;
        } catch (Exception e) {
            return "\"";
        }
    }

    private static String safeSchema(Connection conn) {
        try {
            return conn.getSchema();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }
}
