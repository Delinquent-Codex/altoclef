# AltoClef 26.2 Stability Pack 1 Results

Verification was performed on Minecraft 26.2 with Fabric Loader 0.19.3,
Fabric API 0.153.0+26.2, and Java 25. Detailed stability diagnostics were
enabled only for development runs; release builds keep them disabled by
default.

## Automated verification

The final command was:

```powershell
.\gradlew.bat clean build javadoc --rerun-tasks
```

Result: `BUILD SUCCESSFUL`, with 41 tests, zero failures, zero errors, and zero
skips. Javadoc completed with 100 warnings inherited from the existing source.

The distributable artifact is:

```text
build/libs/altoclef-0.19.0-port.2+mc26.2.jar
SHA-256: 3E3C6AC5D0C9DD92B7B12150DE1A84E15C6FE847C72C5EE6D4CCF73F4297159F
```

## Reproduced failures and fixes

| Failure | Root cause | Correction |
| --- | --- | --- |
| Cursor/crafting softlock | Crafting used inventory-tracker snapshots after the handled-screen state had changed. Parent crafting and cleanup tasks could also take ownership of a cursor stack while an active child slot move still owned the transaction. | Read live player slots for UI transactions, serialize parent/child inventory work, preserve a child's cursor, clear stale grid inputs, invalidate recipe state, and add deterministic UI recovery. |
| False UI watchdog recovery | The watchdog treated total screen-open duration as a stall, so a valid advancing transaction could eventually recover. It also missed short A/B state cycles. | Count repeated UI states instead of screen age, retain a bounded UI-state history, and recognize repeated cycles separately from movement stalls. |
| Passive furnace wait interrupted | A furnace wait with an empty cursor looked like a static handled-screen fault. | Mark passive UI waits in the watchdog fingerprint and suspend recovery while the container is legitimately processing. |
| Root inventory cleanup fought crafting | `BeatMinecraftTask` could start cleanup while its crafting child was still active, repeatedly moving shield ingredients. | Run root cleanup only when no unfinished child owns work and no handled screen is open. |
| Invalid mixed-material recipe plan | Generic matching counted interchangeable ingredients without reserving one recipe-valid variant group. | Select and reserve a complete same-variant batch before committing; mixed wool no longer forms an invalid bed plan. |
| Drop and pickup loops | Throwaway, active requirements, parent requirements, and survival reserves did not share one ownership policy. | Add explicit inventory categories and bounded drop suppression, and make pickup respect active ownership and suppression. |
| Survival/task oscillation | Several urgent states were handled by independent chains without shared ownership, hysteresis, or parent-task preservation. | Add a central survival controller, state ownership, cooldowns, underwater escape planning, and safe parent-task resumption. |
| Scanner spikes and publication crash | Close scans and asynchronous scans had no explicit work budget. Scanner results were also iterated while another thread could mutate their backing collections, causing `ConcurrentModificationException`. | Budget scanner cells/chunks, marshal additions to the client thread, lock publication, and publish deep snapshots. |
| Timeout wander crash | A live recovery path could dereference an absent wander target. | Guard missing targets and let recovery recalculate safely. |

## Cursor and inventory invariants

The implementation enforces these transaction rules:

1. A completed inventory action does not leave an unexpected cursor stack.
2. A new slot transaction does not begin while an earlier click sequence is unresolved.
3. Screen closure returns the cursor to its source, merges it into a compatible stack, or places it in a valid empty player slot.
4. Failed crafting invalidates stale slot and recipe state.
5. UI stalls and movement stalls use separate recovery paths.
6. Recovery never deliberately deletes, duplicates, or overwrites a cursor stack.
7. Parent and child tasks do not issue competing inventory actions.
8. Inventory actions stop when the active handled screen changes unexpectedly.

UI recovery pauses competing work, inspects the cursor, prefers a safe source
return, then a full compatible merge, then an empty player slot. It closes a
stale screen only when safe, refreshes inventory/recipe state, invalidates the
old click plan, and permits one child restart before returning a structured
failure to the parent. It does not probe random slots.

## Watchdog and diagnostics

`@stability` reports the active task chain, Baritone process and goal, path
state, progress age, recovery stage, reservations, survival override, recent
failure, transitions, path calculation rate, scanner work, and tick timing.
The recorder is bounded to 256 samples.

Recovery escalates in this order:

1. Retry the current interaction.
2. Recalculate the path.
3. Clear temporary unreachable targets.
4. Move to a nearby safe position.
5. Re-evaluate the current resource strategy.
6. Restart the current child task.
7. Return a structured failure to the parent.

