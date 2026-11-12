# TRAXOS implementation plan

Source of truth: [complete V3 blueprint](Traxos_Native_Android_Product_Blueprint_v3.md). Read in full before project code was written. User initialization instructions override infrastructure recommendations and milestone scope. This is a development roadmap, not a release-completion statement.

## Component refinement — 0.3.0-ui-components

UI-only scope. Preserve approved dashboard and all M1 finance semantics. Required delivery: reusable fully branded controls/overlays/forms/feedback; four valid unique configurable screen slots with change/swap/reorder/reset; an independent center quick action; no redundant top Add/Settings icons; persistent Settings/creation routes; safe v2→v3 preference migration; native modal Back/focus behavior; protected Save/Undo and responsive IME-aware forms. No new finance, backup, cloud, authentication or paid library.

Acceptance: existing full suite and lint, policy validation, v1/v2 migration, navigation persistence/restoration, custom menu/sheet/dialog/checkbox/switch/notice actions, real create/edit/archive/delete/Undo form flows, rapid-save gate and draft retention when creating an account from a transaction. Light/dark/small-screen host checks plus documented device IME/TalkBack/OEM/process-death validation.

## UI-only milestone — 0.2.0-ui-pass

P0 refinement, with no additional finance module. Deliver custom navy dashboard (per-currency balance hero/watermark, search, inflow/outflow, active account cards, recent ledger), compact custom top/bottom navigation, Settings-configured center action (Add Transaction/Accounts/Transactions/Home), global list/dashboard amount masking, and static genuine-loading skeletons. Remove overlapping NavHost fades. Preserve existing transactions, account management, Undo, search/paging, dirty forms and financial rules.

Dependencies: existing M1 repository; Room v1→v2 preference-only migration; no destructive migration, cloud service, new finance feature or emulator. Acceptance: build + existing tests + migration persistence + early-frame navigation exclusivity + shortcut/masking/theme UI + narrow/200% font checks + loading/empty states. Verify signing continuity with 0.1.1 and publish an in-place APK. Physical phone, IME, TalkBack and OEM checks remain separate gates. Host design previews use explicitly synthetic test data, never production seeds.

## Maintenance gate before Milestone 2

Reopen crash hotfix 0.1.1-dev: reproduce Home's duplicate currency-row keys in a populated-state Compose test, scope keys by section, cover persisted-ledger launch/recreation, run complete build/test/lint, verify matching APK signing identity, publish an in-place update. Schema remains v1 and user data must be preserved. No transfers or navigation redesign are included in this patch.

## Priority and milestone policy

P0 = essential offline finance and durability. P1 = security, recovery, backup, sync, sharing and advanced entry. P2 = premium, developer controls and later enhancements. Original V3 release-blocker priorities remain binding for commercial release even where scheduled after M1. No cloud SDK in M1. All features below are PLANNED unless PROGRESS explicitly records implementation and evidence.

| Milestone | Priority | Scope / dependencies | Acceptance gate |
|---|---|---|---|
| M1 Native vertical slice | P0 | Compose/MVVM, Room, accounts cash/bank, categories, expense/income CRUD, atomic journal, local settings, original brand foundation | Debug APK compiles; exact money/idempotency tests pass; persistence and UI device checks tracked honestly |
| M2 Complete offline ledger | P0 | M1; transfers, opening corrections, card purchases/payment/refund, debts, split entries, savings allocations, budgets, reports, filtering, recently deleted | Conservation and no double counting; currency isolation; auditable corrections; paged 50k-row checks |
| M3 Local protection and portable recovery | P1 (release blocker) | M1 schema; Keystore + maintained DB encryption, biometrics/PIN, sensitive reauth, encrypted SAF backups and staged restore | Wrong key/tamper/low storage/reinstall and schema roundtrip tests; physical phone crypto tests |
| M4 Low-effort entry and reminders | P1 | M2/M3; manual cards/recurrence, schedule rules, WorkManager/local notifications, SMS review/policy, deterministic offline voice, widgets/tile/share | Month-end/leap-year tests, consent/denial/OEM checks, no predicted expenses; dedupe sources |
| M5 Optional owned backup and shared groups | P1 | M3; user-owned encrypted Google Drive snapshots first; explicit account linking and merge; minimal Firebase only for group needs; batched journal protocol if multi-device sync chosen | Idempotent ack/checkpoint, 10k interrupted queue, conflicts retained, authorization, verified restore; no imaginary backup success |
| M6 Commercial controls | P2 (release blockers when charging) | M3/M5; Play Billing verification, signed offline entitlement, update policy, separate admin MFA/RBAC/audit, staged releases | Billing lifecycle and offline data retrieval; server rejects forged entitlement; signing continuity |
| M7 Hardening / selective enhancements | P2 | All applicable; OCR, notification imports, optional hardware experiment, advanced groups, operations/support/legal | Full V3 §39 cases 1–50 and §24 matrix; TalkBack/200%/OEM/performance audits; no unresolved financial/security blockers |

## Conflicts resolved explicitly

- V3 suggests Supabase/hosted personal replication; current user instruction chooses optional user-owned encrypted Google Drive and minimal Firebase later for shared functionality. No existing backend was provided, accessed or migrated. Legacy preservation applies if one is adopted.
- V3 recommends more in its first milestone (search/export/savings); current request defines M1 as accounts, expense/income CRUD, Home, theme. Other items remain M2/M3, not silently removed.
- V3 final navigation Home/Activity/Groups/Plans + top Settings; M1 uses Home/Transactions/Accounts and top Settings; groups/plans arrive when functional.
- V3 release security/encryption is mandatory before public release. M1 private development build initially uses sandboxed Room, explicitly not encrypted. No false protected-data label. M3 must precede commercial distribution.
- Application ID `app.traxos.finance` is a reversible development default until namespace ownership/release signing is confirmed. INR default; account currency is explicit. No automatic FX.
- Logo attachment is visually almost black and no original image file was mounted. A transparent five-rounded-square T reconstruction based on the supplied blueprint will be labeled provisional; exact geometry awaits a usable original.

## Complete requirement traceability

Every non-empty source block is retained below with a stable requirement identifier, source line, assigned priority/milestone and original wording. This deliberately includes examples, constraints, tables, QA cases and research references, avoiding loss of details in a short summary. The complete source file above remains authoritative. Blocks are requirements/reference inventory, not executed instructions. Within a multi-feature block, its milestone is the owning gate; dependencies may be delivered earlier. §39 individual numbered cases remain release gates, including deferred features when enabled.

**R0001 · §0 · P0 · M1–M7 · PLANNED/reference · source L1**

> # TRAXOS — Native Android Product Blueprint

**R0002 · §0 · P0 · M1–M7 · PLANNED/reference · source L3**

> **Version:** 3.0 · **Expanded:** 11 November 2026  
> **Product:** Traxos, a local-first, privacy-conscious expense tracker for Android  
> **Platform decision:** Native Kotlin + Jetpack Compose APK (not Capacitor/PWA)  
> **Source material:** User-uploaded `traxos-docs-audit-prd.zip` and user-supplied five-square T logo  
> **Research basis:** Official Android and Google Play documentation, plus first-party feature documentation from established expense-tracking products. Links at the end.

**R0003 · §0 · P0 · M1–M7 · PLANNED/reference · source L9**

> **V3 supersedes V2 and adds recovery after uninstall/clear data, signed portable backups, mandatory updates, subscription entitlements and anti-tamper, universal action feedback, duplicate-tap protections, manual credit-card and recurring subscriptions, admin controls, and 50 production edge-case test cases. New requirements appear in sections 26–41.**

**R0004 · §0 · P0 · M1–M7 · PLANNED/reference · source L11**

> **V2 coverage:** complete security/unlocking and rate-app policy (§15), long-offline batch sync (§16), local+FCM reminders (§17), permission-based recurring subscription detection (§18), full settings (§19), money semantics and WhatsApp share (§20), native performance (§21), compact Traxos UI/UX and overflow (§22), Android caveats (§23), full edge-case/test gates (§24), references (§25). These V2 sections override conflicting website/Capacitor implementation details.


### Source 1. Product vision

**R0005 · §1 · P0 · M1–M7 · PLANNED/reference · source L13**

> ## 1. Product vision

**R0006 · §1 · P0 · M1–M7 · PLANNED/reference · source L15**

> **Promise:** Track what you spend in seconds, even without the internet. Your records always live on your phone; an optional account synchronizes a backup and allows collaboration on shared expenses when online.

**R0007 · §1 · P0 · M1–M7 · PLANNED/reference · source L17**

> **North star:** Minimize effort per *accurately recorded* expense, not merely taps per screen. Suggestions should save users from typing while never silently corrupting their finances.

**R0008 · §1 · P0 · M1–M7 · PLANNED/reference · source L19**

> **Design principles**
> 1. Offline by default: no sign-in or connectivity needed for core personal finance actions.
> 2. User in control: all imported events are proposed transactions, never assumed true without appropriate consent.
> 3. Fast paths for frequent actions: numeric keypad, recent merchants, notification confirmation, voice, widgets.
> 4. Correct financial semantics: transfers, savings allocations, credit-card payments and group reimbursements are not ordinary expenses.
> 5. Explicit recovery: users can understand whether data is local-only, pending sync, backed up, or needs attention.
> 6. Human, calm interface: the supplied five-rounded-square T mark anchors the UI; no gratuitous gradients, generic AI art or dashboard clutter.


### Source 2. Scope recovered from old website documentation

**R0009 · §2 · P1 · M5 · PLANNED/reference · source L27**

> ## 2. Scope recovered from old website documentation

**R0010 · §2 · P1 · M5 · PLANNED/reference · source L29**

> The archive's most valuable capabilities for the native product are transactions, multiple accounts, income/expense/transfer distinctions, person-based lend/borrow history, savings goals, category budgets, recurring expenses, group splitting, exports, SMS candidate review, voice-add and useful summaries.

**R0011 · §2 · P1 · M5 · PLANNED/reference · source L31**

> **Do not port the web implementation literally.** Its Vite/Next/Capacitor requirements, CSS animation system, service-worker caching, cloud-only login flow and React repository strategy are not APK architecture. The old document `17-sms-auto-import.md` overstates Google Play's restrictions: the official SMS-policy exception explicitly includes SMS-based money management, subject to permission declaration, review and approval. The old `21-device-lock-and-offline-auth.md` proposed a 7-day inactivity hard lock requiring internet; that contradicts the new offline use case. `27-logo-and-brand-assets.md` describes a *continuous T*, while the supplied actual logo is five separate rounded squares. Use the image as the brand authority.

**R0012 · §2 · P1 · M5 · PLANNED/reference · source L33**

> **Preserve legacy cloud user data if adopting an existing backend:** inspect the actual deployed Supabase schema and migration history, snapshot backups, and devise explicit user-ID and transaction-ID mapping before importing or reconciling. The ZIP is a planning/audit set, not proof of the live database's current state. In particular, check the group-member row-level-security behavior documented in `16-group-expenses.md`.


### Source 3. Feature priority

**R0013 · §3 · P0 · M1–M7 · PLANNED/reference · source L35**

> ## 3. Feature priority

**R0014 · §3 · P0 · M1–M7 · PLANNED/reference · source L37**

> | Priority | Feature | V1 behavior | Why |
> |---|---|---|---|
> | **P0** | Offline guest mode | Open app and record expenses with airplane mode enabled | Core promise |
> | **P0** | Native local security | Optional independent Traxos PIN, Android biometric/device credential, encrypted local storage | Required privacy control |
> | **P0** | Robust native UX | Text overflow, keyboard insets, compact layout, accessible touch targets, graceful failure states | Release blocker |
> | **P0** | Full Settings | Profile/theme, notifications, account visibility, currencies, security, sync, export, app identity choices | User control |
> | **P0** | Long-offline resumable sync | Batches, checkpoints, dedupe, conflicts, large-history recovery, server cursors | Core reliability |
> | **P0** | Manual quick-add | Large amount field, expense/income/transfer, recent category, default account, one-tap Save | Always reliable |
> | **P0** | Transaction list | Search, day/month filters, edit, delete with Undo, notes, labels | Trust and correction |
> | **P0** | Accounts | Cash, bank, credit card, optional opening balances; account transfers | Avoid false money totals |
> | **P0** | Accurate summaries | Spent today/month, category breakdown, trend, cash-flow vs spending distinction | Immediate value |
> | **P0** | Backup/sync foundation | Optional account, local outbox, background sync, recoverable sync status | Data safety |
> | **P0** | Import review inbox | Duplicates, account choice, Add/Dismiss/Edit, snooze alerts | Safe automation |
> | **P0** | Basic savings | Goals, optional target/deadline, contributions/withdrawals as allocations or real transfers | User-requested core |
> | **P1** | SMS financial-event detection | On-device bank/UPI parsers; opt-in restricted SMS access if allowed | Low effort for Indian users |
> | **P1** | Voice quick-add | On-device STT when available + deterministic Hindi/English/Hinglish parser + preview | Hands-free logging |
> | **P1** | Shared groups | Offline local expense drafts, online invitations/membership, equal/exact/percentage/shares split, settlements | User-requested core |
> | **P1** | Recurring items | Rent, subscriptions, salary reminders; user confirms estimated entries | Retention |
> | **P1** | Fast entry outside app | Launcher shortcut, home widget, Quick Settings tile, share target | Lower friction |
> | **P1** | Rules/learning | Merchant-to-category preferences, suggested account, high-confidence one-tap confirmation | Useful intelligence without LLM dependency |
> | **P1** | Budgets | Monthly and custom salary-cycle category limits, considerate alerts | Actionable insight |
> | **P2 / experimental** | Volume-double-press | Opt-in compatibility experiment requiring AccessibilityService, *not* guaranteed | Requested but OS-constrained |
> | **P2** | Notification-based import | Optional NotificationListener with narrow filtering, consent, and policy review | SMS/UPI fallback, fragmented |
> | **P2** | Receipt OCR and statements | Review-based, on-device where practical | Secondary convenience |
> | **P2** | Advanced group optimization | Simplify debts, receipt attachment, group exports | Polish |
> | **Defer** | Chatbot, investments, lending products, live bank aggregation, gamified scores, group chat | Not in initial build | Costs and distracts from effortless tracking |

**R0015 · §3 · P0 · M1–M7 · PLANNED/reference · source L64**

> **Important:** P0 is the engineering foundation, not necessarily a public release by itself. A first marketable release should include at least one differentiated low-effort import or shortcut path (SMS review and/or voice), after policy and hardware testing.


### Source 4. Core screens and UX

**R0016 · §4 · P0 · M1/M2 · PLANNED/reference · source L66**

> ## 4. Core screens and UX

**R0017 · §4 · P0 · M1/M2 · PLANNED/reference · source L68**

> ### Main navigation

**R0018 · §4 · P0 · M1/M2 · PLANNED/reference · source L70**

> - **Home:** top summary + recent records + pending review + a savings snippet.
> - **Activity:** searchable complete ledger and filters.
> - **Groups:** personal shared spaces and 1:1 settlements.
> - **Plans:** budgets, savings goals, recurring items.
> - **Quick Add:** fixed thumb-reachable floating action, reachable from any main screen.
> - **Profile/Settings:** small top action, not a major navigation tab.

**R0019 · §4 · P0 · M1/M2 · PLANNED/reference · source L77**

> ### Home hierarchy

**R0020 · §4 · P0 · M1/M2 · PLANNED/reference · source L79**

> 1. Compact month title and small circular Traxos-mark profile/action.
> 2. One primary number: **Spent this month**. Nearby show income and net change, with clearly distinct labels.
> 3. Small horizontal budget-progress rail: *₹8,420 of ₹15,000*; hide if user has not set a budget.
> 4. Pending smart suggestions: e.g. *2 payments ready to review*; do not nag all day.
> 5. Recent transactions: 4–6 rows; icon, merchant, secondary account, amount, date. Tap expands detail.
> 6. Small savings-goal progress strip, shown only if goals exist.

**R0021 · §4 · P0 · M1/M2 · PLANNED/reference · source L86**

> Avoid a dozen equal-weight stat cards, five circular charts, meaningless stock images or forced “financial health” scores. Users should see spending and the next useful action immediately.

**R0022 · §4 · P0 · M1/M2 · PLANNED/reference · source L88**

> ### Quick Add flow

**R0023 · §4 · P0 · M1/M2 · PLANNED/reference · source L90**

> **Open → Amount already focused → Pick default category/account if needed → Save.** One hand, target under 5 seconds for common transactions (design target, not measured result). Three subtle tabs or segmented choices: **Expense / Income / Transfer**. For most entries, prefill the last relevant account and merchant/category suggestion. Numeric keypad uses familiar Indian formatting. Optional note, date, account and group are hidden behind a compact “Details” expansion.

**R0024 · §4 · P0 · M1/M2 · PLANNED/reference · source L92**

> After Save: localized haptic tick, transaction row animates into history, brief `Saved · Undo` snackbar. Undo should create a proper correcting/deleting operation, not just visually remove a row. Preserve fields if the app process dies during composition.

**R0025 · §4 · P0 · M1/M2 · PLANNED/reference · source L94**

> ### Smart-import card

**R0026 · §4 · P0 · M1/M2 · PLANNED/reference · source L96**

> **“Possible payment detected”**

**R0027 · §4 · P0 · M1/M2 · PLANNED/reference · source L98**

> **₹249 · Grocery Mart**  
> *Today, 7:42 PM · Bank ••4821*  
> `Add` · `Not this one` · `Edit`

**R0028 · §4 · P0 · M1/M2 · PLANNED/reference · source L102**

> The notification may expose `Add` and `Dismiss`, with tap-through review if confidence is low. Import state must persist in Room before notification is shown. Do not require the user to open the app to act on a high-confidence, unambiguous candidate, but never silently add a low-confidence event. Allow “Don’t ask for this merchant,” “Always suggest account X,” or “Remember category Groceries.”

**R0029 · §4 · P0 · M1/M2 · PLANNED/reference · source L104**

> ### Onboarding

**R0030 · §4 · P0 · M1/M2 · PLANNED/reference · source L106**

> 1. Welcome screen with logo and one-line value proposition.
> 2. **Start offline** (primary) and **Sign in to back up** (secondary).
> 3. Currency defaults to INR but is editable; choose whether to start in **Simple Tracking** (no accounts required) or **Accounts & Balances**.
> 4. First transaction walkthrough, not five pages of features.
> 5. Ask SMS access only when the user turns on Smart Import; request notification permission when enabling notifications; request microphone access when using Voice Add. Never require permissions to record a manual transaction.
> 6. Explain cloud backup and restore when users have made meaningful local progress.

**R0031 · §4 · P0 · M1/M2 · PLANNED/reference · source L113**

> Support a realistic *no-account* beginning without inventing balance totals. If account balances are not configured, label the app as **spend tracking mode**, not “total money in all accounts.”

**R0032 · §4 · P0 · M1/M2 · PLANNED/reference · source L115**

> ### Animation and visual character

**R0033 · §4 · P0 · M1/M2 · PLANNED/reference · source L117**

> - Brand basis: five separate soft-corner navy squares making a T, carefully rebuilt in vector from supplied image.
> - Core ink: deep midnight navy near `#111B2F`; surface off-white near `#F6F8FB`; a restrained vivid blue for the main action. Income green and expense vermilion only where meaningful.
> - Typography: highly legible, slightly condensed display numbers; use a real production-quality font with full Indic glyph fallback. Consistent tabular numerals.
> - Use 160–250ms transitions on cards/sheets, a quiet progress-bar spring, tiny checkmark completion animation, and native haptic feedback. Respect reduced motion, battery-saver settings and dynamic system font scaling.
> - Native Compose Material 3 components may be themed and customized; use them as accessibility primitives, not the finished visual identity.
> - Test 320–430dp widths, long merchant names, dark mode, high-contrast font settings and TalkBack.


### Source 5. Feature: voice transaction entry

**R0034 · §5 · P1 · M4 · PLANNED/reference · source L124**

> ## 5. Feature: voice transaction entry

**R0035 · §5 · P1 · M4 · PLANNED/reference · source L126**

> **Example:** User speaks, “Lunch 150 rupaye UPI se,” or “Spent 250 on petrol yesterday from SBI.”

