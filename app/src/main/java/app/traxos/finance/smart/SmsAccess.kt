package app.traxos.finance.smart

object SmsAccess {
    fun allowed(
        buildEnabled: Boolean,
        consent: Boolean,
        permissionGranted: Boolean,
        smartEnabled: Boolean,
    ) = buildEnabled && consent && permissionGranted && smartEnabled
}
