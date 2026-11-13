# TRAXOS — V8 Native Android Coding-Agent Contract

**Authority:** User's current instructions + the existing native Android codebase + all prior Traxos native-blueprint documents + cumulative screenshots. The HTML prototype is a **visual and interaction reference**, not a replacement specification or a complete inventory of existing functionality.

## Non-negotiable feature-preservation rule

> **DO NOT DELETE, DISABLE, OMIT, OR REPLACE AN EXISTING TRAXOS FEATURE SOLELY BECAUSE IT IS MISSING FROM A SCREENSHOT, A PROTOTYPE SCREEN, THIS DOCUMENT, OR A SHORT FEATURE LIST.** The screenshots show an incomplete APK under development. Before making changes, inventory all existing screens, navigation destinations, flows, background workers, permissions, local tables, repositories, APIs, settings, and feature flags in the real repository. Preserve all working features and data migrations unless the user explicitly authorizes their removal. Map each existing feature to its place in the redesigned UI. Flag conflicts for review; do not silently drop features.

**The one explicit removal is receipt/document OCR and its text-recognition flow.** This does **not** authorize removing QR scanning, barcode scanning, image capture/selection, image saving, receipt images/attachments, profile photos, screenshots/exports, or gift card images. Keep these independent functions, with no text-recognition dependency.

**Scope of redesign:** Header/nav heights, visual hierarchy, color, component placement, iconography, dialogs, flow structure, accessibility and performance may change. Business logic, security guarantees, financial semantics, persistence, data recovery and existing features must not regress. Do not port HTML storage/security simulations as production services.

## Deliverable reference

- `Traxos_V8_Interactive.html`: V8 visual prototype with sample-data and empty-data workspaces, compact top/bottom navigation, clear central action, mic button, QR/image tools, and a simulated long-press voice workflow.
- `Traxos_Native_Android_Product_Blueprint_v3.md`: cumulative native spec; where it mentions OCR as experimental, the user's subsequent instruction **supersedes OCR** only.
- `Traxos_Features.md` and `Traxos_Architecture.md`: prior feature/screen inventory and native sync/backup recommendations; integrate rather than discard.
- Current native APK screenshots and subsequently provided screenshots: reference for discovered requirements, not an exhaustive list or immutable layout.
- **Actual Android repository at handoff time:** highest fidelity for implemented behavior. Do not assume a feature doesn't exist just because it's absent from a mockup.

## Required end-to-end journey

1. Native splash screen → welcome; initialize **offline local repository immediately**, before network.
2. Onboarding: local-only guest; optional email/Google cloud identity; restore existing backup; onboarding shouldn't require login for manual finances.
3. Profile name/avatar and initial settings; simple tracking vs accounts and balances; PIN setup; eligible Android face/fingerprint via `BiometricPrompt` / device credentials; actual system UI, not fake modal.
4. After unlock/home: logo and **TRAXOS + current page** in compact header; profile image only where meaningful, three-dot menu with active actions; user can reach all tools even when not pinned to bottom dock.
5. Home: spacious total-balance card with **five separate rounded squares forming the T watermark**; visibility toggle, recorded account count, accurate backup/sync status and date; fast search; monthly inflow/outflow; relevant account peek, pending smart review, recent activity, savings/budgets as relevant. The main card is the one deliberately spacious component. No stock backgrounds or meaningless icons.
6. Activity: query, filters, dates/accounts, edit, delete/undo, duplicates, linked card payments/transfers, grouped activity; appropriate **pictogram icons**, never arbitrary first-letter avatars for transactions.
7. Quick Add: tap central Add → amount-first expense/income/transfer with remembered default account/category and expandable optional details. **Press-and-hold central Add → start recording a voice transaction; release → stop, transcribe on device when supported, preview & confirm, then save.** If permission is denied or device cannot recognize locally, offer editable text/manual fallback; never silently upload the recording or use network transcription while promising offline support. Haptic/visual feedback; don't mask financial values in entry fields.
8. Accounts: cash, bank, debit card linked to bank, credit card as liability; opening balance, statement/repayment schedule, card-specific dates, account transfers, edit/archive; correct total exclusions.
9. Card vault: optionally store full PAN and expiry/cardholder **only encrypted in a separate on-device vault**, require device re-auth to reveal/copy, no plaintext logs, screenshots or clipboard leakage; **exclude vault from Google Drive, chosen-folder backup and Traxos cloud sync**. Never collect/store CVV/CVC or ATM PIN. Store last four digits for account identification if consented.
10. Shared groups: online authenticated membership/invitations, equal/exact/percent/shares split, expense payer, partial settlements, offline drafts/pending share, member permissions and reconciliation. Reimbursements are not extra personal income; do not double-book spending.
11. Plans: budgets, savings goals, recurring bills, subscription detection/review, due reminders, debts/lending/borrowing, reports. Don't delete gift cards or their codes, expiry alerts, QR functions, or existing advanced modules.
12. Smart tracking: incoming SMS/UPI payment candidates (permissions and channel dependent), sharing/pasting messages, on-device parsing, deduplication, approvals, opt-outs; voice, launcher shortcuts, widgets/tile and experimental volume-trigger only where policy/device permits.
13. **QR scanning and image saving remain.** Native on-device scanner (e.g. CameraX + a barcode decoder), QR/barcode values and user-initiated scan preview. Capture/select and save images attached to transactions, gifts or profile as appropriate, encrypted at rest and backed up only according to explicit user scope; **no OCR/text recognition**.
14. Three separate data functions: (a) password-encrypted ledger backup in user-owned **Google Drive**, (b) password-encrypted portable backup in **user-chosen phone folder** via Android Storage Access Framework, and (c) Traxos account-linked **backend cloud sync** for personal ledger when enabled and authenticated shared groups. All exclude protected card vault. Distinguish local saved / queued / backing up / backed up / conflict; verify snapshots/restore before replacing or merging.
15. Sync: `Room` is UI/source-of-truth, atomic ledger+outbox, stable op IDs, background `WorkManager` push/pull, backoff, resumable batches, server authorization/RLS and conflict resolution. Trigger automatic sync on permissible network/work events, backend FCM **hint** if configured, manual **Sync now** always available. Never mark an upload complete before real acknowledgment; never erase unsynced entries silently.
16. Settings: profile/photo, appearance/light/dark/system, compact density, accent, accessible sizing, customize nav; categories, merchants, descriptions, accounts, default account/category, currency/date, notifications (bills/debts/expiry/smart/group), app lock/PIN/biometric timeout/private preview, permission dashboard, SMS/UPI choices, quick actions, personal backup, Drive linking, phone folder, cloud account, manual/auto sync, restore, export, privacy/data, gift settings, support/about, and **all additional settings found in repository or future screenshots**.
17. Native UX: compact yet accessible. Top header and bottom dock slightly shorter (~4dp visual reduction, respecting Android inset and ~48dp hit targets). Center Add button stays visibly cobalt in **both themes**. Naked/unboxed icons unless a surface communicates a real state/action. One-word primary action labels where meaning remains clear; proper helper labels for accessibility. Dialog for small choices, full screen only for long editing/critical security flows. 320–430dp, font scale, TalkBack, RTL, reduced motion, offline and rotation tests.

