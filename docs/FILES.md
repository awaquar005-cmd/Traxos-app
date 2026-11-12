# Created files

All files below were created for Milestone 1 in the refreshed workspace. Implementation, status, limitations and commands are documented in README and PROGRESS. SDK/JDK/cache paths and ignored build products are outside this inventory.

- `.gitattributes`
- `.gitignore`
- `README.md`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/schemas/app.traxos.finance.data.TraxosDatabase/1.json`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/traxos/finance/MainActivity.kt`
- `app/src/main/java/app/traxos/finance/TraxosApplication.kt`
- `app/src/main/java/app/traxos/finance/data/Database.kt`
- `app/src/main/java/app/traxos/finance/data/LedgerRepository.kt`
- `app/src/main/java/app/traxos/finance/domain/Money.kt`
- `app/src/main/java/app/traxos/finance/ui/App.kt`
- `app/src/main/java/app/traxos/finance/ui/Theme.kt`
- `app/src/main/java/app/traxos/finance/ui/ViewModels.kt`
- `app/src/main/res/drawable/traxos_mark.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/java/app/traxos/finance/FormViewModelTest.kt`
- `app/src/test/java/app/traxos/finance/LedgerRepositoryTest.kt`
- `app/src/test/java/app/traxos/finance/MoneyTest.kt`
- `assets/brand/README.md`
- `assets/brand/traxos-mark.svg`
- `build.gradle.kts`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEVICE_QA.md`
- `docs/FILES.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/PROGRESS.md`
- `docs/Traxos_Native_Android_Product_Blueprint_v3.md`
- `docs/validation/build.log`
- `docs/validation/host-results.json`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`
- `settings.gradle.kts`

Additional publishing files: `downloads/README.md`, `downloads/TRAXOS-0.1.0-dev.apk`, `downloads/SHA256SUMS.txt`.

Reopen-crash patch modifies `ui/App.kt` (section-scoped lazy keys), app version/test dependencies, version catalog, README/download instructions and maintenance documentation. New host UI tests: `app/src/test/java/app/traxos/finance/HomeStartupTest.kt` and `app/src/test/java/app/traxos/finance/SavedLedgerLaunchTest.kt`. Patch validation evidence is under `docs/validation/reopen/`.

Hotfix artifacts: `downloads/TRAXOS-0.1.1-dev.apk`, updated `downloads/SHA256SUMS.txt`, `docs/validation/reopen/before.txt`, `docs/validation/reopen/build.log`, `docs/validation/reopen/host-results.json`.

UI pass additions: `ui/Components.kt`, `ui/Dashboard.kt`, `ui/LedgerScreens.kt`, `ui/Navigation.kt`, `ui/SettingsScreen.kt`, exported Room schema `2.json`, `PreferencesMigrationTest.kt`, `NavigationUiTest.kt`, `UiAppearanceTest.kt`, `LoadingUiTest.kt`. Modified: App.kt/forms, Theme.kt, ViewModels.kt, Database.kt/preferences DAO/migration, LedgerRepository.kt/partial preference updates, app version/test schema resources, startup tests, architecture/plan/progress/device QA and download documentation. New validation artifacts are under `docs/validation/ui-pass/`.

Published UI-pass artifacts: `downloads/TRAXOS-0.2.0-ui-pass.apk`, checksum update, `docs/validation/ui-pass/{README.md,host-results.json,build.log,dashboard-light.png,dashboard-dark.png}`.

0.3.0 additions: `domain/NavigationLayout.kt`, `ui/Controls.kt`, `ui/Overlays.kt`, `ui/Forms.kt`, `ui/NavigationSettingsScreen.kt`, exported Room schema `3.json`, and `NavigationLayoutTest.kt`, `NavigationPreferencesTest.kt`, `NavigationCustomizationUiTest.kt`, `CustomFormsUiTest.kt`, `BrandedControlsUiTest.kt`. Modified: App/Components/SettingsScreen/Navigation/ViewModels and existing dashboard/list action primitives; Database/LedgerRepository preference support, app version, UI tests and docs. Financial accounting algorithms are unchanged. Validation and synthetic host renders are under `docs/validation/ui-components/` after the final run.

0.3.0 delivery: `downloads/TRAXOS-0.3.0-ui-components.apk`, updated README/checksums, and `docs/validation/ui-components/{README.md,host-results.json,build.log,dashboard-light.png,dashboard-dark.png,transaction-form.png,account-form-dark.png}`.

Gift wallet: `gifts/GiftRules.kt`, `GiftDatabase.kt`, `GiftRepository.kt`, `GiftSecrets.kt`, `GiftReminders.kt`, `GiftImageImport.kt`, `GiftViewModel.kt`; UI `GiftWalletScreens.kt`, `GiftImportSettings.kt`; exported schema `4.json`; tests `GiftRulesTest.kt`, `GiftRepositoryTest.kt`, `GiftMigrationTest.kt`, `GiftReminderWorkerTest.kt`, `GiftWalletUiTest.kt`. Modified: app dependencies/version/manifest, Application/Activity share handling, Room registration/migration, navigation registry/App routes/Settings, shared input password mode, documentation.

