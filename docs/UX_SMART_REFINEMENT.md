# 0.6.0 UX, Smart Entry and Security plan

Preserve existing Room records, signing identity, repositories and custom components. Stages:1 remove image OCR, preserve independent barcode and compact forms;2 custom category/account metadata, inline creation, merchant/description reuse and correct card liability/settlements;3 one prompt per lock episode with secure cancellation/PIN fallback;4 Settings fresh-entry backstack, compact menus and dark semantic contrast;5 personal/play compile-time SMS isolation with consent and OS fallback;6 native splash/custom lock polish, regressions, packaging.

Each stage receives host regression verification before the next. No emulator. Device biometric/installer/permission/camera acceptance must remain explicitly unverified. Use forward migrations only. Personal SMS receiver must never auto-save suggestions; Play flavor must exclude SMS classes and permissions, not just disable a toggle. Credit limits are never assets; credit card signed balances represent debt, purchases are expenses and repayments must use atomic paired non-income/non-expense postings.

## Verified stages so far

1. OCR model, processing and native OCR harness removed. Independent QR/barcode scanning and bounded image attachment decoding retained. Full host regression/build passed after clearing redundant SDK download archives to resolve test temporary-storage exhaustion.
2. Schema6 migration and custom categories/accounts/merchant/description pickers implemented. Full host suite/build passed. Coverage includes shipped schema 5 financial/gift/inbox preservation, inline creation with retained amount and repeated-save callbacks, archived historical references, normalized duplicate rejection and signed card debt/refund/paired-payment/Undo/currency rules.
3. Biometric auto-prompt uses a ViewModel-owned once-per-lock lease, resumed lifecycle gate and native pending guard. PIN/cancellation/rotation/background rules pass host tests. onResume clears the privacy shield after a permission dialog. Actual native biometric presentation still needs a phone.

4. Settings fresh-entry/Back, dropdown privacy, compact menus and root/label/button contrast verified in host UI tests.
5. Personal/play APKs compile; incoming receive-only consent/permission/OTP/personal-sender gates and reviewed duplicate/card handling pass tests. Code and permission exclusion from Play is verified in the packaged artifact.
6. Native splash and custom keypad/fixed footer polished. Full personal128/Play: 126 tests and both lint tasks pass. Physical acceptance remains pending; see validation/ux-refinement.
