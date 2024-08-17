package io.github.g00fy2.quickie.content

@Suppress("ArrayInDataClass")
public sealed class QRContent(
  public open val rawBytes: ByteArray?,
  public open val rawValue: String?,
) {

  /**
   * Plain text or unknown content QR Code type.
   */
  public data class Plain internal constructor(
    override val rawBytes: ByteArray?,
    override val rawValue: String?
  ) : QRContent(rawBytes, rawValue)
}