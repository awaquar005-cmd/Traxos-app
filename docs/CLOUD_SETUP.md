# Google OAuth, Drive and Supabase setup

No project is currently configured. This guide prepares the real integration; it is not a claim that login, Drive backup or backend sync already works. Offline ledger, app lock, QR/images and existing features work without these services. Never paste passwords, private signing keys, OAuth client secrets or Supabase service-role keys into chat or commit them.

## Android identity — enter these exact values

- Application/package: `app.traxos.finance`
- Current development signing SHA-1: `42:E1:93:2C:DA:AB:EE:47:F4:B2:08:99:A0:A4:4B:BA:0A:92:E6:BE`
- Current development signing SHA-256: `9D:F8:F4:7A:6A:EF:A5:1F:5B:1B:61:DF:B1:A3:AE:A3:EA:15:97:57:E3:90:24:82:1A:0C:9F:FE:A4:99:0F:54`

These identify the existing update-compatible debug APK. A later production signing key needs its own OAuth registration/fingerprints; do not change the current signing key to test this update. App version names do not go into OAuth package fields.

## 1. Create Supabase

1. Create a Supabase account, organization and project on the free tier. Choose a nearby region; save the database password privately.
2. In project settings/API, note the **project URL** (`https://<project-ref>.supabase.co`) and **publishable key** (or legacy anon key). These are public client configuration, not server privileges.
3. Keep the service-role/secret key server-side only. Do not place it in the APK, browser or repository.
4. In Authentication → Providers → Google, the provider will need a Google **Web client ID and secret**. Enter the secret directly in Supabase's dashboard, never in Android source/chat.
5. Copy the provider callback URL, normally `https://<project-ref>.supabase.co/auth/v1/callback`.
6. The intended native app redirect is `app.traxos.finance://auth/callback`. Add it to the allowed redirect URLs when native auth is integrated. The callback intent/deep-link handling is not implemented by this setup screen yet; configuration alone does not enable login.

Before syncing data, the implementation still needs reviewed SQL migrations, per-user row-level security, group membership/role policies, idempotent operation acknowledgment, conflict handling, deletion/tombstones and integration tests. A public key must never be relied on instead of authorization. No financial table should be publicly readable/writable.

## 2. Create Google Cloud project

1. Create/select a Google Cloud project. Record its project ID (not the display name).
2. Configure Google Auth Platform / OAuth consent: branding, support email, audience and your own test-user email while the app is in testing.
3. Enable **Google Drive API** in that project for personal backup. There is no paid AI API requirement.
4. Create an **Android OAuth client** using the package and SHA-1 above. Save its client ID. Register the release key separately later.
5. Create a **Web OAuth client** for Supabase Google sign-in. Under authorized redirect URIs, paste the exact callback URL copied from Supabase. Do not guess a different hostname.
6. Enter that Web client ID and secret in Supabase → Authentication → Google. Android uses public client IDs; it must not contain the Web client secret.

Drive authorization is a separate, explicit consent from cloud login. Prefer the narrow `drive.file` scope for app-created/user-selected visible backup files, or `drive.appdata` if an explicitly chosen hidden application-data folder is adopted. Do not request unrestricted Drive access. The final flow must implement secure token handling and reconnection/revocation; no tokens should be pasted into source or logs.

## 3. What to provide after setup

You may share the Google project ID, Android OAuth client ID, Web OAuth client ID, Supabase project URL and publishable/anon key as project configuration. Enter secrets directly in the service dashboard or secure environment settings. The agent must then implement and test actual native auth redirects, token/session storage, Drive consent, encrypted backup acknowledgment and backend sync; setup identifiers alone are not a working transport.

## 4. Keep recovery separate

- Drive snapshot: password-encrypted portable archive in your Google Drive.
- Chosen folder: separate Storage Access Framework destination with verified encrypted round-trip restore.
- Supabase: authenticated ledger/shared-group synchronization with server acknowledgment and conflict handling.

All future portable exports/backups/sync exclude the protected on-device card vault, including CVV. Current encrypted card data is preserved by explicit user instruction; this overrides the V8 document's proposed CVV removal. App lock is not full ledger encryption, and present device-bound keys do not provide portable recovery. Never show “backed up” or “synced” based on local browser state or an unacknowledged request.
