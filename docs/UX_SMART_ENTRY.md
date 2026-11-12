# TRAXOS 0.6.0 — UX, smart entry and security

This development release continues the existing app. Install as Update; never uninstall or clear storage to upgrade. Package/signing identity is retained. Room schema 6 uses an explicit5→6 migration and the existing1→5 chain. Existing financial/gift records and encrypted payloads are preserved.

## OCR removed; barcode and text imports retained

Text recognition, its ML Kit model, preprocessing/reading-order pipeline and native OCR fixture harness are removed. There are no screenshot-text scanning controls or OCR settings. The independent bundled QR/barcode decoder remains, including gallery/photo capture and bounded EXIF-aware image decoding/cropping. Photos are not read for printed text. Existing encrypted gift attachments still display; manual gift entry, shared/pasted text, reminders, redemption/history/Undo/refunds remain.

`gift_settings.imageDetection` is a historical database column retained for migration compatibility; it now gates only QR/barcode scanning and is labeled accordingly. Removing an OCR feature does not require deleting saved gift data or images.

## Categories, accounts and saved entry options

Settings → Accounts & Data now opens Categories, Merchants and Descriptions. Create, rename, style or archive income/expense categories. Built-in categories retain their IDs and can be renamed/archived; category direction cannot change, preserving accounting meaning. Names are normalized for duplicate checks within each category direction, including archived records. Restore archived options rather than creating a duplicate.

Accounts support Cash, Bank, Wallet and Credit Card, custom names and chosen icons/colors. Type, currency and opening balance remain immutable after creation; edit name/style/archive through Accounts. Archived accounts/categories keep balances/history and cannot be chosen for new entries. Existing references remain editable without silently switching categories/accounts. No hard deletion of referenced options.

Category/account selection sheets in transaction entry, Smart Add, default-entry Settings and card-payment entry include **+ Add new**. Inline creation stays in the current form, selects the durable new ID immediately and retains amount/date/note. Existing separate account creation remains available when the ledger has no accounts. Merchant selection in the transaction form uses stable references; renaming updates those displays without rewriting amounts. Smart Add message review copies the chosen merchant into its description; assign a saved merchant in Edit Transaction if a stable catalog reference is wanted. Description templates copy their text into the note, so later template edits do not rewrite history. Gifts keep their existing merchant text; selecting a saved merchant helps new/edit entry without silently renaming saved cards.

Notes, saved merchant/description choices, opening dates and appearance options stay under optional detail controls where applicable. New forms use saved/default/recent valid choices without changing an existing record's currency. Gift details beyond merchant/code/known remaining value/optional expiry remain collapsed. Unknown gift values and expiry remain blank, not invented.

## Credit-card accounting

A card account stores a signed balance: negative means amount owed, positive means a credit balance. An opening amount owed is stored negative; **a credit limit is never treated as money**. Card purchases reduce the signed balance and count as expenses. Explicit card refunds increase the balance and reduce net outflow rather than creating income. A refund can leave a genuine recorded credit balance. Amounts use integer minor units; currencies never combine without conversion.

Accounts → card → Pay card records two linked postings atomically: decrease the paying cash/bank/wallet account and increase the card balance. Payment types are excluded from both expense and income totals. Payment amount cannot exceed recorded debt or the recorded source balance, currencies must match, and dates cannot predate either opening date. Repeated operation IDs create no additional postings. Payment deletion/Undo updates both sides; payment amount correction requires deleting the pair and recording a new payment. Ordinary transaction editing cannot split a payment.

This is a manually maintained card ledger with charges/refunds/payments, not a bank connection, statement/interest engine, available-credit calculation or automatic settlement classifier. Smart Add recognizes explicit credit-card purchases but requires the matching card account; settlements/transfers/reversals remain review-only and cannot become ordinary expenses/income. Use Pay card for settlements. Suggestions created by older builds as review-only stay review-only.

## Personal versus Play builds

- **personal** compiles the incoming-SMS receiver and consent UI and declares RECEIVE_SMS only. It does not declare READ_SMS, SEND_SMS, contacts, Accessibility or internet access. It cannot read historical inbox messages.
- **play** excludes the receiver, ingestion implementation and SMS permission UI at the source-set level. Its manifest explicitly removes SMS permissions. Manual share/paste remains available. A runtime toggle is not used as a substitute for build isolation. This is preparation for future Play distribution, not Play approval or publication.

Personal setup: Settings → Smart Tracking → Incoming financial SMS → read disclosure → Continue → Android permission. Detection starts only when build support, explicit consent, permission and Smart Add enablement all allow it. Consent is checked before SMS PDU decoding and rechecked by ingestion. Denial/revocation stops ingestion; a denied request explains the limitation and keeps share/paste available. Notification alerts are a separate option under Notifications and respect Android notification/channel permission.

