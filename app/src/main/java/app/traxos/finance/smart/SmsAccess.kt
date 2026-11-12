package app.traxos.finance.smart

object SmsAccess {
    fun allowed(
        policyApproved: Boolean,
        consent: Boolean,
        permissionGranted: Boolean,
        smartEnabled: Boolean,
    ) = policyApproved && consent && permissionGranted && smartEnabled
}
