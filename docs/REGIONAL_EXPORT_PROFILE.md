# Currency, exports and local profile

## Currency and display

Settings → Currency & region keeps the existing default currency setting and adds number/date formatting locales (device, English India/US/UK, Hindi India and Urdu India), four date layouts, searchable IANA display timezones and12/24-hour clocks. These are formatting-language preferences; the interface remains English. Original accounting days, scheduled periods, stored transaction zones and original amounts are not rewritten. Form storage and machine exports use ISO dates even when the displayed date changes. Money labels, selectable financial details and activity timestamps use the display preferences; immutable audit identifiers and cycle keys remain unambiguous.

Manual rates are dated source→target prices: one source unit equals the entered number of target units. Calculations use BigDecimal and target-currency minor units with explicit half-even (default) or half-up rounding. The most recent direct rate not later than the requested day wins. Optional inverse rates apply only without a direct rate. Rates are never chained, fetched or assumed. Missing rates leave values unconverted. Conversion preview and optional PDF estimates label the actual rate date/inversion and warn when older than30 days. Original currencies stay separate on the dashboard; estimates never change cash, reports or ledger amounts.

Up to10,000 rates are supported; the latest100 search matches are rendered at once to bound UI work. Search a pair or ISO date to edit older rates.

Manual rates are financial metadata in additive Room15 and encrypted snapshots. Device formatting and accessibility choices stay local and persist over app restarts/upgrades.

## Exports

Settings → Exports opens a custom screen with PDF, CSV and typed JSON. A disclosure precedes Android's Create Document picker. Every export is unencrypted; no file is silently uploaded. Documents are prepared in a private temporary file, copied to the chosen destination and reported saved only after the stream closes. Cancellation/errors remove the temporary file and attempt to delete the incomplete destination. Some providers refuse deletion; the message explicitly asks you to remove any leftover file. A cancelled picker writes nothing. Open and Share are explicit follow-up actions.

PDF is a native, paginated human-readable activity report with wrapping notes, original amounts/currencies, dates, types, stable IDs and paired IDs. Account/currency/inclusive-date/deleted/notes filters are available. Optional estimates use manual rates as of each transaction date; absent rates are labelled. This is a movement report, not a spending total: transfers, card settlements and loan principal are explicitly distinguished. PDF supports up to5,000 filtered records; larger requests fail with an instruction to narrow filters or use streaming CSV/JSON, never silently truncate.

CSV uses the same scope filters, ISO dates, exact decimal amounts, stable/paired IDs and formula-safe escaped text. It preserves the original type and adds a human-readable classification; matched/generated loan principal is labelled separately from ordinary spending. Existing CSV and encrypted archive actions remain available.

JSON is a standalone versioned typed financial export, not an encrypted backup or an import bypass. It exports the complete financial-table whitelist, including deleted/archived history, IDs, revisions, relationships, rates and typed column values. Integer values are decimal strings to avoid JavaScript precision loss. Nulls and text are distinct; blobs use Base64. Scope filters intentionally do not apply to JSON, so related rows are not left dangling.

Gift-secret payloads are omitted by default and clearly tagged `omitted`; the remaining selected data is exact. Explicit inclusion exports portable gift codes/PINs/notes/images as unencrypted Base64-encoded secret payloads, with a stronger warning. Base64 is not encryption. The entire card vault (including CVV), app PIN/keys, login credentials, unapproved Smart Add, device settings/delivery receipts and local profile photo are always excluded. Existing password-encrypted archives remain the supported restore path and accept schema11–15.

## Profile, accessibility and identity

Profile supports a user-selected local photo, removal and rotation. Only `content://` picker inputs are accepted, bounded to12MB/60megapixels and downsampled before a512px center crop. EXIF orientation is respected and the result is re-encoded without metadata. Atomic writes retain the old photo on failure. Photos are app-private plaintext, do not upload, and are excluded from exports/backups. Display name and real online account/provider flows remain independent.

Settings → Accessibility adds optional text enlargement above Android scaling, stronger semantic text contrast, app haptic controls and reduced lock-logo entrance motion. Existing system reduced-motion behavior remains respected. Android accessibility settings are reachable; no fake screen-reader switch is provided. Settings switches now announce their actual names.

App identity switches between TRAXOS and Traxos Finance launcher aliases, retaining the same five-square mark. The new entry is enabled before the old one is disabled without killing the process. Existing MainActivity and inbound share flows remain reachable. Launchers may cache labels or retain old pinned shortcuts; this requires physical-launcher acceptance.

## Notifications, help and privacy

Existing reminder master/offset/quiet-hour and suggestion settings remain. Per-channel Android settings are accessible for gift expiry, money/planning and Personal Smart Add; channel creation never overrides a user's disabled channel. The OS controls sound, vibration and lock-screen visibility, and denied permission has the existing safe fallback.

Help & support provides searchable offline guides, links to the real working app routes, a preview/copy/share of non-sensitive build information and an explicit public issue-page link. Nothing is submitted automatically. Privacy & permissions states the actual storage/encryption boundaries, optional services, export disclosures and recovery limits, and opens real Android permission/security/backup/export controls. About retains truthful version/configuration/store-listing information. No fake profile upload, store rating, backend login or live group status is introduced.

## Validation boundaries

See PROGRESS.md and validation evidence for executed results. The installed Robolectric runtime has no PdfDocument JNI implementation: host tests exercise the actual report pagination and drawing on native Canvas, not PDF serialization. Actual PDF generation/opening/printing is a physical-phone gate. Physical document providers/low-space interruption, actual Android launchers/pinned shortcuts, sharing apps, font/assistive-tech/OEM behavior and real notification delivery remain phone gates. No emulator, new service, added permission or dependency is required. General Room data remains app-private plaintext; secret-field encryption and card-vault exclusions are preserved.
