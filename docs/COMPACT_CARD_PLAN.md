# Personal0.9 compact baseline and card references

Use main's previous/off design, retaining the latest borderless action corrections. Only Personal is built. Compact dashboard/Activity spacing and visual32dp chips keep48dp hit targets; combine redundant active-account summary/header. Preserve logo, watermark, eye and meaningful icons.

Fix on-device pay-code parsing with alphanumeric PIN and URL removal; never infer an amount absent from text. New unredeemed gift drafts default remaining to original only when remaining is blank. Existing/history-locked balances never reset. Promote the existing saved merchant picker into the essential form, with its existing Add New flow; reuse this repository for future merchant fields.

Add optional encrypted card references linked to existing CREDIT_CARD accounts: PAN/Luhn, CVV3–4digits optional, MM/YY expiry, masked/reveal, revision/idempotent save and discard/error handling. Use the existing Keystore AES-GCM envelope in a distinct namespace. Keep plaintext out of saved state/logs/journal; no backup/export or payment processing. Existing signed debt/purchases/refunds/paired card payments remain authoritative; no available-credit-as-assets. Room8→9 adds one encrypted reference table and preserves financial tables. Card details are added after saving the financial account, from Edit account.

Validate parser/redacted fixtures, gift default/history safety, card rules/cipher/persistence/stale saves, migration, existing accounting, compact controls and Personal build/tests/lint. Native device/Keystore/IME acceptance remains pending. Prior experiment remains on experiment/unslop-ui; no comparison toggle on this final baseline.
