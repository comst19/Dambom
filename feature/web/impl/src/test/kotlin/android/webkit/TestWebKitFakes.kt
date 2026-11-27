package android.webkit

internal class RecordingSslErrorHandler : SslErrorHandler() {
    var cancelCount: Int = 0
        private set
    var proceedCount: Int = 0
        private set

    override fun cancel() {
        cancelCount += 1
    }

    override fun proceed() {
        proceedCount += 1
    }
}

internal class FakeWebResourceError(
    private val code: Int,
    private val message: CharSequence = "error",
) : WebResourceError() {
    override fun getErrorCode(): Int = code

    override fun getDescription(): CharSequence = message
}
