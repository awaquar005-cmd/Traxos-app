# Gift wallet 0.4.0 validation

Build passed; **78 tests passed, zero failures/errors/skips** (49 original + 29 gift tests). Lint passed with zero errors and 16 dependency/tool version advisories. [Exact results](host-results.json), [final build/lint log](build.log), [full test-run log](test-run.log).

Coverage includes money/ledger/reopen regressions; Room v1/v2 migration chain and shipped v3→v4 with saved ledger/journal/navigation; gift restart/preferences; normalized duplicate imports; unknown values and discount accounting; precise partial/full redemption, idempotency, stale writes, Undo/refund; encryption envelope nonce/AAD/tamper handling and missing-key refusal; reminder rules/no expiry/expired cards; actual WorkManager schedule replacement/cancellation, private notification receipt deduplication and denied permission/channel behavior; Compose forms/repeated-save callbacks, import Reject/mandatory review, automatic confirmation preference, masked detail, redemption/Undo, optional navigation/More/Settings fallback, light/dark and 320dp enlarged-font layout.

Existing Room entities are structurally identical to schema v3. APK package/version/signing identity match the update path; 16 KiB ZIP and ARM64/x86_64 ELF LOAD alignment checks passed. Final merged permissions exclude internet, SMS, camera and media access; all content providers explicitly remain non-exported. The initialization provider is explicitly non-exported; all 78 tests ran in the final verification.

PNG previews use synthetic host fixtures, not user financial data or physical-phone screenshots. Codes stay masked in list previews. Host crypto tests use the production AES-GCM envelope with ephemeral JCE keys in place of Android Keystore provisioning. Actual hardware Keystore, first-use bundled OCR/camera quality, system permission/channel delivery, real keyboard/TalkBack and process death remain [physical acceptance checks](../../DEVICE_QA.md). No emulator used.

[Wallet guide and accounting/security boundaries](../../GIFT_WALLET.md). Install the APK as Update; do not uninstall/clear storage. This is a development release, not a claim of commercial or physical-device certification.
