# Decisions

- 2026-11-11: Read all 1,164 lines of V3 before code. Preserve exact source and block-level requirement inventory. User milestone/cost constraints take precedence over recommended infrastructure.
- Single app module + package boundaries/manual DI: conservative Gradle overhead; split only as measured build/team needs grow.
- Native Kotlin/Compose/Material3, Room, Navigation, Paging and coroutines. No emulator, web wrapper, cloud SDK, analytics or paid API.
- JDK 21 is installed; pin compatible AGP/Gradle/Kotlin after checking official artifacts. SDK command-line tools installed only under /workspace/tools if absent. SDK licenses are required for builds; no system-wide changes.
- minSdk 26; compile/target SDK 36 (Android 16). Confirm official Android toolchain/artifact availability, record selected versions in build files and validation documentation.
- Integer money and currency per account; INR default, support INR/USD/EUR/JPY/KWD initially. Currency entry limited to supported scales; no automatic currency conversion. No invented account opening balances.
- Single local profile `local` until proper multi-profile isolation exists. Journal payload contains historical finance content locally and never enters diagnostic logs.
- M1 development-only plaintext Room, OS backup excluded, no secure-storage claim. Encryption/PIN/biometric and portable recovery are M3 release blockers.
- Cash and bank only in M1. Cards, transfer, debt/refund/repayment, goals and groups deferred explicitly so expenses are never misrepresented.
- Keep immutable operation IDs across retries, atomic journal, revision checks and tombstone undo from first implementation. No Save debounce.
- Original supplied mark is described as five rounded squares. No accessible raster asset file here; transparent SVG and Android vector reconstruction are provisional, not a pixel-exact conversion. No image-generation replacement.
- System sans typography provides OS Indic fallback and tabular figures with no font download. Compact spacing 4/8/12/16/20/24dp; 48dp controls. No unnecessary animation.
- Start with Home/Transactions/Accounts and top Settings; migrate to V3 Groups/Plans navigation as those features become real.
- Private GitHub repo creation was previously rejected by connected integration (HTTP 403). Keep local Git history; no claimed remote repository.

- Final M1 validation: Gradle wrapper assembleDebug/testDebugUnitTest/lintDebug succeeded; 26 tests passed; lint has 14 dependency-update advisories only. Keep this tested version set for M1 and evaluate upgrades/security advisories deliberately before release. Use locally installed full Temurin JDK 21.0.13+9; system runtime lacked javac launcher. SDK and JDK live under /workspace/tools. Physical phone acceptance is outstanding.
