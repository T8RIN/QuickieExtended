package io.github.g00fy2.quickie.extensions

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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

internal fun Bitmap.readQrCodeIntent(
  barcodeFormats: IntArray,
  onSuccess: (Intent) -> Unit,
  onFailure: (Throwable) -> Unit
) {
  CoroutineScope(Dispatchers.Default).launch {
    val optionsBuilder = if (barcodeFormats.size > 1) {
      BarcodeScannerOptions.Builder().setBarcodeFormats(barcodeFormats.first(), *barcodeFormats.drop(1).toIntArray())
    } else {
      BarcodeScannerOptions.Builder().setBarcodeFormats(barcodeFormats.firstOrNull() ?: Barcode.FORMAT_UNKNOWN)
    }

    val barcodeScanner = runCatching {
      BarcodeScanning.getClient(optionsBuilder.build())
    }.getOrNull()

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

    barcodeScanner?.let { scanner ->
      scanner.process(InputImage.fromBitmap(targetBitmap, 0))
        .addOnSuccessListener { codes ->
          codes.firstNotNullOfOrNull { it }?.let {
            onSuccess(it.toIntent())
          }
        }
        .addOnFailureListener {
          onFailure(it)
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