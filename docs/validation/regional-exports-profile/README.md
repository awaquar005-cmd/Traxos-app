# Regional, export and profile validation

Personal APK built; **328 tests passed across the full run and targeted rerun (0 remaining failures/errors/skips)**. Lint: **0 errors, 25 warnings, 4 hints**. Signing and16KiB ZIP/64-bit ELF alignment verified.

[Exact suites/signature](host-results.json) · [Full build log](build.log) · [Preservation audit](../../REGIONAL_EXPORT_PROFILE_DELIVERY_MANIFEST.json).

All299 existing tests retained.29 new tests cover direct/as-of/inverse/no-chaining/range/rounding rules, rate revisions/duplicates/deletion, durable validated device options, timezone/date/clock/code formatting, typed JSON integer/null/text/blob/secret boundaries, scoped CSV/deleted/notes/formula safety, failing streams, encrypted rate recovery, actual14 archive read and14→15 migration, bounded/oriented/atomic photos and invalid/oversized replacement, launcher aliases, cancellation and denied-destination cleanup, native report pagination/Canvas drawing, settings/rate/conversion/recreate/dark accessibility/disclosure/picker/help/back-navigation flows. PNGs are isolated fixture renders, not production dummy data.

Robolectric4.14 lacks PdfDocument JNI. Actual report layout is exercised on native Canvas via an injected page surface; no fake serialized PDF is claimed tested. Android-native PDF generation/opening/printing, physical SAF providers/low-space interruption, actual launcher/pinned shortcuts, external sharing, font/TalkBack/OEM/notification timing and real Google/backend acceptance remain device/service gates. Interface remains English; formatting-language choices are real.

Reproduce using JDK21, the existing Android SDK/signing setup and `./gradlew --no-daemon --max-workers=1 -I scripts/isolated-tests.gradle :app:assemblePersonalDebug :app:testPersonalDebugUnitTest :app:lintPersonalDebug`. No emulator or new library/permission.

Execution detail: the full327-test run passed. Final review refined report labels for generated/matched loan principal, preserved original CSV type with a classification column, added a missing-picker fallback. A new loan-classification regression test and all28 new-feature tests were rerun on the final code; build/lint also reran. host-results.json merges full-run and final targeted suites; full-regression.log and build.log retain both actual executions.
