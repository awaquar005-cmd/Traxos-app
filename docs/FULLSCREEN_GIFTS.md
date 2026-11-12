# Full-screen editors and gift layout — 0.10.1 Personal

Routed account/transaction/gift/card editors now fill the available screen, with no floating rounded frame or width/height cap. Their scrolling bodies and pinned Save actions remain. Actual selection/confirmation dialogs stay compact and centered. Confirmation buttons show a single word, with the full action retained as an accessibility description; explanatory title/body remain above a spaced action row.

Gift wallet rows are compact and full width, with balance/expiry opposite Open. The individual gift view combines value, metadata and masked code/PIN controls into one full-width panel. Redeem/Edit, Reveal/Copy and Archive/Delete are grouped side by side; common gift actions use short labels. Original value, expiry, redemption/Undo/refund/archive and secret masking remain functional. Dashboard, accounting, recurring rules and schema10 are unchanged.

Screenshots and recent-task previews are allowed in DEBUG builds for development UI review. Non-debug builds still set FLAG_SECURE. Security/Privacy copy reflects development behavior. This does not disable PIN/biometrics or encryption. The Personal debug build was tested on the host; release capture behavior is guarded in source, not claimed as physical-device validation.
