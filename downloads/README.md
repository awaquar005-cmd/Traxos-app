# Personal test APK

**[Download TRAXOS 0.10.1 Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.10.1-personal.apk)**

On Android 8+, allow installation from your browser/files app, then choose **Update**. Never uninstall or clear storage. Package/signature match previous builds; version code14, Room schema10 with explicit migrations. Only Personal was built in this pass. Older APKs are historical artifacts, not recommended downgrades.

Previous / Unslop OFF design is final. Compact dashboard/Activity, borderless actions, gift Pay Code/PIN parsing and saved merchants. Optional encrypted card references: create a Credit Card account → Edit account → Card number, expiry & CVV. Card balance remains ledger-driven. Missing gift amount stays blank; new gifts default remaining to original when omitted.

Personal incoming SMS requires disclosure and Android RECEIVE_SMS permission; OS/installer may refuse. Share/paste fallback remains. No historical SMS, internet/cloud OCR or automatic unapproved entries. Debug development build; phone acceptance pending. No portable PIN/Keystore recovery. [Guide](../docs/COMPACT_PERSONAL.md) · [Actual tests/lint](../docs/validation/fullscreen-gifts/README.md). Checksums: SHA256SUMS.txt.

0.9.1: restored only dashboard hero proportions; direct card-details access in Accounts; Activity SMS pending indicator; inline Approve/Reject and Edit-only sheet; improved bank SMS fields. [Quick-review guide](../docs/SMS_QUICK_REVIEW.md).

0.9.2: compact SMS header button hidden at zero; debit/credit card-reference access including after new-account Save; centered compact forms/pickers; clearly labelled Recorded times (not inferred payment times). [Guide](../docs/COMPACT_FORMS.md).

0.10.0 adds Accounts → Recurring and an account-owned Transfer dialog, compact cards/report totals/selectors, stable form frames and per-field sensitive card copy. Dashboard/branding unchanged. [Current guide](../docs/RECURRING_COMPACT.md).

0.10.1: full-screen routed forms, compact gift wallet/detail layout, concise action rows and development screenshots enabled in DEBUG. Schema10/data unchanged. [Guide](../docs/FULLSCREEN_GIFTS.md).
