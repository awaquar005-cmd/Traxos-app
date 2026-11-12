# Test APK download

[Download TRAXOS 0.3.0 custom components development APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.3.0-ui-components.apk).

Open the link on your Android 8.0+ phone. If it shows a file page, select **Download raw file**. Open the downloaded APK and allow **Install unknown apps** for your browser/files app if prompted.

This is a debug-signed development build. Use test data: database encryption, app PIN/biometrics and encrypted backups are not implemented. Uninstalling/clearing storage deletes local records. Physical-phone UI acceptance remains pending. Build and 49 automated tests passed.

The APK is committed here because the connected GitHub integration accepted Git pushes but rejected release-asset uploads (HTTP 401). Checksum is in SHA256SUMS.txt. Later release automation should use an appropriately scoped release uploader and avoid storing successive APKs in Git history.

0.1.1 fixes Home row-key collisions that caused the app to close with saved financial data. **Install this APK over the existing app (Update); do not uninstall or clear storage.** 0.1.1 used schema v1; the current UI pass upgrades it safely to v2 for preferences. The old APK is retained only as a historical build.

Current 0.2.0-ui-pass release: custom dashboard/chrome, configurable center shortcut, persisted amount visibility, static loading states and non-overlapping navigation. No new finance modules. Same signing identity and package; explicit preference-only Room v1→v2 migration preserves financial records. **Choose Update; do not uninstall/clear data.**

Current 0.3.0: custom dialogs, selectors, sheets, feedback and forms; all four screen slots support changing, reordering and reset. Center quick action remains independent. Schema v3 explicitly migrates preferences from v2; financial records are preserved. Build/tests/lint passed; physical-device acceptance is pending.