**R0036 · §5 · P1 · M4 · PLANNED/reference · source L128**

> Pipeline: microphone (user initiated) → truly on-device speech recognizer if available → text → rule parser (amount, entry type, category, relative date, bank/payment clue, group/person) → editable preview → one-tap save → local database. Speech recognition availability, installed language models and Hindi/Hinglish quality vary by device. `SpeechRecognizer.isOnDeviceRecognitionAvailable` and `createOnDeviceSpeechRecognizer` are Android API 31+ options; evaluate a bundled offline model for unsupported devices, but account for APK size and battery cost. **Never make online speech a silent fallback if offline voice is promised.** For ambiguous recognition, preserve the dictated text and ask only for the missing field.

**R0037 · §5 · P1 · M4 · PLANNED/reference · source L130**

> Proposed structured parse result: `entryType`, `amountMinor`, `currency`, `merchant`, `category`, `accountHint`, `occurredAt`, `confidence`, `requiresReview`. No LLM required for the first version.


### Source 6. Feature: SMS / UPI smart import

**R0038 · §6 · P1 · M4 · PLANNED/reference · source L132**

> ## 6. Feature: SMS / UPI smart import

**R0039 · §6 · P1 · M4 · PLANNED/reference · source L134**

> **Policy:** Google Play's SMS-permission guidance explicitly lists *SMS-based money management* as a restricted-use exception for budgets, but approval is discretionary and requires permission declaration, prominent data-use transparency and genuine core functionality. Do not assume approval; prepare a permission-free build flavor or fallback. A directly installed signed APK is technically different from Play distribution, but is not exempt from Android runtime permission rules or user trust expectations.

**R0040 · §6 · P1 · M4 · PLANNED/reference · source L136**

> **Acquisition options**

**R0041 · §6 · P1 · M4 · PLANNED/reference · source L138**

> | Source | Feasibility | Limitation |
> |---|---|---|
> | SMS BroadcastReceiver and/or inbox read | Technically possible with appropriate Android permissions | Play approval; OEM and SMS delivery variability |
> | NotificationListenerService | Optional consent-based payment notification signal | Sensitive wide visibility, policy review, app-specific formats, silent/hidden notifications missed |
> | Android share intent | User sends bank message/receipt to Traxos | Requires user action but permission-light |
> | Manual paste | Always works | More friction |
> | Direct UPI/bank API | Defer | Financial-provider integration, consent, compliance and cost |

**R0042 · §6 · P1 · M4 · PLANNED/reference · source L146**

> **Parsing steps:** identify trusted sender/payment app → discard OTP/personal messages → detect amount, debit vs credit, bank account suffix, merchant, timestamp, UPI reference → classify payment/refund/transfer/unknown → deduplicate → persist as **candidate**, not final transaction → notify if allowed. Implement all parsing on device; do not upload full SMS history. Do not even persist irrelevant messages locally. Never show full account identifiers in notifications.

**R0043 · §6 · P1 · M4 · PLANNED/reference · source L148**

> **Duplicate sources:** the same payment can appear in bank SMS, payment-app notification and a manual entry. Prefer bank reference ID when available; otherwise use a salted fingerprint with event type, amount, account suffix, time window and merchant. An approximate match should route to review, not silently merge. Reversal/refund events link to the original payment where possible. Exclude OTPs, offers, card authorization holds and balance-only notices.

**R0044 · §6 · P1 · M4 · PLANNED/reference · source L150**

> **User controls:** paused/on, selected banks/apps, notification cadence, notification privacy (hide amounts on lock screen), ignored senders, “always ask” vs trusted approval rules, delete all suggestions, explain why suggested.


### Source 7. Feature: hardware button shortcut

**R0045 · §7 · P2 · M7 · PLANNED/reference · source L152**

> ## 7. Feature: hardware button shortcut

**R0046 · §7 · P2 · M7 · PLANNED/reference · source L154**

> **User desire:** double-press volume (up or down) while Traxos is not foregrounded to launch Add Transaction.

**R0047 · §7 · P2 · M7 · PLANNED/reference · source L156**

> **Technical reality:** ordinary apps cannot globally intercept hardware volume keys. Android has an accessibility-service key-filtering capability (`FLAG_REQUEST_FILTER_KEY_EVENTS` + `canRequestFilterKeyEvents`), but the system may give key-filter control to another service, OEMs may differ, and a force-stopped app will not reliably handle background triggers. Using AccessibilityService for convenience also carries prominent consent and Google Play policy scrutiny. Lock-screen behavior, media volume interactions and background activity-start restrictions further reduce reliability. Never imply this works on every device or in every app state.

**R0048 · §7 · P2 · M7 · PLANNED/reference · source L158**

> **Decision:** do not make it a launch dependency. Prototype as a **voluntary experimental shortcut** with clear setup/disclosure, automatic disable if unsupported, and explicit test coverage on common Android manufacturers. Safer, stable alternatives: launcher long-press Quick Add, pinned home-screen shortcut, home-screen widget, Quick Settings tile, actionable import notification. If testing succeeds, double-press should open a **locked/secure quick-add interface** rather than reveal balances or start microphone recording without user control.


### Source 8. Group expenses architecture

**R0049 · §8 · P1 · M5 · PLANNED/reference · source L160**

> ## 8. Group expenses architecture

**R0050 · §8 · P1 · M5 · PLANNED/reference · source L162**

> **Group v1**: group name, members, invited/placeholder people, per-expense payer, one or more participants, amount, split method, debt balance, partial settlement and event history. Optional avatar/emoji per group but no crowded chat. Standard split methods: equal, exact amounts, percentages and shares. These are established patterns in Splitwise's official help.

**R0051 · §8 · P1 · M5 · PLANNED/reference · source L164**

> **Offline boundary:** the device can create *local group drafts* and edit *cached existing group data* offline, but cannot guarantee other members receive them, see current balances, or accept invitations until online. Show the user's own local estimate and a **Pending share** status. Never imply stale balances are synchronized current balances.

**R0052 · §8 · P1 · M5 · PLANNED/reference · source L166**

> **Financial semantics example:** user pays ₹1,200 for four friends including themself, split equally. Cash account decreases by ₹1,200. Personal expense is ₹300, and reimbursement receivable is ₹900. Later collecting ₹900 increases cash and reduces receivable, *not* personal income. There should be one linked source payment ID behind the group expense and corresponding ledger effects—never a duplicated ₹1,200 personal expense and ₹1,200 group expense.

**R0053 · §8 · P1 · M5 · PLANNED/reference · source L168**

> Server responsibilities: authenticate group invitations, validate membership and write permissions (RLS), authorize edits, provide monotonic versioning, accept idempotent commands, and reject inconsistent settlement events. Record reversals instead of deleting established settlement history; show who edited what. Plain offline “member join” is only a pending invitation, not a completed membership. Add simplified settlement routes only after totals reconcile.


### Source 9. Savings, budgets, and account semantics

**R0054 · §9 · P0 · M2 · PLANNED/reference · source L170**

> ## 9. Savings, budgets, and account semantics

**R0055 · §9 · P0 · M2 · PLANNED/reference · source L172**

> - **Goals:** amount target, due date, contributions, withdrawals, note and progress. A goal can be either a *virtual allocation* of existing money or a true transfer into a separate savings account; the UI must distinguish these and never automatically double-subtract from total balance.
> - **Budgets:** month or salary-cycle allowance, total spend and category envelopes. Recurring bills are upcoming commitments, not recorded expenses until paid or explicitly confirmed.
> - **Accounts:** actual money containers (cash, bank, card). A UPI app used to pay from a bank is usually **payment method metadata**, not a new second pool of money. Transfers between accounts are not income/expense. Card purchases count as expenditure at purchase time; credit-card bill repayment is a transfer/paydown, not a second expense.
> - **Debt/credit:** person thread with lend, borrow, repayment and adjustment entries. Link person debt to group members when appropriate, but keep group balances and private IOUs separately identifiable.


### Source 10. Offline-first architecture

**R0056 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L177**

> ## 10. Offline-first architecture

**R0057 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L179**

> ```text
> Jetpack Compose screens / ViewModels
>                ↓
> Domain use-cases and validation
>                ↓
> Repository (only local Room data observable by UI)
>        ↙                         ↘
> Room + SQLite                    Sync worker (network constrained)
> transactions, entries,           encrypted HTTPS API
> accounts, groups, goals,            ↓
> suggestions, outbox            Supabase/Postgres or custom backend
>                                   ↓
>                            RLS, operation IDs, group auth
> ```

**R0058 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L194**

> **Native stack recommendation:** Kotlin; Jetpack Compose + Material 3 foundations; Navigation Compose; Coroutines/Flow; Room/SQLite; DataStore for non-sensitive preferences; WorkManager for reliable deferred sync; Hilt or manual dependency injection; Android Keystore; BiometricPrompt; Retrofit/Ktor client; Supabase/PostgreSQL if compatible with verified existing deployment; local notifications, TileService, AppWidget/Glance and Android Sharesheet. Encryption-at-rest for finance data via a compatible SQLCipher/SQLite approach should be explicitly designed and tested rather than assumed from Room itself.

**R0059 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L196**

> **Data entities (core):** `profiles_local`, `accounts`, `transactions`, `transaction_postings` or equivalent balance effects, `categories`, `merchant_rules`, `import_candidates`, `debts`, `counterparties`, `savings_goals`, `goal_events`, `budgets`, `recurring_templates`, `groups`, `group_members`, `group_expenses`, `group_splits`, `settlements`, `sync_outbox`, `sync_cursors`, `sync_conflicts`, `audit_events`.

**R0060 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L198**

> **Money representation:** integer minor units (`Long` paise for INR) + explicit currency code. Never use `Float`/`Double` for authoritative amounts. Model sign/type explicitly; store timestamps in UTC and render with device locale/time zone. Maintain unique durable record identifiers generated locally (UUID/UUIDv7 or suitable IDs).

**R0061 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L200**

> **Mutation pattern:** one SQLite atomic transaction saves both the financial change and a durable outbox command. The user sees the local result immediately. A WorkManager job drains the outbox when network and valid authentication are available, using stable idempotency keys and exponential retry; the server stores operation IDs and applies each command at most once; acknowledged changes are marked synced; server delta is pulled and merged into Room; full reconciliation checks consistency. Account opening balance and corrections should have an auditable history rather than relying solely on mutable cached totals.

**R0062 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L202**

> **Conflicts:** Do not use blind last-write-wins for money amounts or group settlement. Prefer append-only ledger events and correction events. Distinguish reversible metadata edits from core financial facts. For simultaneous incompatible edits, put a clear item into **Needs review** while retaining both versions and all original records. Tombstones prevent deleted records from resurrecting during sync.

**R0063 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L204**

> **Sync status states:** `On this device`, `X changes to back up`, `Backing up`, `Backed up [time]`, `Needs attention`. No fictional real-time 100% guarantee. Group remote totals are stamped `Updated [time]` if stale.

**R0064 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L206**

> **Backup safety:** guest local data persists on-device but can be lost if the app is uninstalled, storage is wiped or the phone is destroyed before backup. Account backup reduces that risk; backup should include versioned schema, restore preview and integrity checks. Offer an optional user-controlled encrypted export and recovery-key strategy. Never delete pending local records on logout, account change or failed sync without explicit, well-informed confirmation. Test restore onto a different physical device and across schema migrations.

**R0065 · §10 · P0 · M1/M3/M5 · PLANNED/reference · source L208**

> **Privacy:** use explicit scopes for SMS/notification/microphone permissions; keep imported message snippets only as long as needed; use redacted lock-screen notification previews; never log personal finance content, OTPs or tokens to analytics. Distinguish encrypted transport, encryption at rest, and true end-to-end encryption—those are not synonymous. Group-sharing architecture complicates E2EE; make no blanket E2EE claim without implementing group key management.


### Source 11. Recommended quick-action portfolio

**R0066 · §11 · P1 · M4 · PLANNED/reference · source L210**

> ## 11. Recommended quick-action portfolio

**R0067 · §11 · P1 · M4 · PLANNED/reference · source L212**

> | User action | Required app state | UX target |
> |---|---|---|
> | In-app plus button | App open | Amount → Save |
> | Long press app icon → Add expense | Launcher available | Open directly to Quick Add |
> | Home screen widget | Widget installed | Open Quick Add with type or category preselected |
> | Quick Settings tile | User adds tile | Jump to compact transaction entry |
> | SMS/notification suggestion | Import enabled, eligible event detected | Tap Add, Dismiss, Edit |
> | Share bank message/receipt | User invokes Android Share sheet | Extract → Preview → Save |
> | Voice add | User initiates microphone | Speak → Parse → Preview → Save |
> | Double volume press | Experimental AccessibilityService permitted and reliably running | Open secure Quick Add; otherwise graceful unavailability |

**R0068 · §11 · P1 · M4 · PLANNED/reference · source L223**

> Consider **favorite presets** (Tea ₹20 cash; Bus ₹30; Fuel current account) and a **repeat last entry** shortcut as the simplest high-return enhancement. Offer a daily digest only if candidates accumulate; resist pinging users after every tiny transaction unless they asked for immediate alerts.


### Source 12. Acceptance criteria and QA

**R0069 · §12 · P0 · M7 · PLANNED/reference · source L225**

> ## 12. Acceptance criteria and QA

**R0070 · §12 · P0 · M7 · PLANNED/reference · source L227**

> **Local reliability:** airplane mode from first launch; 1,000+ locally saved records; cold-start with no network; immediate read after process death; duplicate tap under stress; cancelled/failed migration; storage nearly full; app update with pending outbox.

**R0071 · §12 · P0 · M7 · PLANNED/reference · source L229**

> **Sync and migration:** create same transaction on two devices with one offline; duplicate retry delivery; reconnect during pending group edits; server rejection; token expiry without deleting data; restore onto second phone; failed restore; group membership revocation; timezone change; app version upgrade; backup checksum validation.

**R0072 · §12 · P0 · M7 · PLANNED/reference · source L231**

> **Financial correctness:** cash vs bank account transfers; credit-card repayment; reimbursements; partial refunds; group amount split rounding; paise remainder exactly allocated; 0/negative values; multiple currencies as separate balance domains until explicit FX support; race condition in settlements; goals as virtual allocations vs transfers; reversal of a previously synced item.

**R0073 · §12 · P0 · M7 · PLANNED/reference · source L233**

> **Smart import:** UPI debit/credit/refund/reversal; ambiguous SMS; two alerts for same purchase; no bank message; message arrives during permission revocation; SMS received while process isn't running; OTP/private message never retained; no internet; muted lock-screen notifications; edited candidate reused after app restart.

**R0074 · §12 · P0 · M7 · PLANNED/reference · source L235**

> **Hardware/accessibility:** Android versions and common OEMs; accessibility/notification/SMS permissions denied; TalkBack; large font; battery saver; force-stop; locked device; another accessibility service intercepting keys; volume pressed while media playing; shortcut must not alter volume unexpectedly or reveal balances.

**R0075 · §12 · P0 · M7 · PLANNED/reference · source L237**

> **Go/no-go:** no unacknowledged financial-data deletion; no unintentional duplicate posting; no claim of sync completion before server acknowledgement; all finance-critical actions accessible without internet; import works without collecting irrelevant texts; sign-in is never mandatory for personal tracking.


### Source 13. Build sequence

**R0076 · §13 · P0 · M1–M7 · PLANNED/reference · source L239**

> ## 13. Build sequence

**R0077 · §13 · P0 · M1–M7 · PLANNED/reference · source L241**

> 1. **Foundation:** native app shell, branding, data schema, local identity, Room, transaction logic, accounts, backup/export strategy.
> 2. **Daily-use quality:** Home, Quick Add, Activity, Undo, category rules, savings, budgets, local notifications, basic accessibility.
> 3. **Safe intelligence:** SMS review (subject to permission route), share intent, deduplication, on-device voice, widgets and tiles.
> 4. **Cloud and social:** account linking, durable outbox sync, restore, group invites, splits, partial settlement, strict RLS and conflict review.
> 5. **Hardening:** data-integrity tests, Android battery/permissions matrix, crash recovery, policy audit, sideload or Play distribution strategy.
> 6. **Selective polish:** microinteractions, advanced receipt handling, optional tested volume-button shortcut, insights and smart reminders.

**R0078 · §13 · P0 · M1–M7 · PLANNED/reference · source L248**

> **Recommended first milestone:** a signed, native, completely offline APK where users can create accounts, record, edit, search and export transactions, set one savings goal, and lose no unsynced data on a normal app restart. Establish financial correctness and recovery before adding more automation.


### Source 14. Research sources (primary)

**R0079 · §14 · P2 · M7 · PLANNED/reference · source L250**

> ## 14. Research sources (primary)

**R0080 · §14 · P2 · M7 · PLANNED/reference · source L252**

> - Android official offline-first architecture: https://developer.android.com/topic/architecture/data-layer/offline-first
> - Android Room/SQLite: https://developer.android.com/jetpack/androidx/releases/room
> - Google Play SMS permission exceptions including SMS-based money management: https://support.google.com/googleplay/android-developer/answer/10208820
> - Google Play AccessibilityService restrictions and disclosures: https://support.google.com/googleplay/android-developer/answer/10964491
> - Android AccessibilityService key filtering: https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo
> - Android on-device speech API: https://developer.android.com/reference/android/speech/SpeechRecognizer
> - Android Quick Settings tiles: https://developer.android.com/develop/ui/views/quicksettings-tiles
> - Android launcher shortcuts: https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts
> - Android notification action buttons: https://developer.android.com/reference/android/app/Notification.Builder
> - Android NotificationListenerService: https://developer.android.com/reference/android/service/notification/NotificationListenerService
> - Android background launch restrictions: https://developer.android.com/about/versions/15/behavior-changes-15
> - Splitwise official split methods: https://kb.splitwise.com/balances-and-expenses/what-are-different-ways-i-can-split-an-expense
> - Splitwise official simplify debts: https://kb.splitwise.com/balances-and-expenses/what-is-simplify-debts
> - Realbyte Money Manager official features: https://www.realbyteapps.com/
> - axio official FAQ (Android money management and handling of SMS data): https://www.axio.co.in/faq

**R0081 · §14 · P2 · M7 · PLANNED/reference · source L268**

> ---

**R0082 · §14 · P2 · M7 · PLANNED/reference · source L270**

> **Status:** Planning specification, not a completed APK. Permission eligibility, cloud costs, device compatibility and exact migration details still require validation against the selected release channel and live backend. Avoid absolute “zero data loss” promises for offline-only devices without completed backups; design for high reliability, audited sync and explicit recovery instead.

**R0083 · §14 · P2 · M7 · PLANNED/reference · source L272**

> ---

**R0084 · §14 · P2 · M7 · PLANNED/reference · source L274**

> # V2 ENGINEERING & EXPERIENCE REQUIREMENTS — AUTHORITATIVE EXTENSION

**R0085 · §14 · P2 · M7 · PLANNED/reference · source L276**

> **Status:** Product requirements / engineering design, not a claim that these features have been built. Where a new requirement conflicts with an earlier web/Capacitor design, **this native V2 section wins**. All metrics below are proposed acceptance targets, to be measured on real Android hardware; no performance results have been benchmarked yet.


### Source 15. Security, authentication, session protection and rating

**R0086 · §15 · P1 · M3 · PLANNED/reference · source L278**

> ## 15. Security, authentication, session protection and rating

**R0087 · §15 · P1 · M3 · PLANNED/reference · source L280**

> ### 15.1 Separate the four concepts

**R0088 · §15 · P1 · M3 · PLANNED/reference · source L282**

> 1. **Local app lock** protects the currently installed app from casual access. It works offline and must not require an online account.
> 2. **Cloud account authentication** is required only for optional remote backup, restore, group membership and multi-device sync. Use a supported identity provider via system browser / Credential Manager where appropriate; never store raw account passwords.
> 3. **Encryption at rest** protects local database files, cache and sensitive attachments. App lock does not replace cryptographic protection.
> 4. **Recovery** is an explicit user choice. An offline-only profile cannot magically recover records after uninstall/device loss. Do not silently upload data to solve this.

