package io.github.g00fy2.quickie.extensions

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.content.QRContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Bitmap.readQrCode(
  barcodeFormats: IntArray,
  onSuccess: (QRContent) -> Unit,
  onFailure: (Throwable) -> Unit
) {
  readQrCodeIntent(
    barcodeFormats = barcodeFormats,
    onSuccess = { onSuccess(it.toQuickieContentType()) },
    onFailure = onFailure
  )
}

fun Bitmap.readQrCodeIntent(
  barcodeFormats: IntArray,
  onSuccess: (Intent) -> Unit,
  onFailure: (Throwable) -> Unit
) {
  CoroutineScope(Dispatchers.Default).launch {
    val reader = MultiFormatReader().apply {
      setHints(
        mapOf(
          DecodeHintType.CHARACTER_SET to Charsets.UTF_8,
          DecodeHintType.TRY_HARDER to true,
          DecodeHintType.POSSIBLE_FORMATS to barcodeFormats.toList().mapNotNull { BarcodeFormat.entries[it].value }
        )
      )
    }

    val targetBitmap = createBitmap(2000, 2000).apply {
      setHasAlpha(true)
    }.applyCanvas {
      val bitmap = this@readQrCodeIntent
      val left = (width - bitmap.width) / 2f
      val top = (height - bitmap.height) / 2f
      drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
      drawColor(Color.BLACK)
      drawBitmap(
        this@readQrCodeIntent,
        null,
        RectF(
          left,
          top,
          bitmap.width + left,
          bitmap.height + top
        ),
        null
      )
    }.run { QrProcessor.process(this) }

    targetBitmap.apply {
      runCatching {
        val intArray = IntArray(width * height)
        getPixels(intArray, 0, width, 0, 0, width, height)
        val source: LuminanceSource = RGBLuminanceSource(width, height, intArray)
        val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))

        onSuccess(result.toIntent())
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

          onSuccess(result.toIntent())
        }
      }
    }
  }
}

fun interface QrProcessor {
  fun process(bitmap: Bitmap): Bitmap

  companion object : QrProcessor {
    private var processImpl: (Bitmap) -> Bitmap = { it }

    fun setProcessor(
      processor: (Bitmap) -> Bitmap
    ) {
      processImpl = processor
    }

    override fun process(bitmap: Bitmap): Bitmap = processImpl(bitmap)
  }
}