Handled-screen fingerprints divert cursor and crafting faults to the dedicated
UI recovery path. Passive container waits do not advance either recovery path.

## Runtime matrix

| Scenario | Result |
| --- | --- |
| Fresh survival worlds and seeds | Passed more than five new worlds across seeds 2602001, 2602101, 2602103, 2602701, 2602901-2602906, and 2603001-2603003. |
| Fresh sustained `@gamer` run | Passed 30 minutes 1 second in `Stability-30min-Final-7`, seed 2602902, from 15:22:31 to 15:52:32. One movement interaction retry recovered with a new goal/path. No UI recovery fired. |
| Sustained cursor and lifecycle state | Passed: empty cursor at completion, world unloaded cleanly, 12 event handlers remained registered, zero live native render buffers, and no application error. |
| Repeated start/stop and unload/rejoin | Passed repeated task and world lifecycle runs without retained cursor, task, or native render buffer. |
| Same-color bed | Passed: a red bed completed in 25.4 seconds. |
| Mixed-color bed | Passed: red/blue/white wool was rejected as a valid batch and resource acquisition changed to a valid strategy. |
| Nearly full inventory | Passed: a correct 36-slot setup completed the requested cobblestone in 20.7 seconds while preserving food, gold, gravel, and a required ingredient. |
| Item discard/recollection | Passed: deliberately discarded items were suppressed from immediate pickup while active and parent requirements remained protected. |
| Open-water recovery | Passed: parent log task resumed and completed in 9.7 seconds. |
| Capped underwater trap with tool | Passed: with an iron pickaxe, the parent task resumed and completed in 52.4 seconds. |
| Passive furnace wait | Passed: deterministic iron smelting completed in 11.294 seconds with no watchdog recovery. |
| Focused shield crafting | Passed: `get shield 1` completed in 1.327 seconds without cursor recovery. |
| Scanner publication replay | Passed 120 seconds after snapshot publication fix with no exception and zero live native buffers on unload. |
| Hostile at eight blocks | Parent resource task completed in 19.5 seconds under `HOSTILE_RETREAT`; survival ownership did not discard it. |
| Fabric API only | Passed the 30-minute sustained run and lifecycle checks. |
| Sodium 0.9.0+mc26.2 | Passed a 60-second gameplay/lifecycle smoke run; clean unload and zero live native buffers. |
| Sodium 0.9.0 plus Iris 1.11.1 | Passed a 60-second gameplay/lifecycle smoke run; clean unload and zero live native buffers. |
| Fabulously Optimized | Passed a 60-second 42-mod smoke run, including Sodium, Iris, Lithium, Continuity, Entity Culling, and ImmediatelyFast; no application errors and zero live native buffers. |

## Scanner performance

The before/after scanner comparison used the same copied world state, seed
2602001, task, and 60-second capture. The pre-budget baseline was commit
`91871316`; only the current null guard and diagnostic sampling were applied to
that temporary worktree so the old scanner could complete the same harness.
Scanner logic remained pre-budget.

| Measurement | Before | After | Change |
| --- | ---: | ---: | ---: |
| Mean close-scan point sample | 1.440 ms | 0.495 ms | 65.6% lower |
| Largest asynchronous scan | 1398.68 ms / 518 chunks | 142.23 ms / 64 chunks | 89.8% lower maximum duration, with an explicit 64-chunk bound |
| Whole AltoClef tick point mean | 2.526 ms | 2.895 ms | No demonstrated reduction; phase noise dominates these sparse points |
| Current rolling tick median/p95 | Not emitted by baseline | 1.77/19.65 ms and 1.92/3.85 ms | Informational only |

This establishes a scanner improvement, not a general whole-client CPU claim.

## Known limitations

- A one-block capped underwater stone trap without a usable breaking tool still
  resulted in drowning. The controller cannot manufacture a physically
  available escape route.
- A point-blank zombie at three blocks with 12 health still killed the player.
  The eight-block hostile case was recoverable, but close-range survival is not
  guaranteed.
- The final 30-minute run included two hostile deaths. Respawn, cursor cleanup,
  and parent-task resumption worked, but the deaths remain survival failures.
- The sustained run did not defeat the Ender Dragon. This pack does not claim a
  complete autonomous Minecraft victory.
- Offline skin/session service HTTP 401 messages appeared in development logs;
  they were external authentication noise and did not fail the game or tests.