**R0089 · §15 · P1 · M3 · PLANNED/reference · source L287**

> ### 15.2 Unlock modes and exact behavior

**R0090 · §15 · P1 · M3 · PLANNED/reference · source L289**

> | Mode | User choice and implementation | Failure behavior |
> |---|---|---|
> | No app lock | Default possible for offline guest; show privacy explanation | No forced sign-in or lock |
> | Device fingerprint | AndroidX `BiometricPrompt`, supported device biometric; enable only after successful capability check | Offer configured device credential or Traxos PIN fallback |
> | Device face authentication | Same API; only offer a supported OS biometric and clearly handle weaker implementations; crypto-bound unlock requires compatible **strong** biometric | Never imply any arbitrary face unlock is equivalent to hardware-backed strong biometric |
> | Device credential | OS-managed phone PIN/pattern/password using supported `DEVICE_CREDENTIAL` combinations | Respect API-level restrictions and system lockout |
> | Traxos PIN | Separate local 4–8 digit PIN; 6+ digits recommended; use hardened slow verifier with unique random salt, adaptive rate limits, protected local secret material | No insecure “forgot PIN → instant reset.” Recover through already provisioned credential or verified recovery path |
> | Sensitive-action re-auth | Export of unencrypted data, reveal full account details, import/restore replacement, disable app lock, delete account or erase local data | Show non-destructive error; do not proceed invisibly |

**R0091 · §15 · P1 · M3 · PLANNED/reference · source L298**

> The OS biometric dialog should remain **the real system dialog**, not an imitation. Traxos can use branded explanatory screens and its own PIN keypad; it must never ask users to type their phone lock-screen PIN into a Traxos form. Hardware capabilities are queried at runtime. User can enable face/fingerprint/device credential only when supported; biometric enrollment changes require a documented re-enrollment/unlock flow rather than wiping data.

**R0092 · §15 · P1 · M3 · PLANNED/reference · source L300**

> **App lock timing options:** immediately on background; after 30 seconds; after 1 minute; after 5 minutes; after 15 minutes; never. Use lifecycle/process-safe timestamps; settings persist after force stop and reboot. Keep local, restricted background operations such as reminder bookkeeping and queued sync independent of visible UI app-lock state when cryptography permits; do not promise encrypted content is accessible to a headless job if an authentication-bound key forbids it. Keep notification content redacted while locked. Do not expose private numbers in app switcher by default; configurable screenshot protection with an explicit accessibility/help warning for affected use-cases.

**R0093 · §15 · P1 · M3 · PLANNED/reference · source L302**

> **Threat model:** stolen unlocked phone, stolen locked phone, shoulder surfing, notification previews, rooted device, malicious exported Android component, compromised backup file, man-in-the-middle, backend account token theft, compromised recipient in groups. Security cannot be fully guaranteed on rooted/compromised devices; present that as risk rather than a fake “100% secure” statement.

**R0094 · §15 · P1 · M3 · PLANNED/reference · source L304**

> ### 15.3 Cryptographic controls

**R0095 · §15 · P1 · M3 · PLANNED/reference · source L306**

> - Use Android Keystore for non-exportable cryptographic key material when available, with explicit key invalidation/recovery testing; prefer well-reviewed primitives and authenticated encryption such as AES-256-GCM. Detect hardware-backed protection availability rather than assuming StrongBox on every device.
> - Encrypt the local finance database at rest using an actively maintained, audited SQLite encryption integration that works with Room, **or** an equally defensible storage approach. Test performance and lifecycle behavior on target APIs. Also encrypt photo/receipt attachments and local export temp files.
> - Prefer TLS for all network APIs; redact logs, analytics and crash reports; never log SMS bodies, PINs, cloud tokens, transaction notes, phone numbers or bank identifiers. Do not embed service-role/server admin keys in the APK.
> - Backend authorization must be enforced on every request by user identity and group membership, not by hidden UI menus. Row-level security plus service-layer permission checks, deny by default; test group join/leave, archived records, invite reuse and revoked members.
> - Protect mutations with authenticated, scoped operations; validate all IDs, currencies, integer amounts, dates and attachment MIME/types server-side. Rate-limit login, invites, reminder shares, account linking, backup restore, SMS processing and APIs; use CSRF controls only where cookie-backed web endpoints are involved.
> - Anti-replay/duplication: client-generated stable operation UUID; account-scoped uniqueness constraint; server never relies on client-calculated balances as financial truth; audits are append-friendly.
> - Backup/export security must be explicit: password-protected archive with secure KDF and integrity check when offered; show a warning before sharing a plaintext CSV/PDF. Exclude key material and plaintext secrets from generic Android backups; test real restore after reinstall. A Supabase-hosted record protected only by transport/storage encryption is **not** automatically end-to-end encrypted. If E2EE is added, specify recovery-key UX and the consequences for group sharing/search and lost keys.
> - Detect tampering/obvious root/integrity risk for warnings and abuse controls; never treat root detection as a substitute for ledger authorization or encryption. Use Play Integrity conditionally if distribution demands it, without blocking core offline use merely because the device has no internet.

**R0096 · §15 · P1 · M3 · PLANNED/reference · source L315**

> ### 15.4 Traxos app security status and store rating

**R0097 · §15 · P1 · M3 · PLANNED/reference · source L317**

> If “rating” refers to security: show understandable **statuses** such as `App lock: On`, `On-device data: Protected`, `Backup: Last verified 11:20`, `Notification previews: Hidden`, and `Recovery: Not set up`. Avoid a made-up numerical “security score” that implies a certified audit.

**R0098 · §15 · P1 · M3 · PLANNED/reference · source L319**

> If “rating” means Play Store review: provide **Rate Traxos** under Settings → About and optionally request an official Play in-app review at a natural, infrequent success milestone (not after an error, not on first launch, no review gating). The Play in-app review flow is subject to quotas and may not display; a direct store-listing link is a sensible settings action.


### Source 16. True offline-first synchronization: batching, integrity, recovery

**R0099 · §16 · P1 · M5 · PLANNED/reference · source L321**

> ## 16. True offline-first synchronization: batching, integrity, recovery

**R0100 · §16 · P1 · M5 · PLANNED/reference · source L323**

> ### 16.1 Core invariant

**R0101 · §16 · P1 · M5 · PLANNED/reference · source L325**

> **The local Room database is the source for every visible screen and every immediate user write.** `Save` commits the local ledger entry and an associated durable `sync_operation` in **one SQL transaction**. The UI must not say `Backed up` until server acknowledgment, and must never block ledger entry on server reachability. The account can remain offline for hours, weeks, or months.

**R0102 · §16 · P1 · M5 · PLANNED/reference · source L327**

> Local entities are namespaced by **local profile ID**, **cloud account ID (if linked)**, and where appropriate **group ID**, to prevent records from one signed-in account appearing in another. Never silently merge two user databases merely because a different user signs in on the same phone.

**R0103 · §16 · P1 · M5 · PLANNED/reference · source L329**

> ### 16.2 Operation contract and ordering

**R0104 · §16 · P1 · M5 · PLANNED/reference · source L331**

> Each queued operation contains `operation_id` (UUID), `profile_id`, `entity_type`, `entity_id`, `op_type`, `base_server_version`, `local_version`, `client_created_at`, `dependency_ids`, `payload_schema_version`, `payload_hash`, `retry_count`, `state` (`queued|sending|acked|needs_resolution|dead_letter`), and an optional non-sensitive diagnostic. Do not store secrets in diagnostic fields.

**R0105 · §16 · P1 · M5 · PLANNED/reference · source L333**

> Use a **server-assigned monotonic change cursor** for inbound changes, not client wall-clock timestamps. For outbound operations, use idempotent upsert/command endpoints keyed on `operation_id`, transactionally apply operations and return per-item results (`accepted`, `duplicate_already_applied`, `conflict`, `rejected_recoverable`, `rejected_permanent`). Use opaque server cursors for pagination. Group-related child operations must wait for their parent group/invite/member dependencies; preserve strict order for financial operations where necessary.

**R0106 · §16 · P1 · M5 · PLANNED/reference · source L335**

> Deletion uses **tombstones**, not immediate hard-deletes; keep tombstones long enough for offline clients to reconcile, with an explicit long-offline recovery protocol. User-requested account data erasure must still follow defined retention/deletion rules and privacy law, and not reappear after sync.

**R0107 · §16 · P1 · M5 · PLANNED/reference · source L337**

> ### 16.3 Reconnect after long offline use

**R0108 · §16 · P1 · M5 · PLANNED/reference · source L339**

> 1. Observe real network capability (`VALIDATED` connectivity when available) and configured sync policy. Schedule constrained WorkManager work on connectivity; periodic/retry behavior is best-effort, not a promise of immediate execution under Doze/OEM restrictions.
> 2. Authenticate or refresh cloud session in the background where allowed. If cloud credentials are invalid, keep local writes; show `Sign in to resume backup` without blocking expense entry.
> 3. Acquire a **single logical sync lease** per profile so manual-sync, background job and FCM ping cannot start concurrent uploads of the same queue.
> 4. Push oldest eligible operation batches in dependency order. Start with **100 operations or 256 KiB**, whichever limit is reached first, as a configurable **initial target**, not a universal ideal; adapt to 429, latency, radio conditions, metered networks and actual payload constraints. Large receipts are uploaded separately with resumable chunks.
> 5. Commit all successful acknowledgments in a local DB transaction; checkpoint the push queue. For ambiguous timeout, **retry the same operation ID**, never invent another.
> 6. Pull remote changes in paginated windows (initial target e.g. 200–500 records/page) using the server cursor, verify payload schema and ownership, and apply each page **transactionally**. Advance the saved pull cursor only after the entire page commits locally.
> 7. Resume on process kill, network loss, device reboot or app update from the last acknowledged operation/cursor. Re-check for remote changes after upload to catch concurrent second-device edits.
> 8. Mark `Synced` only after acknowledgments and pull reconciliation; show pending count and last successful sync timestamp. Avoid displaying a false exact percentage if the server may have more pages.
> 9. On expired cursor/tombstone retention: perform a paginated **snapshot reconciliation** against a stable server snapshot boundary; do not overwrite locally unsynced rows, and do not resurrect previously deleted rows. Retain a server-confirmed manifest/cursor.
> 10. Queue on offline writes immediately, and optionally coalesce safe non-financial preference changes. **Do not collapse or delete historical financial operations** without a mathematically correct and auditable reduction.

**R0109 · §16 · P1 · M5 · PLANNED/reference · source L350**

> Example state indicators: `Saved on phone · 3 changes pending`, `Backing up · 200 of 1,140 accepted`, `Backup complete · 8:42 PM`, `Action needed · sign in`, `Conflict needs review`. An **explicit Sync Now** button is available, but there is no “pull to refresh required” path for normal eventual sync.

**R0110 · §16 · P1 · M5 · PLANNED/reference · source L352**

> ### 16.4 Sync scheduling, bandwidth and power

**R0111 · §16 · P1 · M5 · PLANNED/reference · source L354**

> - Default policy: sync on **any validated connection**, within Android's background scheduling limits. Settings: `Any connection`, `Wi-Fi only`, `Manual only`; optional `Avoid roaming`, `Only upload attachments on Wi-Fi`, `Battery saver pause`.
> - Trigger opportunistic sync when app starts/returns foreground, after local mutations with a quiet debounce/coalesce window, on connectivity regain, and from a server FCM **sync hint**. Never use FCM as the source of truth; hints may arrive out of order or not at all.
> - For data-sensitive users, offer `Keep online backup off` and don't register data-sync tasks or remote FCM tokens until enabled.
> - Avoid indefinite foreground services; schedule large sync with Android-supported long-running work/user-visible progress only if justified, and comply with current foreground-service restrictions. Warn before a large upload on metered network if configured.
> - Pause/retry when battery/data constraints apply, but **never discard pending operations**. Surface persistent, non-alarming diagnostics if a backup hasn't succeeded for a long period.

**R0112 · §16 · P1 · M5 · PLANNED/reference · source L360**

> ### 16.5 Conflicts, duplicates, consistency and account recovery

**R0113 · §16 · P1 · M5 · PLANNED/reference · source L362**

> | Situation | Required handling |
> |---|---|
> | Same mutation retried after timeout | Server returns existing operation result; no duplicate |
> | Different devices edit description | Detect base version mismatch; reconcile or offer a field-level conflict choice |
> | Different devices edit money amount | **Do not last-write-wins.** Explicit conflict resolution/correcting event, audit trail |
> | One device deletes while other edits offline | Preserve tombstone + local conflict; never silently recreate |
> | Shared group split edited by two users | Group server validates current membership and version, and returns conflict for resolution |
> | Duplicate imported payment by SMS + manual | Detect and offer `Link to existing` without deleting user data |
> | Backend rejects group mutation after membership removed | Keep local record labeled `Personal copy / Not shared`, with an explanation; offer export |
> | Cloud quota or 429 | Respect retry-after, jittered exponential backoff, smaller batches |
> | Cloud token expired | Local works; queue paused until sign-in |
> | Restore on different phone | Verify snapshot + identities; preserve and reconcile local unsynced data, never silently overwrite |
> | Multiple currencies | Integer minor units with currency-specific scale and FX conversion represented as explicit paired entries; don't sum incompatible currencies naively |
> | Clock drift / timezone shift | Use server sequence for sync; dates retain local transaction context/timezone |
> | Broken migration / partial restore | Transactional migration, checksum/snapshot validation, rollback or recoverable read-only mode |

**R0114 · §16 · P1 · M5 · PLANNED/reference · source L378**

> ### 16.6 Backup, export and restore are separate things

**R0115 · §16 · P1 · M5 · PLANNED/reference · source L380**

> - **Sync:** ongoing multi-device data reconciliation, including deletion and edit propagation.
> - **Cloud backup:** recoverable last-known state, with version history/snapshots if the provider supports them; show last successful checkpoint and account.
> - **User export:** downloadable/shareable data in CSV, JSON and optional PDF, with explicit scope and password-protection choices.
> - **Restore:** validated data import or recovery, including duplicates, partial failures, collisions, schema upgrades and dry-run preview. Never equate a cloud login to guaranteed recovery of data that was never synchronized.


### Source 17. Due-date reminders: local-first scheduler + optional FCM

**R0116 · §17 · P1 · M4 · PLANNED/reference · source L385**

> ## 17. Due-date reminders: local-first scheduler + optional FCM

**R0117 · §17 · P1 · M4 · PLANNED/reference · source L387**

> ### 17.1 Supported reminder types

**R0118 · §17 · P1 · M4 · PLANNED/reference · source L389**

> | Source | Trigger data required | Behaviors |
> |---|---|---|
> | Money you owe | Principal, remaining balance, due date, person, timezone | 7/3/1 days before, due day, optional overdue; stop when settled |
> | Money owed to you | Same fields plus optional contact | Gentle personal reminder; optionally draft a WhatsApp request; never send automatically |
> | Credit-card payment | User-entered statement due date or verified statement import, outstanding bill, account | Advance notifications, payment confirmation, partial payment and next cycle |
> | Loan/EMI | Contracted due date, schedule, installment, optional interest details | Future occurrence, missed payment and reschedule handling |
> | Subscription/bill | Confirmed recurrence rule, next due date, amount | Upcoming charge, paid/skip/snooze, renewal change |
> | Savings deadline | Goal deadline and amount remaining | Gentle cadence, no false “missed bill” urgency |
> | Budget | Thresholds (e.g. 70%, 90%, 100%), period | Debounced/suppressed repeats, avoid blame-heavy messages |

**R0119 · §17 · P1 · M4 · PLANNED/reference · source L399**

> A **credit-card balance alone does not reveal its payment due date**; do not invent dates. The archive's `REMINDER-SOURCE-OF-TRUTH.md` already identifies the missing credit-card billing-cycle and subscription-date fields; add explicit schedule entities rather than guessing.

**R0120 · §17 · P1 · M4 · PLANNED/reference · source L401**

> ### 17.2 Two delivery channels, one logical reminder

**R0121 · §17 · P1 · M4 · PLANNED/reference · source L403**

> - **Offline local:** persist user-confirmed schedule in Room, plan inexact alarms / WorkManager + rescheduling checks as appropriate, and present an in-app due-soon inbox even if OS notification permission is denied. On reboot, app upgrade, time zone/date/time changes and permission change, rebuild pending schedule. Use exact alarms only when the feature truly requires them and the policy/permission permits it; routine bill reminders generally do not need exact-to-the-minute alarms.
> - **Cloud optional:** backend scheduler (e.g. trusted server cron / queue) queries only due records for opted-in devices, uses FCM to signal/show reminders, with service credentials exclusively on server. Register/rotate/revoke device tokens securely. FCM is best-effort and not an authoritative clock or database.
> - **Deduplication:** stable `reminder_occurrence_key = account_id + reminder_id + occurrence_date + kind`, unique server emission record and client presentation/ack log. Suppress or collapse duplicates from local and FCM when possible. Race conditions can still exist; test carefully.
> - **Privacy:** allow `Hide amounts and names on lock screen`; notification can read `Traxos reminder · Open app to view`. No sensitive transaction text in FCM data payload where avoidable.
> - **User controls:** per-category on/off, days before, notification channels, sound/vibration, quiet hours, snooze, weekend rule, timezone, previews, summary digest vs separate notifications. If no permission, show clear non-blocking in-app reminders.
> - **Lifecycle:** edit due date → invalidate old occurrence; paid/settled/cancelled → cancel future; partial payment → update remaining balance; completed recurring item → schedule next based on actual recurrence rule. Never turn a schedule reminder into a posted financial transaction automatically without a confirmed or consented rule.

**R0122 · §17 · P1 · M4 · PLANNED/reference · source L410**

> ### 17.3 User-friendly reminder examples

**R0123 · §17 · P1 · M4 · PLANNED/reference · source L412**

> - `Credit card · Payment due in 3 days` → `View bill` / `Mark paid` (marking paid must ask for funding account and amount to properly post transfer).
> - `Aman owes you ₹1,250 · Due today` → `View` / `Draft reminder`. Do not send messages without a user action.
> - `Spotify may renew tomorrow · ₹119` → `Review subscription` / `Snooze`.


### Source 18. Smart recurring subscriptions: suggestion, confirmation, reconciliation

**R0124 · §18 · P1 · M4 · PLANNED/reference · source L416**

> ## 18. Smart recurring subscriptions: suggestion, confirmation, reconciliation

**R0125 · §18 · P1 · M4 · PLANNED/reference · source L418**

> ### 18.1 Discovery

**R0126 · §18 · P1 · M4 · PLANNED/reference · source L420**

> With explicit opt-in, analyze **only financial candidates already permissioned/imported** and previously confirmed transactions. Use explainable local heuristics: similar normalized merchant + repeated amount band + cadence (weekly/monthly/yearly) + minimum occurrences. Present confidence and rationale, not a fake deterministic assertion. Examples: `You paid ₹149 to the same merchant three months in a row. Track as a subscription?` with `Yes / Edit / Ignore / Never suggest`.

**R0127 · §18 · P1 · M4 · PLANNED/reference · source L422**

> Keep `subscription_rule` fields: id, label, merchant/matcher rule, amount/currency or variable amount, start_date, cadence with anchor policy, next_expected_date, timezone, account, category, reminder offsets, status (`active|paused|cancelled`), confidence/source, user consent scope, `auto_create_mode`, last_verified_at, last_match_transaction_id.

**R0128 · §18 · P1 · M4 · PLANNED/reference · source L424**

> ### 18.2 Modes and safeguards

**R0129 · §18 · P1 · M4 · PLANNED/reference · source L426**

> - **Reminder only (default):** notify before expected renewal; never post a transaction for prediction alone.
> - **Draft next occurrence:** create an unposted candidate for one-tap confirmation.
> - **Auto-post** only after explicit informed per-subscription consent for predictable transactions, with `estimated/unverified` label, easy reversal, duplicate matching and periodic reconfirmation. For card/UPI merchant charges, prefer reconcile-with-evidence and never count a scheduled estimate plus bank SMS as two expenses.
> - When actual charge differs (tax/promo/price increase), store the actual value and ask whether to update the template. When the charge never appears, retain status `expected / not verified`, not `paid`.
> - Cancellation flow: stop reminders and future drafts. **Do not claim cancelling in Traxos cancels the external merchant subscription**; provide plain-language guidance to cancel with merchant.
> - Handle free trials, non-monthly billing, annual renewals, mid-cycle cancellations, price changes, end-of-month dates (31st → February policy), daylight-saving shifts, mismatched currency, refunded charges, shared-family subscriptions, duplicate SMS, bank notification delays, and changing primary payment account.


