# Architecture

## Boundary and dependency direction

One Gradle app module to fit an 8 GB laptop; Kotlin packages isolate domain, data and UI. Compose → lifecycle ViewModel → repository → Room DAO. Manual constructor DI in Application; no global UI database access. Room Flow is the only finance read source. Navigation Compose owns destinations; SavedStateHandle owns form/operation identity and search state. No internet permission or cloud SDK in M1.

## Money and local schema

Money = checked signed Long minor units + ISO currency; parse through BigDecimal with UNNECESSARY rounding. No currency Float/Double. Bound amounts/row count to guarantee SQLite SUM remains within signed 64-bit limits, and check resulting account balances transactionally. Currency-specific fractional scales. Accounts: stable UUID, local profile, CASH/BANK type, currency, immutable opening balance, archive state and revision. Categories: seeded labels (not fake financial records), type. Transactions: UUID, account/category FKs, positive minor amount, EXPENSE/INCOME type, epoch day, timezone ID, note, revision and tombstone. Preferences singleton: theme. Journal: operation primary key, entity ID/type, base/local revision, kind, payload version, immutable payload, timestamp, pending state. Every financial/account mutation and journal entry commit together; operation identity is generated before submission and reused on retry. Account opening amount is journaled. Delete/Undo use tombstone/restoration events. Duplicate operation returns without a second mutation.

Amounts limited to 999,999,999.99 for 2-decimal currencies, 1 million transaction rows maximum per device in M1; aggregate worst-case stays below Long overflow. Account currencies are not converted or summed together. SQL computes balances and monthly summaries; indexed and paged ledger. No destructive migration fallback; exported schema v1 tracked in Git; future changes require explicit migration and migration-test fixture before version increment.

## Screens and state

Home has month spending/income and per-currency account balance plus recent six rows, honest local-only status and Quick Add. Transactions is Room Paging 3 with debounced search; add/edit form validates amount, account/category, date/note. Accounts supports cash/bank create, rename, archive/restore (archive blocks new posts). Settings has persisted System/Light/Dark and version/data-loss explanation. Navigation preserves tab state; forms retain inputs and immutable operation ID in SavedStateHandle. Discard confirmation on dirty Back; commit gate disables actions synchronously. UI reports commit failure without clearing form; cancellation propagates. Material accessible controls, scrollable IME-aware form, full money values, ellipsized titles with editable full text.

Mixed-section Home LazyColumn keys are namespaced by section and stable identity; currency summary and balance rows must never share a key. Populated-state Compose rendering and persisted-ledger Activity launch/recreation are tested on the host in addition to repository math.

## Future extensibility (contracts, not implemented features)

Add paired postings for transfer/card settlement, linked refund/debt/group source payment and exact share allocations in M2. Existing expense/income cannot be relabeled as a settlement. Extend schema via tested forward migrations; do not shoehorn transfers into income/expense. Financial corrections preserve journal history and base revisions. Local profile namespace is reserved; account switching must isolate all rows/caches/work.

Optional backup uses streaming versioned canonical data + maintained KDF/AEAD, portable passphrase-derived key separate from Keystore. SAF outside app storage, preview/staging validation/atomic activation, safe merge/conflict review. User-owned Drive stores encrypted snapshots; upload acknowledgment alone is not a verified restore. Personal multi-device replication is a separate future decision, not an implied benefit of snapshot backup.

Future WorkManager schedules unique authenticated network-constrained work only after opt-in. A single profile lease drains dependency-ordered 100-op/256 KiB initial batches with stable IDs; ack transaction then pull opaque cursor pages transactionally. Retain unsent journal and tombstones; money conflicts need review, never blind last-write-wins. Server authorizes memberships/entitlements independently. FCM is only a hint, not financial source of truth. Firebase starts only with shared groups. No headless fake worker that claims to sync before a transport exists.

## Security boundary

M1 Room is app-private, not SQLCipher encrypted and has no PIN/biometric claim. Disable OS backup and explicitly exclude database/preferences/keys in cloud and device transfer rules pending tested recovery. FLAG_SECURE protects task previews in development; revisit accessible screenshot policy in M3. No sensitive logging or network/analytics. M3 must integrate maintained database encryption and Keystore envelope keys with invalidation recovery, supported BiometricPrompt and throttled slow PIN verifier. No custom cryptographic algorithms. Portable backup keys must work on another device. Never expose admin secrets in APK.
