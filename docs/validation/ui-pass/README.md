# UI pass validation

`assembleDebug`, `testDebugUnitTest`, `lintDebug`: passed. 35 tests, zero failures/skips; zero lint errors and 14 dependency-update advisories. See host-results.json and build.log.

The light/dark PNGs are actual native-graphics host renders of a Compose test fixture. Their account names and amounts are **synthetic test-only values**, never production seeds. Capture uses an Android view Canvas in Robolectric; these are not physical-device screenshots. Semantics/layout checks include 320dp and 200% Compose font scaling. Device keyboard, TalkBack, OEM rendering and full process-death remain pending.

Navigation test inspects one active page at 64ms after repeated tab changes, inside the old fade window. Persisted center shortcut, global amount visibility and theme are exercised through Settings and Activity recreation. Migration test creates the original v1 Room schema, inserts financial/account/journal/preference rows, upgrades with actual MIGRATION_1_2, and verifies data and independent preference persistence.
