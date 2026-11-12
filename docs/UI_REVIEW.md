# Traxos 0.7.0 — UI review and daily-flow refinements

Install the Personal APK as **Update**, keeping app storage and the existing signing identity. Open **More → Explore all screen flows**, or **Settings → Full UI review**. More remains available from the header menu if it is absent from your customized bar. The transparent header mark returns to the real dashboard.

## Real changes

- The existing transparent native five-square T appears directly on the splash and centered unlock screen, without a colored logo tile. Geometry/provenance remains in `assets/brand/README.md`; no new pixel-exact trace of an unavailable original image is claimed.
- Settings is grouped into compact sections. Light/Dark/System fits one horizontal selector; five locally persisted accent palettes apply to real controls immediately. Navigation customization includes a live bar preview, independent center action, reorder/swap/assign controls, presets and restore defaults. Only real implemented destinations are selectable.
- More separates everyday tools, customization and sample product journeys. Accounts, gift cards, transfers, Smart Add and other tools have distinct outline symbols. Secondary buttons have a subtle outline.
- Selecting Home discards nested page history and goes to the dashboard. Fresh Settings entry always opens its homepage. Utility sheets and the review sandbox have their own Back/close boundaries. Screen transitions remain disabled to prevent fading text overlap.
- Gift text parsing recognizes currency prefixes/suffixes and labeled values, separates order/tax context and leaves ambiguous amounts blank. Missing currency is suggested from app preferences for explicit review. Encoded QR/barcode text can use this parser; there is **no OCR** or automatic image text recognition. Eligible future SMS still produces review suggestions only.
- **More → Transfer between accounts** records two atomic linked legs between active cash/bank/wallet accounts in the same currency. It requires sufficient recorded funds and valid opening dates. Transfers do not create income or expense. Activity filters separate transfers from spending/income/card payments. Deleting/Undo affects both legs. Correct a linked movement by deleting and re-entering it; individual legs cannot be edited. Card repayments stay in Pay card. Cross-currency conversion, fees and reconciliation are not implemented.
- Explicit self-transfer SMS is reviewed as a transfer requiring both accounts. Generic transfer wording remains review-only. Opposite alerts with the same stable reference, amount and currency collapse into one suggestion. Messages without a stable shared reference cannot be reliably matched and require user review.

## Full-product UI sandbox

[100-page inventory and V3 mapping](UI_REVIEW_COVERAGE.md). These linked sample journeys cover onboarding/authentication, accounts/cards, Smart Add, groups/splits, plans/goals, subscriptions, debts, reminders, Settings/security, backup/restore/conflicts, billing and support/update states. Use the UI-state selector for sample, empty, loading, error/retry, offline, denied, locked, revoked, conflict, success and long-text layouts.

Every page says **UI PREVIEW · Sample only · Nothing is saved**. Forms, pickers, navigation, confirmation and feedback are interactive, but actions do not authenticate, bill, send messages, change real settings, write financial data, sync or export. Sample secrets are held in memory, not saved-state bundles. Future modules are not selectable as real bottom tabs. The separate admin console is not built in this consumer APK. These pages enable one-pass hierarchy review; they are not production feature implementations or bespoke final acceptance for every layout.

## Build and install

```sh
./gradlew --no-daemon :app:assemblePersonalDebug :app:assemblePlayDebug \
  :app:testPersonalDebugUnitTest :app:testPlayDebugUnitTest \
  :app:lintPersonalDebug :app:lintPlayDebug
adb install -r app/build/outputs/apk/personal/debug/app-personal-debug.apk
```

Use the same debug signing key as the installed APK. Do not uninstall to update. Outputs are `downloads/TRAXOS-0.7.0-personal.apk` and `downloads/TRAXOS-0.7.0-play.apk`; the latter excludes automatic SMS code/permissions. Both share the application ID and cannot be installed side by side.

Room 6→7 adds only `preferences.accent`; earlier migrations remain. No new SDK dependencies, permissions, OCR or cloud services. Gradle retains 1.5 GB heap, two workers, in-process Kotlin and one 512 MB test fork. Run one build at a time; no emulator.

Actual results are in [validation/ui-review](validation/ui-review/README.md). Host renders are synthetic, not phone screenshots. Phone acceptance remains necessary for biometric/Keystore hardware, installer-restricted SMS, OS splash/recent previews, keyboard/TalkBack, process death and OEM behavior. Existing app-private ledger/metadata remain unencrypted; code/PIN secret protection and lock have the recovery limits documented in [UX_SMART_ENTRY.md](UX_SMART_ENTRY.md).
