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
