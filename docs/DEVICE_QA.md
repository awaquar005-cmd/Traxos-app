# Physical Android QA checklist

## 0.1.1 update/reopen check (requires user's phone)

- [ ] Install 0.1.1-dev over the existing 0.1.0-dev app; choose Update. Do not uninstall or clear storage.
- [ ] Existing accounts, amounts and notes are visible. Check Home monthly summary and balances; scroll to recent transactions.
- [ ] Force-stop and open from the launcher several times, including in airplane mode. No automatic close and no duplicated records.
- [ ] Repeat with the saved theme and after screen rotation. Existing financial data remains unchanged.

Status: NOT RUN. No USB phone is connected to this cloud workspace. No emulator is permitted. Host JVM/database tests do not certify device behavior.

Use an API 26+ physical phone with USB debugging; repeat on target OEMs and Android 16 edge-to-edge behavior before release. Use only fictional test records.

- [ ] Install debug APK, airplane mode before first launch. Home has no dummy records; create cash/bank account with explicit currency, opening date and balance.
- [ ] Save expense/income, confirm account effects and month totals (INR 100.00 opening − 25.00 + 50.00 = 125.00). Different currencies stay separate.
- [ ] Tap Save 10 times rapidly: one row, one successful feedback/haptic, one journal mutation. Immediately kill/relaunch after success and verify persistence.
- [ ] Edit amount/category/account/date/note; stale version rejects, invalid amount/date/category cannot commit. Expenses cannot predate opening date.
- [ ] Delete and use Undo: balance restored, audit retained; newly saved transaction Undo creates a tombstone rather than dropping history.
- [ ] Rename/archive/restore account; history remains, archive blocks new entries; no destructive account delete control.
- [ ] Search category/account/note, including literal `%`, `_`, `\`, Hindi and emoji. Open/edit/back restores query and scroll. Paging append error offers retry.
- [ ] System/Light/Dark changes persist after restart and work offline. Test theme changes while editing; draft retained.
- [ ] Rotate/process recreation mid-form: amount/date/note/account/category and operation identity retained without accidental save; system Back and toolbar Back offer keep/discard only for dirty fields.
- [ ] New empty-account flow → create account from transaction form → return; entered transaction fields retained.
- [ ] 320/360/430dp, landscape, 200% font, 500-char note/80-char account, maximum money: full amounts readable, Save reachable with keyboard, primary labels unclipped.
- [ ] TalkBack order, selected radio/chip labels, 48dp targets, keyboard traversal, both-theme contrast and redacted task preview. Verify FLAG_SECURE tradeoffs.
- [ ] Low storage/write failure leaves form and existing records intact; retry does not duplicate a successful ambiguous write.
- [ ] Background/force-stop/reboot: saved data persists, no invented backup or cloud status. Clear cache preserves ledger. Do not clear storage on valuable records.
- [ ] Signed in-place update keeps records; schema upgrade tests must exist before schema version changes. Never uninstall as update advice.

Before release: encrypted storage/Keystore/PIN/biometrics, tested portable restore and all applicable V3 §39 cases 1–50. Save measured p95 commit/startup/50k-row results separately; no benchmark claims yet.

## 0.2.0 UI pass — phone acceptance pending

- Install over 0.1.1 without uninstalling/clearing storage; verify all accounts, notes, balances and journal-derived history retained.
- Cycle Home/Activity/Accounts/Settings rapidly; no old-page text fading over new text. Back/Up, tab scroll/search and dirty form handling still correct.
- Choose each center action in Settings, return and invoke it; restart and confirm persistence. Header Quick Add must work regardless of assignment.
- Hide amounts, inspect dashboard/accounts/transactions, close/reopen and confirm no visible amount flash. Entry/edit amount fields remain intentionally visible.
- Check System/Light/Dark and small phones, 200% text, long names/large amounts, landscape, TalkBack and keyboard Save reachability. Only safe touch targets (48dp+) and system insets may increase chrome height.
- Test cold Room loading/empty data and retry/error states; cached data must not receive fake loading delays. Save/Delete/Undo, archive/restore, financial arithmetic and paging/search remain functional offline.
- Compare actual phone render with host previews (synthetic fixture values only); host rendering is not OEM/device certification.

## 0.3.0 custom components/navigation — physical checks pending

- Install over 0.2.0 using Update, without uninstalling or clearing data. Verify accounts, balances, notes and theme/masking/quick action after migration.
- Change each of the four slots; used destinations swap, no duplicates. Move every slot in both directions, reset/cancel reset, rotate/relaunch and verify. Center quick action stays independent and Settings/creation remain accessible.
- Default header has no duplicated Add/Settings. With a non-Add center action, overflow must open real transaction creation and dismiss cleanly.
- Exercise custom account/category sheets, currency dropdown and date picker. Back/outside/close/select must dismiss the correct overlay, preserve drafts and avoid keyboard overlap.
- Create/edit transaction and account, reject invalid amount/date, repeat Save rapidly, cancel discard/delete, delete then Undo, archive/restore. Create an account while composing a transaction; amount/note must survive.
- Check 320dp/landscape/200% text, long names/error text/large exact amounts, TalkBack roles/focus order/announcements, physical IME animation and fixed Save reachability. Verify all custom visual states in light/dark. No page ghosting.
- Host Canvas previews use synthetic fixture data; only real phone testing can certify keyboard, predictive Back, window focus, OEM and process-death behavior.

## Gift wallet 0.4.0 physical acceptance (pending)

- Install as update from 0.3.0 with saved accounts/transactions/custom navigation; verify all remain. Do not uninstall/clear data. No emulator.
- Airplane mode before first gift use: create/rename/archive/restore/delete/mark-expired; scan QR/barcode through native camera app and import a cropped screenshot. Verify bundled Latin OCR without download; ensure code/amount/date mistakes stay editable and unknown values blank. Reject import and verify no saved row.
- Share message text and an image from another app; disable each detection setting and repeat; disable automatic confirmation opening and verify results wait for Review without saving. No SMS/camera/media library permission request from Traxos. Check two barcodes, URLs, unsupported/corrupt/large images and no installed camera app handling.
- Reveal code/PIN/image, background/return to mask; copy into merchant app, verify Android sensitive clipboard preview suppression and timed clearing. Confirm no codes in recents, notifications, work-data or logs. Test real Keystore encryption after process kill/reboot; recovery is unavailable if keys/data are lost.
- Stored value ₹1,000 → ₹300 used → ₹700; repeat-tap confirmation and Undo/refund; one history write per operation. Single-use rejects partial; discount has no cash total. Unknown balance cannot be redeemed until confirmed. Bank/cash totals stay unchanged.
- Set expiry/intervals around today on a test card. Grant/deny notification permission, disable channel, reboot, force-stop/reopen and apply battery restrictions. Confirm private wording, no duplicate alerts and rescheduling/cancellation after edit, archive, delete or redemption. Delivery is deferrable, not exact. Restore normal dates afterward.
- Pin Gift Cards to any nav slot, remove Settings then use fallback, open More and Gift Cards, reorder/reset, restart; center quick action independent.
- Light/dark, narrow phone and enlarged fonts: scroll long merchant/code/notes/history, verify custom sheets, persistent Save above keyboard, validation, Back/discard and no ghosting. Test TalkBack reading/focus and physical camera permission/cancellation.


## 0.5.0 acceptance additions — NOT EXECUTED

Upgrade0.4→0.5 without uninstall: verify ledger/gift codes/history/navigation. Run real redacted OCR corpus offline, camera EXIF/full-resolution/gallery/crop/rescan, multiple amounts/missing expiry. Check duplicate image/shared SMS approvals, declined/revoked SMS/notification permissions on eligible build; default build must not request SMS. Test PIN mismatch/change/disable, five failures/restart/clock delay, no-enrollment and eligible strong fingerprint/face/API30 device credential, immediate/timed background/cold-process locking, previews/lock Back/modal suppression and in-flight OCR. Verify settings/defaults after restart, light/dark320dp/200% text/TalkBack/IME. Existing transaction/account/delete/Undo and reminders must pass. Do not simulate key loss or uninstall against valuable unbacked-up data.


## 0.6.0 physical acceptance — NOT EXECUTED

Upgrade0.5→0.6 in place: verify transaction/account/category/gift history, encrypted codes/PINs/images, lock configuration and navigation. Confirm no OCR controls/model; scan a QR/barcode and retain attachments. Create/rename/style/archive categories and accounts; inline-create from transaction/Smart Add/Settings/payment selectors and verify prior amount/date/note. Record card charge/refund/paired payment, delete/Undo, restart and compare exact debt/net worth/monthly net outflow; reject mismatched currency/overpayment. Enable biometrics: cold launch/timed background automatically prompts once, cancellation stays locked with retry/PIN, rotation and permission dialogs do not loop or expose content. Test no enrollment/device credential/key invalidation without destroying real data. Personal SMS disclosure/permission denial/revocation/unsupported installer/device, real bank/UPI/card/gift messages, duplicates/OTPs/unrelated/failed payments, manual approval and generic private notifications. Play must not request SMS. Settings→Profile→leave→Settings must return home; nested Back returns Settings then preceding main. Test both themes,320dp/200% fonts, fixed Unlock/Save with IME, all pickers/dialogs, TalkBack/system Back, recent-app previews, process death and OEM reminder restrictions. No emulator or phone pass is claimed.

## 0.7 UI review acceptance — not performed on a physical phone

- Update 0.6 in place; verify existing ledger, encrypted gift codes, PIN, custom navigation and preferences.
- Open app locked/unlocked; verify transparent five-square splash and vertically balanced unlock at normal/200% fonts, native auto-prompt once per lock and PIN fallback.
- Switch each accent/theme, restart; check forms/dialogs/sheets/outlined buttons and small-screen labels.
- Remove Home/Settings/More from slots in turn; use header fallback, visit nested screens, close and re-enter, verify correct root and Back.
- Browse More → all UI review journeys. Check state selector, validation, destructive preview, Back/Close/Real dashboard. Confirm real balances/counts unchanged.
- Paste a redacted gift message with prefix/suffix INR, order/tax amounts and ambiguous values; review every field before save. No image OCR should appear.
- Transfer between two owned same-currency accounts; check two linked rows, unchanged net worth/spending, separate Activity filter, delete/Undo and restart. Reject same-account/card/currency mismatch/insufficient amount.
- Personal only: explicit consent and SMS permission accepted/denied/revoked; self-transfer suggestions need two accounts and approval. Play has no automatic SMS access.
