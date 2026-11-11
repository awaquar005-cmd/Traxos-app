# Progress

## Reopen crash hotfix — 0.1.1-dev

User reports the app closes when reopened after saving data. Confirmed in a native-graphics Robolectric Compose regression: `IllegalArgumentException: Key "INR" was already used`. Home reused the raw currency string in separate summary and balance sections of one LazyColumn. It did not occur in an empty first-launch state.

Fix uses section-scoped stable keys (`summary:`, `balance:`, `recent:`). No financial mutation, data reset, schema migration, or new feature is involved. Version code advances from 1 to 2; package ID and development signing identity are preserved for an in-place update. Existing records must not be deleted to work around the crash.

Validation: before-fix UI regression failed as expected. **0.1.1 build passed; all 28 tests passed (zero failures/skips); lint has zero errors.** The formerly failing populated-Home UI test now passes. APK metadata and matching original/update signing certificates verified; 16 KiB ZIP alignment passed. Package `app.traxos.finance`, version code 2, schema v1 unchanged. Additional integration test writes ledger data, closes the database, launches the real MainActivity from persisted data, recreates the Activity and checks the saved record/journal remain unchanged. This is a host test, not physical-device or actual OS process-death certification.

The pasted Milestone 2 brief was read as context. Navigation redesign/transfers are not implemented by this crash-only patch.

Patch APK: `downloads/TRAXOS-0.1.1-dev.apk`; published through the existing GitHub repository. Original and update APK SHA-256 checksums are in `downloads/SHA256SUMS.txt`. Validation evidence is under `docs/validation/reopen/`.

## Actual status

Milestone 1 implementation BUILT and AUTOMATED TESTS PASSED. Physical-phone acceptance is pending. TRAXOS is not complete or commercially ready.

## Implemented source

- Full 1,164-line V3 read before code; preserved source; 333-block requirement inventory, priority/milestone/dependency/acceptance roadmap, architecture and decisions.
- Native Kotlin/Compose/Material3 single app module; domain/data/UI packages; manual DI; Navigation Compose; lifecycle ViewModels/coroutines; Room/Paging 3. No cloud SDK, analytics, paid API, internet runtime permission or emulator.
- Home month expense/income summaries, per-currency balances, six recent transactions, real empty states and honest on-device-only status.
- Expense/income create/edit/delete, account/category/date/amount/note validation, tombstone snackbar Undo. No dummy financial records; only seven seeded category labels.
- Cash/bank create with currency and known opening balance/date; rename and archive/restore. Archives retain all history/balances and reject new entries.
- Persisted System/Light/Dark; deep-navy/cobalt palettes, explicit typography/spacing/shapes, system-bar contrast, transparent provisional SVG/native VectorDrawable.
- Atomic financial/account + immutable journal writes, operation-ID primary key, repository mutex, UI single-flight gate, stale revision checks, soft deletion/restoration events and SHA-256 payload hashes (not encryption).
- Room-indexed paging (40-row target), 300ms debounced escaped-literal search, SQL aggregates, saved form inputs and operation ID, Back discard protection, IME-aware scrollable forms, untruncated amounts and haptic/snackbar feedback.
- Exported Room version-1 schema committed; no destructive migration fallback. No released prior schema exists; future changes require forward migration plus tests.

## Executed validation — original 0.1.0 build

Final wrapper command (cloud-local environment prefix and test init configure proxy/CA/writable user directories):

