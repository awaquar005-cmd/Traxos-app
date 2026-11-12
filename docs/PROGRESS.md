# Progress

## Smart import/security/settings — 0.5.0-smart-security (code 6)

Implemented staged OCR/compact-entry fixes, unified encrypted Smart Add review inbox, offline PIN/native biometric lock and categorized functional Settings. Existing ledger/gift/navigation functionality retained with explicit Room4→5 migration and per-setting atomic updates. See [implementation, security/recovery limits and policy](SMART_SECURITY.md).

**assembleDebug and assembleDebugAndroidTest passed; 113 host tests passed with zero failures/errors/skips; lint passed with zero errors and21 dependency/tool update advisories.** Upgrade signature matches0.4;16KiB ZIP/64-bit ELF alignment passed. Standard APK is78,647,907bytes. Final build/test/lint evidence is recorded in [validation/smart-security](validation/smart-security/README.md). APK: `downloads/TRAXOS-0.5.0-smart-security.apk`; install as Update, never uninstall/clear data. This remains a development release. No native OCR accuracy, physical-device or commercial-readiness claim. Standard APK has no SMS access; manual share/paste works. Real failing Amazon image, hardware biometrics/Keystore, native OCR/camera/system notifications/process death and full accessibility acceptance remain pending. Existing ledger/metadata are not encrypted; app lock and device-bound secret encryption have no portable recovery.

Next task: physical acceptance and genuine redacted image measurement, not another finance module. Earlier sections below are historical snapshots; statements such as “no app lock” describe those older releases.


## Gift wallet — 0.4.0-gift-wallet (code 5)

**BUILT AND AUTOMATED VALIDATION PASSED.** Separate local gift card/voucher tables, manual CRUD/archive/expiry marking, exact stored-value redemption/history/last Undo and refund, Keystore-encrypted secrets/attachments, conservative mandatory-reviewed imports, bundled offline OCR/barcode capture, local reminder settings/workers and optional Gift Cards/More navigation routes. Existing default navigation and ledger records are preserved through explicit schema v3→v4 migration.

Validation: **assembleDebug passed; 78 tests passed (49 original + 29 gift), zero failures/errors/skips; lint passed, zero errors and 16 dependency/tool update advisories**. Integration fixes cover on-demand WorkManager initialization, scrollable import/redemption sheets, Android 8 clipboard compatibility, explicit non-exported initialization provider and loaded preference handling. Test execution/host renders and final packaging evidence are in `docs/validation/gift-wallet/`. Existing v3 entities are structurally unchanged; real migration/restart tests preserve ledger, operation journal and customized navigation. APK signature matches 0.3.0, version code 5 and 16 KiB ZIP/64-bit ELF alignment verified. Merged APK has no internet/SMS/camera/media permissions.

Automatic confirmation opening is a persisted working preference; disabled means detected shares wait for Review, never automatic saving. Originals and remaining value are not conflated. Secret list payloads are omitted from metadata queries; standard AES-GCM/HMAC keys never silently reset around existing data. Full/partial redemption, last Undo and merchant refund history do not mutate cash/bank accounting.

APK: `downloads/TRAXOS-0.4.0-gift-wallet.apk`, also `/workspace/releases/TRAXOS-0.4.0-gift-wallet.apk`; source output `app/build/outputs/apk/debug/app-debug.apk`. Universal APK approximately 74 MiB due to bundled models; no emulator and existing conservative Gradle/test memory settings retained. Install in place; do not uninstall or clear data.

Physical acceptance remains pending: actual hardware Keystore, first-use offline OCR/camera quality, system notification/channel delivery, battery restrictions, real keyboard/TalkBack, process death and full phone workflow checks. Host crypto tests replace only key provisioning with JCE test keys. No SMS access, cloud backup, app lock or whole-database encryption is claimed. Sensitive unsaved drafts remain memory-only. Device-bound secrets have no portable recovery; metadata and the ledger remain unencrypted app-private Room. See `docs/GIFT_WALLET.md` and DEVICE_QA. Next task is physical acceptance of this feature before another module.

## Custom components and navigation — 0.3.0-ui-components (code 4)

**BUILT AND AUTOMATED VALIDATION PASSED.** Finance scope and the approved dashboard structure are unchanged. Shared branded controls now cover confirmation/destructive dialogs, dropdown popups, option/date sheets, success/error/warning/Undo/progress feedback, basic and premium amount inputs, buttons, selection rows, switches and a reusable tested checkbox. No system toast or stock-looking AlertDialog/TextField/Radio/Switch widget is used in application screens. Native Compose/Material surfaces, sheet window behavior and accessibility plumbing remain the internal foundation; no new library was added.

