# Smart Import, Security & Settings — 0.5.0

> Historical0.5 guide. Superseded by [0.6 current guide](UX_SMART_ENTRY.md): OCR/harness removed, personal/play SMS flavors and newer lock/navigation behavior. Do not run the old OCR harness instructions on this branch.


## Working features

- Gift images use full-resolution camera capture or gallery/share content URIs, EXIF rotation/mirroring, bounded decoding, overlapping tall-image OCR tiles and geometric label/value reading order. Bundled ML Kit runs locally; no network permission or image upload. A bounded contrast retry is heuristic, not a measured accuracy guarantee. Crop/rotate/rescan is available when extraction fails.
- Contextual parsing handles labeled multiline codes/PINs, Indian monetary grouping, currencies and unambiguous expiry dates. Order IDs, phone numbers, unrelated prices, OTPs and links are excluded. Tied candidates stay blank for editable review. Original value and remaining balance are distinct; unrelated balance wording cannot change the selected amount's meaning. Review never verifies value with a merchant.
- Compact gift entry shows merchant/code/amount/optional expiry; optional details collapse. One-tap image/paste entry, duplicate preflight and direct reviewed Add retain final repository duplicate/idempotency protection. Expired cards may be imported without inventing a received date before their expiry.
- Smart Add in More, or an optional navigation slot, holds encrypted transaction/gift suggestions. Share/paste text is parsed locally. Add/Edit/Reject/Ignore require user action. Approval and record creation commit atomically using a stable operation ID; duplicate suggestions survive restart. Repayments/transfers/refunds/card activity remain review-only, avoiding false expenses while paired accounting modules are unavailable. Gift redemption remains separate from cash/bank expenses.
- Offline app PIN setup/change/disable, failed-attempt delays and configurable immediate/30s/1min/5min background locking. Cold starts always require authentication when configured. Supported strong fingerprint/face uses native BiometricPrompt; API30+ may use device credential fallback. The custom PIN screen remains available when biometric enrollment is absent.
- Compact Settings categories: Profile, Security & Privacy, Appearance & Navigation, Smart Tracking, Notifications, Accounts & Data, About & Support. Local name/currency/new-entry defaults, themes/navigation, visibility, gift detection/review/reminders and security controls persist. Unavailable online account, backup/export and store rating capabilities have no pretend controls.

## SMS policy and consent