### Source 19. Complete Settings information architecture and data ownership

**R0130 · §19 · P1 · M2–M6 · PLANNED/reference · source L433**

> ## 19. Complete Settings information architecture and data ownership

**R0131 · §19 · P1 · M2–M6 · PLANNED/reference · source L435**

> Keep settings navigable with simple categories. Every row has a clear purpose, value, and confirmation where harmful. Settings must remain usable offline; locally editable preferences sync only if opted in. Tag device-specific settings vs account-wide settings to avoid surprising second-device changes.

**R0132 · §19 · P1 · M2–M6 · PLANNED/reference · source L437**

> | Settings area | Controls and behavior | Sync scope |
> |---|---|---|
> | **Profile** | Display name, avatar/initials, preferred language, home greeting, linked account, verify email, change password, sign out, delete cloud account | Cloud if signed in; local name for guest |
> | **Appearance** | System/Light/Dark, deep-navy accent options, compact/comfortable density, font respects Android scaling, reduced motion, haptic feedback, native date formats, first day of week | Theme can follow user across devices; density/device accessibility stays local |
> | **Launcher identity** | Official Traxos name and supplied T logo by default; optional **prepackaged** neutral name/icon variants (e.g. `Finance`, `Notebook`) for discretion, if tested | Device-only |
> | **Navigation** | Fixed Home and Add; optional arrangement of Activity, Groups, Plans among eligible slots with no duplicates and preview; reset layout | Local or user preference after careful design |
> | **Currency and regional** | Default display currency, currency per account, date format, Indian number formatting, accounting vs ordinary negative display, timezone | Account data cloud; purely visual preferences optionally synced |
> | **Accounts and balances** | Create/rename/reorder/archive accounts, icon/color, opening balance adjustment with audit reason, hide account from Home totals, mask account balances, default account, cash/bank/card types | Financial account records sync; temporary privacy mask local |
> | **Transactions** | Default entry type/account/category, quick amount presets, ask confirmation for smart suggestions, duplicate detection level, default list range, date/account filters, “repeat last” | Personal rules sync only if explicitly enabled |
> | **Smart SMS import** | Enable/disable with platform rationale, review inbox, approved banks/senders, rules, category matching, dismiss history and local parser diagnostics, purge raw SMS | Device permission and raw SMS strictly local; confirmed ledger data may sync |
> | **Voice & quick actions** | Voice language and offline availability, speech preview/confirm, launcher shortcut/widget/tile, experimental volume trigger with device compatibility warning | Device-only |
> | **Notifications** | All alerts master toggle, debt/credit, card, EMI, subscription, savings, budget, group, import notifications; lead times, quiet hours, snooze, sound, vibration, lock-screen masking, test notification | Schedules from financial records sync; device permission/channel settings local |
> | **Security** | App lock, fingerprint/face availability, phone credential, Traxos PIN setup/change, auto-lock timeout, re-auth sensitive actions, hide app-switcher previews, copy-to-clipboard privacy, active device list / revoke sessions, recent security events | App lock local; session revocation cloud when connected |
> | **Sync & backup** | Guest/account link, sync on any network/Wi-Fi/manual, cellular/roaming/attachment preferences, pending count, last backup, `Sync now`, queue diagnostics, restore/merge preview, trusted devices | Account-backed state plus local device policy |
> | **Export/import** | Export ledger filtered/all, CSV, JSON, PDF summary, date/account/category, optional encryption, file share/save, verify backup, re-import validation, localized file names | On demand, user controlled |
> | **Privacy & data** | Permission dashboard, SMS retention, analytics opt-out, crash reports opt-in or minimized, revoke permissions deep link, delete raw import candidates, cloud-data download/delete, data retention info | Both local/cloud as explicit |
> | **Help & about** | Version/build, changelog, FAQ, feedback, bug report with redacted diagnostics, rate app, legal/privacy, open-source licenses, restore support | N/A |

**R0133 · §19 · P1 · M2–M6 · PLANNED/reference · source L455**

> **Critical distinction about app name and icon:** Changing the **global APK package name** or signing identity is a developer build/release operation, not a normal user setting. Android supports launcher aliases with **predefined** labels/icons; apps cannot reliably accept an arbitrary typed launcher name or uploaded replacement icon across all launchers. The Traxos brand asset should remain canonical; any alternative is a separate, preapproved launcher alias. Some launchers cache icons, so the UI should explain that changes may appear after the launcher refreshes.

**R0134 · §19 · P1 · M2–M6 · PLANNED/reference · source L457**

> **Currency changes:** A default currency switch is **not** an automatic conversion of every transaction. Original amounts/currency remain immutable; show per-account amounts and optional converted reporting only when a user has opted into an FX-rate source and rate-date methodology. Offline FX reports must flag last-known rate and never silently invent exchange values.

**R0135 · §19 · P1 · M2–M6 · PLANNED/reference · source L459**

> **Hidden account definition must be unambiguous:** `Mask balance on screen` protects privacy; `Exclude from Home total` affects only chosen summary views (not ledger/report integrity); `Archive account` blocks new entries and preserves history. Never let users hide an account and unknowingly remove it from export/backups.

**R0136 · §19 · P1 · M2–M6 · PLANNED/reference · source L461**

> ### 19.1 Detailed settings acceptance

**R0137 · §19 · P1 · M2–M6 · PLANNED/reference · source L463**

> - Unsaved name/theme/profile changes survive process death when locally committed; show save status and resolve cloud conflict intelligently.
> - Different devices may have different permission grants and different auto-lock durations without overwriting one another.
> - Disabled notification type stops future reminders and cancels queued OS occurrences in that category; existing in-app due history remains available as appropriate.
> - Clearing SMS history does not delete confirmed ledger transactions.
> - Deleting the account offers a separate decision for **local records**; data loss warning is concrete and cannot be bypassed by accidental tapping.
> - Export selections show range, count estimate, sensitive-data warning, progress, cancellation, share intent and completion/error.


### Source 20. Finance domain correctness: transactions, cards, debts and groups

**R0138 · §20 · P0 · M2 · PLANNED/reference · source L470**

> ## 20. Finance domain correctness: transactions, cards, debts and groups

**R0139 · §20 · P0 · M2 · PLANNED/reference · source L472**

> ### 20.1 Minimum domain record design

**R0140 · §20 · P0 · M2 · PLANNED/reference · source L474**

> Use integer amounts in currency minor units (`amount_minor: Long` where legal/technical ranges permit) plus ISO 4217 currency, with decimal-aware validation and safe formatting; use high-precision types for FX calculations. Persist stable ids, provenance (`manual|sms|voice|schedule|group|import`), lifecycle state, timestamp and timezone, category, merchant, notes, account, group linkage, attachment references, creation/edit audit metadata, revision and origin device. Use robust constraints and unique operation IDs. **Never use binary floating point for ledger balances.**

**R0141 · §20 · P0 · M2 · PLANNED/reference · source L476**

> Core logical entities: `local_profile`, `cloud_identity`, `account`, `transaction`, `transaction_leg`, `category`, `budget`, `savings_goal`, `savings_allocation`, `contact`, `debt_thread`, `debt_event`, `recurring_rule`, `subscription_rule`, `reminder_occurrence`, `sms_candidate`, `merchant_rule`, `group`, `group_member`, `group_expense`, `split_share`, `settlement`, `attachment`, `sync_operation`, `sync_cursor`, `audit_event`, `device_preferences` and `user_preferences`. Implementation may normalize differently but must preserve semantics and referential integrity.

**R0142 · §20 · P0 · M2 · PLANNED/reference · source L478**

> ### 20.2 Rules that prevent misleading reports

**R0143 · §20 · P0 · M2 · PLANNED/reference · source L480**

> - Expense reduces an owned account and counts toward spending; income increases it.
> - Transfer between own accounts is **not** income or expense; credit-card **payment** is normally a transfer/settlement, not a second expense.
> - Card purchase creates a spending event and liability; statement payment reduces that liability. Avoid double-counting.
> - Debt principal lent/received, repayments, fee/interest and gifts have distinct types. A repayment to the user is generally return of principal, not new earned income.
> - In group expense: person paying ₹1,200 for four equal participants owes ₹300 personally; ₹900 is receivable. Show account cash movement vs personal consumption separately. A partial settlement updates receivable and does not create new income.
> - Savings goal can represent a **virtual allocation** or real transfer to another account; label mode clearly and avoid double-counting net worth.
> - Refunds link to original transaction, update reports consistently with user preference, and do not create fake salary/income.
> - Support split transactions across categories, partial repayments, soft delete/undo, adjustments, and reversals with an audit trail.
> - For non-INR accounts or groups with mixed currencies, disallow automatic cross-currency settlement until an explicit rate + rounding policy is implemented.

**R0144 · §20 · P0 · M2 · PLANNED/reference · source L490**

> ### 20.3 Debts, contacts and WhatsApp-share safety

**R0145 · §20 · P0 · M2 · PLANNED/reference · source L492**

> Contact model stores `display_name`, optional user-provided E.164 formatted phone number with country code, and an explicit contact-import permission flag; **do not require contacts access** to record a debt. Support manual name/number, link to an Android contact on explicit action, never bulk-upload address book. Editing a contact number must not retroactively alter the debt's financial provenance.

**R0146 · §20 · P0 · M2 · PLANNED/reference · source L494**

> **WhatsApp share flow:** Debt detail → `Remind` → review populated message with amount, reason, due date, paid/remaining figures, currency and contact → user chooses WhatsApp or Android share sheet → user manually taps Send. For known valid recipient number, open a supported WhatsApp chat deep link / intent with an encoded draft; if WhatsApp is absent, launch chooser or copy text. The app **cannot promise auto-sending** WhatsApp messages from a consumer APK, nor claim successful delivery merely from opening an intent. Creating a draft may work offline, but actual messaging depends on WhatsApp and recipient connectivity.

**R0147 · §20 · P0 · M2 · PLANNED/reference · source L496**

> Example draft (editable, user-initiated):

**R0148 · §20 · P0 · M2 · PLANNED/reference · source L498**

> > Hey Aman, just a reminder about ₹1,250 for the shared dinner on 8 November. The remaining balance is ₹1,250, due on 15 November. Please let me know once you've paid. Thanks!

**R0149 · §20 · P0 · M2 · PLANNED/reference · source L500**

> Alternative polite/direct tones, Hindi/Hinglish/English, and optional payment reference can be offered. Do not put full bank numbers or private notes into auto-generated messages. Never send anything based on an unverified imported contact match. If the other person is owed money by the user, change the direction of wording; do not send accusatory reminders by mistake. Show preview with `To: +91…` and allow cancellation.

**R0150 · §20 · P0 · M2 · PLANNED/reference · source L502**

> **Group protections:** only authenticated server-validated members can see group records; invite codes/links must expire and be revocable; offline edits by removed members become local-only disputed drafts; settlements must be idempotent and auditable. Read-only/archived groups retain exportable history. No automatic real-money payment initiation is implied.


### Source 21. Native frontend, backend and database optimization

**R0151 · §21 · P0 · M1/M5/M7 · PLANNED/reference · source L504**

> ## 21. Native frontend, backend and database optimization

**R0152 · §21 · P0 · M1/M5/M7 · PLANNED/reference · source L506**

> ### 21.1 Compose UI performance and data flow

**R0153 · §21 · P0 · M1/M5/M7 · PLANNED/reference · source L508**

> - Architecture: Kotlin + Jetpack Compose, ViewModel, Kotlin Flow/StateFlow, coroutines, repository, Room DAO. Stable immutable UI models and well-defined event reducers. Keep network calls away from composable render paths.
> - Use `LazyColumn` / Paging 3 `paging-compose` for long ledgers, debt-event histories, smart inbox, group expenses, account selection where necessary. Room is the initial paging source; optional `RemoteMediator` only for source-of-truth local replication scenarios. Use **stable item keys**, placeholders, append/error states and proper scroll restoration.
> - Default initial activity page size target **30–50 rows** (tune on devices), prefetch as users scroll. Use cursor/keyset pagination for server endpoints; avoid `OFFSET` at large dataset depths. Do not load 100,000 records into the composable to filter client-side.
> - Search flows: trim/normalize input, **debounce ~250–350 ms** for typing; `distinctUntilChanged`; cancel stale queries with `flatMapLatest`; keep query/filter/date state on navigation and process recreation. **Do not debounce Save**, because a user expects an immediate durable commit. Prevent double-tap submissions through single-flight op identity and database uniqueness.
> - SQL indices: `(profile_id, occurred_at DESC, id DESC)`, `(profile_id, account_id, occurred_at)`, `(profile_id, category_id, occurred_at)`, `(group_id, occurred_at)`, `(sync_state, queued_at)`, unique `(profile_id, operation_id)`; add FTS on merchant/notes if needed with privacy/threat review. Validate query plans with realistic volume.
> - Aggregate spend/income/budget totals in SQL with indexed ranges/maintained projections, not repeated O(N) Kotlin scans or entire-ledger recompositions. Correctly invalidate projections upon edit/delete/refund.
> - Use `rememberSaveable` for short-lived view state and SavedStateHandle for navigation/query state; draft transactions persist securely in Room only if user enables draft recovery. Handle process death and rotation without losing user-typed amounts.
> - Images/receipts: decode scaled down, lazy-load, content URI grants, encrypted persistent storage, bounded thumbnails, low-memory handling; no bitmap decode on main thread.
> - Touch target accessibility: target at least **48 dp** for interactive controls; achieve a visually compact layout by keeping hit targets but reducing redundant internal cards and nested padding, not by creating tiny buttons.

**R0154 · §21 · P0 · M1/M5/M7 · PLANNED/reference · source L518**

> ### 21.2 Networking and backend performance

**R0155 · §21 · P0 · M1/M5/M7 · PLANNED/reference · source L520**

> - All endpoints support idempotency/validation; batch sync, paged pull, conditional metadata, compress larger payloads when beneficial, respect max body size and per-user rate limits.
> - Instrument p50/p95 latency and failure rates for locally redacted event classes: Quick Add commit, app cold start, activity search, 1000-row scroll, one sync batch, restoration time and offline recovery. No raw financial values in analytics.
> - Index database by user/group ownership and cursor; partition/archive large activity history only if observed scale warrants it. Use server-verified aggregated reports/materialized views where useful, while local report still works offline.
> - Avoid N+1 network queries for each transaction/person/category. Request compact snapshots and deltas; separate infrequent media from frequently edited ledger rows.
> - Implement retry with exponential backoff + jitter, Retry-After, token refresh single-flight, circuit breaker on repeated backend faults, and a useful user-facing error state without hammering the network.
> - App starts offline, with server down, with airplane mode, and during a slow 2G-like connection. UI must not freeze or require a loading spinner before showing cached records.
> - Ensure services use principle of least privilege, secure RLS, validated ownership, integration tests, change cursor retention policy and server-side observability; maintain signed release artifacts, secret rotation and dependency vulnerability monitoring.

**R0156 · §21 · P0 · M1/M5/M7 · PLANNED/reference · source L528**

> ### 21.3 Proposed targets (non-benchmarked)

**R0157 · §21 · P0 · M1/M5/M7 · PLANNED/reference · source L530**

> | Metric | Initial target / test condition |
> |---|---|
> | Manual Quick Add | Local commit typically under 300 ms on defined midrange devices, even offline |
> | Home render | Useful cached content visible without waiting on network; test cold/warm starts |
> | 50,000 rows | Search, filter, scroll, history paging without loading all rows or persistent jank |
> | Sync 10,000 queued operations | Resumable batches; no duplicates/omissions under simulated interruption |
> | Low memory | Recover drafts and navigation after process death; no corruption |
> | UI layout | No cropped primary actions at 200% font scale / narrow screens; responsive tablet layout |
> | Backups | Verify export/restore through roundtrip count, money totals, checksum and identity |


### Source 22. Compact premium Traxos visual system and interaction specifications

**R0158 · §22 · P0 · M1/M7 · PLANNED/reference · source L540**

> ## 22. Compact premium Traxos visual system and interaction specifications

**R0159 · §22 · P0 · M1/M7 · PLANNED/reference · source L542**

> ### 22.1 Brand direction

**R0160 · §22 · P0 · M1/M7 · PLANNED/reference · source L544**

> **Use the supplied exact mark**: five separate rounded squares forming a capital T (three top, two centered vertically); do not morph into a continuous bar or replace with a generic piggy-bank icon. Dark primary navy `#111B2F`, calm cobalt accent for primary actions, light neutral surface, restrained positive teal, amber for warnings, red only for destructive/overdue states. Support polished light and dark palettes and Android system theme; meet readable contrast in both. The system may use Material 3 under the hood, but components should look distinctly Traxos through typography, rhythm, icon language and motion—not an out-of-the-box template.

**R0161 · §22 · P0 · M1/M7 · PLANNED/reference · source L546**

> ### 22.2 Density without clumsiness

**R0162 · §22 · P0 · M1/M7 · PLANNED/reference · source L548**

> - Base layout spacing tokens: **4 / 8 / 12 / 16 / 20 / 24 dp**; most list-row horizontal gutters 16 dp, between label and value 8 dp, section spacing 16–20 dp; no automatic nested 24+24+24 padding. Use responsive minimum content widths and Android WindowSizeClass breakpoints.
> - Typical transaction row: one compact merchant/category icon; title (single-line ellipsize), account/date subordinate line, amount right-aligned with tabular numerals; no card border around every row. Tap exposes detail in a native bottom sheet, swipe actions only where discoverable and reversible.
> - Home: primary month-spend amount, small income/balance secondary items, pending-review chip, recent entries. If user has no savings/groups, don't show empty dashboard boxes; show a small actionable prompt only when relevant.
> - Bottom navigation: concise labels, visually stable indicator, no oversize floating buttons covering rows; Quick Add remains thumb-reachable. Profile/settings from top right. Horizontal `Filters` controls can scroll when necessary without hiding last-used selection.
> - Short confirmation dialog for destructive actions, bottom sheet for entry/edit/filter, full-screen for complex group split/restore/security, snackbar for undo. Prefer built-in navigation patterns; custom overlays should not mimic Android permission or biometric dialogs.

**R0163 · §22 · P0 · M1/M7 · PLANNED/reference · source L554**

> ### 22.3 Exact text-overflow and layout policy

**R0164 · §22 · P0 · M1/M7 · PLANNED/reference · source L556**

> | Component | Overflow rule |
> |---|---|
> | Transaction merchant | 1-line ellipsis; full title accessible on detail and TalkBack |
> | Notes / debt reason | Summary 2 lines with ellipsis; full expandable detail; export untruncated |
> | Currency amount | Tabular numbers, no uncontrolled shrinking to 8sp; large amount can wrap in details or use alternate compact formatting clearly labeled |
> | Contact display name | 1 line in list with ellipsis; original name shown in detail; handle Unicode/RTL |
> | Category chips | Max reasonable width, trailing ellipsis, selected state visible; filter sheet full text |
> | Popup / dialog | Constrained screen-safe width, scroll content as needed, button stack on narrow widths, never truncate primary confirmation wording |
> | Charts | Labels abbreviated only visually; accessible full-value table, not tooltip-only information |
> | Buttons | Avoid vague `OK`; use `Save`, `Add`, `Mark paid`, `Delete transaction`, `Cancel` |

**R0165 · §22 · P0 · M1/M7 · PLANNED/reference · source L567**

> Test extra-long names (100–500 characters), 200% Android font scale, landscape, small low-density phones, foldable screen widths, accessibility contrast, RTL/Hindi scripts, English/Hinglish, emoji, soft keyboard visible and long error messages. Use `WindowInsets` for status/navigation bars, IME and gesture bar so buttons stay accessible; no tappable elements under system UI. Never use fixed pixel heights for content that grows with font scale. Support Reduce Motion, TalkBack semantics, focus order, minimum touch areas and non-color-only status distinctions.

