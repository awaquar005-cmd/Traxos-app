# Gift Card & Voucher Wallet

## Using the wallet

Open **More tools → Gift Cards** or pin **Gift Cards** to an existing slot in Settings → Navigation Customization. The four default slots and separate center quick action are kept. More is also an optional slot. Settings stays reachable through the contextual menu if it is removed from the bar.

Add a merchant, card name/type and code. PIN, values, expiry, notes and an image are optional. Supported currencies match the existing ledger: INR, USD, EUR, JPY and KWD. Unknown amounts remain blank; no merchant balance lookup is performed. Stored-value cards support partial use only when you explicitly enable it based on merchant terms. Single-use vouchers require full redemption. Discount/promotional codes have no cash value and are excluded from monetary totals.

Wallet totals contain only known remaining value on active, unarchived stored-value/single-use cards, separated by currency. Unknown balances have a count. The existing global hide-amount preference masks wallet totals/list balances; code visibility is a separate gift setting. Search covers merchant/card name; filters cover active, expiring, redeemed/expired and archived cards. Sort by expiry, merchant, newest or balance (currency grouped, never converted). Expiry status refreshes on resume and at midnight.

Open a card to Reveal or Copy its code, view a PIN/notes/image, edit metadata, archive/restore, delete or mark expired. A manually expired card can return to its recorded expiry status. Codes clear from the visible detail when the app pauses. Copy uses a sensitive clipboard flag and attempts to clear matching clipboard content after a minute while the process survives. Android 13+ additionally manages clipboard expiry. Uninstalling/clearing app data removes the only available wallet; install APK updates in place.

## Smart import

- Paste text explicitly or share text/images to Traxos from another app.
- Import a selected screenshot using bundled on-device Latin OCR plus barcode recognition.
- Scan a QR/barcode by taking a photo through the installed system camera app. This is a capture-and-read scanner, not a continuous CameraX viewfinder. Small/dense codes may need a cropped full-resolution image import.
- A custom editable **Gift card detected — Add to Traxos?** sheet precedes the full form. Reject stores nothing; Add opens the draft; only Save persists it.
- Automatic opening of the confirmation sheet is configurable. When off, shared results wait for **Review detected card**. Confirmation and Save are required in every mode.
- Parsing is deliberately conservative: labeled codes and amounts, explicit supported currency and unambiguous ISO expiry dates. Bare `$`/`¥`, ambiguous dates and unknown merchants stay blank. Unsupported amount grouping stays blank rather than becoming a truncated amount. Review all extracted values. The detected amount can be labeled as original value or remaining balance; a face value does not imply the card is unused.
- Multiple barcodes require cropping to one; URLs are not followed or treated as codes. No merchant APIs or paid/hosted AI. No direct SMS reading or SMS permission. Text/image detection switches control the corresponding imports.

Codes are compared using keyed fingerprints, normalizing whitespace/hyphens/case. Stored-value and single-use codes share a duplicate scope; discount codes are scoped to their merchant so unrelated stores may use the same promotion. Archived cards still prevent duplicates. Deleted cards leave that active index, allowing deliberate re-entry.

## Accounting and history

A gift card is a separate value store, not a bank/cash account. Gift operations never insert expense/income records or alter cash account balances.

| Event | Gift wallet | Cash ledger |
| --- | --- | --- |
| Purchase | Record original and confirmed remaining value; source Purchased | Record the actual payment once yourself if needed; no automatic posting |
| Gift received / promotion | Record confirmed value or leave unknown | No invented earned income |
| Redeem ₹300 of ₹1,000 | Remaining ₹700; immutable redemption event | No second expense |
| Full redemption / discount use | Mark redeemed; stop reminders | No posting |
| Undo latest redemption | Restore that value/status; append Undo event | Unchanged |
| Merchant restores latest redeemed value | Append Refund and restore that value | Unchanged; a cash refund is a separate actual ledger operation |

Only the latest redemption can be reversed, and only once. A later action cannot silently rewind earlier history. Amount/type/currency fields are locked once history exists; use redemption and refund actions. Stable operation IDs, expected revisions, Room transactions and UI single-flight prevent duplicate deductions/retries. This wallet is an organizer; it cannot verify whether a merchant actually accepted a code, restored value or permits partial redemption.

## Reminders

Default intervals: 30, 7, 1 and 0 days before expiry, around local 09:00. Set up to eight distinct intervals from 0–365 in Gift settings. Android notification permission and the Gift card expiry channel must be enabled. WorkManager persists jobs across normal restarts/reboots, but Android battery/force-stop restrictions can delay delivery; these are not exact alarms.

Daily/startup/resume/change reconciliation reschedules unique jobs. Workers recheck card revision, expiry, settings, archive/delete/redeemed state and permission/channel state. Stable notification tags and durable receipts deduplicate retries. Missed early reminders are skipped; an overdue expiry can notify once. No-expiry cards have no expiry jobs. Editing, deleting, archiving or redeeming cancels/reconciles pending work. Notification wording never includes a merchant, balance, code or PIN.

## Security boundary and recovery

Codes, PINs, notes and bounded images are encrypted with standard AES-256-GCM, random IVs and row-ID associated data. Separate non-exportable Android Keystore keys supply encryption and HMAC duplicate fingerprints. Lists and scheduler queries omit encrypted attachment blobs to keep memory use low. Sensitive drafts and extraction results stay in memory, outside saved-state bundles; an unsaved draft can be lost on process death. Keys are never silently replaced when existing wallet data is present. A failed decrypt is reported without clearing data.

Merchant names, balances, dates and existing financial records remain app-private **unencrypted metadata** in Room. No app lock, biometric protection, whole-database encryption or portable encrypted backup is implemented. OS backup/transfer remains disabled. Device-bound encryption cannot recover after key/data loss. The versioned envelope supports future explicit decrypt/export/re-encrypt recovery work; copying this database alone is not a portable backup. Deleted entries are hidden tombstones with retained encrypted history, not a secure-erasure claim.

Bundled detection dependencies include vendor SDK infrastructure; their inherited internet permission is explicitly removed from the merged APK. The app cannot upload OCR text/images, use hosted recognition or deliver SDK telemetry over the network. It requests no SMS, camera or media-library permission; notification permission is requested only through its settings action.

## Verification limits

Automated host evidence belongs in `docs/validation/gift-wallet/`. It includes real Room migration/restart, exact accounting, duplicate/idempotency checks, standard crypto envelope tamper tests, WorkManager scheduling/private notification tests and Compose interaction/layout tests. Host crypto tests replace only Android Keystore provisioning with ephemeral JCE keys. Synthetic previews are not user data or phone screenshots.

Actual hardware Keystore, first-use offline OCR/camera quality, system notification permission UI/channel delivery, OEM background restrictions, real keyboard, enlarged fonts/TalkBack and OS process death remain physical-device checks in DEVICE_QA. No emulator is used.