Additional gift UI coverage: `GiftAppearanceTest.kt` (light/dark synthetic renders and narrow large-font actions), `GiftNavigationUiTest.kt` (optional Gift Cards/More slots, fallback Settings, restart and permission exclusions). Feature/accounting/security/usage guide: `docs/GIFT_WALLET.md`.

Gift release artifacts: `downloads/TRAXOS-0.4.0-gift-wallet.apk`, README/checksum updates, `docs/validation/gift-wallet/{README.md,host-results.json,build.log,test-run.log,gift-wallet-light.png,gift-wallet-dark.png}`. Existing schemas 1/2/3 and financial accounting sources retained.


## 0.5.0 smart import/security/settings pass

Current guide: [SMART_SECURITY.md](SMART_SECURITY.md). Added/modified source/configuration/tests and documentation:

- `.gitignore`
- `README.md`
- `app/build.gradle.kts`
- `app/schemas/app.traxos.finance.data.TraxosDatabase/5.json`
- `app/src/androidTest/java/app/traxos/finance/OcrCorpusInstrumentedTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/traxos/finance/MainActivity.kt`
- `app/src/main/java/app/traxos/finance/TraxosApplication.kt`
- `app/src/main/java/app/traxos/finance/data/Database.kt`
- `app/src/main/java/app/traxos/finance/data/LedgerRepository.kt`
- `app/src/main/java/app/traxos/finance/domain/NavigationLayout.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftImageImport.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftParser.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftRepository.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftRules.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftSecrets.kt`
- `app/src/main/java/app/traxos/finance/gifts/GiftViewModel.kt`
- `app/src/main/java/app/traxos/finance/gifts/OcrReadingOrder.kt`
- `app/src/main/java/app/traxos/finance/security/DeviceAuthentication.kt`
- `app/src/main/java/app/traxos/finance/security/LockStore.kt`
- `app/src/main/java/app/traxos/finance/security/LockViewModel.kt`
- `app/src/main/java/app/traxos/finance/smart/FinancialSmsReceiver.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartDatabase.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartParser.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartRepository.kt`
- `app/src/main/java/app/traxos/finance/smart/SmartViewModel.kt`
- `app/src/main/java/app/traxos/finance/smart/SmsAccess.kt`
- `app/src/main/java/app/traxos/finance/ui/App.kt`
- `app/src/main/java/app/traxos/finance/ui/Controls.kt`
- `app/src/main/java/app/traxos/finance/ui/Forms.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftCropSheet.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftImportSettings.kt`
- `app/src/main/java/app/traxos/finance/ui/GiftWalletScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/Navigation.kt`
- `app/src/main/java/app/traxos/finance/ui/Overlays.kt`
- `app/src/main/java/app/traxos/finance/ui/SecurityScreens.kt`
- `app/src/main/java/app/traxos/finance/ui/SettingsScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/SmartInboxScreen.kt`
- `app/src/main/java/app/traxos/finance/ui/ViewModels.kt`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/xml/gift_capture_paths.xml`
- `app/src/smsApproved/AndroidManifest.xml`
- `app/src/test/java/app/traxos/finance/GiftContextParserTest.kt`
- `app/src/test/java/app/traxos/finance/GiftImagePreparationTest.kt`
- `app/src/test/java/app/traxos/finance/GiftRulesTest.kt`
- `app/src/test/java/app/traxos/finance/GiftWalletUiTest.kt`
- `app/src/test/java/app/traxos/finance/LockSessionTest.kt`
- `app/src/test/java/app/traxos/finance/LockStoreTest.kt`
- `app/src/test/java/app/traxos/finance/NavigationCustomizationUiTest.kt`
- `app/src/test/java/app/traxos/finance/NavigationUiTest.kt`
- `app/src/test/java/app/traxos/finance/PreferenceIsolationTest.kt`
- `app/src/test/java/app/traxos/finance/SecurityUiTest.kt`
- `app/src/test/java/app/traxos/finance/SmartAddTest.kt`
- `app/src/test/java/app/traxos/finance/SmartInboxUiTest.kt`
- `app/src/test/java/app/traxos/finance/SmartMigrationTest.kt`
- `app/src/test/java/app/traxos/finance/SmartParserTest.kt`
- `app/src/test/java/app/traxos/finance/StructuredSettingsUiTest.kt`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEVICE_QA.md`
- `docs/GIFT_WALLET.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/PROGRESS.md`
- `docs/SMART_SECURITY.md`
- `docs/SMART_SECURITY_PLAN.md`
- `downloads/README.md`
- `gradle/libs.versions.toml`

Delivery also includes schema5, downloads/TRAXOS-0.5.0-smart-security.apk, checksums and docs/validation/smart-security build/test/packaging evidence. Native instrumentation harness is compiled but not run; its private fixture folder is ignored.