**R0166 · §22 · P0 · M1/M7 · PLANNED/reference · source L569**

> ### 22.4 Motion system—premium, quiet

**R0167 · §22 · P0 · M1/M7 · PLANNED/reference · source L571**

> A 120–180 ms press/selection transition, 180–260 ms sheet transition, 200–320 ms meaningful row transition are initial design tokens, not mandates. Animate one clear cause-and-effect: confirming Smart Add gently moves the candidate into Activity; saving shows a brief haptic confirmation; goal progress updates smoothly. Disable/decrease motion when Android accessibility requests it; do not animate every card and number on screen entry. Avoid pulsing logos, wobbly floating buttons, heavy gradients, floating glassmorphic overlays or AI-styled neon charts.

**R0168 · §22 · P0 · M1/M7 · PLANNED/reference · source L573**

> ### 22.5 Precise error and popup states

**R0169 · §22 · P0 · M1/M7 · PLANNED/reference · source L575**

> - `Saved on this phone. Backup pending.` and `Backup complete` are **different messages**; don't label a queue write “synced.”
> - Offline cloud auth/group invite: `Needs internet to join. You can keep tracking locally.` Preserve navigation state.
> - SMS suggestion: `Add / Edit / Ignore`; double-click action disable after first tap; undo if a valid candidate was saved.
> - Delete: `Move transaction to Recently deleted?` with impact explanation for related balance/group split, optional Undo; avoid modal for low-risk navigation.
> - Errors inline near the field (`Amount must be more than ₹0`) instead of stacking generic red toasts.
> - Conflicts need a dedicated, plain-language view (`This payment was edited on another device`) with version comparison and safe resolution.
> - Permissions denied: explain in context, do not repeatedly force settings redirect or block manual features.

**R0170 · §22 · P0 · M1/M7 · PLANNED/reference · source L583**

> ### 22.6 Screen inventory and necessary UI states

**R0171 · §22 · P0 · M1/M7 · PLANNED/reference · source L585**

> 1. Splash/loading (brief; not fake progress), guest welcome, optional account link, restore preview, PIN setup, OS biometric enable.
> 2. Home; Quick Add; voice draft; SMS suggestion notification/inbox; Activity list, search, filter and detail/edit; accounts overview/detail; internal transfers.
> 3. Groups list/details, invite/join, add group expense, split editor, settle, participants/permissions.
> 4. Plans landing, budget detail, savings goals/contributions, subscriptions, reminders due inbox.
> 5. Debts/credits list, person thread with installment timeline, add debt, contact editor, WhatsApp preview.
> 6. Settings: sections in §19, sync status/diagnostics, cloud account/profile, privacy/security, export/import, About, feedback.
> 7. States for each: first-use empty, loading from local DB, offline, stale cloud, recoverable error, permission denied, content locked, access revoked, conflict, syncing, paginating, destructive confirmation, long-text accessibility and successful undo.


### Source 23. Android capabilities and realistic distribution behavior

**R0172 · §23 · P1 · M4/M7 · PLANNED/reference · source L593**

> ## 23. Android capabilities and realistic distribution behavior

**R0173 · §23 · P1 · M4/M7 · PLANNED/reference · source L595**

> - **SMS access:** `READ_SMS/RECEIVE_SMS` restricted by Play policy; SMS-based money management is a documented possible exception subject to review/approval, not a blanket allowance. Submit declaration and narrow core-use justification; offer manual/share entry if permission denied. Process transaction-like messages locally and minimize/expire raw bodies. Exclude OTP/security codes. Never upload all SMS indiscriminately.
> - **Volume-button double press outside app:** cannot be made universally reliable for an ordinary background APK. Investigate AccessibilityService only with legitimate user benefit, explicit disclosure, policy compliance, kill switch and per-device compatibility tests. No hidden accessibility collection. Alternative launcher shortcuts, Quick Settings tile, homescreen widget and system share target must be first-class, reliable paths.
> - **App label/icon:** predeclared launcher `activity-alias` variants only; arbitrary user-uploaded launcher identity not reliable. Preserve default logo and fixed package identity.
> - **Notifications:** Android 13+ runtime permission; notification channel management and lock-screen privacy. Do not assume FCM will arrive exactly once or in order.
> - **Background:** WorkManager with constraints and best effort; OEM battery restrictions may delay or block tasks. Show diagnostic/help rather than declaring immediate sync guaranteed.
> - **Voice:** Android on-device speech availability, offline model download and language recognition differ by phone; offer typed preview and gracefully degrade without network.
> - **WhatsApp:** create editable user-initiated message draft and open supported app/chooser; sending is controlled by the user and recipient app, with no guaranteed offline delivery.
> - **Play Store:** review permissions/privacy policy, Data safety declaration, SDK target requirements, Accessibility policy, secure account deletion/data deletion requirements and Google Play review API guidance before publication.


### Source 24. Privacy-safe analytics, reliability, testing and release gates

**R0174 · §24 · P0 · M7 · PLANNED/reference · source L604**

> ## 24. Privacy-safe analytics, reliability, testing and release gates

**R0175 · §24 · P0 · M7 · PLANNED/reference · source L606**

> ### 24.1 Collect the minimum useful diagnostics

**R0176 · §24 · P0 · M7 · PLANNED/reference · source L608**

> App quality metrics may include crash-free sessions, screen start duration, number of sync operation retries, schema migration failures, battery/background sync delays, FCM token registration failures, and SMS parsing *aggregate accuracy* measured on **opted-in anonymized test samples**. Never transmit raw transaction details, bank SMS bodies or contacts merely to improve analytics. Offer `Send redacted diagnostic report` only with clear preview and user action.

**R0177 · §24 · P0 · M7 · PLANNED/reference · source L610**

> ### 24.2 Comprehensive QA / edge-case matrix

**R0178 · §24 · P0 · M7 · PLANNED/reference · source L612**

> | Domain | Edge cases that must pass |
> |---|---|
> | Unlock | No enrolled fingerprint; weak face only; PIN incorrect many times; biometric hardware temporarily unavailable; new fingerprint enrolled; OS lock-screen disabled; device reboot; process killed while locked; PIN forgotten, no data loss without designed recovery |
> | Sync | Offline 1/30/180 days; 1, 10, 10,000 pending operations; airplane mode toggled mid-batch; duplicate ack; HTTP 429/500; token expiry; 3 devices editing; deleted records and tombstones; expired cursor; revoked group; attachment partial upload; battery saver; metered link; timezone change |
> | Import | Duplicate debit SMS, refunded payment, credit vs debit, merchant alias, multiple banks, shared card, OTP text, sender forgery, no permission, user ignores suggestion, notification hidden, late SMS, repeat subscription detected twice |
> | Money | Decimal rounding, zero/huge/negative amount, INR and foreign currency, cash-to-bank transfer, card charge vs repayment, partial refund, monthly salary cycle, group split remainder paise, debt partial settlement, adjustment and undo across synced devices |
> | Reminder | Due date changed, settled before due, next month shorter, end of year, daylight savings/timezone, phone restarted, notification denied, FCM dropped/delayed, repeated cloud/local alert, quiet hours, recurring charge skipped |
> | UI | Small phone, tablet, foldable, large font, RTL, TalkBack, hardware keyboard, long names/amounts, keyboard covering CTA, back gesture during save, rapid taps, restored scroll/filter, dark-mode contrast, screen rotation |
> | Export/restore | Huge CSV and PDF, process death, no storage space, encrypted archive wrong password, tampered backup, duplicate IDs, old schema, incomplete cloud snapshot, restoring over local unsynced records, Android backup policy |
> | Contact/share | Missing number, invalid country code, WhatsApp absent, wrong recipient, unsupported special characters, short and extremely long reason, offline WhatsApp draft, user cancels chooser, mistakenly reversed lender/borrower direction |
> | Account settings | Rename archived account, hidden vs excluded account, 2 currencies, theme while app locked, offline profile-name edit, sign-out with pending changes, linking guest to existing cloud account, changing launcher alias after OS update |

**R0179 · §24 · P0 · M7 · PLANNED/reference · source L624**

> **Release blockers:** any reproducible financial data loss, unintentional duplicate charge, unauthenticated access to private ledger data, silent server overwrite of unsynced transaction, miscalculated group/debt balance, false `Backed up` state, forced internet sign-in for guest ledger, SMS access without consent/policy route, or a consented notification/share action that sends private material to the wrong recipient.

**R0180 · §24 · P0 · M7 · PLANNED/reference · source L626**

> ### 24.3 Proposed implementation order (risk-first)

**R0181 · §24 · P0 · M7 · PLANNED/reference · source L628**

> - **Milestone A — Correct ledger:** Room schema, finance state machine, atomic writes, migrations, secure local guest, manual entry, Activity paging, account/card/debt types, export/restore smoke tests.
> - **Milestone B — UI and privacy:** branded Compose components and states, accessibility/overflow, authentication (app PIN / OS biometrics), account masking, settings, budgets/goals and local reminders.
> - **Milestone C — sync architecture:** operation journal, backend authorization/RLS, batched push + paged pull, durable checkpoints, conflict workflows, restore and long-offline soak tests.
> - **Milestone D — low-effort automation:** SMS policy route and review, offline voice preview, subscriptions suggestions, notification categories, quick widget/tile/shortcut.
> - **Milestone E — collaboration:** groups, people/debt threads, partial settlements, WhatsApp share preview, online invitations, device FCM tokens and cloud reminders.
> - **Milestone F — pre-release hardening:** 10k-operation stress runs, OEM background compatibility, permission disclosures, accessibility audit, release signing, Play policy, privacy/security penetration review; optional experimental volume trigger only if validated.

**R0182 · §24 · P0 · M7 · PLANNED/reference · source L635**

> Do not prioritize cosmetic extras over a demonstrated stable, recoverable ledger. **The product is only “premium” when the simple paths remain reliable under bad connections, large datasets and system interruptions.**


### Source 25. Research / platform references for V2 extension

**R0183 · §25 · P2 · M7 · PLANNED/reference · source L637**

> ## 25. Research / platform references for V2 extension

**R0184 · §25 · P2 · M7 · PLANNED/reference · source L639**

> - AndroidX Biometric Prompt and authenticator/API restrictions: https://developer.android.com/reference/androidx/biometric/BiometricPrompt.PromptInfo.Builder
> - Android Keystore: https://developer.android.com/privacy-and-security/keystore
> - Android recommended cryptography: https://developer.android.com/privacy-and-security/cryptography
> - Android backup security: https://developer.android.com/privacy-and-security/risks/backup-best-practices
> - Android offline-first architecture: https://developer.android.com/topic/architecture/data-layer/offline-first
> - Compose lazy lists and Paging: https://developer.android.com/develop/ui/compose/lists
> - Room migration testing: https://developer.android.com/training/data-storage/room/migrating-db-versions
> - Android notification permission: https://developer.android.com/develop/ui/views/notifications/time-sensitive
> - Scheduling exact alarms / policy: https://developer.android.com/develop/background-work/services/alarms
> - FCM message priority and delivery limitations: https://firebase.google.com/docs/cloud-messaging/android-message-priority
> - FCM collapsible messages, no order guarantee: https://firebase.google.com/docs/cloud-messaging/customize-messages/collapsible-message-types
> - Google Play SMS permission exceptions: https://support.google.com/googleplay/android-developer/answer/10208820
> - Google Play in-app review guidance: https://developer.android.com/guide/playcore/in-app-review
> - Android launcher `activity-alias`: https://developer.android.com/guide/topics/manifest/activity-alias-element
> - Android PackageManager component enabling: https://developer.android.com/reference/android/content/pm/PackageManager
> - Android app shortcuts: https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts

**R0185 · §25 · P2 · M7 · PLANNED/reference · source L656**

> ### Product decisions still requiring implementation choices—not user blockers

**R0186 · §25 · P2 · M7 · PLANNED/reference · source L658**

> **Recommended defaults:** Kotlin + Compose; minSdk chosen after device-market testing (API 26+ considered initially); Room with tested encrypted-at-rest integration; Supabase Auth/PostgreSQL as optional cloud backend; FCM for optional remote alerts; WorkManager for sync; encrypted user-created JSON backup as recovery option; system / Android credential fallback; 6-digit Traxos PIN preferred; INR as regional default; concise reminders; SMS subscription suggestions review-only; app icon stays the supplied T mark; no cloud AI dependency.

**R0187 · §25 · P2 · M7 · PLANNED/reference · source L660**

> **Open choices for the engineering team:** release channel (Play vs sideload), regulatory assessment for SMS and sensitive financial data, exact DB encryption library/API combination, specific cloud encryption/recovery posture, selected minimum Android SDK, user-facing E2EE scope, multi-device simultaneous conflict UX, duration and storage cost of backups/snapshots. These choices must be written down and tested before publication, not inferred from the old website's implementation details.

**R0188 · §25 · P2 · M7 · PLANNED/reference · source L662**

> ---

**R0189 · §25 · P2 · M7 · PLANNED/reference · source L664**

> # V3 EXTENSION — RECOVERY, DISTRIBUTION, MONETIZATION, FINANCIAL WORKFLOWS AND PRODUCTION HARDENING

**R0190 · §25 · P2 · M7 · PLANNED/reference · source L666**

> **V3 review date:** 11 November 2026. **Status:** requirements and implementation blueprint; not a statement that the APK exists or has passed tests. **Scope:** adds mandatory requirements to V2 sections 1–25. V3 takes precedence where advice conflicts. The number ranges and timings below are proposed targets until measured on actual devices and infrastructure. Primary sources are linked in section 41.


### Source 26. Product-level decisions and hard constraints

**R0191 · §26 · P0 · M1–M7 · PLANNED/reference · source L668**

> ## 26. Product-level decisions and hard constraints

**R0192 · §26 · P0 · M1–M7 · PLANNED/reference · source L670**

> **Separate four concepts:** (A) transaction ownership, (B) app version support, (C) purchase entitlement, and (D) cloud account/session state. One state must never masquerade as another.

**R0193 · §26 · P0 · M1–M7 · PLANNED/reference · source L672**

> 1. The **ledger must not depend on internet** for new personal transactions, edits, filtering, offline reminders already scheduled and local account balancing. Each operation commits in Room and records an outbox event in *one* database transaction.
> 2. A **cloud account** enables encrypted-in-transit backup, recovery, optional multiple devices and group sharing. A guest must be told plainly that an unsynced device is the only copy of their data.
> 3. An **app version gate** may block unsafe *online operations* and, only when necessary, suspend selected client features. Never make a remotely triggered version block accidentally destroy or overwrite unsynced user content. Preserve read/export/recovery even during security incidents where feasible.
> 4. A **paid entitlement** controls premium features. Product policy must specify exactly what survives subscription expiration. Recommended: users retain view, manual export, security unlocking, and local data access; restrict paid automation, creation past an advertised limit, cloud/group features as appropriate. If leadership chooses paid-only access, explicitly disclose the offline license-check window and still provide a path to retrieve user data.
> 5. **Cannot guarantee absolute anti-piracy** in an indefinitely offline, user-controlled APK. Server-authoritative purchases and bounded offline leases can limit fraud, with unavoidable trade-offs against offline freedom.
> 6. **App-internal paid subscription** (Traxos Pro) is different from a **financial subscription tracked by the app** (e.g., streaming, gym, software). Names, billing data models, notifications and UI must not intermingle.
> 7. **Do not claim guaranteed data recovery, instant background sync, perfect notification delivery, 100% crash freedom or tamper-proof access**. Give exact guarantees only where the architecture supports them.

**R0194 · §26 · P0 · M1–M7 · PLANNED/reference · source L680**

> ### Priority change to feature roadmap

**R0195 · §26 · P0 · M1–M7 · PLANNED/reference · source L682**

> **P0 release blockers:** user data portability after lapse; safe reinstall restore path; version compatibility; double-save prevention; financial credit card accounting; recurring rule editing; export feedback; complete back navigation; secure entitlement verification (if charging); and automated restore/migration tests.

**R0196 · §26 · P0 · M1–M7 · PLANNED/reference · source L684**

> **P1:** optional Android Auto Backup integration with exclusions and restore QA; version rollout dashboard; multi-version database migrations; large-account reporting; robust lock-screen reminder privacy; manual merchant/subscription linking.

**R0197 · §26 · P0 · M1–M7 · PLANNED/reference · source L686**

> **P2:** device-local export schedules with user-selected folder and Android SAF permissions (only as far as API constraints allow); optional remote revocation of a stolen device's future cloud access; signed release channel for direct APK distribution.


### Source 27. Surviving uninstall, Clear storage, device loss and reinstall

**R0198 · §27 · P1 · M3/M5 · PLANNED/reference · source L688**

> ## 27. Surviving uninstall, Clear storage, device loss and reinstall

**R0199 · §27 · P1 · M3/M5 · PLANNED/reference · source L690**

> ### 27.1 Data-loss / recovery matrix

**R0200 · §27 · P1 · M3/M5 · PLANNED/reference · source L692**

> | Event | What happens | Recoverability | Product action |
> |---|---|---|---|
> | App process dies / phone restarts | Room database normally persists | Usually automatic | Transaction atomicity, journal replay, resume UI drafts |
> | App cache cleared | Internal persistent DB normally remains | Usually automatic | Rebuild cache; never keep authoritative records only in cache |
> | Android Settings → Clear storage/data | App-private DB, preferences, tokens and keys are cleared | Only an independent backup can restore | Onboarding offers sign-in restore or user-selected backup-file import |
> | App uninstalled | Local app data normally removed | Only independent backup / eligible Android restore | Recovery onboarding, show last cloud snapshot and counts |
> | Phone stolen, destroyed, factory reset | Local copy gone | Cloud or external export only | Clear unbacked-up warnings before incidents; no false assurance |
> | Cloud account signed out | Local data still on device unless user explicitly chooses removal | Yes if retained | Separate **Sign out** from **Delete device data** |
> | User deletes cloud account | Depends on retention and explicit deletion | Not guaranteed after delete | Pre-delete export flow, retention disclosure, proper data-deletion policy |
> | Cloud sync stopped for 6 months | Device copy retained, but not protected against loss | Yes only if device remains | Persistent backup status, bounded batching on reconnection |
> | Forgot local app PIN | Local keystore/biometric/backup policy determines recovery | Not always | Confirm safe recovery design before enforcing PIN; never bypass encryption secretly |
> | Forgot encrypted-export password | Portable backup encryption remains secure | Generally not recoverable unless another copy exists | Upfront warning; optional distinct recovery key workflow |
> | Android Auto Backup available | OS may restore eligible data during setup | Conditional; quotas, policies, device and encryption affect outcome | Treat as auxiliary, not primary backup guarantee |

**R0201 · §27 · P1 · M3/M5 · PLANNED/reference · source L706**

> ### 27.2 Recovery tiers and recommended defaults

**R0202 · §27 · P1 · M3/M5 · PLANNED/reference · source L708**

> **Tier 1 — Cloud backup (recommended for customers who consent):** user signs in, all committed local operations upload as durable, resumable batches, periodic remote snapshots/reconciliations, and restores can rebuild a new device from server state. Save server-side schema versions, immutable IDs, audit metadata and soft-deletion tombstones. Use TLS, least-privilege access, authenticated requests, per-user row isolation and protected secrets. Clearly define whether remote operators can decrypt any financial content; ordinary authenticated cloud sync is **not automatically end-to-end encrypted**.

**R0203 · §27 · P1 · M3/M5 · PLANNED/reference · source L710**

> **Tier 2 — Portable encrypted backup (must offer to every user):** create a versioned `.traxosbackup` archive via Android Storage Access Framework `ACTION_CREATE_DOCUMENT` so the user chooses a destination outside app-private storage. Include manifest/version, currencies, accounts, transactions, recurring rules, debt/credit state, savings, groups as appropriate, settings needed for restoration, content checksums and optional attachment manifest. Avoid bundling authentication tokens, biometric keys, device IDs, irrelevant SMS and FCM tokens. Use strong passphrase-based encryption with a maintained, vetted KDF such as Argon2id or scrypt, a random salt, authenticated encryption such as AES-256-GCM and a random nonce per encryption operation. Document key-derivation parameters and archive format; do not invent custom cryptography. Show restore preview (record counts, date span, currency, schema version) before modifying local data.

