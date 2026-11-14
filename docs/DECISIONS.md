# 0.18.0 decisions

- Split categories use child allocations rather than child financial transactions, preserving account balances, idempotency and Undo. Shared/loan/plan ownership remains exclusive.
- Receipt bodies use an independent existing Keystore cipher and persistent encrypted drafts. Media limits protect RAM/archive size. Metadata is not represented as fully encrypted. External viewing is opt-in and cache grants are revocable.
- Previous-period/year/month comparisons and parent-amount filters are explicit; currencies remain isolated and zero/negative baselines have no misleading percent.
- Native Android surfaces reuse reviewed forms behind app lock; they carry no financial payload or secrets. No additional dependency or broad permission. Presets/rules are portable; device surface preferences and unfinished drafts are not.

# 0.17.0 decisions

- Manual FX estimates never alter original balances, dates or currency totals. No network pricing, silent rate chaining or implicit conversion. Dated rates are backed up; display policies are device-local.
- Typed JSON encodes exact integer strings and blobs with explicit types, whole financial relationships and optional portable gift payloads. Vault/PIN/login/SMS remain excluded. It is not an importer or encrypted recovery substitute.
- Native PDF/CSV scoped reports disclose plaintext. PDF refuses over5,000 entries to bound phone memory rather than silently truncate. Streaming JSON/CSV remain available for full histories.
- Local profile pictures use selected URI, bounded downsampling/EXIF correction, metadata stripping and atomic replacement. No photo-upload service or broad photo permission.
- Accessibility augments system font scaling and applies semantic contrast/haptic/lock-motion preferences. Formatting locales are real; unimplemented interface translations are not advertised. Launcher choices retain the actual mark and name.
- Support is offline guidance plus explicit safe diagnostic sharing/public issue link. No unconfigured help server, automatic message or fake store rating.

# 0.16.0 decisions

- Keep loan principal distinct from income/spending; match rather than repost existing bank records, with explicit release and linked Undo. Never initiate bank payments.
- Contact picker instead of broad contacts permission; explicit editable WhatsApp preview instead of automatic messaging.
- Salary anchors clamp per month, fixed periods are inclusive; exact integer threshold arithmetic avoids overflow.
- Goal earmarks do not change account balances; actual transfers use existing paired writer. Aggregate reservations include archived goals and never silently disappear. Later spending shows underfunding.
- Reuse WorkManager delivery/quiet hours/receipt infrastructure; reminders are best effort, no exact alarm. General Room encryption is not claimed.

# 0.15.0 decisions

- Owner explicitly chose local groups while live invitations/collaboration await setup; never simulate acceptance, current shared balances or backend authorization.
- Recognize self shares across all unreversed local group expenses, including mixed payers; explicitly linked full payments/reimbursements/settlements are suppressed from ordinary reports. Group rows do not post bank transactions.
- Card statements overlap: actual payments after closing reduce each applicable snapshot. Audit allocations do not create money or artificial extra dues. Bank-confirmed revisions retain old figures; do not infer refund/interest/minimum formulas.
- Subscriptions/bills require actual confirmation or matching. Preserve legacy auto-posting recurring rules and warn about overlap. EMI tracks already-booked principal; fees remain explicit expenses.
- Add no SDK; use Room13, existing exact Money/ledger/backup, native permission APIs and private WorkManager reminders.

# 0.14.0 decisions

- IOUs start as explicit standalone tracking of existing debts; no implicit expense/income or duplicate bank writer. Existing card liabilities stay ledger-derived. Bank-linked lending and group integration require a later accounting contract.
- Account setup remains honest: missing Firebase opens an Import sheet on Google tap; it cannot bypass Google/Firebase configuration.
- Spacing is opt-in for instruction/form groups, preserving dense Settings rows and48dp control targets.
- Room12 is additive; portable archives read11/12 and validate debt histories before restore.

## 0.13.0 — real auth boundary and independent Drive

Use Credential Manager for Google authentication, Google AuthorizationClient for narrow drive.file consent, Firebase's HTTPS Authentication APIs for requested email/password/provider linking, and existing local storage for finance. No Firebase SDK/Analytics/Firestore/Supabase module is introduced. Owner-supplied Firebase configuration is required; do not invent keys or simulate sign-in. Drive can authorize/bind its actual owner independently of Firebase, using the existing Android OAuth registration, so the account-setup prerequisite does not disable local-user Drive backups. A Drive permission ID is not a Firebase UID/Google ID-token subject. Preserve one permanent local owner binding across sign-out; prevent provider changes/automatic cross-account merging. Keep tokens encrypted, passwords ephemeral, vault/cloud auth excluded from portable backups. Native sign-in needs a Web client ID; the owner has not supplied one and the ambiguous “don't” reply was not treated as permission to create credentials.

