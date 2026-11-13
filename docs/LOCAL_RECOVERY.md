# Local recovery and exports — 0.12.0 Personal

Settings → Backups & recovery → Backup folder opens Android's Storage Access Framework folder picker. Access is persisted only when the provider grants it. A revoked/moved/read-only folder must be selected again. The live Room database remains app-private; SAF folders are snapshot/export destinations, not a relocated SQLite database. Selecting a folder is not a backup. No broad storage permission is requested.

## Two delivered features

1. **Password-encrypted local snapshots and additive restore.** Choose a password of12–128 characters and confirm it. Each backup creates a uniquely named `.traxos` file, reopens it and verifies decryption, structure and financial references before reporting a verified snapshot. Newer changes are not automatically protected. Retain a copy outside the phone; a folder on the same phone does not protect against losing that phone. There is no password recovery.
2. **Transaction CSV export.** Explicit plaintext disclosure/confirmation, streamed exact decimal amounts and currency/type/link/deletion columns. Transfers/repayments remain movements, not expenses. Formula prefixes and quotes are escaped. This readable report is not a restorable archive. User-entered names/notes can contain private information.

## Archive coverage and exclusions

Included: accounts (signed credit liabilities), categories, merchant/description labels, transactions including tombstones and paired movements, operation journal, recurring rules, budgets, savings earmarks/history, gift cards including tombstones, encrypted gift secrets/images, redemption/refund/reversal history.

Excluded: **all account/card vault payloads (including PAN/CVV/expiry)**, app PIN/biometrics/security keys, unapproved Smart Add inbox and SMS consent, notification delivery tokens, device/UI preferences and chosen-folder grants. The UI states these exclusions before backup/restore. Existing vault/settings/security data are neither deleted nor replaced by restore. Gift reminder delivery is reconciled with the destination's current notification preferences and permissions.

## Cryptography and restore rules

Standard JCA AES-256-GCM, random16-byte PBKDF2 salt, random12-byte IV, PBKDF2-HMAC-SHA256 with310,000 iterations, authenticated versioned36-byte header. GZIP compression precedes encryption. Passwords remain in memory, outside saved state/preferences; operation char arrays are cleared in finally blocks. There is no custom cryptographic algorithm, persisted token or cloud call.

Device-bound gift ciphertext is decrypted only in memory inside the encrypted archive writer. Import reseals gifts with the destination device key and recomputes HMAC fingerprints, retaining deleted-card identities so reused codes do not collide with tombstones. Card vault data never enters the archive. This is not full live-database encryption.

Import accepts format1/schema11 only. It uses an isolated app-private temporary Room database, a fixed table/column allowlist, typed fields, bounds, foreign-key checks, pair/currency validation and financial consistency checks. The entire AEAD stream must authenticate before a preview is returned. Invalid passwords, modified/truncated files and unsupported schemas cannot modify the live database. Limits:64MiB encrypted input,512MiB decompressed input,4MB per record and1million rows per table; aggregate live transaction/account limits are also checked. Larger archives are rejected explicitly, never truncated.

After review, one Room transaction inserts missing records, skips semantically identical ones and aborts on **any same-ID disagreement or unique constraint conflict**. No last-write-wins merge. Only untouched seed categories in an otherwise empty installation may be replaced by the snapshot's category definitions; existing customized categories/data are never cleared. Repeat restore is idempotent. Keep conflicting snapshots for future manual resolution; this release does not merge divergent edits.

Temporary restore databases contain ordinary ledger metadata and destination-encrypted gift payloads. They close/delete after review cancellation, verification, successful restore or handled failure. OS process termination can leave an app-private temporary file; Android cache cleanup may remove it. Physical provider durability, actual low-storage/revocation/OEM behavior and process-kill cleanup need phone acceptance. The app does not claim a remote durable copy.

## Google configuration

The user provided Google project `micro-atrium-514609-u5` and Android OAuth client configuration and confirmed package/SHA-1 registration. The uploaded JSON has no client secret. Only public setup identifiers are documented in CLOUD_SETUP.md; the file is not an APK asset or committed credential. Drive API enablement does not implement native authorization/upload. **Drive connection and cloud transport are still pending.** Supabase has not been created and is not needed for local recovery or future personal Drive backup.

## Physical acceptance

Install as Update. Select a writable local/removable folder, back up, reopen the archive, review and restore into a separate test installation with a different Keystore; verify gift image/code, exact balances, paired movements and repeated restore. Exercise wrong password, read-only/revoked provider, cancelled picker, insufficient space, backgrounding/rotation and USB copy. Do not clear the real application's data for testing. Host test results are reported separately in validation/local-recovery.
