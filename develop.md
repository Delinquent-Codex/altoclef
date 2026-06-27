# Development Guide

This checkout is a single-target Minecraft 26.2 Fabric project. The old multi-version ReplayMod preprocessor workflow is no longer active.

## Requirements

- Java 25
- Gradle wrapper from this repository
- A Fabric 26.2 development client

The build pins Fabric Loader 0.19.3, Fabric API 0.153.0+26.2, Fabric Loom 1.17.12, and MixinExtras 0.5.4 in `gradle.properties`.

## Build

On Windows:

```powershell
.\gradlew.bat clean build
```

On macOS or Linux:

```bash
./gradlew clean build
```

The build produces:

```text
build/libs/altoclef-0.19.0-port.1+mc26.2.jar
build/libs/altoclef-0.19.0-port.1+mc26.2-sources.jar
```

`check` depends on `verifyJarContents`, which validates that the packaged jar contains the Fabric metadata, AltoClef entrypoint, mixin configs, Baritone classes, required nested libraries, and no bundled Minecraft or Fabric classes.

## Run Client

```bash
./gradlew runClient
```

## Native renderer regression

Run a repeated-world test with Java 25, a 4096 MiB heap, and native-memory tracking enabled:

```powershell
$env:JAVA_TOOL_OPTIONS='-Xmx4096m -XX:NativeMemoryTracking=summary -Daltoclef.renderRegression=true -Daltoclef.renderRegression.cycles=10 -Daltoclef.renderRegression.activeSeconds=90'
.\gradlew.bat runClient
```

The harness starts `@gamer`, keeps path visualization active for 90 seconds, exits and rejoins the world ten times, checks live render-buffer ownership at every unload, and shuts the client down. Set `-Daltoclef.renderRegression.renderPath=false` to disable path lines only, or `-Daltoclef.renderRegression.visuals=false` to repeat the same pathfinding test with all Baritone visualization disabled. While it runs, use `jcmd <pid> VM.native_memory baseline` and `jcmd <pid> VM.native_memory summary.diff` for native-memory snapshots.

The default save directory is `New World`. Set `-Daltoclef.renderRegression.world=SaveDirectoryName` when testing another existing save. Use a pre-created disposable save for large modpacks so each cycle tests normal unload/reopen behavior instead of one-time world creation.

Use a disposable test profile or world. Confirm the title screen loads, AltoClef initializes once, and the log has no required mixin failures before testing tasks.

## Runtime Smoke Checklist

- Open a local world.
- Run AltoClef help/status commands.
- Start and stop a simple task.
- Check inventory tracking by opening a container.
- Break or place one block through a task.
- Run a simple Baritone-backed movement/path command.
- Leave and re-enter the world.
- Review the log for mixin, renderer, registry, networking, and resource errors.

## Baritone Source

Baritone is vendored in `third_party/baritone` because no complete published Fabric 26.2 artifact matched AltoClef's API and internal usage. Provenance is recorded in `third_party/baritone/SOURCE.txt`.

Do not replace the vendored source with a local-only jar. If Baritone changes are needed, edit the vendored source and keep the changes reviewable.

## Configuration

AltoClef config files are created under:

```text
config/altoclef/
```

The main settings file is:

```text
config/altoclef/altoclef_settings.json
```

## CI

The GitHub Actions build runs on pushes and pull requests, sets up Java 25, runs `./gradlew clean build`, uploads the 26.2 jar and sources jar, and uploads test reports on failure.
