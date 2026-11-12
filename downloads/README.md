# Test APK download

[Download TRAXOS 0.4.0 Gift Wallet development APK](https://github.com/awaquar005-cmd/Traxos-app/raw/refs/heads/main/downloads/TRAXOS-0.4.0-gift-wallet.apk).

Open on your Android 8.0+ phone. If a file page appears, select **Download raw file**. Open the downloaded APK and allow **Install unknown apps** for your browser/files app if prompted. The universal APK is approximately 74 MiB because OCR/barcode models are bundled for offline first use.

**Choose Update over the existing app. Do not uninstall or clear storage.** Package and debug signing identity match previous builds; version code 5 and explicit Room v3→v4 migration preserve records/navigation. Older schemas follow the existing forward migration chain. Local data has no portable backup or recovery.

Open **More tools → Gift Cards** or add Gift Cards to a navigation slot in Settings → Navigation Customization. More is also an optional slot. Gift preferences are under Settings → Gift card settings. The center quick action stays independent. [Wallet guide](../docs/GIFT_WALLET.md) · [validation and previews](../docs/validation/gift-wallet/README.md).

Gift codes/PINs/notes/images use device-bound encryption, but merchant/balance/date metadata and the existing ledger are not encrypted. There is no app lock or encrypted backup. This remains a debug-signed development build; physical-phone acceptance is pending. Older APKs are historical and should not be installed over this update.

The APK is committed here because Git pushes are available and release-asset uploads previously returned HTTP 401. SHA-256 checksums are in SHA256SUMS.txt. Future release automation should use a scoped artifact uploader rather than retain successive APKs in Git history.