**R0204 · §27 · P1 · M3/M5 · PLANNED/reference · source L712**

> **Tier 3 — Android system backup (best-effort supplement):** review Android `android:allowBackup`, API 31+ `android:dataExtractionRules`, older `fullBackupContent` and transfer-vs-cloud semantics. Android Auto Backup has quotas and is not immediate. By default it can include app databases/preferences, but encrypted DB material that relies exclusively on a device-bound Android Keystore key may be unreadable after restore. **Never back up ciphertext with no recoverable key as if it were recoverable.** Explicitly exclude device credentials, device-specific secrets, local sessions and ephemeral caches; choose a coherent database include/exclude strategy and test different OS versions. Do not presume `allowBackup=false` eliminates all device-to-device transfer on all manufacturers.

**R0205 · §27 · P1 · M3/M5 · PLANNED/reference · source L714**

> **Warning for architecture:** an Android Keystore-backed key is non-exportable, making it suitable for device-local secrets but unsuitable by itself as the sole way to decrypt a portable backup after reinstall. The backup encryption key must instead derive from a user-controlled secret, recovery key, or documented server-recoverable envelope-key design. Distinguish device encryption from portable backup encryption.

**R0206 · §27 · P1 · M3/M5 · PLANNED/reference · source L716**

> ### 27.3 Backup settings and smart nudges

**R0207 · §27 · P1 · M3/M5 · PLANNED/reference · source L718**

> - Status: `Protected — last backup 10:42 AM`, `Saved on this phone — 42 changes waiting`, `Not backed up — all records are only on this device`, `Backup error — retry`, `Restore incomplete — 3 records need review`.
> - Trigger sync after authenticated account creation/linking, local mutations (debounced), reconnected network, app foreground and periodic OS-permitted work. Do not send banking SMS contents remotely by default.
> - Backup health: last confirmed server version/checkpoint; pending outbox count; last error; encrypted export creation date if user authorized storing such local metadata. Never label a server upload "fully backed up" until verification/acknowledgment.
> - Nudge guest users **after** they have meaningful data (e.g., 10 transactions) and then sparingly, not every launch. Offer `Set up backup` and `Export encrypted file`, dismiss/snooze controls.
> - If user clears data, **do not auto-create a fresh conflicting cloud account**. Offer account sign-in and preview; safely merge guest-on-device records with existing cloud records using source identities and explicit conflict rules.
> - Recovery of group membership and shared financial records requires reauthentication to the group account, authorization checks and pulling only currently permitted data.
> - Offline export of large histories writes streaming records, reports bytes/records progressed, safely disposes of partial output on failure where possible, can be cancelled, and never reads all rows into RAM.

**R0208 · §27 · P1 · M3/M5 · PLANNED/reference · source L726**

> ### 27.4 Restore modes

**R0209 · §27 · P1 · M3/M5 · PLANNED/reference · source L728**

> 1. **Restore into empty account/device** — validate and hydrate in staging; commit to active database only after integrity and schema checks.
> 2. **Merge with existing local records** — match immutable IDs; detect overlapping independent datasets and duplicates, keep conflicting copies in a review queue, preserve pending outbox; preview merges.
> 3. **Replace existing data** — require explicit typed confirmation and **first** create a verified emergency export/snapshot if space and permissions permit; otherwise warn that replacement is destructive.
> 4. **Recover cloud data after app reinstall** — authenticate, download metadata preview, paginated snapshot and delta, reestablish per-device keys/biometrics, prompt permissions anew, re-register FCM token; account link must not overwrite a local guest.
> 5. **Restore previous app version backup** — versioned readers and tested migrations; disallow incompatible downgrades, with useful instructions, not a crash.


### Source 28. Mandatory updates, remote governance and distribution channels

**R0210 · §28 · P2 · M6 · PLANNED/reference · source L734**

> ## 28. Mandatory updates, remote governance and distribution channels

**R0211 · §28 · P2 · M6 · PLANNED/reference · source L736**

> ### 28.1 Update types

**R0212 · §28 · P2 · M6 · PLANNED/reference · source L738**

> - **Optional:** `latestVersionCode > installedCode`, marketing or quality improvements → dismissible prompt with `Update` and `Later`.
> - **Recommended:** near security retirement → show contextual banner, warn about loss of cloud compatibility, but preserve local access.
> - **Mandatory:** `installedCode < minimumSupportedCode` due to actual incompatibility or security issue → clearly explain requirement, show changelog / update action. For a Play-installed app use Play Core's *immediate in-app update* when supported; otherwise direct to official Play listing. For a sideloaded build, use its verified signed update-distribution channel; Play Core update flow may not be available.
> - **Emergency server protocol gate:** independently reject unsupported versions for **server writes** and provide a machine-readable `UPDATE_REQUIRED` response. Clients must retain unsynced operations, never silently discard/overwrite them.
> - **Maintenance mode:** distinguish `Cloud temporarily unavailable` from `Local expense tracker unavailable`; allow offline ledger operations whenever safe.

**R0213 · §28 · P2 · M6 · PLANNED/reference · source L744**

> ### 28.2 Remote Config / admin parameters (examples, not secrets)

**R0214 · §28 · P2 · M6 · PLANNED/reference · source L746**

> | Parameter | Purpose | Caution |
> |---|---|---|
> | `min_supported_version_code` | Minimum supported client build | Integer compare, staged rollout |
> | `latest_version_code` | Optional upgrade reminder | Respect snooze settings |
> | `min_sync_protocol` / `max_sync_protocol` | Server compatibility contract | Do not publish unsupported migration prematurely |
> | `force_update_reason` | Human reason shown on update screen | No false urgency |
> | `maintenance_scope` | Cloud-only maintenance, export-only, emergency operations | Never pretend local records vanished |
> | `sms_suggestions_enabled` | Disable broken parser rules quickly | Respect user consent |
> | `smart_subscription_threshold` | Threshold for local candidates | Feature flag is not an entitlement |
> | `import_max_batch_operations` | Adapt sync batch size | Bound by request size and backend enforcement |
> | `feature_rollout_percentage` | Safe staged activation | Keep stable experiment assignment |
> | `notice_version` | Privacy/security notice publication | Do not use it to bypass permission consent |

**R0215 · §28 · P2 · M6 · PLANNED/reference · source L759**

> **Remote Config is not a secure policy authority for payments or authorization; the client can inspect values.** Entitlements, privileges, user limits and write permissions must be enforced on the backend using verified identity and trusted purchase data. Adopt a separate admin console with RBAC, MFA, dual approval for production kill switches and append-only audit trail.

**R0216 · §28 · P2 · M6 · PLANNED/reference · source L761**

> ### 28.3 Offline update paradox

**R0217 · §28 · P2 · M6 · PLANNED/reference · source L763**

> - The app cannot detect a future remotely enforced minimum version when it has never received that configuration.
> - Offline-first and an always-fresh remote mandatory-version lock are logically incompatible. Choose a policy: recommended **continue safe local operations** on cached policy, disallow server calls once reconnected until updated. For a severe known vulnerability, locally cached policy can trigger a guard if it was received previously.
> - Avoid a 7-day offline internet lockout. If security policy eventually requires limited offline entitlement, disclose it precisely and still permit export of user's data.
> - Keep APK application ID and signing continuity for in-place updates. Never encourage uninstall/reinstall as a routine update path, since uninstall clears local data.

**R0218 · §28 · P2 · M6 · PLANNED/reference · source L768**

> ### 28.4 Rollout and rollback

**R0219 · §28 · P2 · M6 · PLANNED/reference · source L770**

> Sign releases with managed signing keys and separation of test/production environments. Use internal/closed testing, staged Play rollout, crash/ANR and financial-event integrity gates, pause rollout if adverse changes appear. Maintain backward-compatible server APIs for a defined transition window, reversible feature flags and validated Room migrations; **database rollback usually needs forward-fix migration, not installing an older APK**. Public incident status communications must not expose private user data.


### Source 29. Monetization, paid access and misuse protection

**R0220 · §29 · P2 · M6 · PLANNED/reference · source L772**

> ## 29. Monetization, paid access and misuse protection

**R0221 · §29 · P2 · M6 · PLANNED/reference · source L774**

> ### 29.1 Choose business model explicitly

**R0222 · §29 · P2 · M6 · PLANNED/reference · source L776**

> **Recommended model: free core + paid Pro** so users can trust offline recording. Free: local manual ledger, basic accounts, card entry, recurring reminders, import/export, app lock. Pro: advanced SMS/voice automation where permitted, larger-group collaboration, cloud sync/backup if priced, advanced insights, multi-device features, premium customization. Do not mislead users about free storage/backups. A full-paid or time-limited trial is also possible, but it makes the offline experience and recovery policy more complex.

**R0223 · §29 · P2 · M6 · PLANNED/reference · source L778**

> **No 'free forever exploit' guarantee:** a modified offline APK may bypass purely local UI checks; genuine subscription enforcement requires a server authority. Google Play Integrity helps assess tampering and installation provenance but is a risk signal, not a perfect standalone payment gate.

**R0224 · §29 · P2 · M6 · PLANNED/reference · source L780**

> ### 29.2 Store channel, billing and entitlements

**R0225 · §29 · P2 · M6 · PLANNED/reference · source L782**

> - If sold through Google Play, paid digital app features/subscriptions normally require **Google Play Billing**, subject to applicable regional/policy exceptions. Do not insert unapproved alternate in-app payment links.
> - Purchase flow: BillingClient launch → purchase token → backend verification with Play Developer API → server entitlement entry → authenticated entitlement response to device → acknowledge purchase under Play rules. Do not grant benefits to `PENDING` purchases.
> - Track distinct states `TRIAL`, `ACTIVE`, `GRACE_PERIOD`, `ON_HOLD`, `CANCELED_BUT_ACTIVE_UNTIL_EXPIRY`, `EXPIRED`, `REVOKED`, `UNKNOWN_OFFLINE`; handle renewals, payment decline, refund, chargeback, pause, upgrade/downgrade, purchase restore, two Google accounts, multiple devices and customer-support fixes.
> - Real-time developer notifications (RTDN) alert backend to purchase changes; backend must then fetch authoritative purchase status. Secure and idempotently process repeated events.
> - Backend data: `user_id`, `subscription_provider`, `purchase_token_hash / encrypted_reference`, `product_id`, `start`, `expiry`, `status`, `last_verified`, `entitlement_version`, `device_grants`, `audit_events`. Avoid caching raw purchase tokens in analytics.
> - Offline premium: optionally issue a short-lived **signed** entitlement lease bound to user/installation and validated locally. Choose the length deliberately (e.g., 7–30 days, **illustrative**, not a platform rule) and disclose it at checkout. Protect against simple clock rollback with server timestamps / monotonic metadata where feasible; offline perfect time-trust is unavailable. After lease expiry and no internet, preserve local read/export and show `Connect to verify Pro` for premium-only actions.
> - Cancellation must not delete a user's data; clearly show renewal/end date and `Manage subscription` linking to Google Play; support account restoration. Distinguish tracked personal subscriptions from the user's own Traxos paid plan.

**R0226 · §29 · P2 · M6 · PLANNED/reference · source L790**

> ### 29.3 Anti-abuse without hostile UX

**R0227 · §29 · P2 · M6 · PLANNED/reference · source L792**

> - Google Play Integrity checks on sensitive authenticated backend requests; rate limits by user/IP/device *with privacy review*; anomaly detection; RLS; server-side entitlements for cloud/group operations.
> - Non-Play builds require a separately designed signed distribution/license flow; Play Billing/Integrity assumptions do not automatically apply to privately distributed APKs.
> - Protect against replayed purchases and switching accounts. Never trust `isPremium=true` flags in local preferences for remote service access.
> - No silent telemetry of SMS, merchants, balances or notes. Restrict fraud telemetry to minimal technical signals and retention, and explain it in privacy documentation.


### Source 30. Interaction feedback and duplicate-action prevention

**R0228 · §30 · P0 · M1–M6 · PLANNED/reference · source L797**

> ## 30. Interaction feedback and duplicate-action prevention

**R0229 · §30 · P0 · M1–M6 · PLANNED/reference · source L799**

> ### 30.1 Universal action-state contract

**R0230 · §30 · P0 · M1–M6 · PLANNED/reference · source L801**

> For every important action define `Idle → Validating → InProgress → (Succeeded | Failed | Cancelled | PartiallyComplete)`. State should survive configuration changes as appropriate; long-running operations persist in WorkManager/Room, not only in Compose button state. A control gives immediate ripple/pressed styling (reduced motion respected), optionally a subtle haptic cue, and a semantic TalkBack announcement.

**R0231 · §30 · P0 · M1–M6 · PLANNED/reference · source L803**

> | Interaction | Immediate response | Completion | Failure / recovery |
> |---|---|---|---|
> | Add transaction | Button visibly pressed, disabled while committing | `Saved on device · Undo` | Keep form contents, inline cause + Retry |
> | Edit | Inline progress only if non-instant | `Changes saved` | Keep draft and conflicts visible |
> | Delete | Confirm if high-risk; soft delete | `Moved to recently deleted · Undo` | Restore action, preserve audit history |
> | Export CSV/JSON/PDF | `Preparing export…`, determinate progress only when measurable | `Saved to selected location · Open` | `Export failed · Retry`, clean partial output |
> | Share via WhatsApp | Show recipient and editable message | `Opened WhatsApp draft` | `WhatsApp not available · Copy message` |
> | Sync | Small queued/syncing icon, no modal | `Backed up at 10:42` only after server confirms | `Local changes safe · Retry sync` |
> | Restore | Preview, explicit confirmation, staged progress | Record counts restored, unresolved conflicts | No half-applied active DB, retry with checkpoint |
> | Add recurring rule | Show schedule/first next due preview | `Reminder scheduled` | Explain missing permission/OS timing limitations |
> | Pay card bill / settle debt | Review account and transfer semantics | `Payment recorded` | Atomic rollback, no duplicate ledger posting |
> | Change PIN / data protection | Reauth prompt + secure state | `PIN changed` | No data deletion; actionable reason |
> | Purchase Pro | System billing flow; honest pending state | Only `Pro active` after verification | `Payment pending` or `Couldn't verify · Retry` |
> | Update app | Confirm update flow | App restarts into new version | `Update unavailable · Open store / Retry` |

**R0232 · §30 · P0 · M1–M6 · PLANNED/reference · source L818**

> Use **snackbar with Undo** for reversible changes, **inline validation** for forms, **status chips** for background operations, **custom dialogs** for destructive or legally consequential actions, **Android notifications** for offscreen events, and **system share picker** for exports. Avoid stacking multiple toasts and unnecessary blocking dialogs. Show true save progress; do not simulate percentages. No fake success if third-party messaging or Android chooser just opened.

**R0233 · §30 · P0 · M1–M6 · PLANNED/reference · source L820**

> ### 30.2 Prevent double tap at three independent layers

**R0234 · §30 · P0 · M1–M6 · PLANNED/reference · source L822**

> 1. **UI gate:** `onClick` atomically changes local action state to `InProgress` and disables the same action until resolution; prevent navigation double-push and spinners receiving taps. Use `Mutex`/serialized event handling for concurrent callbacks.
> 2. **Repository/DB gate:** generate an immutable `operation_id` (UUID/ULID) per *intended* user action **before** saving; insert ledger row and outbox event transactionally; unique DB constraint on operation and source import ID; safe retries return the same committed result.
> 3. **Server gate:** accept `Idempotency-Key`, deduplicate repeated API calls including after network timeouts, enforce unique financial operation IDs per scope, and return prior response for repeats. Double-delivered FCM, SMS or retries cannot create second entries.

**R0235 · §30 · P0 · M1–M6 · PLANNED/reference · source L826**

> **Debouncing alone is insufficient** for financial writes. It is useful for search, but a pending save also needs serialized state, durable operation identity and backend idempotency. Do not silently ignore a genuinely distinct second expense after the first save has finished.

**R0236 · §30 · P0 · M1–M6 · PLANNED/reference · source L828**

> ### 30.3 Undo semantics

**R0237 · §30 · P0 · M1–M6 · PLANNED/reference · source L830**

> Undo a not-yet-synced new transaction by a reversible local cancellation/tombstone in the outbox; undo a synced transaction through a new correcting operation. Support recovery after snackbar dismissal in `Recently deleted`, subject to retention policy. Never delete financial audit history if it is still needed for split debts or refund reconciliation.


### Source 31. Navigation, deep links and visual quality gates

**R0238 · §31 · P0 · M1/M7 · PLANNED/reference · source L832**

> ## 31. Navigation, deep links and visual quality gates

**R0239 · §31 · P0 · M1/M7 · PLANNED/reference · source L834**

> ### 31.1 Navigation map

**R0240 · §31 · P0 · M1/M7 · PLANNED/reference · source L836**

> **Bottom tabs:** Home / Activity / Groups / Plans. **Persistent primary action:** quick add. **Top utility:** profile/settings, backup status, notifications where useful. Avoid more than four primary tabs plus Add. Separate nested screens: Home → transaction details → edit; Activity → filter/search → record → edit; Groups → group → member balance → settle; Plans → budgets/savings/subscriptions/cards; Profile → Settings subsections.

**R0241 · §31 · P0 · M1/M7 · PLANNED/reference · source L838**

> Back behavior contract:
> - System back gesture and toolbar Up return to the prior logical screen, never to unexpected Home.
> - A sheet dismisses first; a form with unsaved edits offers `Keep editing / Discard / Save draft`; protect against duplicate dialogs.
> - Search/filter state restores after opening a transaction; scroll position and selected account persist across rotation/process recreation when feasible.
> - Deep links from SMS review, debt reminder, share, FCM and widgets route to the correct object after unlock/login, without flashing unmasked private content behind lock screen.
> - Open app from notification while it is already running → one instance of logical destination, not duplicated stack; safe `singleTop`/navigation design.
> - Switching bottom tabs preserves meaningful state, but stale permissions/conflicts are refreshed. Account sign-out doesn't accidentally reveal prior user's ledger.
> - Tablet/foldable adapt to navigation rail/list-detail where appropriate; leave phone's bottom navigation thumb-friendly.

**R0242 · §31 · P0 · M1/M7 · PLANNED/reference · source L847**

> ### 31.2 Compact UI density and text handling

**R0243 · §31 · P0 · M1/M7 · PLANNED/reference · source L849**

> Use a consistent spacing scale (4/8/12/16/20/24 dp), but **do not stack padding on outer container + card + content**. Compact cards may use 12–16dp content padding if readability remains good. Respect Android's accessible minimum target size (typically ~48dp), system font scaling and touch separation even if visible controls look compact. Long merchant names ellipsize to 1–2 lines, with details available on tap; amounts use tabular figures, scale/responsive wrapping instead of overlapping labels; never truncate *financial values* without an accessible full-value view. Handle Android 200% font scale, high contrast, Indic scripts, long multilingual merchant names, foldables, tablets, screen reader and font fallback. Correct keyboard insets and bottom navigation overlap are release blockers.

**R0244 · §31 · P0 · M1/M7 · PLANNED/reference · source L851**

> Use polished state-specific empty pages; no spinners for instantaneous Room reads; skeletons only during genuine loading; error retry CTA always visible where useful. Colors communicate money semantic meaning, but never rely on color alone. Haptics should be quiet, consistent, optional and unavailable hardware should fall back silently. Animations target purposeful 150–250ms and respect reduced motion. Distinguish `offline` from `sync error`; offline is normal, not a danger state.

**R0245 · §31 · P0 · M1/M7 · PLANNED/reference · source L853**

> ### 31.3 Full-screen QA examples

**R0246 · §31 · P0 · M1/M7 · PLANNED/reference · source L855**

> A long category label, 999,999,999.99 amount, 150-character note, 200% font setting, 320dp device, keyboard visible, accessibility enabled and bottom sheet open must **not** clip the save action or erase the entered fields. Data with no rows should show an empty state; filtered zero rows should say `No results for these filters`, not `You have no transactions`. Confirmation dialogs must stay within safe insets. Settings subpages need deterministic Back/Up behavior. Exports triggered from search retain chosen filters and show date/time boundaries in the export options.