```sh
JAVA_HOME=/workspace/tools/jdk/jdk-21.0.13+9 ANDROID_USER_HOME=/workspace/tools/android-user ANDROID_HOME=/workspace/tools/android-sdk GRADLE_USER_HOME=/workspace/tools/gradle-home ./gradlew -Duser.home=/workspace/tools/java-home -I /workspace/tools/cloud-tests.init.gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

- **BUILD SUCCESSFUL**: debug APK generated, Kotlin/Java/resource/KSP compilation passed.
- **26 tests passed, 0 failures, 0 skipped**: eight exact-money tests, fifteen Robolectric Room/repository tests, three ViewModel tests.
- Repository tests execute real Room/SQLite on host Android API 28 framework: expense/income balances, atomic journal, ten concurrent repeated operations, reopening persistence/replay, edit/delete/Undo balances, stale revisions, injected journal failure rolls back record, category validation, archive enforcement, currency isolation, currency-change rejection, preferences/notes after reopen, dates before opening rejected, category-only seed and escaped literal paging search.
- ViewModel tests: ten immediate Save callbacks produce one row/one event, invalid amount preserves draft/writes nothing, recreated ViewModel retains saved draft and operation ID. This tests SavedStateHandle retention, not full OS process-death UI restoration.
- Lint passed with **0 errors, 14 version-update advisories**. Versions intentionally pinned to a verified compatible stable set; newer available versions are not silently adopted. Dependency vulnerability/security audit remains a release task.
- Gradle distribution matched official published SHA-256. Selected dependencies resolved from Google Maven/Maven Central. Full Temurin JDK 21.0.13+9 downloaded from official Adoptium release and SHA-256 checked; original system Java lacked javac launcher, so it could not satisfy Gradle's compiler capability check.
- APK signature verifies (debug v2 signing), 16 KiB ZIP page alignment check passed. Metadata: application ID `app.traxos.finance`, minSdk 26, target/compile 36, version `0.1.0-dev` (1). No Internet/SMS/microphone/contacts/notification runtime permission. AndroidX adds its signature-protected non-exported-receiver permission.
- No physical-device tests and no emulator used. Local adb discovery attempted but this cloud adb cannot create its default `/home/agent/.android` directory; no USB phone validation is claimed. Run adb on the user's laptop.
- Source formatted with standalone ktfmt 0.54, not included in the APK or build dependencies. Git whitespace check passed with explicit Markdown hard-break / Windows wrapper attributes.

## Current limitations / known unverified behavior

- UI navigation, real process death/rotation, IME behavior, 320–430dp, dark/light visual audit, 200% text, TalkBack, real restart persistence and performance require physical-device checks in DEVICE_QA.md. No app screenshots or measured latency/jank claims.
- Room is app-private plaintext in M1. PIN, biometrics, Keystore database encryption and portable recovery are not implemented; Settings says so. Backup excluded from OS cloud/device transfer rules. FLAG_SECURE protects captures/task previews but is not encryption and needs accessibility/privacy policy review in M3.
- Delete recovery is snackbar Undo; persistent Recently deleted is M2. Date uses ISO text entry. No opening-balance correction flow, card/transfer/refund/debt/recurring/group/savings/budget workflows yet.
- INR/USD/EUR/JPY/KWD supported; currency never auto-converted or added across currencies. Limits: 99,999,999,999 minor units per amount, 1 million transaction rows, 1,000 accounts; account names 80/note 500 characters. Large-history stress testing pending.
- Amounts saved before account opening date are rejected. Opening balances nonnegative in M1; future liabilities/overdraft opening corrections need explicit semantics.
- Transparent logo SVG is a provisional five-square reconstruction, not a verified exact raster trace: inline attachment was nearly black and no image file was mounted.
- Source successfully pushed to the user-created https://github.com/awaquar005-cmd/Traxos-app repository. Release asset upload was rejected with HTTP 401; the same verified APK is provided in `downloads/` with its SHA-256 checksum and direct download instructions.

## Resource use and local setup

Tools/caches are under `/workspace/tools`, not system-wide. Android platform 36, Build Tools 36.0.0, platform-tools and full JDK present. Gradle heap 1.5 GB/two workers/no parallel project builds, Kotlin in process, one 512 MB test fork, no emulator. Observed build + test processes stayed around 2.5 GB combined RSS in this cloud run; not a laptop performance guarantee. Run one build at a time on an 8 GB laptop.

Internet was temporarily disabled mid-setup; user enabled it and fresh access was verified before successful builds. No current network blocker remains.

## Next tasks / remaining milestones

1. Download debug APK and source; install using `adb install -r` on a USB-authorized physical phone. Execute DEVICE_QA.md before calling device acceptance complete. README contains exact build/install commands; never uninstall as an upgrade strategy.
2. M2: paired-posting transfer/card/refund/debt accounting, correction/recently-deleted workflows, filters, goals/budgets/reports.
3. M3: database encryption + app lock, encrypted SAF export, staged verified restore and real migration tests; required before public distribution. Portable recovery is the highest next durability task after physical M1 acceptance.
4. M4: recurrence, local WorkManager/reminder execution, consent-based smart import, deterministic offline voice and shortcuts.
5. M5: optional encrypted user-owned Google Drive snapshots; minimal Firebase only for shared groups; explicit conflict-aware multi-device sync if selected.
6. M6: verified premium entitlements, update/version controls, signed release channel and separate admin operations.
7. M7: applicable V3 fifty release cases, OEM/background/permission/performance/restore/accessibility/security/policy gates.