Use resumable Drive uploads with reserved IDs and durable encrypted staging; verify by remote read-back, not upload acknowledgment. Probe SAF before reporting a selected destination; do not request broad storage access or bypass OS root-folder restrictions. Do not upgrade Kotlin to use incompatible Googleid metadata; verified1.1.1 remains compatible with2.1.20.

## 0.12.0 — local snapshots before cloud transport

Use Android SAF and standard JCA to keep local recovery independent of OAuth/backend availability and avoid broad storage permission/new dependencies. Preserve current data and app PIN/vault: restore is additive and aborts conflicts, not replacement/destructive import. Exclude the entire card vault by the preservation contract; portable gift secrets use the password envelope and new device keys. Keep device preferences/consents outside the archive. Exact schema compatibility and explicit bounded imports are safer than interpreting unknown financial formats. CSV is clearly labelled plaintext/report-only. See LOCAL_RECOVERY.md for limits and recovery tradeoffs.

Google public project/client configuration is now supplied and the Android package/SHA-1 registration is user-confirmed. Record setup identifiers without pretending a successful native Drive session. Supabase is unnecessary for personal Drive snapshots and can remain separate from a future membership-authorized shared expense backend.

# Decisions

## 0.11.0 V8 authority and service gates

Current user overrides preserve the hero and encrypted CVV despite contrary V8 wording. Keep configurable center actions; hold/release voice applies when the center action is Add, with Home/More Voice always accessible. Restrict real speech to API31+ on-device support, use typed fallback otherwise, and never auto-save. Prefer Supabase per user direction; Google/Supabase projects do not exist yet, so show truthful setup guidance instead of fake login/sync. Retain existing local features absent from HTML. Prototype colors are adapted for native accessibility; light muted text is slightly darker for contrast.

## 0.10.0 recurring defaults

Default new schedules to paused until explicit automatic-posting consent. Month-end anchoring and100-item resumable batches avoid skipped days/large blocking work. Do not infer bank payment confirmation or SMS matching from a schedule. Keep recurring transfers/card settlements out of this expense/income feature. Clipboard marking/clearing is best-effort and must not remove another clip. Reserve a future long-press callback without implementing fake recording.

## 0.9.0 Personal and compact baseline

Use the previous appearance as final, preserve experiment separately, build only Personal. Keep compact visual controls inside accessible touch regions. Reuse saved MERCHANT catalog in current and future entry forms. Keep optional encrypted card references separate from financial postings; no payments or inferred credit limits. Apply original→remaining fallback only to new unredeemed gifts. Never infer an absent gift amount.

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


## 0.6.0 decisions

Remove OCR entirely at the user’s request while retaining independent offline barcodes and encrypted attachments. Keep legacy gift preference column for barcode compatibility instead of destructive migration. Use catalog IDs/archival and duplicate validation, with immutable account kind/currency/opening semantics. Support basic card purchase/refund/repayment ledger correctness, not invented credit-limit/statement values. Separate personal/play source sets and manifests, rather than treating consent as Play policy approval or leaving a disabled receiver in Play. RECEIVE_SMS remains installer-restricted; no bypass/default-handler workaround. Keep native security prompt trusted, with one automatic request per lock episode and an explicit retry after cancellation. Fixed Unlock footer handles small screens/IME; no delayed welcome screen. [Details and evidence](UX_SMART_ENTRY.md).

## 0.7 — separate review UI from live functionality

The latest user request authorizes sample UI for the complete V3 roadmap. An explicit isolated review gallery is safer and clearer than silently replacing the live app with fictitious financial data. Reuse branded controls and layout families across 100 linked page definitions, expose state variants, keep the sample boundary visible and avoid repositories/services in the preview. This is design-review coverage, not completion of the roadmap.

Implement small requested real improvements: actual persisted appearance options, deterministic roots and exact owned transfers. Same-currency paired records preserve net worth and never alter income/spending totals; card repayment remains a distinct flow. Do not infer an own transfer from generic bank transfer wording. Retain removal of OCR per the prior explicit request. Keep existing vector provenance honest rather than claim a newly traced unavailable original.

## 0.8 — finish low-cost ledger tools before larger modules

Promote filtering, monthly reports and deleted-record recovery from the roadmap to real end-to-end offline features. Reuse Room paging/aggregation and the existing atomic journal instead of adding analytics/chart/sync libraries or new money tables. Original currencies remain separate; card refunds affect their posted month and may produce negative net spending. Do not call this bank cash flow or infer original refund links. Recovery keeps tombstones indefinitely because the shipped schema has no deletion timestamp; restoring paired history is atomic and may change current balances beyond original source availability. No automatic purge or fake retention setting. Persist only global sorting; keep filters/month as task state. The user refined the styling request: half-dp decorative outlines on buttons only, no outline on the logo and no extra frames on amount/search cards. Keep readable contrast for labels/pressed states, compact spacing and accessible touch targets. Android12 splash uses a dedicated safe-area vector with fixed outer padding separate from the animated inner group; legacy splash geometry remains unchanged.