Policy checked 2026-11-12: [Google Play SMS policy](https://support.google.com/googleplay/android-developer/answer/10208820), [Android default handlers](https://developer.android.com/guide/topics/permissions/default-handlers). Money management may qualify for a reviewed exception; this repository has no verified approval. **The delivered standard APK declares neither READ_SMS nor RECEIVE_SMS.** Share/paste is the supported fallback.

A separately reviewed debug build may set `-PtraxosSmsPolicyApproved=true` to include the receive-only manifest. This build flag is not permission or policy approval. The receiver additionally checks explicit app consent, runtime RECEIVE_SMS, Smart Add enablement and sender/content eligibility; revocation stops ingestion. It never reads historical messages or uses Accessibility. Eligible messages produce suggestions, never automatic financial writes. Generic private notifications contain no amounts, codes or PINs; Review opens the inbox. Live SMS delivery is untested on a phone.

## Security and recovery boundaries

PINs are 6–12 digits, salted PBKDF2-HMAC-SHA256 (600,000 iterations), constant-time verified using platform cryptography. Security state is AES-GCM encrypted with a separate Android Keystore key and written atomically under noBackup. Five failures start exponential delays from 30 seconds up to 30 minutes. In-process elapsed time prevents clock changes shortening the delay; after restart the persisted policy imposes at least a fresh delay, potentially inconveniencing legitimate users who waited while closed.

Current PIN is required to change/disable the lock. Enabling biometrics requires PIN authentication and a fresh auth-per-use Keystore cipher challenge; native success is verified with that cipher. Missing keys or corrupt state fails closed. There is **no PIN recovery/reset shortcut** and no portable backup. Losing the PIN plus enrolled authentication/key access can make local data inaccessible. Do not uninstall or clear storage to upgrade.

App locking protects access, not the whole database. Existing ledger and gift merchant/balance/date metadata remain app-private plaintext Room. Gift secrets/attachments and Smart Add payloads are encrypted in separate device-bound namespaces. No plaintext PIN, secret logging, online account or database-wide encryption is claimed. FLAG_SECURE shields task previews/screenshots; existing content stays composed behind an opaque, input-blocking gate so forms/imports survive locking. Dialogs/popups are suppressed while locked. Hardware Keystore, biometric enrollment/fallback, OS background behavior and recent-app previews still need phone verification.

## Database and architecture

Schema5 adds seven preference columns and indexed `smart_suggestions`; explicit4→5 migration preserves old ledger/gift/journal/navigation data. Schemas1–5 and the forward migration chain are retained. Per-column finance preferences and transactional gift-settings mutations prevent one Settings control reverting another. No destructive migration or cloud SDK.

Smart parsing → encrypted suggestion repository → explicit editable approval → existing ledger/gift repository inside a Room transaction. Settings use reactive persisted flows; security configuration uses a separate fail-closed encrypted store. Native authentication is bridged through FragmentActivity; the main UI is gated before first unlock. Navigation retains instant non-overlapping transitions and independently configurable slots/center quick action.

## Validation boundaries

See [actual build/test report](validation/smart-security/README.md). Host tests cover parser formats/candidate ambiguity, duplicate approval, unsafe transfer classification, Room4→5 preservation/restart, permission gates, PIN setup/change/throttling, app-gate interception/modal hiding, Settings persistence/defaults/concurrent writes and existing finance/gift regressions. They do not measure image recognition accuracy. Crypto host tests replace only hardware key provisioning with ephemeral JCE test keys.

No real redacted Amazon failing image corpus was provided. No physical Android phone or emulator was used. Native OCR accuracy, low-quality camera images, offline first-use models, hardware key lifecycle, biometric/device-credential prompts, permission dialogs, process death, TalkBack and real IME/small-screen behavior remain unverified. Host light/dark/narrow Compose checks do not replace device acceptance.

### Native OCR fixture harness (compiled, not executed)

Put privately supplied, genuinely redacted image fixtures and JSON labels under ignored `app/src/androidTest/assets/local-ocr-fixtures/`. For example:

```json
{"image":"redacted-screenshot.png","expected":{"merchant":"Amazon","amount":"1000","currency":"INR","code":"TEST-NONREDEEMABLE","pin":"","expiry":""}}
```

Labels must match visible redacted pixels; keep provenance and expected values locally. Include different merchants, EXIF camera rotations, low-quality/multiline images, missing expiry and competing prices. Do not commit live secrets or count synthetic images as real OCR evidence. With an authorized USB phone and network disabled on the phone:

```sh
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.traxos.finance.OcrCorpusInstrumentedTest
adb shell run-as app.traxos.finance cat files/ocr-corpus-metrics.json
```

The test runs the actual decoding/ML Kit/parser path and reports image/field/match counts without secret values. With no corpus it skips rather than claiming accuracy. Physical native OCR has not been run for this delivery.

## Build/install and resources

```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Published file: `downloads/TRAXOS-0.5.0-smart-security.apk`; source output `app/build/outputs/apk/debug/app-debug.apk`. Preserve the installed signing key. Gradle1.5GB/two workers/no parallel projects, in-process Kotlin, one512MB host-test fork, bounded6MP image decoding and one OCR task at a time suit an8GB laptop. No emulator; do not run multiple builds concurrently. Bundled models increase APK size but enable offline first use.

Next task: execute the phone/image corpus and security acceptance checklist before further feature work; then address measured failures and plan recoverable encrypted backup separately.
