# Physical Android QA checklist

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