All four outer navigation slots are configurable among implemented Home, Transactions, Accounts and Settings. Picking an already-used screen swaps positions; buttons reorder slots; a custom confirmation restores default tabs. The center quick action is a separate preference and is not reset. Only implemented routes are offered. Future screens require registration and real route implementation. Top Add/Settings icons are removed. A contextual overflow offers transaction creation when the center no longer provides it; Settings remains reachable in the bottom layout. The navigation customization page shows the updated bar immediately.

Schema v3 adds only `preferences.navigationSlots` using explicit 2→3 migration, with the existing 1→2 path retained. Financial tables, integer money, operation IDs, repository single-flight/revision guards and journal behavior remain unchanged. Existing slot, theme, amount-mask and quick-action preferences survive independent updates.

Transaction/account create/edit forms now use reusable custom selectors, date sheet, inputs and a fixed Save footer. Content scrolls above the footer and IME insets are respected. Selection overlays close the keyboard first; Save closes it before validation/navigation. Required account/category selection and loaded revision gate submission/delete controls. Custom discard/delete confirmations retain native Back/dismiss behavior. Financial snackbar Undo remains real and errors preserve the form. Feedback is hidden while editing so an earlier success cannot cover the Save footer.

Final validation: assembleDebug passed; **49 tests passed, zero failures/errors/skips**; lint passed with zero errors and 14 dependency-update advisories. Signing certificate matches the previous APK, version code 4 and 16 KiB ZIP alignment verified. Host light/dark dashboard and narrow form renders inspected. Evidence: `docs/validation/ui-components/`. No emulator or physical-device certification. Real keyboard, TalkBack, large fonts, OEM Back and process death remain device checks. APK: `downloads/TRAXOS-0.3.0-ui-components.apk`, also `/workspace/releases/TRAXOS-0.3.0-ui-components.apk`. Install as an update; do not uninstall or clear storage.

## UI-only pass — 0.2.0-ui-pass (version code 3)

**BUILT, TESTED AND PUBLISHED.** No new finance module. Dashboard/custom compact chrome, neutral/blue surfaces, smaller 20dp mark, five-square hero watermark, per-currency balance hero/visibility toggle, monthly mini summaries, search shortcut, active account rail and recent activity implemented. Lists, empty states, settings and forms use shared components. Center action choices persist in Room and dispatch real existing routes. Header Quick Add stays available when the center is reassigned.

Navigation cause: default Navigation Compose fades drew outgoing/incoming page text together. Enter/exit/pop fades are explicitly disabled and each route is opaque. Static skeletons cover real dashboard/account/preferences/paging/form loading; no artificial delay or shimmer. Preference success snackbars were also found to obstruct lower settings choices; successes now use selected state, a saving indication and haptic. Errors and financial Undo snackbars remain.

Schema 2 changes only two preferences; explicit v1→v2 migration preserves finance tables/journal. Independent preference updates cannot reset each other. Until preferences load, financial totals are masked/held behind genuine loading state. Physical-phone validation is pending; no emulator used.

Final validation: **assembleDebug passed; 35 tests passed, no failures/skips; lint passed with zero errors and 14 dependency-update notices**. All original money/repository/form/startup tests retained. New migration, navigation/settings, skeleton/empty-state and light/dark/narrow-layout tests pass. Host previews reviewed with synthetic test fixture data; they are not phone screenshots. APK signature matches 0.1.1; version code 3 and package ID verified; 16 KiB ZIP alignment passed.

APK: `downloads/TRAXOS-0.2.0-ui-pass.apk` (also `/workspace/releases/TRAXOS-0.2.0-ui-pass.apk`). Source/debug build output: `app/build/outputs/apk/debug/app-debug.apk`. Install in place; do not uninstall/clear storage. Preferences migrate v1→v2; financial tables are unchanged. No API/cloud/permissions added. Code, checksums, rendered previews and exact host evidence are in GitHub; `docs/validation/ui-pass/` contains the report. See DEVICE_QA for remaining physical-device checks.

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
- Exported Room schemas v1/v2/v3 committed; explicit preference-only migration from shipped v1 is tested. No destructive migration fallback; future changes require forward migration plus tests.

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
