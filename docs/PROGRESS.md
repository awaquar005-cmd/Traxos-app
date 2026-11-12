# Progress

## 0.10.0 recurring and compact interactions

**BUILT: 180 Personal host tests passed, zero failures/errors/skips. Lint passed: 0 errors, 21 existing warnings and one non-blocking primitive-state allocation hint.** Same signing identity, 16KiB ZIP/64-bit ELF alignment verified. No Play build or emulator. [Actual validation](validation/recurring-compact/README.md). Phone frame timing/IME/OEM and background acceptance remain unverified.

Accounts → Recurring: create daily/weekly/monthly income or expenses, choose account/category, exact amount, description and next date. Automatic posting is opt-in per schedule; paused schedules create nothing. Edit, pause/resume and delete are functional. Deleting schedules never deletes posted transactions. Month-end schedules preserve the original day (Jan31→Feb28/29→Mar31). New/edited next dates must be today/future, within10 years. Resuming an overdue schedule catches up missed dates. Credit cards allow purchases, not recurring income, repayments or transfers. No currency conversion or bank-payment verification is implied. Avoid importing the same payment again from SMS; cross-source matches are not inferred.

Room schema10 adds only recurring_rules with account/category foreign keys and due-date indexes, using explicit9→10 migration. WorkManager checks offline daily and on app foreground, at approximate OS-controlled times, with100-occurrence batches and retry. Force-stop delays work until reopening. Deterministic occurrence IDs and one atomic transaction for ledger posting/schedule advancement prevent duplicate retry writes. A private ledger repository inside this transaction avoids UI mutex/database lock inversion. Validation failures retain due dates and show errors; fix the references and edit or pause/resume to retry. No failed operation is silently converted into income/expense. Rules can be paused independently; there is no fake global setting.

Accounts owns Transfer as a dialog; no standalone route or More entry. Paired accounting and snackbar Undo remain. Account/card cards are smaller; report income/net spending share a row; selectors use one compact line; segmented controls retain48dp accessible targets. Confirmations have side-by-side actions. Instruction notices use smaller text, a tinted surface and a fine accent border. Dashboard source, logo assets and watermark are unchanged.

Form surfaces now have a stable bounded frame during loading; editors use a scrolling body and fixed footer. Native dialog window animations are disabled. Selectors and confirmations no longer forcibly hide the parent keyboard/clear focus as their window appears. These address identifiable layout/window causes, but no phone FPS/jank certification is claimed. Native host tests do not replace physical OEM/IME/TalkBack verification.

Card-detail editors have separate Copy number/CVV/expiry actions beside their inputs. No account-list decryption or combined credential copying. The clipboard is marked sensitive; a60-second timer attempts to clear only the same owned clip, never later copied content. Android background restrictions/process death can prevent clearing; do not rely on the timer as guaranteed erasure. Copy failure has explicit feedback without secrets. Keystore/PIN portability and general ledger-encryption limitations remain.

The centre Plus control exposes an optional future long-press callback while preserving normal taps. Recording is not implemented; no microphone permission or misleading recording control.

Build only Personal: `./gradlew :app:assemblePersonalDebug :app:testPersonalDebugUnitTest :app:lintPersonalDebug`. Install `adb install -r downloads/TRAXOS-0.10.0-personal.apk` as Update; never uninstall/clear storage. Same signing key/package, code13. See validation/recurring-compact for actual checks. No emulator.


## 0.9.2 Personal — compact forms and card references

**BUILT: 169 Personal host tests passed; zero failures/errors/skips. Lint passed: 0 errors / 21 existing warnings.** Update signing identity and 16KiB ZIP/64-bit ELF alignment verified. No Play APK or emulator. The Bank-row account-title tap target is now explicit, preventing the nested card-details action from intercepting the account-name click. [Actual validation](validation/compact-forms/README.md).

Activity's SMS shortcut is a compact header action, absent at zero pending suggestions. No separate full-width SMS row or empty gap. Existing Smart Add approvals are unchanged.

