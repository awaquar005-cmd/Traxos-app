# Smart import, security and settings pass

Execution order: (1) image pipeline/contextual gift extraction + compact entry, host tests; (2) unified encrypted Smart Add suggestions with explicit transactional approval and tests; (3) offline PIN/biometric app gate and tests; (4) categorized working Settings; (5) full regressions, lint, build and delivery. Existing schemas/data/navigation and gift/ledger accounting are preserved; no unrelated finance module.

## Findings and evidence

0.4 gallery decode ignores EXIF, power-of-two downsamples to 1600 pixels; camera preview is low resolution and calls detection with OCR disabled. Parser only accepts labels directly adjacent to values, strict international amount grouping and ISO dates; it lacks PIN/context/candidate scoring. Correct these components, not merchant-specific financial rules.

No user-supplied Amazon failing image is available in this checkout. Synthetic fixtures must be labeled synthetic. Redacted message formats and physical-device instrumented ML Kit fixtures must have provenance. Measure parser field results separately from native OCR accuracy; no native OCR accuracy claim until a phone runs the instrumented corpus. No emulator.

## SMS policy (verified 2026-11-12)

Primary sources: https://support.google.com/googleplay/android-developer/answer/10208820 and https://developer.android.com/guide/topics/permissions/default-handlers . SMS money management is a possible exception requiring declaration/Play review, not automatic authorization. This project has no verified Play approval. Default APK therefore declares no SMS read/receive permission and offers manual share/paste. An explicitly approved build may enable minimum receive-only access with runtime opt-in; never scan historical SMS, use Accessibility, scrape links or request unrestricted access. Permission revocation stops ingestion. Filter OTPs/conversations/unrelated messages before persistence; suggestions require review.

## Security boundary

Use standard PBKDF2-HMAC-SHA256 with random salt and a maintained platform JCA provider, constant-time verification, encrypted/atomic device-local security state bound to Android Keystore, persisted failure throttling, cold-start locking and background timeout. Never put PINs in saved-state bundles or logs. Native BiometricPrompt permits eligible strong fingerprint/face; device credential fallback follows supported API combinations. PIN change/disable requires current PIN. Missing/corrupt configuration or lost keys fails closed and never resets/deletes records. An app gate does not encrypt the existing ledger; document offline recovery limitations.

Sources: https://developer.android.com/identity/sign-in/biometric-auth and https://developers.google.com/ml-kit/vision/text-recognition/v2/android . Keep bundled Latin OCR and no INTERNET permission. Real hardware Keystore/biometric/camera and native OCR remain device validation, independent of host test success.

## Execution outcome

All five stages implemented. Final assembleDebug/assembleDebugAndroidTest,113 host tests and lint passed; zero failures/errors/skips/lint errors,21 version advisories. See [current guide](SMART_SECURITY.md) and [machine-readable evidence](validation/smart-security/host-results.json). Real native OCR/phone acceptance remains unmeasured; no corpus was supplied.
