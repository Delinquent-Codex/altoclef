# AltoClef

AltoClef is a client-side Fabric automation mod powered by Baritone. This branch is a single-target port for Minecraft Java Edition 26.2.

This fork is maintained at [Delinquent-Codex/altoclef](https://github.com/Delinquent-Codex/altoclef). The original AltoClef authors and contributors remain credited in `fabric.mod.json`; this port preserves the MIT license and bundles a source-based Baritone integration under Baritone's LGPL-3.0 license.

## Supported Version

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.153.0+26.2 |
| Fabric Loom | 1.17.12 |
| Gradle | 9.6.0 |
| MixinExtras | 0.5.4 |
| Nether Pathfinder | 1.6 |

Minecraft 26.2 is built with the current Fabric toolchain using Mojang names. No Yarn 26.2 mapping dependency is declared because the Fabric metadata for 26.2 did not publish a Yarn artifact for this version when this port was made.

## Install

1. Install Minecraft 26.2 with Fabric Loader 0.19.3.
2. Install Fabric API 0.153.0+26.2 in the `mods` folder.
3. Put the remapped AltoClef jar from `build/libs` into the same `mods` folder.
4. Do not install a separate Baritone jar. The required Baritone implementation is included in AltoClef.

AltoClef is client-only. It is not a server mod.

## Build

Use Java 25. The Gradle build also declares a Java 25 toolchain.

```bash
./gradlew clean build
```

The distributable jar is written to:

```text
build/libs/altoclef-0.19.0-port.2+mc26.2.jar
```

The build runs `verifyJarContents`, which checks that the final jar contains Fabric metadata, AltoClef and Baritone mixin configs, the AltoClef entrypoint, required bundled libraries, and no bundled Minecraft or Fabric Loader/API classes.

## Development Client

```bash
./gradlew runClient
```

Use a throwaway Fabric 26.2 instance for runtime testing. Basic smoke checks for this port are:

- Reach the title screen without AltoClef or mixin errors.
- Create or load a local world.
- Confirm AltoClef initializes once.
- Run help/status commands.
- Start and stop a simple collection or movement task.
- Verify inventory and slot handling by opening a container.
- Verify one Baritone-backed pathing command.

## Configuration

AltoClef writes configuration under the Minecraft config directory in the `altoclef` folder. The main settings file is:

```text
config/altoclef/altoclef_settings.json
```

Existing old Baritone configuration can interfere with AltoClef behavior. Remove old standalone Baritone configs before testing this bundled build.

## Baritone

No published Fabric 26.2 Baritone artifact with the required AltoClef internals was available for this port. Baritone source is vendored under `third_party/baritone` from:

```text
https://github.com/cabaletta/baritone
commit 775f4ca97f64bba5780f0f012485dce20b36fb44
branch 1.21.11
```

The vendored source is compiled into the AltoClef jar. Nether Elytra pathfinding uses `dev.babbaj:nether-pathfinder:1.6` as a nested dependency.

All Baritone path, goal, selection, cached-chunk, and debug visualization can be disabled without disabling pathfinding:

```text
#set renderBaritoneVisuals false
```

For native-render regression testing, `#set renderDiagnostics true` logs one bounded ownership snapshot every 30 seconds. It is disabled by default.

Every transient Baritone render batch owns and closes its native vertex allocator, mesh data, and uploaded GPU buffers. World unload also cancels pathing, clears selections and goals, and closes world-cache workers before another world can be joined.

## Migration Summary

- Removed the active ReplayMod preprocessor multi-version build.
- Flattened the project into one Fabric 26.2 target.
- Updated Minecraft, Fabric Loader, Fabric API, Loom, MixinExtras, Jackson, Gradle, and Java toolchain versions.
- Ported AltoClef and vendored Baritone sources to current 26.2 client, item component, inventory, recipe display, registry, rendering, screen, entity, and chunk APIs.
- Updated Fabric metadata for a client-only mod.
- Added CI and local jar-content verification for the 26.2 distributable.

## Known Limitations

The port has been runtime-tested on Fabric API alone, Sodium, Sodium with Iris, and Fabulously Optimized 14.0.0-alpha.3. Renderer regression coverage includes a 15-minute NMT run, ten unload/rejoin cycles, and runs with path rendering or all Baritone visualization disabled. This does not replace task-specific gameplay testing across AltoClef's full command catalogue.

## Useful Docs

- [Usage Guide](usage.md)
- [Development Guide](develop.md)
- [Changelog](changelog.md)
