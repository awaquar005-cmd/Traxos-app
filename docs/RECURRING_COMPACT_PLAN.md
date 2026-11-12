# Recurring account entries and compact interaction pass

Preserve dashboard, financial records, card encryption, navigation and Personal-only delivery.

1. Stabilize form dimensions through initial loading and keyboard changes; disable only app dialog-window animations, remove parent keyboard/focus mutations while opening selectors, keep accessibility touch targets. Phone frame timings remain a device gate.
2. Compact account cards and report currency totals, segments/selectors; stronger small instructional notices; side-by-side confirmation actions with accessible wrapping text.
3. Accounts owns transfer dialog and recurring entries. Remove standalone Transfer route/tool entry; preserve paired movement/delete/Undo accounting.
4. Recurring rules: account, category, expense/income, exact amount, description, daily/weekly/monthly anchor and next date; explicit automatic-posting consent; edit/pause/resume/delete; no deletion of posted history. Credit cards allow purchases only. New schedules start today/future. WorkManager local daily processing plus foreground catch-up, atomic bounded batches, deterministic occurrence IDs, retry-safe financial postings. Validation failures retain due dates and show actionable errors. Month-end anchoring, safe 9→10 migration and preservation tests required.
5. Sensitive per-field copy buttons inside card details only. Sensitive clipboard flag, timed matching-content clearing and no secret feedback/logs. No account-list decryption.
6. Add an optional long-press callback contract for future Quick Add voice entry; no recording feature, fake control or microphone permission now.
7. Build Personal, full tests and lint; publish signed update with exact results and remaining phone/OEM/background/clipboard limits.
