package io.github.g00fy2.quickie.extensions

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.github.g00fy2.quickie.QRCodeAnalyzer.Companion.reader
import java.io.ByteArrayOutputStream
import kotlin.experimental.inv

fun Bitmap.readQrCode(): String {

  val byteArray = ByteArrayOutputStream().use {
    compress(Bitmap.CompressFormat.JPEG, 100, it)

    it.toByteArray()
  }

  return runCatching {
    val planarYUVLuminanceSource = PlanarYUVLuminanceSource(
      byteArray,
      width,
      height,
      0, 0,
      width,
      height,
      false
    )
    val hybridBinarizer = HybridBinarizer(planarYUVLuminanceSource)
    val binaryBitmap = BinaryBitmap(hybridBinarizer)
    val rawResult = reader.decodeWithState(binaryBitmap)

    rawResult.text
  }.getOrElse {
    if (it !is NotFoundException) throw it
    else {
      val data = byteArray.clone()
      for (y in data.indices) {
        data[y] = data[y].inv()
      }
      val planarYUVLuminanceSource = PlanarYUVLuminanceSource(
        data,
        width,
        height,
        0, 0,
        width,
        height,
        false
      )
      val hybridBinarizer = HybridBinarizer(planarYUVLuminanceSource)
      val binaryBitmap = BinaryBitmap(hybridBinarizer)
      val rawResult = reader.decodeWithState(binaryBitmap)

      rawResult.text
    }
  }
}