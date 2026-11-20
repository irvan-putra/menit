# Synthetic input contract, not an Android feature

This directory is host-only test/research code. It is outside Android source sets, is not bundled in an APK, and never calls Android APIs. Packages, labels, times and boot epochs are fake. The main and diagnostic apps, manifests, signing identities and privacy boundaries are unchanged.

Run with JDK 11 or newer:

```sh
sh diagnostic/tools/input-contract/run.sh
```

The existing diagnostic CI also runs this command. It runs 18 input-contract checks, including 1,000 input-order shuffles, and 12 named attribution traces against the unchanged real `Reconciler`. Counts are regression examples, not input-space coverage. The first recorded run is in `evidence/`; later runs should be interpreted against their commit and compiler.

## Contract under test

`SyntheticInputContract` adapts deliberately small typed fake streams into foreground intervals, label observations and coverage intervals for `Reconciler`:

- Each call names one boot epoch and an elapsed-time query window. Other epochs and out-of-window events are ignored and counted. No wall-clock timestamps are accepted, and there is no conversion across boots.
- A resume starts a single foreground package. A different resume closes the previous package; a matching pause, screen-off or shutdown closes it. Duplicate events do not add time. A pause from another package cannot close it.
- A missing close leaves the entire open tail unsettled, so `Reconciler` returns pending rather than recognised time. This deliberately loses some potentially valid time instead of inventing a clean boundary.
- Same-time conflicting resumes or a resume/pause/stop contradiction are not silently ordered. The preceding interval, if any, is unsettled; no new foreground package is invented. Time with no defensible foreground interval is not emitted or credited. It is outside attribution, not proof that the phone was unused.
- Coverage requires two actual heartbeat observations at most the configured gap apart. No final-heartbeat extrapolation. A longer gap creates no coverage, and screen-off/shutdown resets the heartbeat chain. The 10-second gap in tests is a chosen fixture constant, not a measured Android guarantee.
- Labels remain typed observations. They do not create foreground time or coverage, survive a boot change, or carry into a later foreground run. Same-time conflicting IDs are handled by the existing `Reconciler` as unknown.
- The existing 30-second label grace and probable-off default are unchanged. Grace is an estimate, not an accuracy bound. An unseen header change inside it can still be misattributed.

## Recorded result

All 18 input-contract checks and 12 new attribution traces passed in a Java 11 host run. The existing reconciler suite also passed 4,000 randomized cases with zero oracle mismatches, and the real `DiagStore` host/stub suite passed 10 archive/rotation/reload checks. These are not Android runtime, real-header detection or physical-device accuracy results. The compiler in the recorded local run was Eclipse ECJ 3.26.0 because that environment had a Java runtime but no `javac`; CI uses its JDK compiler. No new Android run or release was part of this result.

## Remaining boundary

There is no general real `UsageEvents` adapter, heartbeat coverage collector or messenger header reader here. `UsageCompare` is an own-package diagnostic comparison, not this missing adapter. `DiagService` still reads only its own synthetic package and fixed fake headers. Do not widen its package scope because these tests pass.

A next source/synthetic review can challenge this event contract using more incomplete and contradictory fake streams, then compare it with the actual event shapes documented for a chosen Android API level. Real private headers, account access, new permissions, app feature integration, signing and release remain separate steps. This test-only contract does not authorize or implement them.
