# 0.15.0 preservation addendum

Real local Groups, Card statements and Subscriptions & bills are added to More and optional navigation. Their former preview links alone are replaced; the reference preview catalog remains available. All existing routes/fields/defaults/QR/images/gifts/vault/CVV/lock/voice/recurring/IOUs/Plans/cloud/local backup behavior and brand/hero remain. Old Room12 entity structures are unchanged. Monthly/category reports intentionally recognize group personal shares and suppress explicitly linked cash/reimbursement entries. Backend sharing is deferred by owner choice, not claimed implemented.

# 0.14.0 preservation addendum

Real debts route is added, replacing only the More private-debt preview link. All prior routes, preview catalog, financial/gift/QR/images/vault/security/Plans/recurring/voice/default/preferences/Google/Drive/local recovery remain. New schema12 tables and backward-compatible archive reader preserve schema11 data; no vault export or dashboard hero change. See DEBTS_CREDIT.md.

# 0.13.0 account and recovery addendum

Native Drive consent, fixed-owner linking, encrypted resumable snapshots with remote read-back/restore, local-copy support and probed SAF selection are implemented. Email/password/Google account-login/provider linking use real Firebase endpoints with encrypted session storage, but owner configuration is still required; no Firebase API key/Web client was supplied and no real account/backend/phone result is claimed tested. Drive-only linking is independent of that setup. See OAUTH_DRIVE.md and the final validation report. All previous feature routes and schema11 remain; the older snapshots below are historical when superseded.

# 0.12.0 preservation addendum

See LOCAL_DELIVERY_MANIFEST.json for the0.11.0→0.12.0 route/source/schema audit. All prior routes remain; backup is added. Core financial/gift/QR/images/security/recurring/voice and brand assets remain unchanged. Home uses the existing hero geometry with a truthful optional local-snapshot caption; current selection/default/Undo/navigation functionality remains. Local recovery/CSV are now real features, excluding the entire card vault, app lock, pending SMS and device preferences. Google configuration is received; native Drive/group backend transports remain future work. The map below describes the preceding V8 delivery and is retained as a cumulative inventory.

# V8 preservation and implementation map

User overrides: native Kotlin/Compose; current dashboard hero remains; slightly shorter chrome/search, cobalt center Add in dark mode; microphone home action and hold/release voice; preserve current functionality/defaults. OCR alone stays removed. QR/barcode and encrypted gift-image attachments remain. V3 attachment SHA-256 matches the already adopted source exactly. V8 HTMLs are visual/interaction references, never production authentication/storage or an exhaustive feature inventory. Original references are in references/v8; they are not APK assets.

| Existing feature | Owner / new location | Preservation |
|---|---|---|
| Money/currencies, account opening, expense/income/refund, liability accounting | data/LedgerRepository, domain/Money; Activity/Accounts | Exact minor units and accounting unchanged |
| Edit/delete/Undo, linked transfer/card settlement, deleted restore | Forms, TransferScreen, CardPaymentScreen, LedgerToolsScreens | Retain atomic journal/paired operations; Transfer remains account-owned dialog |
| Room schema10/migrations and operation IDs | data/Database; schemas | Forward migration only for new Plans data; no wipe |
| Paging, debounced literal search, account/date/category/currency filters, ordering | MainViewModel, LedgerScreens | Retain all controls and saved state |
| Monthly reports and drill-through | LedgerToolsScreens | Keep currency isolation and exclusions |
| Custom categories/icons/colors, account labels, merchant/description catalogs | CatalogComponents/CatalogScreen | Keep inline Add New, archive/references and immediate selection |
| Remembered/default account/category, optional details and draft identity | FormViewModel, preferences | Voice approval enters this same form, never a parallel writer |
| Gift creation/edit/delete/archive, remaining/original values, expiry/refund/redemption/Undo | gifts package and GiftWalletScreens | Retain all functions and encrypted secrets |
| QR/barcodes, saved encrypted gift images/photo attachments | GiftMedia and gift secret payload | Keep independent of OCR; no OCR dependency restored |
| Expiry jobs/notification intervals, permissions and privacy | gift workers/settings | Preserve scheduling and cancellation |
| Incoming Personal SMS and share/paste Smart Add | personal sources, smart package, SmartInboxScreen | Retain consent, dedup/review and denied/revoked fallback |
| Recurring rules, account-based entry, manual due processing and background catch-up | data/Recurring, recurring/RecurringWork, RecurringPanel | Keep pause/edit/resume/delete, stable IDs and error retention |
| Offline PIN/biometric/device fallback, timeout/throttling/shield | security package, SecurityScreens | No HTML auth bypass; preserve real system prompts |
| Encrypted debit/credit card references and per-field clipboard controls | CardVault/CardDetailsScreen/CardClipboard | Keep encrypted data and CVV by explicit current user instruction; exclude from future exports/backups |
| Debug screenshots and release secure window | MainActivity | Preserve explicit development behavior; no false release certification |
| Light/dark/system/accent and configurable nav/center action | Theme, NavigationLayout, NavigationSettings | Keep user choices, reordering/reset, Settings reachability |
| Settings nested Back/home reset, More, app information | SettingsScreen, UiHierarchy, App | Map every existing destination; no fake connected account |
| 100 isolated future-screen previews | ReviewCatalog/DesignReviewScreen | Still labelled previews; never counted as implemented finance modules |

## New work and gates

- Native V8 visual translation: tokens, flat list surfaces, unboxed useful pictograms, compact settings groups, smaller chrome/search, cobalt Plus. Current hero geometry/brand assets protected; no HTML wrapper.
- On-device voice: explicit microphone permission, hold/release plus home mic, local speech recognizer only where supported, typed fallback, editable review, same account/category defaults and normal Save. Never silent/cloud transcription or automatic posting.
- Plans: real local budgets and goal progress, separate from bank balances and transfers; explicit accounting description. Missing cloud modules remain tracked, not simulated.
- Cloud sign-in, Drive snapshots and Supabase shared/backend sync require actual project setup, OAuth Android package/certificate and authorized backend configuration. A browser-demo login or fake “synced” label is prohibited. Ask for project identifiers/configuration through appropriate settings; no secret pasted into code/chat.
- V8 proposes no CVV storage. The user explicitly answered “everything store but encrypted”; current encrypted CVV storage is therefore preserved. No deletion/migration is authorized. Per-reveal device re-authentication and portable vault policy remain separate security work; do not claim complete V8 vault compliance.
- Remaining large roadmap items (portable verified backups, authenticated groups, debts, subscriptions, widgets, server sync) must be separately implemented/tested, not omitted from the plan or represented as working controls.

## Acceptance

Full Personal build/tests/lint; before/after native host renders against original HTML references; dark/light, small screen/font size, saved defaults, financial/data migration, QR/images, voice denial/unavailable/local review and new Plans rules. Real mic/model/OEM/phone performance and cloud acceptance remain distinct device/service gates. Only Personal APK.
