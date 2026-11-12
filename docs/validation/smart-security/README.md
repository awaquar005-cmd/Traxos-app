# 0.5.0 validation

**Build passed; 113 host tests passed, zero failures/errors/skips. Lint passed with zero errors and 21 dependency/tool update advisories.** Android instrumentation APK compiles; native tests were not executed. [Exact results and APK checksum](host-results.json), [final build/test/lint log](build.log). Stage1–4 logs record successive validations before moving to the next requested stage.

Coverage includes the retained78 finance/gift tests plus contextual gift parsing, bitmap budgets/reading order, duplicate/atomic Smart Add approval and safe review-only classifications, denied/revoked policy gates, real Room4→5 encrypted-gift/ledger/navigation preservation and restart, PIN setup/change/disable/throttling, app-gate input/modal protection and persistent independent Settings/defaults. Parser fixtures are synthetic/nonredeemable text; they do not establish native ML Kit accuracy.

Update package/signature verified against0.4.0. Version code6;16KiB ZIP and ARM64/x86_64 ELF LOAD alignment passed. Existing non-preference Room entities are structurally unchanged. Default merged APK has no internet, SMS, camera or media-read permissions; all providers non-exported. No emulator used.

No genuine redacted failing Amazon image or physical phone was available. Hardware Keystore, native biometric/device-credential enrollment/fallback, native OCR/camera accuracy, system notifications/permissions, OS process death, real IME/accessibility and full device acceptance remain pending. Host crypto tests use production envelopes with test JCE keys in place of hardware provisioning. [Fixture harness and security/recovery boundaries](../../SMART_SECURITY.md).
