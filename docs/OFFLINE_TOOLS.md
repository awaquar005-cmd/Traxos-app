# Traxos 0.8 — offline ledger tools

This release completes three real features on top of the existing finance app. The separate 100-page UI review sandbox remains sample-only. No new SDK, network service, permission or cloud cost is introduced.

## Activity filters and ordering

Open **Activity → Filters**. Choose optional inclusive From/Through dates, financial account, category and currency; Today and This month fill date ranges. Archived accounts/categories remain available for history. Invalid dates or reversed ranges keep the sheet open with a visible error above Reset/Apply. Nested selection sheets preserve your typed values. Reset edits the filter draft; Apply commits it. Closing without Apply leaves existing filters unchanged.

Filters combine with literal debounced search and the existing All/Expenses/Income/Transfers/Card payments choices in Room before paging. A visible scope summary and clear control remove search and filters together. Clearing does not reset your sort preference. A valid filter combination can deliberately return no matches (for example, an INR account with USD currency); it never converts records.

**Activity → Newest first / Oldest first** and **Settings → Accounts & Data → Activity order** change the same locally persisted preference immediately. Ordering uses transaction date and a stable ID tie-breaker. Date/account/category/currency/search filters are session state, preserved through task recreation; a fresh task starts unfiltered. Sorting persists across fresh launches.

## Monthly reports

Open **More → Monthly reports**, the Activity header's overflow, or Settings → Accounts & Data. Reports is also a real optional destination in Navigation Customization; existing default slots are unchanged.

Use previous/next month, or tap the month and enter YYYY-MM. Spending and Income views share exact per-currency totals. Spending shows purchases, refunds when present and net spending (purchases minus refunds). Income stays separate. Transfers, credit-card repayments, deleted records, account opening balances and gift redemption are excluded. Archived account/category history remains included. These are calendar-month spending reports, not a bank cash-flow statement, budget or credit-card statement.

Refunds reduce spending in the month their existing REFUND entry is dated. A refund of a prior month's purchase can make this month's category total negative. No original purchase is invented or retrospectively matched. Existing card-refund accounting remains unchanged; this does not add bank refund entry or reconciliation features.

Tap a category to open Activity with that exact month/category/currency/type, clearing previous search and account scope. Back returns to the report and selected month. Reports react to local edits, deletions, restores and Undo. Global balance masking hides every displayed report amount, including purchase/refund subtotals. Empty months, loading and retry states are real.

## Deleted transactions and restore

Open **More → Deleted transactions**, Activity's overflow, or Settings → Accounts & Data. Search is paged and independent from Activity filters. One linked transfer/card payment appears once. Restore asks for confirmation, restores original references/dates, updates balances and offers Undo. For a linked movement, both legs and journal entries change atomically. Repeated taps/retries, stale revisions and inconsistent pairs cannot partially restore data.

The list uses original transaction dates, **not deletion timestamps**. All existing tombstones stay recoverable; there is no automatic expiry or permanent purge. No retention timer is invented for old records. Deleted gift cards are not included; the gift wallet's archive/history behavior is unchanged.

Restoring history can reduce today's balance below zero or create a card credit balance, particularly if other records changed after deletion. This is an explicit recovery of the original event, not a new payment. The confirmation and journal retain that distinction. Archived references remain archived; restore does not unarchive or rename anything.

## Shared UI refinements

Action buttons, text buttons and icon buttons have a subtle half-dp outline and existing pressed/disabled/busy feedback. The logo is explicitly exempt from outlines. No new border is added to amount/search cards; text fields retain their existing minimal focus/error treatment. The center action remains filled and distinct. Thin borders are decorative; normal text/pressed-state contrast checks remain. Compact Activity controls replace redundant local Add, while central Quick Add/header fallback and empty-state creation remain accessible. Fixed Apply/Reset and restore confirmations use shared Traxos overlays. Native touch targets stay at least 48 dp; normal spacing is compact and expands for larger fonts. Report drill-through and Settings utility entries have an explicit Up/Back path.

## Splash correction

Android12+ was masking the full-bleed splash vector, clipping the corner squares into curved fragments. A dedicated transparent padded drawable puts the complete five-square mark inside the system icon safe area at a balanced approximately80 dp visible width. A fixed outer padding group stays separate from the small inner entrance animation, so animation cannot undo the safe area. The pre12 splash remains64 dp; the lock/header mark keeps its own compact size and has no tile or outline.

A native host drawable test compares every pixel before/after the standard circular mask, verifies five separate square components and the animated resource reference, and checks the legacy mark. Its PNG is a **mask simulation**, not a screenshot from the user's phone. OEM startup rendering still needs phone acceptance.

## Data and validation

Room7→8 adds only `preferences.activitySort`, default NEWEST. Financial, gift, inbox and journal entities remain identical to7, with every earlier migration retained. There is no destructive fallback. Tests use the published7 schema with encrypted synthetic gifts and existing ledger/preferences, then reopen and verify preservation.

See [actual validation results](validation/offline-tools/README.md), [implementation plan](OFFLINE_TOOLS_PLAN.md), and [phone acceptance](DEVICE_QA.md). Host renders/tests are not physical-device certification. Native keyboard, TalkBack, OEM process/background behavior, hardware biometrics/Keystore and installer-restricted SMS still require a phone. Existing security/recovery limits remain unchanged; app lock is not full-database encryption and portable backup is unavailable.

```sh
./gradlew --no-daemon :app:assemblePersonalDebug :app:assemblePlayDebug \
  :app:testPersonalDebugUnitTest :app:testPlayDebugUnitTest \
  :app:lintPersonalDebug :app:lintPlayDebug
adb install -r app/build/outputs/apk/personal/debug/app-personal-debug.apk
```

Install as **Update** with the existing signing key; do not uninstall/clear storage. APKs: `downloads/TRAXOS-0.8.0-personal.apk` and `downloads/TRAXOS-0.8.0-play.apk`. Personal retains optional incoming SMS; Play excludes its permissions and ingestion code. Both APKs share app identity and replace one another. No emulator; Gradle retains 1.5 GB heap, two workers and one 512 MB test fork, with one build at a time.
