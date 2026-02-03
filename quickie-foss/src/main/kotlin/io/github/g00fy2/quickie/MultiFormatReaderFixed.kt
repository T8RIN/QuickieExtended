package io.github.g00fy2.quickie

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.Reader
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.aztec.AztecReader
import com.google.zxing.common.StringUtils
import com.google.zxing.datamatrix.DataMatrixReader
import com.google.zxing.maxicode.MaxiCodeReader
import com.google.zxing.oned.MultiFormatOneDReader
import com.google.zxing.pdf417.PDF417Reader
import com.google.zxing.qrcode.QRCodeReader
import java.nio.charset.Charset

class MultiFormatReaderFixed : Reader {
  private var hints: MutableMap<DecodeHintType?, *>? = null
  private var readers: Array<Reader>? = emptyArray()

  /**
   * This version of decode honors the intent of Reader.decode(BinaryBitmap) in that it
   * passes null as a hint to the decoders. However, that makes it inefficient to call repeatedly.
   * Use setHints() followed by decodeWithState() for continuous scan applications.
   *
   * @param image The pixel data to decode
   * @return The contents of the image
   * @throws NotFoundException Any errors which occurred
   */
  @Throws(NotFoundException::class)
  override fun decode(image: BinaryBitmap?): Result {
    setHints(null)
    return decodeInternal(image)
  }

  /**
   * Decode an image using the hints provided. Does not honor existing state.
   *
   * @param image The pixel data to decode
   * @param hints The hints to use, clearing the previous state.
   * @return The contents of the image
   * @throws NotFoundException Any errors which occurred
   */
  @Throws(NotFoundException::class)
  override fun decode(image: BinaryBitmap?, hints: MutableMap<DecodeHintType?, *>?): Result {
    setHints(hints)
    return decodeInternal(image)
  }

  /**
   * Decode an image using the state set up by calling setHints() previously. Continuous scan
   * clients will get a **large** speed increase by using this instead of decode().
   *
   * @param image The pixel data to decode
   * @return The contents of the image
   * @throws NotFoundException Any errors which occurred
   */
  @Throws(NotFoundException::class)
  fun decodeWithState(image: BinaryBitmap?): Result {
    // Make sure to set up the default state so we don't crash
    if (readers == null) {
      setHints(null)
    }
    return decodeInternal(image)
  }

  /**
   * This method adds state to the MultiFormatReader. By setting the hints once, subsequent calls
   * to decodeWithState(image) can reuse the same set of readers without reallocating memory. This
   * is important for performance in continuous scan clients.
   *
   * @param hints The set of hints to use for subsequent calls to decode(image)
   */
  fun setHints(hints: MutableMap<DecodeHintType?, *>?) {
    this.hints = hints

    val tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER)
    val formats =
      if (hints == null) null else hints.get(DecodeHintType.POSSIBLE_FORMATS) as MutableCollection<BarcodeFormat?>?
    val readers: MutableCollection<Reader> = ArrayList<Reader>()
    if (formats != null) {
      val addOneDReader =
        formats.contains(BarcodeFormat.UPC_A) ||
          formats.contains(BarcodeFormat.UPC_E) ||
          formats.contains(BarcodeFormat.EAN_13) ||
          formats.contains(BarcodeFormat.EAN_8) ||
          formats.contains(BarcodeFormat.CODABAR) ||
          formats.contains(BarcodeFormat.CODE_39) ||
          formats.contains(BarcodeFormat.CODE_93) ||
          formats.contains(BarcodeFormat.CODE_128) ||
          formats.contains(BarcodeFormat.ITF) ||
          formats.contains(BarcodeFormat.RSS_14) ||
          formats.contains(BarcodeFormat.RSS_EXPANDED)
      // Put 1D readers upfront in "normal" mode
      if (addOneDReader && !tryHarder) {
        readers.add(MultiFormatOneDReader(hints))
      }
      if (formats.contains(BarcodeFormat.QR_CODE)) {
        readers.add(QRCodeReader())
      }
      if (formats.contains(BarcodeFormat.DATA_MATRIX)) {
        readers.add(DataMatrixReader())
      }
      if (formats.contains(BarcodeFormat.AZTEC)) {
        readers.add(AztecReader())
      }
      if (formats.contains(BarcodeFormat.PDF_417)) {
        readers.add(PDF417Reader())
      }
      if (formats.contains(BarcodeFormat.MAXICODE)) {
        readers.add(MaxiCodeReader())
      }
      // At end in "try harder" mode
      if (addOneDReader && tryHarder) {
        readers.add(MultiFormatOneDReader(hints))
      }
    }
    if (readers.isEmpty()) {
      if (!tryHarder) {
        readers.add(MultiFormatOneDReader(hints))
      }

      readers.add(QRCodeReader())
      readers.add(DataMatrixReader())
      readers.add(AztecReader())
      readers.add(PDF417Reader())
      readers.add(MaxiCodeReader())

      if (tryHarder) {
        readers.add(MultiFormatOneDReader(hints))
      }
    }
    this.readers = readers.toTypedArray()
  }

  override fun reset() {
    if (readers != null) {
      for (reader in readers) {
        reader.reset()
      }
    }
  }

  @Throws(NotFoundException::class)
  private fun decodeInternal(image: BinaryBitmap?): Result {
    if (readers != null) {
      for (reader in readers) {
        try {
          val result = reader.decode(image, hints)
          return if (reader is AztecReader || reader is DataMatrixReader) {
            Result(
              guessEncodingAndReencode(result.text),
              result.rawBytes,
              result.numBits,
              result.resultPoints,
              result.barcodeFormat,
              result.timestamp,
            )
          } else {
            result
          }
        } catch (re: ReaderException) {
          // continue
        }
      }
    }
    throw NotFoundException.getNotFoundInstance()
  }

  private fun guessEncodingAndReencode(code: String): String {
    val bytes = code.toByteArray(Charsets.ISO_8859_1)

    val ff = 0xFF.toByte()
    val fe = 0xFE.toByte()
    val ze = 0.toByte()

    fun hasUTF32BOM(b: ByteArray): Boolean {
      return (b[0] == ff && b[1] == fe && b[2] == ze && b[3] == ze) ||
        (b[0] == ze && b[1] == ze && b[2] == fe && b[3] == ff)
    }

    fun hasUTF16BOM(b: ByteArray): Boolean {
      return (b[0] == ff && b[1] == fe) ||
        (b[0] == fe && b[1] == ff)
    }

    if (bytes.size > 4 && hasUTF32BOM(bytes)) {
      return String(bytes, Charsets.UTF_32)
    }

    if (bytes.size > 2 && hasUTF16BOM(bytes)) {
      return String(bytes, Charsets.UTF_16)
    }

    val zxingGuess = StringUtils.guessEncoding(bytes, hints)
    return String(bytes, Charset.forName(zxingGuess))
  }

  companion object {
    private val EMPTY_READER_ARRAY = arrayOfNulls<Reader>(0)
  }
}
