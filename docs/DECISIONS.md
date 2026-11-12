# Decisions

- 2026-11-11: Read all 1,164 lines of V3 before code. Preserve exact source and block-level requirement inventory. User milestone/cost constraints take precedence over recommended infrastructure.
- Single app module + package boundaries/manual DI: conservative Gradle overhead; split only as measured build/team needs grow.
- Native Kotlin/Compose/Material3, Room, Navigation, Paging and coroutines. No emulator, web wrapper, cloud SDK, analytics or paid API.
- JDK 21 is installed; pin compatible AGP/Gradle/Kotlin after checking official artifacts. SDK command-line tools installed only under /workspace/tools if absent. SDK licenses are required for builds; no system-wide changes.
- minSdk 26; compile/target SDK 36 (Android 16). Confirm official Android toolchain/artifact availability, record selected versions in build files and validation documentation.
- Integer money and currency per account; INR default, support INR/USD/EUR/JPY/KWD initially. Currency entry limited to supported scales; no automatic currency conversion. No invented account opening balances.
- Single local profile `local` until proper multi-profile isolation exists. Journal payload contains historical finance content locally and never enters diagnostic logs.
- M1 development-only plaintext Room, OS backup excluded, no secure-storage claim. Encryption/PIN/biometric and portable recovery are M3 release blockers.
- Cash and bank only in M1. Cards, transfer, debt/refund/repayment, goals and groups deferred explicitly so expenses are never misrepresented.
- Keep immutable operation IDs across retries, atomic journal, revision checks and tombstone undo from first implementation. No Save debounce.
- Original supplied mark is described as five rounded squares. No accessible raster asset file here; transparent SVG and Android vector reconstruction are provisional, not a pixel-exact conversion. No image-generation replacement.
- System sans typography provides OS Indic fallback and tabular figures with no font download. Compact spacing 4/8/12/16/20/24dp; 48dp controls. No unnecessary animation.
- Start with Home/Transactions/Accounts and top Settings; migrate to V3 Groups/Plans navigation as those features become real.
- Private GitHub repo creation was previously rejected by connected integration (HTTP 403). Keep local Git history; no claimed remote repository.

- Final M1 validation: Gradle wrapper assembleDebug/testDebugUnitTest/lintDebug succeeded; 26 tests passed; lint has 14 dependency-update advisories only. Keep this tested version set for M1 and evaluate upgrades/security advisories deliberately before release. Use locally installed full Temurin JDK 21.0.13+9; system runtime lacked javac launcher. SDK and JDK live under /workspace/tools. Physical phone acceptance is outstanding.

- User created `awaquar005-cmd/Traxos-app` and authorized pushing source/APK. Git push succeeded. GitHub release asset upload failed with HTTP 401; publish this single 11 MB test APK in `downloads/` for accessible phone testing. Later CI should use a properly scoped release uploader to avoid successive binary commits.

- 0.1.1 crash patch: reproduce duplicate `INR` LazyColumn keys before fixing; namespace keys by Home section. No Room/financial/schema change. Add host Compose graphics tests for populated Home and file-backed ledger startup/Activity recreation. Compile, 28 tests and lint passed. Use original development keystore via `ANDROID_USER_HOME=/workspace/tools/android-user`; certificate match with published 0.1.0 verified before publishing update. Milestone 2 attachment is context, not scope for this maintenance patch.

- UI-only 0.2.0 (code 3): no transfers/cards/groups/cloud features. Preserve financial tables and calculations. Room v2 adds two independent preference columns, tested from published v1 schema. Custom line vectors/no icon/font SDK; 60dp nominal bottom chrome/20dp logo. Explicit no-transition NavHost and opaque pages resolve default crossfade ghosting. Static skeletons only for genuine loading; preference successes use local selected/saving/haptic feedback to avoid snackbar-covered settings rows. Financial Undo/error snackbars retained. 35 host tests and lint pass, signing identity/16KiB alignment verified. Host PNGs use synthetic test data and do not certify physical-device rendering.

## 0.3.0 custom controls and configurable navigation

Use Foundation inputs/buttons and custom dialog/popup content, retaining native Compose accessibility and Material modal window behavior internally. Share compact pressed/disabled/loading/validation styles rather than adding a UI library. Keep the approved dashboard structure.

Model screen slots separately from center quick actions. Four unique implemented destinations include Settings, ensuring permanent Settings access; replacement swaps occupied positions. Defaults remain explicit when future destinations register. Contextual creation access appears when the center does not create transactions. Store only a validated slot string in preferences with explicit schema 2→3 migration; financial tables are unchanged.

Forms retain existing ViewModels, exact money rules, operation identities and single-flight guards. Save/delete readiness waits for required loaded data. Host verification: build and 49 tests passed, lint has no errors and 14 dependency advisories. Physical acceptance remains pending; no emulator or new finance module.

## Gift wallet scope and accounting

Gift value is separate from account balances and expense/income aggregates. Purchased cards do not silently create an expense; users may record the cash purchase once in the ledger. Received/promotional cards do not create earned income. Redemption is a reduction of the gift asset only; refunds/Undo restore the latest redeemed amount and preserve history without bank postings. A cash refund requires a separate real ledger entry. Discount codes have no monetary balance; single-use vouchers require full redemption. Partial redemption is off until the user confirms merchant support. Unknown balance remains null and excluded from known totals.

Use bundled ML Kit Latin OCR and barcode models (verified Maven versions 16.0.1 / 17.3.0), native system image picker and camera capture contract; no Google Play Services model download or hosted processing. Camera takes a frame through the installed camera app rather than adding CameraX. Ambiguous currencies/dates and unlabeled codes stay blank. Always confirm and then explicitly Save. The automatic-confirmation preference controls opening the review sheet, never permission to save automatically. Direct SMS reading is not eligible/implemented and no SMS permission is requested.

Gift secrets use standard AES-256-GCM with random IVs, row-ID AAD and non-exportable Android Keystore keys. A separate Keystore HMAC key fingerprints normalized codes to avoid plaintext code indexes. Merchant/balance/date metadata and the existing ledger remain unencrypted app-private Room. Device-bound keys are not portable backup. Backup remains disabled; key invalidation fails visibly without resetting keys/data. Future recovery must decrypt and re-encrypt through a versioned, separately portable backup design. No app-lock or full-database-encryption claim.

WorkManager handles offline deferrable reminders at local 09:00; Android may delay them. Default intervals 30/7/1/0 days. Reconcile on mutations/resume/daily; jobs recheck revision, expiry, preferences and status. Notification text excludes even merchant/balance. Stable per-card/expiry/offset tags and persisted receipts prevent repeated alerts; missed intermediate reminders are skipped, an overdue expiry can appear once. No exact-alarm permission.


## 0.5.0 decisions

Keep offline bundled ML Kit, bounded EXIF-aware image processing and generic context parsing; never fetch extracted URLs. Use mandatory review and atomic inbox approval. Default SMS access stays absent pending policy review; approved builds are receive-only/consent gated. Use standard PBKDF2 and Keystore AES-GCM plus native BiometricPrompt, fail closed on lost security state; no reset bypass. App lock is not ledger encryption. See [tradeoffs/recovery/validation](SMART_SECURITY.md).
