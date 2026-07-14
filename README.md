<h1 align="center">JDA-Forge</h1>

<p align="center">
A lightweight <b>module engine</b> for Discord bots built on <a href="https://github.com/discord-jda/JDA">JDA 5</a>.<br/>
Run the framework as a single jar and add features as independent <b>module</b> jars — no core changes.
</p>

<p align="center">
<code>Java 21</code> &nbsp;•&nbsp; <code>JDA 5.3.0</code> &nbsp;•&nbsp; <code>MIT License</code>
</p>

---

## ✨ Features

| | Feature | |
|---|---|---|
| 🧩 | **Module engine** | Drop `.jar` modules into `/modules` and restart — each is self-contained. |
| 🔗 | **Ordered loading** | Declare `depend:` in `module.yml`; modules boot in the right order. |
| 🔒 | **Clean API** | Build against a small, stable surface (`api` + `util`); internals never leak. |
| 🗄️ | **Dynamic drivers** | SQLite, H2, MySQL, MariaDB & PostgreSQL out of the box, auto-deployed. |
| ⚡ | **Real data layer** | HikariCP pooling + fluent JDBI 3 for fast, boilerplate-free SQL. |
| 💾 | **Auto backups** | Scheduled snapshots to `/backups` (native for SQLite/H2, SQL dump otherwise). |
| 🏷️ | **Placeholders** | Built-ins like `%bot_ping%`, `%member_count%`, plus your own custom tags. |
| 📡 | **Event bus** | Tiny, type-safe pub/sub so modules talk without hard dependencies. |
| 🔄 | **Presence rotation** | Cycle status & activities on an interval, with placeholder support. |
| ♻️ | **Hot reload** | Reload and inspect modules at runtime via the `Modules` API. |
| ⚙️ | **Config-driven** | Toggle intents, presence, sharding, database & logging from one file. |
| 🎨 | **Clean logging** | Soft ANSI colors, daily log files, and automatic crash dumps. |
| 🧵 | **Sharding ready** | Flip a flag to run under a `ShardManager` for large bots. |

---

## 🚀 Running the bot

1. Download **`jda-forge-1.1.0.jar`** from the [Releases](../../releases) page.
2. Put it in an empty folder and run it once — it generates the default files:
   ```bash
   java -jar jda-forge-1.1.0.jar
   ```
3. Open **`jda-forge.yml`**, set your bot `token` (and any intents/features you need), then start again.

### `start.bat` (Windows)
```bat
@echo off
java -Xms512M -Xmx2G -jar jda-forge-1.1.0.jar
pause
```

---

## 🧩 Writing a module

Add JDA-Forge as a **provided** dependency via [JitPack](https://jitpack.io). The default artifact only contains the public `api` + `util` packages, so you get a clean API and `internal` stays out of your project.

### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.drgnbld7</groupId>
        <artifactId>JDA-Forge</artifactId>
        <version>1.1.0</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>net.dv8tion</groupId>
        <artifactId>JDA</artifactId>
        <version>5.3.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle
```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// build.gradle
dependencies {
    compileOnly 'com.github.drgnbld7:JDA-Forge:1.1.0'
    compileOnly 'net.dv8tion:JDA:5.3.0'
}
```

> Both dependencies are **provided/compileOnly** because the running framework already ships them — never shade JDA-Forge or JDA into your module jar.

### `module.yml` (at the root of your module jar)
```yaml
name: MyModule                 # required, unique
main: com.example.MyModule     # class that extends ForgeModule
version: 1.0.0
author: YourName
depend:                        # optional: load these modules first
  - EconomyModule
```

### Minimal module
```java
package com.example;

import me.vasir.jdaforge.api.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class MyModule extends ForgeModule {

    @Override
    public void onEnable() {
        Config cfg = config("mymodule.yml");                 // -> config/mymodule.yml (copied from the jar)
        String greeting = cfg.getString("greeting", "Hi!");

        registerCommand(Commands.slash("hello", "Say hello"));
        registerListener(new Handler(greeting));
        placeholders().register("%my_tag%", ctx -> "custom-value");

        Log.done(name() + " v" + version() + " enabled by " + author());
    }

    static class Handler extends ListenerAdapter {
        private final String greeting;
        Handler(String g) { this.greeting = g; }
        @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
            if (e.getName().equals("hello")) e.reply(greeting).queue();
        }
    }
}
```

Build your module and drop the jar into the framework's `/modules` folder. Slash commands from all modules are synced globally after enable.

---

## ➕ Official extension

- **[JDA-Forge-Plus](https://github.com/drgnbld7/JDA-Forge-Plus)** — a ready-made feature pack: admin utility commands, database backups delivered to Discord, backup & log retention, and ops placeholders. Just drop it into `modules/`.

---

## 📂 Working directory

On first run the framework creates:

```
jda-forge.yml      # main configuration
database.yml       # database configuration (when database is enabled)
modules/           # your module .jar files go here
config/            # per-module config files
drivers/           # JDBC driver jars (auto-deployed when database is enabled)
backups/           # automatic database backups
logs/              # daily logs + crash dumps
```

---

## ⚙️ Configuration (`jda-forge.yml`)

```yaml
settings:
  bot-name: "JDA-Forge-Bot"
  token: "YOUR_TOKEN_HERE"       # required
  status: "ONLINE"               # ONLINE | IDLE | DO_NOT_DISTURB | INVISIBLE
  logging: "BOTH"                # CONSOLE | FILE | BOTH
  debug-mode: false
  database: false                # enable the database subsystem
  sharding:
    enabled: false
    total-shards: 1

presence:
  update-interval-seconds: 60
  activities:
    - type: "WATCHING"           # PLAYING | STREAMING | LISTENING | WATCHING | COMPETING | NONE
      text: "%member_count% Members"

intents:                         # enable only what you need (privileged ones need the Dev Portal too)
  message-content: false
  guild-members: false
  # ... and more
```

When `database: true`, a `database.yml` is generated for driver, JDBC URL, HikariCP pool and auto-backup settings.

---

## 📚 Documentation

Full guides live in the [Wiki](https://github.com/drgnbld7/JDA-Forge/wiki):

- **[Startup Pipeline & Lifecycle](https://github.com/drgnbld7/JDA-Forge/wiki/Startup-Pipeline-&-Lifecycle)** — how the framework boots and shuts down.
- **[How to Create a Module](https://github.com/drgnbld7/JDA-Forge/wiki/How-to-Create-a-Module)** — `module.yml`, the `ForgeModule` API, commands, config and events.
- **[Configuration Reference](https://github.com/drgnbld7/JDA-Forge/wiki/Configuration-Reference)** — every `jda-forge.yml` / `database.yml` option.
- **[Database Engine & Drivers](https://github.com/drgnbld7/JDA-Forge/wiki/Database-Engine-&-Drivers)** — dynamic drivers, HikariCP, JDBI and backups.

---

## 🛠️ Building from source

Requires JDK 21.

```bash
mvn clean package
```

The built jar is placed in the `target/` directory.

---

## 📄 License

Released under the MIT License. See [LICENSE](LICENSE).
