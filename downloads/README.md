# Test APK download

## Personal-only design experiment

**[Download TRAXOS Unslop Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/experiment/unslop-ui/downloads/TRAXOS-0.8.0-unslop-personal.apk)**

On the experiment branch only. Install as Update; never uninstall/clear data. Same package/key/version code9/schema8 as0.8. All offline tools remain. Settings → Appearance & Navigation → Experimental interface switches back to the previous styling with the latest borderless-button corrections. Logo, watermark, useful eye/finance icons and splash are preserved. Add Account, Back, overflow and other actions have no outline. No new Play experiment. [Actual validation](../docs/validation/unslop-experiment/README.md) · [Unslop source credit and comparison guide](../docs/UNSLOP_EXPERIMENT.md).

The links below preserve the separate0.8 baseline.

**[Download TRAXOS 0.8.0 Personal APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.8.0-personal.apk)**

[SMS-free Play build](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.8.0-play.apk).

Open on Android 8.0+. If GitHub shows a file page choose **Download raw file**. Allow installing from your browser/files app if prompted. **Choose Update; never uninstall or clear storage.** Package/signing identity matches 0.7.0; version code 9 and explicit Room 7→8 migration retain data. Older schemas follow the existing migration chain. These two variants replace each other using the same app/data, not separate installations.

Personal: Settings → Smart Tracking → Incoming financial SMS → disclosure → Android permission. It requests RECEIVE_SMS only, not historical inbox access. OS/installer restrictions can block this permission; Traxos explains denial and offers share/paste without bypassing restrictions. Play has no SMS permissions, receiver or ingestion classes. No suggestion creates a record until you review and approve.

OCR is removed; QR/barcode photo capture, saved gift images, manual/text entry/reminders/redemption remain. Custom catalogs/inline creation and card liability/payment accounting are implemented. Native biometric auto-prompt still needs physical-phone acceptance. There is no portable recovery; losing PIN/Keystore access may make protected data inaccessible. App lock does not encrypt ledger/metadata. [Current guide](../docs/OFFLINE_TOOLS.md) · [actual tests/lint/previews](../docs/validation/offline-tools/README.md).

SHA-256 checksums are in SHA256SUMS.txt. APKs are in Git because release uploads previously returned HTTP 401. Old APKs are historical, not downgrade candidates. Debug-signed development release; not a commercial or Play certification.

More → Explore all screen flows opens 100 sample UI pages. Future modules are preview-only and never write real records. The live app adds sectioned Settings/More, compact theme/accent choices, navigation preview/presets, stronger gift-text amount parsing, and exact own-account transfers separated from expenses/income. See the guide before reviewing prototype flows.

0.8 adds real monthly reports, combined Activity filters/saved order and deleted-transaction restore/Undo. Open More or Activity's menu. Splash padding fixes clipped corner squares on Android12+; logo has no outline and button borders are minimal. Native host mask simulation is not physical-device certification.
