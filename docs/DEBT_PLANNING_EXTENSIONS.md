# Debt, budget periods and account-backed goals

## Debt workflow

More → Debts retains existing manual IOUs, adjustments, repayment history and Undo. Opening an IOU or recording a repayment now offers optional account movement: keep manual, record a real payment/receipt, or match an existing transaction. Account linking is optional, not a bank payment service. Only same-currency active cash/bank/wallet accounts are eligible; available credit cannot fund a loan. Amount, date, direction and account must match. A record already owned by another feature cannot be reused.

New loan principal rows affect the account balance but never ordinary income or spending. Matching an old ordinary transaction does not post it again and excludes the principal from income/spending reports. Opening links can be removed: generated rows are tombstoned, matched pre-existing rows remain and regain their ordinary report treatment. Undo a linked repayment through the IOU so ledger and outstanding balance remain consistent. Spent incoming funds cannot be silently undone. These operations correct local records; they do not reverse a real bank payment. Interest is outside this principal-only workflow.

Saved people support inline create/edit and a system contact picker that reads only the explicitly selected phone contact. No broad address-book permission is requested. Names/phone numbers are stored in the app-private database and included in password-encrypted personal snapshots. WhatsApp sharing always opens an editable preview first; Share hands text to WhatsApp, or the Android chooser if unavailable. The user chooses the recipient and sends it. Traxos does not claim delivery. Private notes are excluded by default; no account/card details are attached.

## Budget periods

Plans → Cycles adds fixed inclusive custom ranges and repeating salary cycles. Choose currency, optional category, start date, limit and thresholds. A salary anchor of31 clamps to February's last day and returns to31 in later months; the initial start date is respected. Review date and per-card previous/next controls inspect exact ranges. Currency totals stay separate. Report rules include refunds and personal group shares and exclude transfers, credit settlements and loan principal.

Monthly budgets remain available. Each budget has optional threshold alerts; Plans → Alerts controls the global budget switch and defaults. Thresholds are exact integer percentages, not floating-point calculations. Archiving preserves history.

## Goals

Goals support optional deadlines and reminder offsets. Progress can remain manual, earmark funds already in an account, or record an actual paired transfer between eligible same-currency accounts. An earmark does not move money or create an expense. A recorded transfer changes both account balances once and remains separate from spending. Withdrawal releases that goal's earmark; it cannot consume another goal's allocation. Account-backed progress cannot be removed through the old manual-only action.

Allocations across goals cannot initially exceed the account balance. Later spending can underfund an allocation: Traxos retains progress and shows the shortfall rather than inventing a balance or silently releasing savings. Archiving a goal does not release its allocation. To convert existing manual progress to account-backed progress, first withdraw that manual portion and then add it with the account option; adding it again without withdrawal would double the goal progress. History shows the latest50 entries; all entries remain in Room and encrypted snapshots.

Undo reverses both progress and any paired local transfer. It is rejected if transferred-in funds have since been spent or a released allocation cannot safely be reinstated. It never reverses money at a bank.

## Reminders and privacy

Debt due dates, unfinished goal deadlines and budget thresholds use the existing WorkManager notification infrastructure. Configure per-item offsets and shared delivery/quiet hours through Debt & planning alerts / Payment reminders. Notification permission and the shared master switch are respected. Notification text omits amounts, person names, phone numbers and secrets. Persistent delivery receipts prevent duplicates for a due date/offset or budget period/threshold. Edits, repayment, reaching a goal, archival and disabled settings cancel stale notifications during reconciliation.

Delivery is best effort: periodic work runs approximately every6hours plus change/start reconciliation, subject to Android battery restrictions. No exact alarm or guaranteed time is promised. Receipts and device delivery settings are excluded from portable snapshots; financial preferences, contacts, deadlines and allocations are included. No network service is required.

## Upgrade and remaining validation

Room13→14 is explicit and additive. Existing entities and records are retained. Portable encrypted snapshots accept schemas11–14; card vault, PIN, sessions and device-only delivery receipts remain excluded. The general Room database is app-private, not fully encrypted. Existing secret-field encryption boundaries are unchanged.

Host tests cover principal/report conservation, retries, matching/Undo, reservation limits, salary/leap boundaries, thresholds, actual migration, encrypted recovery, selected-contact intents, share previews and private reminder deduplication/cancellation. Physical-phone contact providers, WhatsApp, notification delivery timing, keyboard, TalkBack and large-font/OEM layouts still require device acceptance. Live group sharing still awaits backend setup by owner choice.

## Reproducing checks

Use JDK21 and the installed Android SDK. `./gradlew --no-daemon --max-workers=1 -I scripts/isolated-tests.gradle :app:assemblePersonalDebug :app:testPersonalDebugUnitTest :app:lintPersonalDebug` runs the full suite with sequential, isolated test classes. The optional init script prevents native test state/memory accumulation; it skips no tests. Keep `ANDROID_USER_HOME` pointing to the existing signing setup. APK: `app/build/outputs/apk/personal/debug/app-personal-debug.apk`. Install with `adb install -r <apk>`.
