# Test APK download

**[Download TRAXOS 0.7.0 Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.7.0-personal.apk)**

[SMS-free Play build](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.7.0-play.apk).

Open on Android 8.0+. If GitHub shows a file page choose **Download raw file**. Allow installing from your browser/files app if prompted. **Choose Update; never uninstall or clear storage.** Package/signing identity matches 0.6.0; version code 8 and explicit Room 6→7 migration retain data. Older schemas follow the existing migration chain. These two variants replace each other using the same app/data, not separate installations.

Personal: Settings → Smart Tracking → Incoming financial SMS → disclosure → Android permission. It requests RECEIVE_SMS only, not historical inbox access. OS/installer restrictions can block this permission; Traxos explains denial and offers share/paste without bypassing restrictions. Play has no SMS permissions, receiver or ingestion classes. No suggestion creates a record until you review and approve.

OCR is removed; QR/barcode photo capture, saved gift images, manual/text entry/reminders/redemption remain. Custom catalogs/inline creation and card liability/payment accounting are implemented. Native biometric auto-prompt still needs physical-phone acceptance. There is no portable recovery; losing PIN/Keystore access may make protected data inaccessible. App lock does not encrypt ledger/metadata. [Current guide](../docs/UI_REVIEW.md) · [actual tests/lint/previews](../docs/validation/ui-review/README.md).

SHA-256 checksums are in SHA256SUMS.txt. APKs are in Git because release uploads previously returned HTTP 401. Old APKs are historical, not downgrade candidates. Debug-signed development release; not a commercial or Play certification.

More → Explore all screen flows opens 100 sample UI pages. Future modules are preview-only and never write real records. The live app adds sectioned Settings/More, compact theme/accent choices, navigation preview/presets, stronger gift-text amount parsing, and exact own-account transfers separated from expenses/income. See the guide before reviewing prototype flows.