Bank accounts can hold optional debit-card references; credit-card accounts retain liability accounting. New Bank/Credit Card accounts offer “Add card number, CVV & expiry after saving”. It saves the financial account first, then opens the encrypted/masked editor; cancelling details leaves the account intact. Existing Accounts rows and edit forms expose the same editor. No credentials enter SavedStateHandle or the audit journal. No new account type, credit-limit semantics or payment processing. Existing Keystore recovery limits apply.

Account, transaction, gift and card-detail entry use a bounded centered surface, compact spacing, scrollable body and pinned Save. Pickers/review sheets use a centered Dialog without the old ModalBottomSheet slide/settle animation. Safe hit targets, Back/dismiss and draft state remain. This removes a known animated sheet transition, but does not establish phone frame-time/jank results; actual IME and OEM form-opening smoothness require phone validation.

Activity/recent rows show a clearly labelled Recorded timestamp from the existing indexed operation journal where available, formatted in the record's time zone. This is entry-recording time, not a fabricated bank payment time: old ledger rows contain a transaction date only. Rows without a timestamp omit it. No schema migration or timestamp backfill was introduced. Actual user-editable payment-time capture remains a future data-model change.

Only Personal built. Update without uninstalling. Same package/key, version code12, Room schema9 unchanged. See validation/compact-forms for actual build/test/lint results and device limitations.


## 0.9.1 Personal — SMS quick review and hero correction

**BUILT: 168 Personal host tests passed, zero failures/errors/skips; lint 0 errors / 21 existing warnings.** Signature matches earlier releases; 16KiB ZIP/64-bit ELF alignment verified. Only Personal built. [Actual validation](validation/sms-quick-review/README.md). Physical-device SMS/UI acceptance remains pending.

Only the dashboard watermark hero's padding/gaps return to its previous proportions; other compact UI stays unchanged. Accounts now exposes Card number, expiry & CVV directly on credit-card rows. Activity has a live pending-count SMS Smart Add entry. Smart Add lists direction, amount/currency, date, counterparty, issuer bank, masked account hint, reference and proposed save account/category. Approve and Reject act directly, with busy/idempotent protection; only Edit opens the review sheet. If required fields/defaults are missing, use Edit; ambiguous transfers/settlements still cannot become ordinary expenses. No record is saved without an approval tap.

SMS parser supports explicit debited-by/credited-with INR amounts without a currency prefix, compact named dates, RRN/Refno including wrapped digits, merchant boundaries before references and trailing bank names. Issuer and counterparty are separate. Missing fields stay blank. A name alone does not prove ownership of both accounts: use the existing Transfer flow for self transfers. No hardcoded personal identity or new SMS permissions. Old encrypted suggestions decode with an empty issuer field; no Room migration is required. Previously captured suggestions retain their original extraction, so Edit old rows if needed. New messages use the improved parser.


## Compact Personal release — 0.9.0 (code10)

**BUILT: 166 Personal host tests passed; lint passed with 0 errors / 21 existing warnings.** APK signature matches the previous release; 16KiB ZIP/64-bit ELF alignment and permission checks passed. No Play APK built.

Previous / Unslop OFF styling is final. Dashboard/card/page gaps and Activity controls reduced without shrinking 48dp hit targets; duplicate account summary removed. Action buttons and logos are borderless. Generic gift Pay Code/alphanumeric PIN extraction, safe new-card remaining-value default and promoted saved merchant picker implemented. Optional encrypted/masked credit-card number/CVV/expiry editor connects to existing liability accounts and balances. Schema9 adds only the reference table through explicit 8→9 migration. No new dependency or cloud access. Personal only. [Behavior/security/access](COMPACT_PERSONAL.md), [actual validation](validation/compact-cards/README.md). Physical phone acceptance remains pending.

## Complete offline ledger tools + splash fix — 0.8.0-offline-tools (code9)

