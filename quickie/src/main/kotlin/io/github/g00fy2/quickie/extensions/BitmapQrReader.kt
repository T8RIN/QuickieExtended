package io.github.g00fy2.quickie.extensions

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.experimental.inv

fun Bitmap.readQrCode(
  onSuccess: (String) -> Unit,
  onFailure: (Throwable) -> Unit
) {
  CoroutineScope(Dispatchers.Default).launch {
    val byteArray = ByteArrayOutputStream().use {
      compress(Bitmap.CompressFormat.JPEG, 100, it)

      it.toByteArray()
    }
    val reader = MultiFormatReader().apply {
      setHints(
        mapOf(
          DecodeHintType.CHARACTER_SET to Charsets.UTF_8,
          DecodeHintType.TRY_HARDER to true,
          DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
      )
    }

    runCatching {
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

      onSuccess(rawResult.text)
    }.getOrElse {
      if (it !is NotFoundException) onFailure(it)
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
        val rawResult = runCatching {
          reader.decodeWithState(binaryBitmap)
        }.onFailure(onFailure).getOrNull() ?: return@launch

        onSuccess(rawResult.text)
      }
    }
  }
}