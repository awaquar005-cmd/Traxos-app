# Current extension

Current0.16.0 adds principal-only account-linked IOUs, selected contacts/explicit sharing, budget periods/thresholds and account-backed goals/deadlines. Room14 is additive; preserve old entities and schema11–14 encrypted snapshots, reservation ownership and principal/report semantics. Read docs/DEBT_PLANNING_EXTENSIONS.md. Do not undo allocated/archive safeguards or claim bank payment execution.

# TRAXOS coding-agent handoff

Current0.15.0 adds offline local Groups, card statements and confirmed subscription/bill/EMI plans (docs/GROUPS_CARDS_BILLS.md). Owner deferred live sharing until backend setup: never claim accepted invitations or server-current balances. Room13 is additive; preserve schema11/12 archives, group self-share report semantics, prior entities/routes and vault exclusions.

Historical Personal release0.14.0 adds standalone private IOUs and existing card-liability overview (docs/DEBTS_CREDIT.md). Room12 is additive; keep schema11 backup import and exclude the card vault.

Read docs/V8_FEATURE_PRESERVATION.md, docs/V8_STATUS.md, docs/PROGRESS.md and the architecture before changing this app. Original HTMLs in docs/references/v8 are visual/interaction references, not a replacement specification, runtime or complete feature inventory. The V3 specification remains cumulative.

- Never remove, disable, omit or replace an existing implemented feature because a screenshot/HTML/short list does not mention it. Inventory actual routes, settings, workers, entities and repository APIs; update the before/after manifest. Keep current defaults, inline creation, extra fields, undo/recovery, navigation customization and financial semantics.
- OCR is the sole explicitly removed recognition feature. Keep independent QR/barcode scanning and encrypted gift-image saving. Do not reintroduce OCR or a web wrapper.
- Keep the current dashboard hero and five-square brand assets. Native Kotlin/Compose, compact custom UI, useful unboxed pictograms, readable dark mode and safe48dp touch targets. Large routed editors are full screen; small choices use real dialogs. Keep context above concise actions.
- Latest user override: retain existing encrypted card fields, including CVV. Do not silently delete or migrate vault data based on the contrary proposed V8 contract. Keep vault data out of future portable backups/exports/sync. Never claim full database encryption or portable Keystore recovery that does not exist.
- Finance uses exact minor units. Transfers/card repayments are paired movements, not ordinary expenses/income. Goal earmark progress is separate from bank balances. No automatic SMS/voice posting without approval. Do not infer missing amounts or sum currencies.
- Google project/Android OAuth identifiers have been supplied and the user confirms package/SHA-1 registration; native Drive consent/snapshot transport is implemented in0.13.0, but real Google/phone acceptance is pending. Firebase Authentication API key/Web client configuration has not been supplied: account login uses the real REST implementation with setup-file import, never a fake configured session. Drive-only linking must work independently of Firebase. Supabase is not configured. docs/CLOUD_SETUP.md has exact package/fingerprint and setup instructions. No simulated login/sync/backup success, embedded client secrets or Supabase service-role keys. Protected services need real auth, RLS and acknowledgment tests.
- Personal APK only unless the user explicitly changes this. No emulator. Use one Gradle process, conservative1.5GB heap/two workers; no unnecessary dependencies/SDKs or paid APIs. Physical-phone speech/biometrics/OEM/IME results must not be fabricated.
- Preserve signing identity and user data. Use explicit forward Room migrations and committed schemas; no destructive fallback or uninstall/clear-storage upgrade advice. Build/test/lint appropriate to changes, report actual results and pending gates; update docs/PROGRESS.md and docs/FILES.md.

Cloud build: JAVA_HOME=/workspace/tools/jdk/jdk-21.0.13+9 ANDROID_USER_HOME=/workspace/tools/android-user ANDROID_HOME=/workspace/tools/android-sdk GRADLE_USER_HOME=/workspace/tools/gradle-home ./gradlew -Duser.home=/workspace/tools/java-home -I /workspace/tools/cloud-tests.init.gradle --no-daemon :app:assemblePersonalDebug :app:testPersonalDebugUnitTest :app:lintPersonalDebug

Do not omit ANDROID_USER_HOME: it selects the update-compatible debug signing setup. Install with adb install -r, never uninstall as an upgrade strategy. /workspace paths refer to the cloud workspace, not the user's laptop.