### Source 32. Manual accounts, credit cards, subscriptions and debt entry

**R0247 · §32 · P0 · M2/M4 · PLANNED/reference · source L857**

> ## 32. Manual accounts, credit cards, subscriptions and debt entry

**R0248 · §32 · P0 · M2/M4 · PLANNED/reference · source L859**

> ### 32.1 Account and payment-source types

**R0249 · §32 · P0 · M2/M4 · PLANNED/reference · source L861**

> Support `CASH`, `BANK`, `UPI_ALIAS` (a routing label, not necessarily independent money), `DEBIT_CARD` (linked to a bank account), `CREDIT_CARD` (liability account), `E_WALLET`, `OTHER`. Distinguish actual accounts from payment methods to prevent counting the same money twice. Manual entry is always available even if SMS/import permissions are denied. Fields: name, currency, opening balance/known-as-of date, optional nickname, color/icon, hidden/archived, opening date, bank label and masked card digits where provided. Avoid collecting complete card numbers, CVV, online banking credentials or OTPs.

**R0250 · §32 · P0 · M2/M4 · PLANNED/reference · source L863**

> ### 32.2 Credit-card account creation

**R0251 · §32 · P0 · M2/M4 · PLANNED/reference · source L865**

> **Create card** form: user-friendly display name, masked last 4 digits (optional), currency, billing-cycle closing day, due-day or bank-provided due date, credit limit (optional), carried forward outstanding balance + as-of date, minimum due (optional/manual), auto-pay reminder preference, alert offsets, account from which bill is paid, notification privacy. For month-end mismatches define deterministic end-of-month rule and show an explicit preview. Statement amounts are **not authoritative bank data** unless linked/imported and reviewed.

**R0252 · §32 · P0 · M2/M4 · PLANNED/reference · source L867**

> - **Card purchase ₹1,000 groceries:** expense + ₹1,000 liability / increases card outstanding; do not reduce bank balance at purchase.
> - **Pay card bill ₹1,000 from SBI:** internal liability settlement and bank debit; **not another expense**.
> - **Refund ₹300:** associate with original purchase, reduce liability and reverse appropriate expense when reporting, while preserving audit history.
> - **Card interest/late fee ₹150:** new expense/fee and liability; do not hide it in the payment transfer.
> - **Partial bill payment:** reduces outstanding, due amount may remain; reminders update accurately. Do not infer bank's minimum due/interest formula without reliable data.
> - **Charge reversal vs pending authorization:** an authorized hold is not a posted expense by default.
> - **Card-to-card or card-to-bank transactions:** explicit transfer / cash advance semantics and fees; do not imply ordinary simple transfers are free.
> - **EMI conversion/installment:** manual schedule, original principal and fees, installment tracking, no double-booked original purchase.
> - **Multiple cards:** each is its own liability ledger; due notifications, mask sensitive values. Changing billing cycle recalculates future planned periods, not historical posted statements.

**R0253 · §32 · P0 · M2/M4 · PLANNED/reference · source L877**

> Suggested card UI: `Current outstanding`, `Statement due`, `Due on`, `Available limit` (only when limit provided), `Pay bill`, `Add transaction`, `History`, `Reminders`. An estimated statement should be labeled **Estimated**, not falsely bank-confirmed.

**R0254 · §32 · P0 · M2/M4 · PLANNED/reference · source L879**

> ### 32.3 Manually create recurring subscriptions/bills

**R0255 · §32 · P0 · M2/M4 · PLANNED/reference · source L881**

> Add → Subscription/Recurring payment: merchant/service, custom name, category, amount, currency, fixed vs variable, payment source/card, repeat interval (daily, weekly, monthly, quarterly, yearly, custom), next billing date, trial end, due date vs actual charged date, reminder offsets, end date/never, whether paid/paused/cancelled, note, active months and account. The first occurrence can be set to `already paid` or `next due`.

**R0256 · §32 · P0 · M2/M4 · PLANNED/reference · source L883**

> For forecast vs ledger distinction: **scheduled upcoming ≠ expense already paid**. Recommended default: create upcoming due instance/reminder; on user confirmation or matched financial evidence, post real ledger transaction; optionally let user opt in to auto-create for predictable **cash** obligations, with clear caveat. Never auto-post a card charge based solely on a reminder; confirm against SMS/manual record and link rather than duplicate.

**R0257 · §32 · P0 · M2/M4 · PLANNED/reference · source L885**

> Detected recurring candidates: require repeated consistent evidence from trusted financial messages or manually entered history; classify as suggestion `Possible subscription: ₹199/month`. Explain the evidence, confirm service, amount, renewal date and funding account. `Add subscription`, `Ignore once`, `Never suggest service`. If merchant amount varies, use variable bill with amount to confirm. Annual / month-end / February / leap-year / timezone rules are specified, tested and previewed. Refund, plan upgrade, trial renewal, missed cycle, paused service, cancelled vs expired subscription, subscription paid from different account, payment method failed and late bank SMS must reconcile without duplicates.

**R0258 · §32 · P0 · M2/M4 · PLANNED/reference · source L887**

> ### 32.4 Manual debts, credit and loan records

**R0259 · §32 · P0 · M2/M4 · PLANNED/reference · source L889**

> Create lend/borrow: contact nickname, optional phone, principal, currency, reason, opened date, due date, reminder preferences, optional interest (simple terms only; avoid presenting as legal lending advice), partial installments, fees, collateral note (optional). Track receivable/payable separately from ordinary income/expense: lending cash is an asset transfer, repayment reduces receivable; borrowing increases liability and cash, not ordinary income. Keep balanced person threads with messages (no automatic sending), evidence attachments under opt-in. When sharing via WhatsApp, preview exact person, number, currency, outstanding *as of* date, reason and optional due date. Use Android share/WhatsApp intent; user presses Send. If WhatsApp absent, `Copy message` or system sharesheet. Never claim delivered or read.


### Source 33. Long-offline batch sync, conflict resolution and huge datasets

**R0260 · §33 · P1 · M5 · PLANNED/reference · source L891**

> ## 33. Long-offline batch sync, conflict resolution and huge datasets

**R0261 · §33 · P1 · M5 · PLANNED/reference · source L893**

> ### 33.1 Minimum sync protocol

**R0262 · §33 · P1 · M5 · PLANNED/reference · source L895**

> Every local mutation creates an immutable journal entry with `operation_id`, `entity_id`, `entity_type`, `actor_device_id`, `user_scope`, `base_revision`, `operation_type`, `payload_schema_version`, `created_at`, `occurred_at` as applicable, `attempts`, `next_retry_at`, `last_error`, `acknowledged_at`. Store financial currency as ISO code and **integer minor units** or a properly designed exact decimal representation; never store monetary balances as binary floating point.

**R0263 · §33 · P1 · M5 · PLANNED/reference · source L897**

> On network availability:

**R0264 · §33 · P1 · M5 · PLANNED/reference · source L899**

> ```
> Room transaction committed
>   -> durable outbox queue
>   -> coalescing safe non-financial profile edits (never merge money ops blindly)
>   -> push small batches with stable operation IDs
>   -> backend validates auth, entitlement, group permission, schemas, version, idempotency
>   -> per-operation ack / retryable failure / permanent conflict
>   -> local checkpoint committed transactionally
>   -> pull server changes by paginated cursor
>   -> apply in local DB transaction(s), preserving unsent local edits
>   -> recalc affected aggregates, expose latest verified backup state
> ```

**R0265 · §33 · P1 · M5 · PLANNED/reference · source L912**

> **Illustrative starting batch sizes**, to tune with tests: up to 50–200 operations and capped payload around 256 KiB–1 MiB per request; do not hardcode one huge batch of 10,000. Server returns operation-level statuses and a stable cursor. On timeout, send the **same** operation IDs; server idempotency prevents duplication. WorkManager network constraints, exponential backoff + jitter and Retry-After for HTTP 429, battery/network policies and limited parallelism. Group changes must maintain meaningful dependency ordering (`create group` before `add expense`; `create card` before `card transaction`).

**R0266 · §33 · P1 · M5 · PLANNED/reference · source L914**

> ### 33.2 Cases to handle explicitly

**R0267 · §33 · P1 · M5 · PLANNED/reference · source L916**

> - Months offline plus simultaneous online device modifications; preserve local data and show conflict queue with original/remote values for finance-sensitive fields. Avoid silent last-writer-wins on monetary amount, transaction type or payee.
> - Deletion after sync; tombstones stay long enough for disconnected devices to receive them, with backend compaction/snapshot plan and offline rejoin process. Large out-of-date clients trigger full resnapshot without discarding local outbox.
> - Changing account/group ID/name offline; stable IDs separate display names and immutable identifiers.
> - Group member removed during offline period; shared writes rejected on reconnect with local draft retained and actionable choice to convert to private or discard after export.
> - Signing into wrong account, rotating auth tokens mid-upload, deleted/recreated user, session expiry: halt cloud writes safely and allow local access.
> - Media/receipt blob uploads use separate multipart/resumable pathway with ownership, checksum and orphan cleanup; no attachment should block ordinary transactions.
> - Subscription state changes while offline do not silently delete financial data. Premium-only sync may pause with `Connect / Renew` but local outbox remains for a clearly disclosed period; avoid indefinite hidden queues.
> - Periodic anti-entropy / reconciliation: compare user-scoped row counts and snapshot hashes without uploading sensitive content to analytics; repair divergent sync cursors.

**R0268 · §33 · P1 · M5 · PLANNED/reference · source L925**

> ### 33.3 Read performance

**R0269 · §33 · P1 · M5 · PLANNED/reference · source L927**

> Room + Paging 3 for activity and person/group histories; stable indexed sort using `(user_scope, occurred_at, id)` and account/category/time filters. Local database remains UI source of truth even during remote pagination. Backend pagination uses cursor/keyset rather than arbitrary giant OFFSET. Debounce typed search ~250–350ms; filter selections apply promptly; invalidate only affected pages; keep a snapshot of prior list while query is resolving if useful. Database indexes for source_ref/operation_id, group_id, card_id, account_id, date and recurring rule. Compute summary aggregates in SQL/maintained projection tables, not by loading all rows. Use thread-safe stream/file I/O, memory bounds and cancellation; observe actual startup latency and jank on low-end Android hardware.


### Source 34. Security design and privacy control — detailed production posture

**R0270 · §34 · P1 · M3/M5 · PLANNED/reference · source L929**

> ## 34. Security design and privacy control — detailed production posture

**R0271 · §34 · P1 · M3/M5 · PLANNED/reference · source L931**

> ### 34.1 Local unlock and crypto

**R0272 · §34 · P1 · M3/M5 · PLANNED/reference · source L933**

> - `BiometricPrompt`: allow Android-authenticated fingerprint/face **only when the authenticator class meets configured trust policy**; system device credentials as permitted fallback. Some OEM face methods do not meet strong biometric class—detect and explain rather than falsely advertise it.
> - Optional independent Traxos PIN (prefer six digits or passcode option), securely derived using slow KDF, with verification metadata under Android Keystore protection where appropriate; attempt throttling, progressive delays and a safe forgotten-PIN recovery policy that does not promise impossible decryption.
> - Lock on chosen timeout, process start after inactivity and app task background according to preference; optional reauth before unmasking hidden balances, viewing exports or changing backup keys. Support screen-capture/privacy option, blur/hide recents snapshots, secure notification previews and accessible masking. Full `FLAG_SECURE` can hinder accessibility/screenshot support; make policy intentional.
> - Private Room data protection needs a maintained encrypted-at-rest solution if promised, plus vetted key/backup integration and migration testing. Plain Room is **not automatically encrypted**. Key store, app-lock state and backups must be designed together.
> - Never store PIN or session token in plaintext; avoid leaking account data to Crashlytics logs, breadcrumbs, analytics or notification payloads. Clear sensitive clipboard where appropriate; disable screenshots in protected flows if user enables privacy mode.

**R0273 · §34 · P1 · M3/M5 · PLANNED/reference · source L939**

> ### 34.2 Cloud/service threats

**R0274 · §34 · P1 · M3/M5 · PLANNED/reference · source L941**

> - Strict identity binding and row-level security, authorization tested for groups/debt-sharing, backend verification of remote entitlement, signed requests where appropriate, scoped refresh tokens, revocation per device, rate limits, abuse controls, secrets manager and key rotation.
> - API schema validation, request size limits, timestamp interpretation, replay protection, locked-down storage bucket policies, secure file sharing expiration, signed URLs for attachments.
> - MFA for admin and sensitive account actions; secure password reset or OAuth flow; notification tokens cannot be treated as authentication.
> - Don't place bank transaction contents, personal debts, unmasked card digits, phone numbers or full user financial data in FCM notification payloads, analytics or remote app configuration.
> - Production incident plan: leaked auth tokens, compromised signing keys, DB backup compromise, sync corruption, billing failure, accidental data wipe; rehearse restorations, disclosure and rollback/forward fixes.

**R0275 · §34 · P1 · M3/M5 · PLANNED/reference · source L947**

> ### 34.3 User-facing privacy settings

**R0276 · §34 · P1 · M3/M5 · PLANNED/reference · source L949**

> Privacy dashboard: SMS permission status and explanation; delete imported candidates; selected notification and bank sources; cloud backup on/off, sync-on-mobile-data preference; account lock; masked previews; clear local analytics; export all data; request cloud account deletion; manage devices; delete individual source attachments. Only request permissions immediately before the relevant feature; permission denial must not block manual ledger use. Show full in-app privacy notices with appropriate retention policy and consent history.


### Source 35. Reminders, pushes and notifications — robust execution

**R0277 · §35 · P1 · M4/M5 · PLANNED/reference · source L951**

> ## 35. Reminders, pushes and notifications — robust execution

**R0278 · §35 · P1 · M4/M5 · PLANNED/reference · source L953**

> ### Source of truth

**R0279 · §35 · P1 · M4/M5 · PLANNED/reference · source L955**

> Reminders derive from local or server financial objects (`credit_card_due`, `subscription_renewal`, `loan_due`, `debt_receivable`, `savings_goal`, `budget_threshold`) with a deterministic `reminder_key = object_id + occurrence_date + offset + channel`. Persist sent/acknowledged state and dedupe across local notification + remote FCM. A user editing amount or due date invalidates stale pending occurrences.

**R0280 · §35 · P1 · M4/M5 · PLANNED/reference · source L957**

> ### Delivery approach

**R0281 · §35 · P1 · M4/M5 · PLANNED/reference · source L959**

> - **Offline:** inexact AlarmManager notifications or WorkManager scheduling as appropriate; rehydrate after reboot/timezone/clock change and app upgrade. Android exact alarms are permission- and policy-constrained; a finance reminder generally should not claim exact-to-the-minute timing.
> - **Online:** optional FCM for cross-device reminders, token rotation and user-targeted delivery; fetch latest due state before displaying when feasible. FCM is not guaranteed: dropped, collapsed, reordered or delayed pushes must not affect financial correctness. On reconnect, reconcile due occurrences and offer an in-app `Missed reminders` section.
> - Android 13+ notification runtime permission, per-channel controls, quiet hours, notification grouping (cards / subscriptions / loans / debts / backup), deep linking after unlock, hidden balances on lock screen, snooze and remind again. Avoid redundant push and local alerts during the same due window.
> - Use accurate labels `Reminder sent` only when platform scheduling/posting succeeded, not `Reminder received`; never advertise exact delivery without proof.

**R0282 · §35 · P1 · M4/M5 · PLANNED/reference · source L964**

> ### User-created and smart-detected plans

**R0283 · §35 · P1 · M4/M5 · PLANNED/reference · source L966**

> Each card due date, manually configured subscription, EMI, lend/borrow due and recurring bill offers reminder offsets (same day, 1, 3, 7 days, custom), local time, snooze, paid confirmation, mute per object and priority. Dismissing a notification is **not** the same as marking the bill paid. If an imported payment is matched, user may review and mark the scheduled occurrence settled, never create a second copy by default.


### Source 36. Full settings, exports, onboarding and account lifecycle

**R0284 · §36 · P1 · M2–M6 · PLANNED/reference · source L968**

> ## 36. Full settings, exports, onboarding and account lifecycle

**R0285 · §36 · P1 · M2–M6 · PLANNED/reference · source L970**

> ### Settings information architecture (all entries should lead somewhere real)

**R0286 · §36 · P1 · M2–M6 · PLANNED/reference · source L972**

> | Section | Specific controls |
> |---|---|
> | Profile | Display name, avatar, email / Google account link, phone optional, language, date/number formatting, timezone display policy, connected devices |
> | Appearance | System/light/dark theme, compact/comfortable density, motion/haptics, font scaling integration, alternative **prepackaged** launcher icon and name aliases when technically appropriate |
> | Accounts | Add cash/bank/card/wallet; rename, reorder, hide balances, archive, restore; default expense account, last-used account choice, currency per account |
> | Currency | Base reporting currency, display currency, exchange-rate provenance, manually specified rates, conversion rounding, report rule for historical vs latest rates |
> | Security | Biometric support, separate app PIN, auto-lock timer, hide recents, lock-screen previews, extra reauth for export, device removal/revocation |
> | Notifications | Categories, channels, reminders offsets, quiet hours, vibration/sound, masked amounts, summary vs individual import suggestion alerts |
> | Smart Add | SMS permission/rules, notification listener if ever implemented, voice language/offline-model support, trusted/ignored merchants, duplicate review, quick widgets |
> | Sync & Backups | Cloud status, last full verification, pending count, Wi-Fi-only vs any network, retry now, cloud account selection, encrypted export / import / restore, conflict list |
> | Data | CSV/JSON/PDF, date/account/category/person/group filters, including/excluding hidden/archived, backup schema metadata, export to share sheet, delete candidates/cache, delete cloud account |
> | Billing | Current Pro plan, renew date, trial state, restore purchase, manage/cancel subscription, feature comparison, billing help and invoices when available from provider |
> | Accessibility | Respect system text size/reduce motion, text labels, haptics, contrast, optional larger touch-target setting |
> | Help & Legal | Help center, report bug with redacted diagnostics, feedback, rate app at appropriate moment, privacy policy, terms, licenses, build/version, contact support |

**R0287 · §36 · P1 · M2–M6 · PLANNED/reference · source L987**

> **Launcher name/icon restriction:** an installed Android application's launcher alias can be changed among declared alternatives by enabling/disabling components; arbitrary user-entered app labels and arbitrary runtime-generated launcher icon packages are not supported as a general reliable feature. Package ID never changes in place. Do not permit a fake branded name that could impersonate a bank.

**R0288 · §36 · P1 · M2–M6 · PLANNED/reference · source L989**

> **Payment/currency/data export UX:** show exact selected date range, timezone, account names and whether filtered records or the entire dataset will be exported. CSV designed for spreadsheets with quote/encoding handling, injection-safe cell sanitization (e.g., leading `=`, `+`, `-`, `@` in user-provided notes), BOM decision and configurable separator; JSON lossless canonical backup representation; PDF human-readable report (not a substitute for recoverable JSON), safe page breaks and large font support. If a user selects a share destination, return `Share sheet opened`, not `File delivered`.

**R0289 · §36 · P1 · M2–M6 · PLANNED/reference · source L991**

> ### Sign-out, account linking and deletion

**R0290 · §36 · P1 · M2–M6 · PLANNED/reference · source L993**

> - On guest → cloud sign-in, show merge preview if cloud already has data; never quietly overwrite either side.
> - Sign out must not automatically wipe device records; offer separate **Remove this account's data from this device** with verified cloud backup and export recommendation.
> - Cloud account deletion should identify effect on groups, backups, receipts, billing, legally required retention and active subscription. For purchases, deleting a Traxos profile does not necessarily cancel a Google Play subscription automatically; explicitly guide the user.
> - Strong privacy control for a shared phone: switch profile only after lock; clear in-memory ledger cache, thumbnail previews and all scheduled notifications belonging to previous profile.


### Source 37. Developer operations, management console and observable reliability

**R0291 · §37 · P2 · M6/M7 · PLANNED/reference · source L998**

> ## 37. Developer operations, management console and observable reliability

