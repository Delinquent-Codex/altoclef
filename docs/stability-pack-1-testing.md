# AltoClef 26.2 Stability Pack 1 Test Procedure

Detailed recording is off by default. Enable the bounded 256-sample recorder only for development runs:

```powershell
$env:JAVA_TOOL_OPTIONS='-Daltoclef.stabilityDiagnostics=true'
```

Use `@stability` at every checkpoint. The command reports the task stack, Baritone process and goal, path state, progress age, recovery stage, reservations, survival override, recent failure, transition count, path-calculation rate, scanner workload, and client tick duration. Inventory, player survival state, nearby hostile count, and crafting plan remain in the bounded in-memory samples. No account or session data is recorded.

## Fixed scenarios

Use Minecraft 26.2 and Java 25. Run each scenario in a new survival world with cheats enabled so setup can be repeated exactly. Record seed, mod set, start/end time, `latest.log`, and `@stability` at setup, intervention, and completion.

The lifecycle harness can create seeded worlds, run a command, emit diagnostics every 30 seconds, unload, and repeat:

```powershell
$env:JAVA_TOOL_OPTIONS='-Daltoclef.renderRegression=true -Daltoclef.stabilityDiagnostics=true -Daltoclef.renderRegression.createWorldIfMissing=true -Daltoclef.renderRegression.freshWorldEachCycle=true -Daltoclef.renderRegression.world=Stability-Pack-1 -Daltoclef.renderRegression.seed=2602001 -Daltoclef.renderRegression.cycles=5 -Daltoclef.renderRegression.activeSeconds=120 -Daltoclef.renderRegression.command=gamer'
.\gradlew.bat runClient
```

Deterministic scenarios may provide pipe-delimited vanilla commands before the AltoClef command starts. For example,
this creates a capped water column around the player and then runs `@get oak_log 1`:

```powershell
$env:JAVA_TOOL_OPTIONS='-Daltoclef.renderRegression=true -Daltoclef.stabilityDiagnostics=true -Daltoclef.renderRegression.createWorldIfMissing=true -Daltoclef.renderRegression.world=Stability-Water-Trap -Daltoclef.renderRegression.seed=2602301 -Daltoclef.renderRegression.cycles=1 -Daltoclef.renderRegression.activeSeconds=120 -Daltoclef.renderRegression.setupCommands=fill^~-2^~-3^~-2^~2^~3^~2^stone|fill^~^~-2^~^~^~1^~^water -Daltoclef.renderRegression.command=get^oak_log^1'
.\gradlew.bat runClient
```

| ID | Setup and action | Required observation |
| --- | --- | --- |
| WATER-1 | Build a 1x1 water column three blocks deep, cap it with stone, set air to 80, then start `@get oak_log 1`. | Survival owns control, chooses air or breaks a safe exit, and resumes the get task. |
| WATER-2 | Start the same task in a river, ocean, water cave, and waterfall. | No spinning or repeated failed exit; air recovers to maximum. |
| INV-1 | Fill 35 slots, keep food, gold nuggets, gravel, and a task ingredient, then request one more item. | One disposable stack is selected; reserves remain; the dropped entity is not reacquired. |
| INV-2 | Request gravel while gravel is configured throwaway. | The active requirement owns gravel until satisfied. |
| CRAFT-1 | Give three red wool, three planks, then request one bed. | A red bed is planned and crafted. |
| CRAFT-2 | Give one red, one blue, one white wool, and three planks, then request one bed. | The planner rejects the mixed set and collects one valid same-color group first. |
| CRAFT-3 | Leave one inventory slot free and run plank, stick, table, tool, bread, and bed recipes. | Inputs and output are retained across screen changes and partial stacks. |
| COMBAT-1 | Put a hostile within eight blocks while a resource task runs at low health and hunger. | Survival/retreat owns control without losing the parent resource task. |
| STUCK-1 | Enclose the target behind an unreachable wall and run a get task for five minutes. | Recovery advances in order, emits one message per stage, and returns a structured failure. |
| LIFE-1 | Start/stop `@gamer` ten times, then unload/rejoin the world ten times. | No retained task, scanner thread, native render allocation, or stale override. |

## Performance protocol

Use the same seed, spawn position, render distance, and mod set for before/after runs. Capture five minutes each for idle, active pathing, resource scanning, unreachable search, staged recovery, and `@gamer`. Report median and 95th-percentile AltoClef tick time, close-scan time, asynchronous scan time/chunks, path calculations per second, and any tick above 50 ms. Do not compare runs with different render distances or loaded chunk sets.

## Compatibility matrix

Repeat the smoke and lifecycle scenarios with Fabric API only, Sodium, Sodium plus Iris, and Fabulously Optimized. The sustained matrix requires five new worlds across at least three seeds and one uninterrupted 30-minute `@gamer` run. Record unresolved failures; a run is not an autonomous victory unless the Ender Dragon is actually defeated and the completion state is observed.
