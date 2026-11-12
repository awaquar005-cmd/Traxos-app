# 0.3.0 custom components validation

Build, all 49 host tests and lint passed. Lint has zero errors and 14 dependency update advisories. Exact suite totals are in [host-results.json](host-results.json); Gradle output is in [build.log](build.log).

Tests cover exact money rules, Room/journal persistence, existing reopen regression, v2→v3 preference migration preserving financial records, navigation swap/reorder/reset/persistence, independent center action, custom popup/sheet/dialog dismissal, form validation, repeated-save callbacks, account creation/edit/archive, delete cancellation and Undo. No new finance module was added.

The APK has the same signing certificate as 0.2.0; version code 4 and 16 KiB ZIP alignment are verified. Schema v3 changes only preferences. Install as an update, not by uninstalling.

PNG files are native Robolectric host renders with synthetic test fixtures, not production sample data or physical-phone screenshots. Dashboard light/dark and narrow transaction/account form renders were inspected. Real IME, OEM behavior, TalkBack, enlarged fonts, process death and physical back/navigation acceptance remain in [DEVICE_QA](../../DEVICE_QA.md). No emulator was used.
