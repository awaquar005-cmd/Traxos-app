# Debts & credit — 0.14.0 Personal

Native More → Debts & credit is a real offline page, also selectable in navigation customization. It replaces the former More debt-preview link with implemented private IOUs; other future previews remain explicitly labelled.

An IOU records a person, fixed direction (they owe me / I owe them), currency, opening amount/date, optional due date and note. Rename/update note/due date, record partial/full repayment, increase/reduce by adjustment, inspect dated history, Undo latest unreversed movement, archive settled threads and restore. Active/settled/archived views and separate per-currency receivable/payable totals never net unrelated currencies. There is no destructive deletion of history. Archive requires zero outstanding. Opening/direction/currency are immutable; corrections use recorded adjustments.

Credit-card rows read the existing ledger liability, preserve overpayment as a credit balance, never display credit limits as assets and open the existing paired card-payment flow. Encrypted PAN/CVV/expiry storage is untouched and remains excluded from portable snapshots.

## Accounting and persistence

This release tracks **existing private IOUs**, independently of bank/cash ledger transactions. Recording IOU repayments/adjustments does not move account money or create expense/income. The UI states this before entry. If users also record the actual money movement, that stays a separate explicit ledger action. No claim of automatically linked lending/borrowing bank postings, interest, group netting, shared settlement, cloud sync or due-date reminders.

Room12 adds only debt_threads and debt_movements using explicit11→12 migration. Stable UUID operations, atomic revision checks, bounded exact Long minor units and immutable reversible movement history prevent duplicate submissions and stale updates. No Float/Double accounting; no data reset. UI guards reentry and retains draft fields in Saveable state; operations run off the main thread. Private IOU metadata, like the current ledger, is app-private Room plaintext, not a new encryption claim.

Portable/Drive snapshots include both new tables, validate totals/history/references, and still accept original schema11 snapshots with empty debt tables. Additive restore aborts conflicts. No card vault, PIN, tokens or unfinished Smart Add is exported.

## Interaction corrections

Account/Drive uses a form-specific SettingsGroup with12dp internal margins and10dp text/control gaps rather than row-list2dp/zero-gap layout. Other row lists retain compact spacing. Buttons retain48dp targets; no logo outlines added. The Google button now opens actionable setup guidance/Import when Firebase is absent, instead of appearing available but being disabled. It launches real Google authentication only with valid configuration; independent Drive consent remains usable. No Google validation bypass is introduced.

Overflow menu uses transient open state rather than restoring an open popup across recreation, unique accessible button tags and the proper tools icon. Native pointer-input regression exercises More tools navigation and menu dismissal, independently of semantic click tests. Phone/OEM touch and real Google consent remain device checks.

## Validation

See PROGRESS.md for actual executed build/test/lint results. Host fixtures are isolated; no user records or fake balances are seeded. Physical keyboard, TalkBack/large fonts, process-kill/rotation, cloud consent and upgrade checks remain on-device acceptance gates.
