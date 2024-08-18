package io.github.g00fy2.quickie.extensions

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.core.graphics.applyCanvas
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Bitmap.readQrCode(
  onSuccess: (String) -> Unit,
  onFailure: (Throwable) -> Unit
) {
  CoroutineScope(Dispatchers.Default).launch {
    val optionsBuilder = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE)

    val barcodeScanner = runCatching {
      BarcodeScanning.getClient(optionsBuilder.build())
    }.getOrNull()

    val targetBitmap = Bitmap.createBitmap(
      2000,
      2000,
      Bitmap.Config.ARGB_8888
    ).apply {
      setHasAlpha(true)
    }.applyCanvas {
      val bitmap = this@readQrCode
      val left = (width - bitmap.width) / 2f
      val top = (height - bitmap.height) / 2f
      drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
      drawColor(Color.BLACK)
      drawBitmap(
        this@readQrCode,
        null,
        RectF(
          left,
          top,
          bitmap.width + left,
          bitmap.height + top
        ),
        null
      )
    }

    barcodeScanner?.let { scanner ->
      scanner.process(InputImage.fromBitmap(targetBitmap, 0))
        .addOnSuccessListener { codes ->
          codes.firstNotNullOfOrNull { it }?.let {
            onSuccess(it.rawValue ?: "")
          }
        }
        .addOnFailureListener {
          onFailure(it)
        }
    }
  }
}