package io.github.g00fy2.quickie.extensions

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


fun Bitmap.readQrCode(
  onSuccess: (String) -> Unit,
  onFailure: (Throwable) -> Unit
) {
  CoroutineScope(Dispatchers.Default).launch {
    val reader = QRCodeReader()

    runCatching {
      val intArray = IntArray(width * height)
      getPixels(intArray, 0, width, 0, 0, width, height)
      val source: LuminanceSource = RGBLuminanceSource(width, height, intArray)
      val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))

      onSuccess(result.text)
    }.getOrElse {
      if (it !is NotFoundException) onFailure(it)
      else {
        val intArray = IntArray(width * height)
        getPixels(intArray, 0, width, 0, 0, width, height)
        for (y in intArray.indices) {
          intArray[y] = intArray[y].inv()
        }
        val source: LuminanceSource = RGBLuminanceSource(width, height, intArray)
        val result = runCatching {
          reader.decode(BinaryBitmap(HybridBinarizer(source)))
        }.onFailure(onFailure).getOrNull() ?: return@launch

        onSuccess(result.text)
      }
    }
  }
}