**BUILT AND HOST VALIDATION PASSED.** Three real end-to-end features: Activity inclusive date/account/category/currency filters with preserved sheet drafts and saved newest/oldest ordering; per-currency calendar-month category reports with income/purchases/refunds, reactive totals and exact Activity drill-through/Back; paged deleted transactions with ordinary/paired restore, confirmation, stale-revision protection and Undo. Archived references stay available. No automatic purge or fictitious deletion timestamps; linked movements appear once and restore together. Reports exclude transfers/card repayments/opening balances/gift redemption/tombstones; posted-month refunds can produce negative net spending. New tools are in More/Activity's menu/Settings, and Reports is a real optional nav destination. [Behavior and limits](OFFLINE_TOOLS.md), [plan](OFFLINE_TOOLS_PLAN.md).

Follow-up UI feedback incorporated: **no outline on the logo**; minimal half-dp button borders, no extra frames on amount/search cards, existing input focus/error styling. Compact controls retain48dp touch targets and branded pressed/disabled/error states. More preserves the original everyday order, with new ledger tools grouped separately. Settings puts currency/defaults before management/recovery. These hierarchy changes resolve regressions where Gift Cards/currency were pushed outside the initial viewport. The isolated100-page gallery remains sample-only; larger future modules are not implemented by this release.

Android12+ splash clipping was caused by a full-bleed vector entering the system mask. A dedicated transparent safe-area vector has a fixed outer padding group and an independently animated inner mark. All five squares fit at approximately80dp visible width; the pre12 64dp mark remains intact. Native host pixel comparison/component/reference tests pass. This is geometry validation, **not a phone launch result**. Original logo geometry/provenance remains in assets/brand/README.md; no new original image trace is claimed.

