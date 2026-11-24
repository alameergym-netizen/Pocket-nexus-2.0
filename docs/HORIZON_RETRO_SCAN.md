# Interrupted ROM scan protection

Track observed directory enumeration failures, missing/unreadable game files, interruption and per-system scan limit. Do not overwrite the per-source index when a scan reports incomplete; use the existing readable cached index when possible and show a warning in RetroActivity. Four new pure unit tests. Intentional depth and noload rules remain. This is best-effort detection, not a filesystem snapshot: media removal, metadata read failure or mount changes can still escape detection. No simultaneous multi-root library or changes to game IDs. Physical device testing required.

## Integrated validation
Source switching passed 138 tests on its earlier baseline (run 45418220826). This integrated candidate is based on the verified 147-test offline PC library foundation; adds 15 storage tests in total. Full integrated CI and RP6 physical-media validation required.