## Sample vs empty data: preview only

The HTML provides two isolated test workspaces: **With data** (fictional seeded transactions/accounts/goals) and **Without data** (blank ledger, blank-state CTAs). A chooser bypasses the HTML's fake login to prevent testing dead ends. It is intentionally **not** an authentication bypass to implement in production. In the native build, use debug-only sample-data fixtures and a clean test profile; production PIN/biometric/cloud authentication must be real and never bypassed by this chooser.

## QA and acceptance checklist

- Preserve every previously functioning feature; report a before/after screen and feature manifest with file/route owners and migration impact. Include unknown-but-implemented features, even if absent from docs.
- No JavaScript/browser-demo PIN or hardcoded cloud credentials copied into the Android release.
- Splash → offline onboarding → profile → optional PIN/biometric → empty ledger → add account → add transaction → see total works with airplane mode.
- Separate debug sample data includes transaction rows and realistic budgets; changing demo mode doesn't destroy the other workspace's entries.
- Tap Add works; long-press Add starts a consented microphone recording; release ends; speech preview never silently posts; fallback works.
- Dashboard search reaches Activity and focuses/query-selects appropriately; icons are meaningful pictograms; dark-mode Add button stays visible; header/dock slightly shorter but clickable.
- QR scan and saved local images work after OCR code is removed; attachments survive process death, app restart and migration.
- Card vault plaintext excluded from backup/export/sync, no CVV retained; require re-auth for reveal and copy; screenshot/cache/log protection.
- Google Drive backup and chosen-folder backup independently verify encrypted round trips and recovery on a second device; last verified time shown; user-selected folder permission loss handled.
- Manual sync after offline edits completes with server acknowledgment; auto sync resumes on reconnect/backend hint where OS allows; duplicate and conflict testing passes; group membership server-enforced.
- No missing settings/features compared with supplied codebase and later screenshot batches; not just happy-path screenshots.

## UX constraints / current non-negotiable design notes

- Dashboard should remain similar to the reference with a big dark-navy balance card and five-square watermark; add search directly below it.
- Compact header: `TRAXOS` over page name, five-square T on main tabs, back arrow on subpages, three-dot overflow, meaningful optional profile picture.
- Bottom nav four destinations around center Add, configurable as already implemented. **Do not shrink usable touch targets** while reducing visual height.
- No 'W', 'A', etc. placeholder icons on transaction rows. Unboxed purposeful SVG/vector icons, not stock icon-tile backgrounds.
- Primary action labels short; mic icon for Voice; press-and-hold Add for voice transaction capture.
- Keep existing account/profile/gift/scanning/image/notification/backup features even if not pictured.

**If a feature is uncertain, preserve it, document it in the audit, and present a migration plan before proposing removal. Absence from a screenshot is not a removal request.**