Validation: both APK builds, both full test suites and both lint tasks passed. **Personal159 tests / Play157 tests, zero failures/errors/skips.** The157 shared cases run in each flavor; two Personal-only cases cover SMS gates. **Lint zero errors: Personal21 warnings / Play20 warnings** (19 dependency/tool advisories, one VectorRaster advisory for the288dp native splash canvas, and Personal's existing UseKtx suggestion). The splash canvas follows the native system format with a small visible mark; no raster logo is embedded. [Exact suites, hashes, permissions, logs and synthetic renders](validation/offline-tools/README.md).

New tests cover combined/literal/paged filters, stable sorting, leap/year boundaries, empty months, negative refunds and multiple currencies, archived history, report drill/Back, ordinary/card-payment/transfer restore, repeated operations, stale/corrupt-pair rejection, Undo/restart, Settings persistence and published-schema7→8 preservation of ledger/encrypted gifts/inbox/preferences. A50,000-row SQLite fixture loads distinct40-row pages and aggregates without UI-side whole-history filtering; this is not a phone scroll benchmark. Filter UI tests run at320dp, including nested picker preservation, visible validation, cancel/reset/apply. Existing finance/gift/security/navigation/theme suites pass.

Schema8 adds only `preferences.activitySort`; all non-preference entities are identical to7 and the full forward migration chain remains. Signing identity matches0.7; package/version code9 and16KiB ZIP/64-bit ELF alignment verify. Personal retains optional incoming RECEIVE_SMS; Play excludes SMS permissions and ingestion classes. Both exclude internet/historical SMS/camera/media-read permissions and OCR. No library/permission added, no emulator; existing1.5GB Gradle heap/two workers/single512MB test fork retained.

APKs: `downloads/TRAXOS-0.8.0-personal.apk` (36,662,982 bytes), `downloads/TRAXOS-0.8.0-play.apk` (36,608,814 bytes); copies in `/workspace/releases/`. Install as **Update**, never uninstall/clear storage. Checksums: downloads/SHA256SUMS.txt.

Pending physical acceptance: the reported phone's cold-launch splash/OEM mask, keyboard/TalkBack/large-font ergonomics, native Keystore/biometrics/device credentials, installer-restricted SMS/broadcasts and process/background behavior. Existing unencrypted ledger/metadata and device-bound secret recovery limits remain. Export/portable backup, budgets/goals/debts/groups/billing and other roadmap services remain planned. Next: phone acceptance of this complete batch before selecting the next feature.

Earlier release sections are historical snapshots.

## UI hierarchy and full-product review — 0.7.0-ui-review (code 8)

**BUILT AND HOST VALIDATION PASSED.** Read the complete newly attached 1,164-line V3 document. Reused the existing transparent five-square native mark directly, removed its unlock tile and centered the unlock body. Sectioned Settings, one-line accessible theme choices, five persisted accent palettes, distinct tool icons, secondary button outlines, grouped More and navigation live preview/presets/reorder/assign/restore are implemented. Home explicitly returns to the dashboard without restoring saved child stacks; fresh Settings entry and preview Close/Back remain bounded. Transitions stay disabled and routes opaque.

Gift text amount detection handles labeled values, currency prefixes/suffixes, order/tax context and ambiguous candidates. Missing currency remains subject to review with the app default suggested. Encoded QR/barcode text can use the parser; **OCR remains removed**. Explicit own-transfer messages require reviewed source/destination; failed/declined/pending/reversed messages remain review-only. Real same-currency cash/bank/wallet transfers atomically write two linked legs, preserve net worth, stay outside expense/income totals and support pair delete/Undo/reopen. Activity has separate filters. Card repayments retain their existing distinct accounting. Cross-currency conversion/fees/reconciliation are not implemented.

**100 linked UI preview pages** cover the remaining V3 user-facing hierarchy with shared custom components, forms, confirmation, feedback and state variants. Open **More → Explore all screen flows** or **Settings → Full UI review**. The persistent sample-only banner and isolated renderer prevent future finance/security/cloud/billing/export/message actions from being presented as real. These are UI-review prototypes, not implementation of all V3 features. Future modules remain outside real configurable tab choices. [Coverage and section mapping](UI_REVIEW_COVERAGE.md), [current guide and limits](UI_REVIEW.md).

Validation: both assemble tasks, both complete test suites and both lint tasks passed. **Personal: 140 tests; Play: 138 tests; zero failures/errors/skips.** The 138 shared tests run in both variants; two additional Personal cases cover SMS gates. **Zero lint errors; Personal: 20 warnings (19 version advisories + 1 UseKtx); Play: 19 version advisories.** Added exported schema6→7/reopen preservation, accent persistence/all-palette contrast, paired transfer/idempotency/filter/dedup/rejection rules, gift text candidates, 100-page render/link checks, preview hierarchy/no ledger writes, Home fallback and 200% single-line theme checks. [Exact evidence and synthetic host previews](validation/ui-review/README.md).

Schema7 adds only `preferences.accent`; every non-preference entity is identical to6. Existing forward migrations, app identity and signing certificate are preserved. 16 KiB ZIP/64-bit ELF alignment passed. Personal has incoming RECEIVE_SMS only; Play excludes SMS permissions and receiver/ingestion classes. Both exclude internet, historical SMS, camera/media-read permissions and OCR text recognition classes. No dependency added, no emulator; conservative 1.5 GB Gradle heap/two workers/single 512 MB test fork retained.

APKs: `downloads/TRAXOS-0.7.0-personal.apk` (35,776,261 bytes), `downloads/TRAXOS-0.7.0-play.apk` (35,753,952 bytes); copies in `/workspace/releases/`. Install as **Update**, never uninstall/clear storage. SHA-256 values are in `downloads/SHA256SUMS.txt`.

Unverified: native phone Keystore/biometrics/face/device credentials, installer-restricted SMS and real broadcasts, system splash/recent previews, process death/OEM behavior, camera barcode quality, real IME/TalkBack and comprehensive physical accessibility. PNGs are synthetic host renders. The existing mark geometry remains provisional as documented in `assets/brand/README.md`; no pixel-exact trace from a newly supplied original is claimed. App lock does not encrypt the ledger/metadata; device-bound secrets still have no portable recovery. Next task: install on the phone and review the whole UI, then make screen-specific adjustments before selecting another real feature.

Earlier sections below are historical release snapshots.

## UX, smart entry and security —0.6.0-smart-entry (code 7)

**BUILT AND HOST VALIDATION PASSED.** OCR/model/settings/harness removed; independent QR/barcodes and saved gift attachments retained. Custom income/expense categories and Cash/Bank/Wallet/Credit Card accounts have icons/colors, duplicate validation, archive/history-safe edits and inline creation. Saved merchant/description management, compact optional details, once-per-lock native biometric prompting, a fixed-footer branded PIN keypad/native splash, fresh Settings back stack, compact menus and semantic dark/pressed-state contrast are implemented.

Credit cards use signed debt, explicit expense/refund effects and atomic paired repayment/delete/Undo without duplicate expenses; available credit is never an asset. Schema 5→6 preserves ledger, encrypted gifts/inbox, journal and preferences; the complete forward chain remains. Personal/play flavors isolate SMS code and permissions. Personal requests incoming RECEIVE_SMS after disclosure/consent where OS/installer permits; Play excludes receiver/ingestion and SMS permissions. All suggestions require editable approval. OTPs, unrelated conversations, failed/pending/requested payments and scanned URLs do not become ordinary financial entries.

Validation: assemblePersonalDebug/assemblePlayDebug and both lint tasks passed. **Personal: 128 tests / Play: 126 tests; zero failures/errors/skips. Zero lint errors; personal: 20 warnings (19 version advisories + 1 UseKtx suggestion), Play: 19 version advisories.** Shared tests are run in both variants, not counted as distinct new tests. Signature matches 0.5.0; 16 KiB ZIP/64-bit ELF alignment verified. Actual compiled Play APK contains no SMS receiver/ingestion classes, and neither variant contains the text recognition class. [Evidence and synthetic host previews](validation/ux-refinement/README.md).

APKs: `downloads/TRAXOS-0.6.0-personal.apk` (36,490,717 bytes), `downloads/TRAXOS-0.6.0-play.apk` (36,468,433 bytes); also `/workspace/releases/`. Install as Update, never uninstall/clear storage. [Current behavior, commands and security boundaries](UX_SMART_ENTRY.md).

Physical acceptance remains pending: native biometric/device credential/enrollment and preview behavior, real SMS broadcasts and installer-specific restricted permission, barcode camera quality, OS process death, keyboard/TalkBack/200% fonts/OEM background behavior. No emulator or physical phone was used. Host crypto tests replace only hardware key provisioning with JCE test keys. Ledger/metadata remain app-private plaintext; app lock/device-bound encrypted secrets have no portable recovery. Next task is phone acceptance and fixing measured issues before another feature.

Earlier sections are historical snapshots; their OCR/SMS/lock limitations describe older releases.


## Smart import/security/settings — 0.5.0-smart-security (code 6)

Implemented staged OCR/compact-entry fixes, unified encrypted Smart Add review inbox, offline PIN/native biometric lock and categorized functional Settings. Existing ledger/gift/navigation functionality retained with explicit Room4→5 migration and per-setting atomic updates. See [implementation, security/recovery limits and policy](SMART_SECURITY.md).

**assembleDebug and assembleDebugAndroidTest passed; 113 host tests passed with zero failures/errors/skips; lint passed with zero errors and21 dependency/tool update advisories.** Upgrade signature matches0.4; 16 KiB ZIP/64-bit ELF alignment passed. Standard APK is78,647,907bytes. Final build/test/lint evidence is recorded in [validation/smart-security](validation/smart-security/README.md). APK: `downloads/TRAXOS-0.5.0-smart-security.apk`; install as Update, never uninstall/clear data. This remains a development release. No native OCR accuracy, physical-device or commercial-readiness claim. Standard APK has no SMS access; manual share/paste works. Real failing Amazon image, hardware biometrics/Keystore, native OCR/camera/system notifications/process death and full accessibility acceptance remain pending. Existing ledger/metadata are not encrypted; app lock and device-bound secret encryption have no portable recovery.

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
