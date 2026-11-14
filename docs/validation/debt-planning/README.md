# Debt and planning validation

Personal APK built; **299 tests passed across the full run and targeted rerun (0 remaining failures/errors/skips)**. Lint: **0 errors, 23 warnings, 4 hints**. Signing and16KiB ZIP/64-bit ELF alignment verified.

[Exact test suites/signature](host-results.json) · [Build log](build.log) · [Preservation manifest](../../DEBT_PLANNING_DELIVERY_MANIFEST.json). Native PNGs use isolated fixtures.

Tests cover principal movement/report conservation, matching without duplicate cash, concurrent/retry protection, linked undo and spent-funds rejection; normalized people/duplicate validation; account reservations and paired transfers/withdrawals/undo; salary month-end/leap periods and exact thresholds; personal group shares/refunds/currency separation; additive13→14 migration and encrypted new-table recovery; contact picker/no broad permission and explicit share preview; private notification delivery/deduplication/cancellation/permission denial. All273 previous regression tests retained.

Physical contact provider/WhatsApp/notification timing/OEM/IME/TalkBack/large-font acceptance remains pending. Live groups remain deferred by owner. General Room data is app-private plaintext; existing vault/PIN/session portable exclusions remain.

The first shared-JVM native run reported five older UI timing failures (retained in initial-shared-jvm.log). Ordinary transaction routing now remains synchronous; loan lookup is limited to principal rows. Native tests wait for rendered controls/frames, and the full run uses sequential class isolation (`-I scripts/isolated-tests.gradle`) to avoid retained native test state. No assertions or tests are disabled.

The isolated299-test run passed298 tests. The gift-notification privacy assertion scanned Bundle.toString(), including arbitrary internal object IDs that can coincidentally contain the short fixture PIN. It now checks all visible title/text/expanded/subtext/summary/text-line/ticker/public-version fields. The complete gift-worker class was rerun successfully after this test-only correction; no production source changed afterward. host-results.json merges the full-run suites with this rerun; full-regression.log and build.log retain both actual executions.