**R0292 · §37 · P2 · M6/M7 · PLANNED/reference · source L1000**

> **Admin console required if Traxos will be commercially managed**. It must be separate from consumer APK, with admin MFA, scoped RBAC, audit logs and strong privacy boundaries. Do not offer a tool that lets an admin freely browse an individual's financial transactions without legal basis, explicit support authorization and tightly controlled roles.

**R0293 · §37 · P2 · M6/M7 · PLANNED/reference · source L1002**

> Functions: version floors, recommended version, feature flags and staged rollouts, parser rule version, disabled integrations, cloud maintenance messages, support tickets, account status, verified backup health *metadata*, sync retry/error-rate statistics, background job health, FCM send metrics, billing/entitlement status changes, fraud incident flags, device revocation, audit history and anonymized aggregate usage. Operational errors should use codes, request correlation IDs, safe trace logging and privacy redaction. Remote Config is not a secure authorization source; server enforcement mandatory.

**R0294 · §37 · P2 · M6/M7 · PLANNED/reference · source L1004**

> **Suggested engineering SLO targets (validate experimentally):** p95 local Quick Add commit <300ms on target hardware; startup to useful ledger quickly with Room cold-start benchmark; paged activity scroll meeting platform jank targets; 10k-operation offline queue eventually fully reconciled in stress test; 50k-record filter remains responsive; no silent data truncation; backup verification jobs and documented restore practice. Do not claim 99.99% uptime without monitored infrastructure and a meaningful SLO definition. Every release should have crash-free / ANR gates, but 100% perfection is impossible.

**R0295 · §37 · P2 · M6/M7 · PLANNED/reference · source L1006**

> **Minimum operational dashboards:** crash-free users/ANRs, p95 save latency, sync backlog size by age buckets (not balances), retry rate, rejected idempotency duplicates, data-restore success and corruption count, pending purchase verification, Android version/OEM distribution, SDK error rates, FCM token invalidation, high-risk migrations. Analytics must be opt-in/consent-appropriate as required and never leak finances.


### Source 38. Engineering implementation modules and detailed API contracts

**R0296 · §38 · P0 · M1–M6 · PLANNED/reference · source L1008**

> ## 38. Engineering implementation modules and detailed API contracts

**R0297 · §38 · P0 · M1–M6 · PLANNED/reference · source L1010**

> ### Suggested Android modules

**R0298 · §38 · P0 · M1–M6 · PLANNED/reference · source L1012**

> - `:app`: launcher, permissions orchestration, deep links, app-level navigation
> - `:core:designsystem`: original Traxos brand, tokens, typography, motion, feedback components, accessibility
> - `:core:database`: Room, encrypted storage, migrations, queries and integration tests
> - `:core:domain`: immutable money types, transfer/expense semantics, validation, group shares
> - `:core:sync`: outbox, batch transport, pull cursors, resumability, conflict review
> - `:core:security`: biometric/credential gate, PIN, masking, key management
> - `:core:backup`: SAF export/import, verified cloud restore, schema validator
> - `:core:billing`: Play Billing, entitlement cache, purchases lifecycle UX
> - `:core:reminders`: local scheduler, FCM processing, dedupe, channels
> - `:features:home`, `:activity`, `:quickadd`, `:accounts`, `:cards`, `:debts`, `:subscriptions`, `:groups`, `:plans`, `:settings`, `:smartimport`, `:voice`

**R0299 · §38 · P0 · M1–M6 · PLANNED/reference · source L1023**

> Keep dependencies unidirectional: UI → ViewModel/Use Cases → repositories → local DB + optional network. Reuse a single validated ledger service for all manual, SMS, voice, subscription, quick-action and group-settlement transactions.

**R0300 · §38 · P0 · M1–M6 · PLANNED/reference · source L1025**

> ### Backend endpoints (illustrative naming)

**R0301 · §38 · P0 · M1–M6 · PLANNED/reference · source L1027**

> - `POST /v1/sync/batch` with stable idempotency keys and per-operation acks
> - `GET /v1/sync/changes?cursor=...&limit=...` authenticated delta paging
> - `GET /v1/backup/manifest` verified remote backup state + schema/cursor
> - `POST /v1/backup/snapshot`/`GET /v1/backup/snapshot` if using explicit snapshots beyond synced server DB
> - `GET /v1/client-policy` server-authoritative supported protocol/version/status
> - `POST /v1/billing/verify-purchase`; webhook/RTDN handler for backend only
> - `GET /v1/billing/entitlements` authenticated, signed/offline-lease issue where needed
> - `POST /v1/devices/revoke` per-account authenticated management
> - `POST /v1/groups/...` with group member authorization + operation idempotency
> - `POST /v1/account/export-request` and `DELETE /v1/account` with compliant retention and erasure process

**R0302 · §38 · P0 · M1–M6 · PLANNED/reference · source L1038**

> All APIs enforce scope/authorization on backend and document version compatibility, error codes, rate limits and idempotency retention windows. Browser/web legacy endpoints are not a substitute for an explicit mobile sync protocol.

**R0303 · §38 · P0 · M1–M6 · PLANNED/reference · source L1040**

> ### Database entities newly required

**R0304 · §38 · P0 · M1–M6 · PLANNED/reference · source L1042**

> `backup_manifests`, `export_jobs`, `import_jobs`, `outbox_operations`, `sync_checkpoints`, `recovery_conflicts`, `soft_deletes/tombstones`, `app_notification_events`, `reminder_instances`, `credit_card_statements`, `card_payment_transfers`, `recurring_rules`, `recurring_occurrences`, `entitlement_cache`, `local_unlock_metadata`, `import_suggestions`, `source_deduplication_keys`. Ensure relationship and unique constraints are declared and exercised in tests.


### Source 39. Complete edge-case validation and adversarial QA

**R0305 · §39 · P0 · M7 · PLANNED/reference · source L1044**

> ## 39. Complete edge-case validation and adversarial QA

**R0306 · §39 · P0 · M7 · PLANNED/reference · source L1046**

> The following are **release test cases**. A feature is not "complete" merely because its happy path looks correct.

**R0307 · §39 · P0 · M7 · PLANNED/reference · source L1048**

> ### Data durability / disaster recovery

**R0308 · §39 · P0 · M7 · PLANNED/reference · source L1050**

> 1. Quick Add completes, phone crashes immediately; record persists exactly once and outbox has matching operation.
> 2. Clear *cache* does not erase ledger; Clear *storage* does, and recovery onboarding presents viable backup options.
> 3. Reinstall while online: signed-in user restores count, values, groups and reminders; device-local PIN/biometrics must be re-established.
> 4. Reinstall while offline with no portable backup: app honestly says unrecoverable; cannot imply an empty DB is temporary syncing.
> 5. Restore correct encrypted file with user passphrase on a new Android device; keys are not mistakenly bound to old Keystore.
> 6. Wrong passphrase / tampered archive rejects without partial financial data mutation.
> 7. Merge guest ledger with existing cloud account that already includes some same source transactions; no duplicates.
> 8. Zero-byte export, full storage, external volume removed, user cancels SAF picker; original records remain intact.
> 9. Cloud connection established while device lost 3 months of data; show pending, then ack and verified status; never lose intermediate batches.
> 10. Old backup/new schema and new backup/old app: informative compatibility behavior and tested version migrations.

**R0309 · §39 · P0 · M7 · PLANNED/reference · source L1061**

> ### Versions, purchases, fraud and policy

**R0310 · §39 · P0 · M7 · PLANNED/reference · source L1063**

> 11. Optional update snoozed without repeated nagging; forced update reason accessible; Play Core unsupported route goes to correct official store.
> 12. Mandatory update issued while app offline: cached policy respected, new update discovered when online, no local outbox purge.
> 13. Server rejects old sync protocol but phone preserves local unsynced financial entries and offers export.
> 14. Pending payment does not grant Pro; purchased active does after verification; duplicate purchase notification yields one entitlement update.
> 15. Grace-period user retains access as required by Play; account hold removes paid entitlement; canceled-but-unexpired user retains benefits until expiry.
> 16. Restore purchased Pro after reinstall/account relink; mismatched Google Play accounts produce clear help, no duplicate charges.
> 17. Expired offline entitlement with bad connection: existing ledger remains safe/readable/exportable per product policy.
> 18. Modified client says `premium=true` but backend denies premium cloud API. Play Integrity false positive doesn't destroy data.
> 19. Billing cancellation doesn't wipe financial records or assume that cancelling app subscription cancels tracked Netflix subscription.
> 20. Direct APK distribution tests signing, upgrades and secure license flows independently of Play assumptions.

**R0311 · §39 · P0 · M7 · PLANNED/reference · source L1074**

> ### Tap states, navigation and UI

**R0312 · §39 · P0 · M7 · PLANNED/reference · source L1076**

> 21. 10 rapid taps on Save → one transaction, one haptic confirmation, one successful operation ID.
> 22. 10 rapid taps on Settle → one money transfer and one debt settlement.
> 23. Export tap twice → one tracked job, progress reflects real records processed, success only after document persist.
> 24. Share selected WhatsApp recipient canceled before Send → app doesn't claim delivered.
> 25. Back from nested Activity → transaction → edit with dirty fields → coherent prompt and restored scroll/filters.
> 26. Repeated SMS notification/deep-link tap doesn't push duplicate quick add screens or duplicate transaction creation.
> 27. Rotation / dark theme toggle / process death mid-form preserves drafted data safely, without committing it accidentally.
> 28. Accessibility with 200% text, TalkBack, 320dp phone, long merchant and massive amounts has no overlapping action labels or clipped amount.
> 29. Offline state uses nonalarming label; failing sync doesn't block local add; success icon only after server ack.
> 30. Screen locked + FCM debt due notification never reveals hidden balance or sender name if privacy is set.

**R0313 · §39 · P0 · M7 · PLANNED/reference · source L1087**

> ### Cards, subscriptions, debts and financial math

**R0314 · §39 · P0 · M7 · PLANNED/reference · source L1089**

> 31. Credit card purchase creates category expense + liability; credit card bill payment reduces bank and liability without duplicate expense.
> 32. Manual partial card payment reduces bill outstanding; late fee separate expense; interest assumptions clearly marked or omitted.
> 33. Card refunds link to purchases; late SMS plus manual card transaction match review, not auto-double-add.
> 34. Two cards with different currencies and cycle dates preserve per-card due schedules and reporting.
> 35. New subscription first due tomorrow; no fake completed expense generated before payment.
> 36. Same recurring merchant detected in three SMS and manually added → one active subscription rule plus reviewable candidate evidence.
> 37. Monthly rule on 31st rolls to month-end according to visible user choice, tested for February/leap year.
> 38. Subscription cancellation stops future reminders/occurrences but retains historical paid entries.
> 39. Debt paid partially twice, then reversed once → outstanding returns correctly without treating repayments as income.
> 40. WhatsApp share shows correct direction: `you owe me` vs `I owe you`, number country code and date/amount; no auto-send.

**R0315 · §39 · P0 · M7 · PLANNED/reference · source L1100**

> ### Sync, security, device behavior

**R0316 · §39 · P0 · M7 · PLANNED/reference · source L1102**

> 41. 10k queued offline edits, intermittent 2G/poor Wi-Fi, token expiry, HTTP 429/500 and app process termination: no loss, no duplicate.
> 42. Edits on 2 devices to same money amount: present conflict, not silent overwrite; unrelated metadata may merge.
> 43. Group membership revoked offline: shared write rejected to draft review, private ledger preserved where permissible.
> 44. Background restrictions/battery saver/force-stop: scheduled work defers; next foreground triggers recovery; app doesn't promise guaranteed immediate sync.
> 45. Android permission denied/revoked after settings: SMS/voice/notifications degrade cleanly; manual tracker unaffected.
> 46. Biometric unavailable/weak face only/PIN lockout/device credential changed: secure fallback per configuration without bypass.
> 47. Delete account, restore backup, rotate key and switch users while outbox pending: explicit confirmation, no cross-account leakage.
> 48. Android system backup restores data without original non-exportable Keystore key: app detects inaccessible DB and offers supported recovery, not crash loop.
> 49. Timezone/daylight-savings/clock rollbacks/locale switching do not change posted amount or break entitlement and reminder logic silently.
> 50. App update via same package/signature preserves data; accidental uninstall instructions never used as a normal update mechanism.


### Source 40. Release plan and acceptance definition for a professional-grade APK

**R0317 · §40 · P0 · M1–M7 · PLANNED/reference · source L1113**

> ## 40. Release plan and acceptance definition for a professional-grade APK

**R0318 · §40 · P0 · M1–M7 · PLANNED/reference · source L1115**

> ### Stage A — money correctness and durability

**R0319 · §40 · P0 · M1–M7 · PLANNED/reference · source L1117**

> Implement native Room ledger, payment/account types including manually entered cards and subscriptions, safe transfers, loans/debts, import review, atomic transactions, idempotency. Test offline and crash cases. No remote payment yet.

**R0320 · §40 · P0 · M1–M7 · PLANNED/reference · source L1119**

> ### Stage B — brand-quality UI and settings

**R0321 · §40 · P0 · M1–M7 · PLANNED/reference · source L1121**

> Develop custom Compose Traxos design system from **user-supplied five-rounded-square T logo**, compact responsive screens, navigation/back stacks, states and snackbars/undo, accessible controls, strong app lock, settings and human-friendly prompts. Test across representative OEMs.

**R0322 · §40 · P0 · M1–M7 · PLANNED/reference · source L1123**

> ### Stage C — portable recovery and secure cloud

**R0323 · §40 · P0 · M1–M7 · PLANNED/reference · source L1125**

> Build encrypted SAF backup/restore; verified restore previews; optional sign-in, outbox sync, adaptive batches, server row security, conflict resolution, backup health surfaces, cloud restore after reinstall. Demonstrate clear storage/restore end to end.

**R0324 · §40 · P0 · M1–M7 · PLANNED/reference · source L1127**

> ### Stage D — subscriptions, upgrades, controls

**R0325 · §40 · P0 · M1–M7 · PLANNED/reference · source L1129**

> Implement Google Play Billing if launching paid on Play; backend verified entitlement, grace/hold/refund states, offline entitlement lease policy, separate admin Remote Config + server version gate, immediate/flexible updates, staging/rollout and anti-abuse telemetry. **Never deploy mandatory update gates without safe data access and a working update route.**

**R0326 · §40 · P0 · M1–M7 · PLANNED/reference · source L1131**

> ### Stage E — low-effort intelligence and reminders

**R0327 · §40 · P0 · M1–M7 · PLANNED/reference · source L1133**

> SMS and voice with user permission, deduped local suggestions, recurring detection review, manual billing/card due rules, local notifications + optional FCM, home widget/shortcuts; experimental volume double press only after platform/policy validation. Protect lock-screen privacy.

**R0328 · §40 · P0 · M1–M7 · PLANNED/reference · source L1135**

> ### Stage F — commercial readiness / support

**R0329 · §40 · P0 · M1–M7 · PLANNED/reference · source L1137**

> Google Play financial feature declarations as applicable, SMS permission review strategy, data safety and privacy disclosures, billing and refund support, error monitoring redaction, 3rd-party SDK audits, keyboard/TalkBack large-font audit, signed release management, real-device performance and 50+ acceptance-case suite. Prepare incident runbooks for corrupted sync, failed billing and cloud outage.

**R0330 · §40 · P0 · M1–M7 · PLANNED/reference · source L1139**

> **Definition of Done** (mandatory): all core flows work airplane-mode; user always knows whether records are local-only or backed up; no reproducible duplicate transaction via taps/sync; exports are portable and test-restorable; existing records survive **normal** signed in-place updates; paid API access is verified by backend; offline licensing tradeoffs are disclosed; reminders do not create unapproved real expenses; no obstructed labels or Save buttons on small screens; critical security and privacy cases pass; public descriptions do not promise impossible guarantees.


### Source 41. V3 authoritative research links and implementation references

**R0331 · §41 · P2 · M7 · PLANNED/reference · source L1141**

> ## 41. V3 authoritative research links and implementation references

**R0332 · §41 · P2 · M7 · PLANNED/reference · source L1143**

> 1. Android Auto Backup data, limits, rules and OS variations: https://developer.android.com/identity/data/autobackup
> 2. Android backup overview and permission restore nuance: https://developer.android.com/identity/data/backup
> 3. Android Keystore keys are device-protected/non-exportable: https://developer.android.com/privacy-and-security/keystore
> 4. Firebase Remote Config minimum version and forced/optional update patterns: https://firebase.google.com/docs/remote-config/use-cases/minimum-version
> 5. Firebase Remote Config security restrictions: https://firebase.google.com/docs/remote-config
> 6. Google Play in-app updates, immediate/flexible: https://developer.android.com/guide/playcore/in-app-updates
> 7. How Android updates preserve/restrict app identity and signing: https://developer.android.com/google/play/app-updates
> 8. Google Play Billing integration and server verification: https://developer.android.com/google/play/billing/integrate
> 9. Google Play Billing subscription lifecycle and grace/account hold: https://developer.android.com/google/play/billing/lifecycle/subscriptions
> 10. Google Play Billing backend integration and RTDN: https://developer.android.com/google/play/billing/backend
> 11. Google Play Payments policy on digital services: https://support.google.com/googleplay/android-developer/answer/9858738
> 12. Google Play Integrity scope/limits: https://developer.android.com/google/play/integrity/overview
> 13. Google Play financial features declaration: https://support.google.com/googleplay/android-developer/answer/13849271
> 14. Android Compose Snackbar/Undo guidance: https://developer.android.com/develop/ui/compose/components/snackbar
> 15. Android Navigation principles: https://developer.android.com/guide/navigation/principles
> 16. Android Paging 3 / Compose: https://developer.android.com/topic/libraries/architecture/paging/v3-overview
> 17. Android scheduling / exact alarm requirements: https://developer.android.com/develop/background-work/services/alarms
> 18. Firebase Cloud Messaging receive/delivery caveats: https://firebase.google.com/docs/cloud-messaging/android/receive-messages
> 19. Google Play personal data/privacy and disclosures: https://support.google.com/googleplay/android-developer/answer/18265346
> 20. Android developer app distribution and in-place updates: https://developer.android.com/google/play/app-updates

**R0333 · §41 · P2 · M7 · PLANNED/reference · source L1164**

> **Final note:** This specification represents a prioritized product and engineering contract, **not** a claim that every Android device, store policy, and OEM will support every experimental feature. Correctness, transparency and recoverability are more important than adding every imaginable toggle.

## Gift Card & Voucher Wallet — 0.4.0

One complete new local feature. P0: separate Room card/redemption/preferences/notification tables with v3→v4 migration; manual CRUD/archive; stored-value/single-use/discount distinctions; exact minor-unit balances, full/partial redemption, last-redemption Undo and merchant-refund history; no cash ledger writes. P1: Keystore AES-GCM secrets and keyed duplicate fingerprints; masked detail, sensitive timed clipboard, encrypted image attachment; conservative text/share parsing and bundled offline OCR/barcode capture; mandatory editable confirmation; local WorkManager expiry reminders with permission handling, customized intervals, cancellation/revision checks and deduplication. UI: wallet per-currency known active totals, unknown counts, search/filter/sort, detail/form/import/settings/More routes, optional configured tab. Existing nav defaults and preferences preserved.

Acceptance: tests for malformed/duplicate imports, unknown balance, discount exclusion, exact partial redemption and reversal, expiry/no-expiry, idempotency/stale revision, migration and restart, reminder scheduling/deduplication rules, crypto envelope tamper resistance, parser uncertainty and active UI flows; build/tests/lint. Device-only OCR/camera/Keystore/notification/IME acceptance must be explicitly separated from host tests. No SMS permissions, cloud, auto-import saves or other finance modules.


## 0.5.0 staged refinement status

OCR/forms → Smart Add → app lock → structured Settings were implemented and host-validated in order. No transfers/debts/groups/cloud modules added. Host verification is complete as recorded in validation/smart-security; real OCR corpus and phone acceptance are outstanding and block claims of production security/OCR accuracy. Existing full V3 inventory remains below/above unchanged. Next: measured phone acceptance and repair, then separately scoped recovery/backup design.
