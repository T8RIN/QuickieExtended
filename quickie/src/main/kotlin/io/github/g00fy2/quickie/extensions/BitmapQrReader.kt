package io.github.g00fy2.quickie.extensions

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

fun Bitmap.readQrCode(
  onSuccess: (String) -> Unit,
  onFailure: (Throwable) -> Unit
) {
  val optionsBuilder = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE)

  val barcodeScanner = runCatching {
    BarcodeScanning.getClient(optionsBuilder.build())
  }.getOrNull()

  barcodeScanner?.let { scanner ->
    scanner.process(InputImage.fromBitmap(this, 0))
      .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it }?.let { onSuccess(it.rawValue ?: "") } }
      .addOnFailureListener {
        onFailure(it)
      }
  }
}