Android documents RECEIVE_SMS as a **hard restricted permission**: the installer on record must permit it in addition to runtime consent. Sideloading does not guarantee this. Traxos does not change permission allowlists, request an unrelated default-SMS role or bypass OS/installer controls. [Android permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS), [runtime permission guidance](https://developer.android.com/training/permissions/requesting), checked 2026-11-12.

Eligible alphanumeric service senders are screened; personal phone-number senders, OTP messages and unrelated text are rejected before suggestions are persisted. Parsing is local. URLs are removed/ignored, never opened, resolved or scraped. Only reviewed Add creates a financial/gift record; Edit, Reject and Ignore remain available. Missing/ambiguous values need correction. Account hints are hints, not proof of which account was used. Message dates use explicit unambiguous formats; SMS receipt date can supply a fallback that must be reviewed.

Encrypted inbox payloads and keyed duplicate identities survive restart. Stable payment references deduplicate equivalent confirmations even when account hints are missing; exact legacy identities remain recognized where available. Without a stable reference, body normalization only catches equivalent text, not every differently worded bank/payment-app notification. Review against the ledger before approving uncertain duplicates; no fuzzy financial record is silently discarded. Discount-code identities are merchant-scoped, while stored-value codes retain wallet duplicate enforcement. Approval and repository writes commit together using stable IDs.

## Lock, navigation and visual changes

With app lock and device authentication enabled, a resumed locked session automatically requests the native Android biometric/device prompt once. A ViewModel-owned lease survives rotation/recomposition; a pending guard prevents a second native prompt. Cancellation leaves the app locked, with a manual device retry and offline PIN fallback. Eligible strong fingerprint/face and supported API 30+ device credentials remain Android-controlled. Missing enrollment/key access produces PIN fallback, never a bypass. Privacy shielding clears onResume as well as onStart, fixing a shield left behind by permission-dialog returns.

The custom PIN screen uses the existing five-square T mark, compact keypad, press/haptic feedback and a short entrance. PIN verification remains salted PBKDF2-HMAC-SHA256 with encrypted Keystore-bound state and persisted throttling; no plaintext PIN storage or custom cipher. Recent-app/screenshot protection remains enabled. The native Android 12+ splash uses a 180 ms mark animation; older Android uses a native static mark background. There is no artificial wait or new welcome screen. Compose motion follows system reduced-motion settings.

Settings is now a fresh utility back stack on each entry. Leaving Settings removes its nested branch instead of saving it as a tab to restore later. Profile/Security/Appearance return to Settings; Settings returns to the preceding main destination. Rotation inside a detail page can still preserve that active page. Instant opaque page replacement is retained; no outgoing/incoming crossfade overlap.

Overflow uses a compact border-free surface, aligned icons/labels, 4 dp outer padding and 48 dp minimum touch rows. Shared controls use tighter padding/radius with clear pressed/disabled states. The theme now supplies semantic root text color, including text outside a Surface, and clearer placeholders/disabled text. Palette contrast is host-tested in both themes. Custom icon/color choices are shown in category/account lists.

## Security and recovery limitations

App lock protects access; the ledger and merchant/category/gift metadata remain app-private plaintext Room. Gift secrets and Smart Add payloads stay encrypted in separate device-bound key namespaces. There is no portable backup or PIN-reset shortcut. Losing PIN/device authentication/Keystore access may make protected data inaccessible. Do not clear data to recover a PIN or fix an upgrade. No financial data is uploaded.

## Build and install

```sh
./gradlew --no-daemon :app:assemblePersonalDebug :app:testPersonalDebugUnitTest :app:lintPersonalDebug
adb install -r app/build/outputs/apk/personal/debug/app-personal-debug.apk
```

For the SMS-free build:

```sh
./gradlew --no-daemon :app:assemblePlayDebug :app:testPlayDebugUnitTest :app:lintPlayDebug
adb install -r app/build/outputs/apk/play/debug/app-play-debug.apk
```

Both variants use the same application ID and data. They replace each other; they are not parallel installations. A local rebuild must use the same signing key as the installed app. The downloadable debug APK retains the previous development signing identity.

Gradle retains1.5 GB heap/two workers/no parallel projects/in-process Kotlin and512 MB per host test fork. Build one invocation at a time; use a USB phone, never an emulator. QR photo decoding remains bounded to 6 MP; the removed OCR model reduces artifact size. No new paid/cloud service or UI library was added.

[Actual results and remaining physical checks](validation/ux-refinement/README.md). Next task is phone acceptance: upgrade preservation, native biometric/credential/cancellation behavior, installer-specific SMS access/permission revocation, incoming bank/UPI/voucher formats, real keyboard/TalkBack/large-font/small-screen use and barcode capture. No physical-phone acceptance is claimed by host tests.
