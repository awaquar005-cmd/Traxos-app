# Compact Personal release — 0.9.0

Uses the previous (Unslop OFF) design as final. The experiment remains on its separate branch; no experimental appearance toggle is added here. Action buttons, Back, overflow and logos have no outlines. Inputs retain focus/error indicators. Dashboard/page/card padding is reduced, duplicate account summary removed, Activity chips have compact visual surfaces within 48dp touch targets. Useful icons, eye toggle and logo watermark remain. No new dependency or internet permission.

## Gift entry

Generic Pay Code context and alphanumeric PINs are recognized from pasted/shared text. Links are ignored; nothing is opened or fetched. Missing values remain missing: the user's example has no amount. A new stored-value gift's blank remaining amount defaults to its original value; explicit zero and historical unknown values are preserved. Existing saved merchant choices and inline Add New are now next to the main merchant field. Future merchant fields should reuse this local catalog rather than add another list. Import still requires review/confirmation.

## Credit cards

Create a Credit Card financial account with its opening amount owed, then Edit account → Card number, expiry & CVV. Store optional number, expiry and CVV in a separate masked form. PAN format/Luhn, expiry and CVV length are validated. These are local references, not a payment processor. Existing card expenses increase debt, refunds reduce debt, and paired repayments remain separate from spending. Current balance comes from the ledger; no available-credit or issuer balance is invented.

Credentials are AES-256-GCM encrypted with an Android Keystore key and row-bound authenticated data, never journaled or saved to state bundles. CVV storage is optional. Detail deletion preserves the account and financial history. Revision checks and operation IDs prevent stale/duplicate writes. Room 8→9 adds only an encrypted reference table; prior schemas migrate through the existing chain. Keystore/PIN loss has no portable recovery; app lock does not encrypt the general ledger. Device validation remains required. Sensitive screenshots/task previews are protected by the existing secure window flag.

## Build/install

Build only Personal: `./gradlew :app:assemblePersonalDebug :app:testPersonalDebugUnitTest :app:lintPersonalDebug`. Local SDK/JDK configuration is required. Install `adb install -r downloads/TRAXOS-0.9.0-personal.apk`; choose Update, never uninstall. Same application/signing identity, version code10. See validation/compact-cards for actual host results. No Play artifact is produced this pass.
