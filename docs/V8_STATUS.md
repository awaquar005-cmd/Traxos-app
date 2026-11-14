# Current0.15.0 addendum

Offline local groups/members, four split modes, partial settlements/reversal history and personal-share reports; manual/estimated credit statements, cycle/limit/minimum/reminders and paired payments; subscription/bill/EMI plans, actual confirmation/matching, detection/history/Undo and private due reminders are implemented. Live invitations/group transport/authorization remain awaiting setup by owner instruction. Room13 and backup11/12 compatibility are tracked in GROUPS_CARDS_BILLS.md and current PROGRESS.md. Earlier tables below are historical snapshots.

# 0.13.0 account and recovery addendum

Native Drive consent, fixed-owner linking, encrypted resumable snapshots with remote read-back/restore, local-copy support and probed SAF selection are implemented. Email/password/Google account-login/provider linking use real Firebase endpoints with encrypted session storage, but owner configuration is still required; no Firebase API key/Web client was supplied and no real account/backend/phone result is claimed tested. Drive-only linking is independent of that setup. See OAUTH_DRIVE.md and the final validation report. All previous feature routes and schema11 remain; the older snapshots below are historical when superseded.

# 0.12.0 update

Local SAF folder selection, password-encrypted financial snapshots with staged additive restore and explicit CSV reports are implemented; validation/delivery evidence is in validation/local-recovery. Card vault, lock state, pending Smart Add and device preferences remain excluded. Native screenshot refinements preserve existing routes and the balance hero. Google public OAuth configuration is received and Android registration user-confirmed, but native Drive authorization/transport remains pending; Supabase is not configured. The0.11.0 status snapshot below remains historical where superseded; see LOCAL_RECOVERY.md and PROGRESS.md for current limits.

# V8 implementation status — native Personal build

This is a native Compose adaptation of the supplied V8 visual reference, not the HTML running in a wrapper and not completion of the entire V3/V8 roadmap. Current user instructions preserve the existing hero and encrypted CVV fields. Production sample-data/authentication bypasses are not implemented.

| V8 journey | Actual status |
|---|---|
| 1–3 Splash / offline start / onboarding / profile / lock | Existing native splash, immediate offline Room, local profile name, real PIN/biometrics preserved. New welcome/cloud identity/restore/profile-photo setup remains future work. Existing users are not forced through fake onboarding. |
| 4 Header and all tools | Native V8 colors/list hierarchy, compact header, slightly shorter dock; existing More/settings and configured slots preserved. New Plans and Voice are optional destinations. |
| 5 Home | Existing hero source, five-square watermark/brand assets unchanged. Shorter search, real month data/account peek, unboxed useful icons, mic/Add/Repeat actions. No prototype balances seeded. |
| 6 Activity | Existing paging/search/filter/sort/edit/delete/Undo/linked entries/restore retained; useful transaction pictograms are unboxed. |
| 7 Voice / Quick Add | Real local speech API on supported Android12+ devices; hold Plus/release, foreground cancellation, editable text/manual fallback, review→normal form→Save. Explicit permission only when supported. Existing default/last selections retained. Negative/ambiguous/failed/settlement phrases are not silently posted. |
| 8 Accounts/cards | Existing cash/bank/wallet/credit liability, debit/credit vault, opening balances, transfers, edit/archive/payment/refunds retained. Flatter account list and currency-separated active-money summary. Advanced statement schedules remain future work. |
| 9 Vault | Current encrypted PAN/CVV/expiry fields preserved by explicit user override. No plaintext credentials added. Existing masking/copy timer remains. Per-reveal device re-authentication, portable-vault policy and release acceptance remain separate security work; do not claim full V8 vault compliance. |
| 10 Shared groups | Requires Supabase project/auth/membership/RLS and integration work. Not simulated as a working native group backend. |
| 11 Plans | NEW real local monthly/category/currency budgets and savings earmark progress with edit/add/remove/Undo/archive; existing recurring rules and reports preserved. Earmarks do not move bank money or add assets. Debts, subscription detection and advanced bill/group modules remain tracked future work. |
| 12 Smart tracking | Existing Personal incoming-SMS consent/review/dedup/share/paste preserved, plus new voice. Widgets/tile/volume trigger remain future work. |
| 13 QR/images | Existing native barcode/QR photo scanning and encrypted gift-image saving unchanged. OCR remains absent. Additional transaction/profile image attachment flows are not claimed implemented. |
| 14–15 Recovery / cloud sync | Not configured or implemented as a transport. Setup guide covers Google OAuth/Drive/Supabase exact Android identity. Password-encrypted chosen-folder/Drive round-trip recovery, RLS/outbox push-pull/conflicts and server acknowledgment require separate implementation/testing; no fake backed-up/synced status. |
| 16 Settings | Existing functional controls preserved under compact groups; theme/accent remain in Appearance. Setup/recovery guidance is explicit about being unconfigured. Additional language/date/app-identity/cloud settings are not fabricated. |
| 17 Native quality | Native Compose, conservative Gradle, no emulator, no paid/new SDK, real Room/WorkManager kept. Host tests/render checks are separate from physical voice/model/OEM/IME/TalkBack acceptance. |

## Important boundaries

- New budget limits are metadata, not expenses. They use the same existing SQL report exclusions and never mix currencies. Refunds may make net spending negative.
- Savings contributions are manually tracked earmarks, not linked bank transfers. Progress and reversals are immutable local movements; current account balances stay unchanged. Currency cannot be reinterpreted after progress history.
- Core transaction/account forms preserve drafts/defaults. New short Plans dialogs retain inputs during their selectors, but full process-death/rotation recovery of an uncommitted Plans dialog is not yet a claimed feature. Voice is transient until handed to the normal transaction form; failed recognition retains typed words.
- Device-bound Keystore recovery/general ledger encryption limitations remain. No cloud or portable backup success is claimed. Debug screenshots remain enabled per the user's earlier development instruction; non-debug secure-window behavior is preserved.
- HTML reference renders use fictional browser-only data. Native host renders use isolated SQLite fixtures. Neither seeds production user records.

## Next milestones, not omitted features

P0: physical update/voice/QR/image/default/navigation acceptance and fix reported device regressions.
P1: recoverable encrypted chosen-folder backups; profile/transaction attachments; production vault re-authentication; Google OAuth/Drive and Supabase integration after real configuration; server-authorized groups, debts and subscription workflows with accounting tests.
P2: widgets/tiles/volume-trigger eligibility, premium entitlement validation and later accessibility/regional/app-identity enhancements. Keep every feature in V8_FEATURE_PRESERVATION.md and the machine-readable before/after manifest when implementing these.
