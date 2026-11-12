# Compact forms and cards — 0.9.2 Personal

Activity's SMS shortcut is a compact header action, absent at zero pending suggestions. No separate full-width SMS row or empty gap. Existing Smart Add approvals are unchanged.

Bank accounts can hold optional debit-card references; credit-card accounts retain liability accounting. New Bank/Credit Card accounts offer “Add card number, CVV & expiry after saving”. It saves the financial account first, then opens the encrypted/masked editor; cancelling details leaves the account intact. Existing Accounts rows and edit forms expose the same editor. No credentials enter SavedStateHandle or the audit journal. No new account type, credit-limit semantics or payment processing. Existing Keystore recovery limits apply.

Account, transaction, gift and card-detail entry use a bounded centered surface, compact spacing, scrollable body and pinned Save. Pickers/review sheets use a centered Dialog without the old ModalBottomSheet slide/settle animation. Safe hit targets, Back/dismiss and draft state remain. This removes a known animated sheet transition, but does not establish phone frame-time/jank results; actual IME and OEM form-opening smoothness require phone validation.

Activity/recent rows show a clearly labelled Recorded timestamp from the existing indexed operation journal where available, formatted in the record's time zone. This is entry-recording time, not a fabricated bank payment time: old ledger rows contain a transaction date only. Rows without a timestamp omit it. No schema migration or timestamp backfill was introduced. Actual user-editable payment-time capture remains a future data-model change.

Only Personal built. Update without uninstalling. Same package/key, version code12, Room schema9 unchanged. See validation/compact-forms for actual build/test/lint results and device limitations.
