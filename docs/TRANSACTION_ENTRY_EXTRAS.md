# Transaction/report extras and faster entry — 0.18.0

## Financial model
A split is a category allocation on ONE existing ledger record. It never creates another account movement. Expenses, income and refunds may have 2–20 distinct allocations with positive integer minor units summing exactly to the parent amount. Refund allocations use expense categories. Old allocations are retained when an older edit caller supplies no split argument; a mismatched new total is rejected. Explicitly clearing the list restores a single category. Archived historical categories remain readable. Transfers, loan principal, group-owned and payment-plan-owned records retain their existing accounting ownership and cannot be repurposed into ordinary splits.

Room16 adds six tables and a read-only personal_report_lines view through a forward15→16 migration. Category budgets/reporting use allocations; parent account and summary calculations retain the original one-movement model. Group reports retain only the user's share, and funded loan principal remains excluded from spending. Currencies are always separate.

## Receipts
Transaction forms include optional receipt controls; Settings/More → Transaction receipts also finds saved and linked records without editing money. Android Files selects images/PDFs, with no broad storage permission. Import reads at most 12 MB; images are oriented, downsampled to 2000 px maximum and compressed to a bounded JPEG. Saved bodies are at most 1 MB, five per transaction including recoverable deleted rows. PDF validation checks size/header, not document safety or rendering compatibility. No OCR, URL fetching or receipt parsing is performed.

Bodies are encrypted using the existing Keystore-backed cipher with a separate traxos.receipts key and receipt-ID authentication. Metadata remains app-private Room data, not a fully encrypted database. SHA256 detects exact processed-payload duplicates within a transaction; it does not recognize different photographs of the same bill. New-form media uses encrypted persistent drafts adopted atomically with Save. Back/discard removes those drafts; Settings can clear leftovers after process loss. Attached receipts have revision-checked delete/restore. A deleted transaction retains its receipts for Undo/recovery.

Open grants a chosen external viewer temporary access to a decrypted private-cache copy. Settings can revoke grants and clear copies; copies older than 15 minutes are removed on a subsequent app/surface refresh, not an exact timer. Third-party viewer copies cannot be erased by Traxos. Encrypted recovery archives re-encrypt bodies under the destination device key. Standalone JSON excludes bodies by default; explicit inclusion exports portable plaintext bytes with a warning. Card vault, PIN and credentials stay excluded.

## Trends and filters
Settings/More → Trends provides date, account, category, merchant, currency, parent amount, income/expense, ledger/group, receipt and split filters. Comparison supports the preceding equal-length period, previous calendar month and previous year. Dates are inclusive, maximum366 days. Amount bounds require one currency. Category filters show allocations in trends, while Activity drill-down shows complete matching parent transactions. One split parent counts as one entry per aggregate.

Charts zero-fill days for short periods and use calendar months for longer periods, displaying net spending and income separately. Refunds may produce negative spending. Drawing coordinates use floating point; financial calculation/storage remains exact integers/decimals. Percentage changes appear only for a positive comparison baseline. No exchange conversion is implied.

## Faster entry
Settings/More → Faster entry manages ordered, editable favorites (amount/account/category/optional merchant/note), exact/prefix/whole-phrase merchant rules, widget buttons, shortcuts and Quick Settings tile. Rules are deterministic, account/type scoped, priority ordered and optional. Suggestions preserve explicit category choices and never approve SMS/voice or save money automatically.

The native widget has three configurable actions; it displays no balances. Preset names on external surfaces require opt-in, and never display amounts or card secrets. Dynamic shortcuts expose reviewed actions and top favorites within launcher limits. Favorites may request a pinned launcher shortcut; Android decides placement. Stale/archived favorites fail safely instead of substituting another account. The tile opens an expense for review; Android may require manual placement on older releases.

Every external action routes through the existing app lock. Intents accept action/preset identity, never untrusted amounts/accounts. Pending actions open review forms; dirty drafts require replacement confirmation. Surface refreshes use WorkManager and may be delayed by Android. Widgets/tiles/launcher support is OEM dependent. No new library or permission was introduced; the tile service uses Android's system binding permission.

## Validation
See PROGRESS.md for actual build/test results. Physical-device gates include widget resizing/pinning, Quick Settings placement/keyguard behavior, locked launches, OEM shortcuts, Files providers/large real images, encrypted viewer sharing, PDF serialization, accessibility/keyboard behavior and upgrade over the installed release. No phone tests or emulator tests are claimed.
