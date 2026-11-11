# Test APK download

[Download TRAXOS 0.1.1 development APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.1.1-dev.apk).

Open the link on your Android 8.0+ phone. If it shows a file page, select **Download raw file**. Open the downloaded APK and allow **Install unknown apps** for your browser/files app if prompted.

This is a debug-signed development build. Use test data: database encryption, app PIN/biometrics and encrypted backups are not implemented. Uninstalling/clearing storage deletes local records. Physical-phone UI acceptance remains pending. Build and 28 automated tests passed.

The APK is committed here because the connected GitHub integration accepted Git pushes but rejected release-asset uploads (HTTP 401). Checksum is in SHA256SUMS.txt. Later release automation should use an appropriately scoped release uploader and avoid storing successive APKs in Git history.

0.1.1 fixes Home row-key collisions that caused the app to close with saved financial data. **Install this APK over the existing app (Update); do not uninstall or clear storage.** Its signing certificate matches 0.1.0, package ID is unchanged, and schema v1 is unchanged. The old APK is retained only as a historical build.
