# Debts, credit and interaction validation

Personal assemble, **242 tests passed (0 failures/errors/skips)** and lint passed: **0 errors, 23 warnings, 1 hint**. Signing matches0.13.0; ZIP/64-bit ELF16KiB alignment passed.

[Actual suites/signing](host-results.json) · [Full build log](build.log) · [Preservation manifest](../../DEBTS_DELIVERY_MANIFEST.json). Native host PNGs use isolated fixture records, never production dummy balances.

New checks cover partial repayment/Undo/retry/concurrency, rejected overpayments/stale revisions/invalid date/currency/direction, adjustments/archive/restore, operation payload collisions, schema11→12 preservation, encrypted IOU/history round-trip, schema11 archive compatibility and inconsistent history rejection. Native tests exercise actual touch on Google setup and More tools (including closing/reopening), dialog creation/repayment and no ledger posting. All prior232 tests retained.

No physical phone/OEM/keyboard/TalkBack/large-font or live Google/Firebase acceptance claimed. Google login still requires real Firebase configuration. IOUs are standalone local tracking, not automatically linked bank loans or group balances. See ../../DEBTS_CREDIT.md.
