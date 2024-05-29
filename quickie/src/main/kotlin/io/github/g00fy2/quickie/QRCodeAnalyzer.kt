package io.github.g00fy2.quickie

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Paint
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.inv


internal class QRCodeAnalyzer(
  private val barcodeFormats: IntArray = IntArray(1) { BarcodeFormat.QR_CODE.ordinal },
  private val onSuccess: ((String) -> Unit),
  private val onFailure: ((Throwable) -> Unit),
  private val onPassCompleted: ((Boolean) -> Unit)
) : ImageAnalysis.Analyzer {

  @Volatile
  private var failureOccurred = false
  private var failureTimestamp = 0L

  private val isScanning = AtomicBoolean(false)

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    if (imageProxy.image == null) return

    // throttle analysis if error occurred in previous pass
    if (failureOccurred && System.currentTimeMillis() - failureTimestamp < 1000L) {
      imageProxy.close()
      return
    }

    isScanning.set(true)

    reader.apply {
      setHints(
        mapOf(
          DecodeHintType.CHARACTER_SET to Charsets.UTF_8,
          DecodeHintType.TRY_HARDER to true,
          DecodeHintType.POSSIBLE_FORMATS to barcodeFormats.map { BarcodeFormat.entries[it] }
        )
      )
    }

    if ((imageProxy.format == ImageFormat.YUV_420_888 || imageProxy.format == ImageFormat.YUV_422_888
        || imageProxy.format == ImageFormat.YUV_444_888) && imageProxy.planes.size == 3
    ) {
      val rotatedImage = RotatedImage(getLuminancePlaneData(imageProxy), imageProxy.width, imageProxy.height)
      rotateImageArray(rotatedImage, imageProxy.imageInfo.rotationDegrees)

      try {
        runCatching {
          val planarYUVLuminanceSource = PlanarYUVLuminanceSource(
            rotatedImage.byteArray,
            rotatedImage.width,
            rotatedImage.height,
            0, 0,
            rotatedImage.width,
            rotatedImage.height,
            false
          )
          val hybridBinarizer = HybridBinarizer(planarYUVLuminanceSource)
          val binaryBitmap = BinaryBitmap(hybridBinarizer)
          val rawResult = reader.decodeWithState(binaryBitmap)
          onSuccess(rawResult.text)

          onPassCompleted(failureOccurred)
          imageProxy.close()
        }.onFailure {
          if (it !is NotFoundException) throw it
          else {
            val data = rotatedImage.byteArray
            for (y in data.indices) {
              data[y] = data[y].inv()
            }
            val planarYUVLuminanceSource = PlanarYUVLuminanceSource(
              data,
              rotatedImage.width,
              rotatedImage.height,
              0, 0,
              rotatedImage.width,
              rotatedImage.height,
              false
            )
            val hybridBinarizer = HybridBinarizer(planarYUVLuminanceSource)
            val binaryBitmap = BinaryBitmap(hybridBinarizer)
            val rawResult = reader.decodeWithState(binaryBitmap)
            onSuccess(rawResult.text)

            onPassCompleted(failureOccurred)
            imageProxy.close()
          }
        }
      } catch (e: Throwable) {
        if (e !is NotFoundException) {
          failureOccurred = true
          failureTimestamp = System.currentTimeMillis()
          onFailure(e)
        }
        e.printStackTrace()
      } finally {
        reader.reset()
        imageProxy.close()
      }

      isScanning.set(false)
    }
  }

  // 90, 180. 270 rotation
  private fun rotateImageArray(imageToRotate: RotatedImage, rotationDegrees: Int) {
    if (rotationDegrees == 0) return // no rotation
    if (rotationDegrees % 90 != 0) return // only 90 degree times rotations

    val width = imageToRotate.width
    val height = imageToRotate.height

    val rotatedData = ByteArray(imageToRotate.byteArray.size)
    for (y in 0 until height) { // we scan the array by rows
      for (x in 0 until width) {
        when (rotationDegrees) {
          90 -> rotatedData[x * height + height - y - 1] =
            imageToRotate.byteArray[x + y * width] // Fill from top-right toward left (CW)
          180 -> rotatedData[width * (height - y - 1) + width - x - 1] =
            imageToRotate.byteArray[x + y * width] // Fill from bottom-right toward up (CW)
          270 -> rotatedData[y + x * height] =
            imageToRotate.byteArray[y * width + width - x - 1] // The opposite (CCW) of 90 degrees
        }
      }
    }

    imageToRotate.byteArray = rotatedData

    if (rotationDegrees != 180) {
      imageToRotate.height = width
      imageToRotate.width = height
    }
  }

  /**
   * IMPORTANT: There's a known issue with the combination of CameraX and Zxing in some phones,
   * especially OnePlus phones, where zxing doesn't detect any QR/Barcodes at all.
   *
   * To resolve that issue, we're required to cleanup the image data with this fix method
   * @see <a href="https://github.com/beemdevelopment/Aegis/commit/fb58c877d1b305b1c66db497880da5651dda78d7">Aegis Authenticator Github Commit</a>
   *
   * @param image imageProxy from camera analyzer
   * @return cleaned image bytearray
   */
  private fun getLuminancePlaneData(image: ImageProxy): ByteArray {
    val plane = image.planes[0]
    val buf: ByteBuffer = plane.buffer
    val data = ByteArray(buf.remaining())
    buf.get(data)
    buf.rewind()
    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    // remove padding from the Y plane data
    val cleanData = ByteArray(width * height)
    for (y in 0 until height) {
      for (x in 0 until width) {
        cleanData[y * width + x] = data[y * rowStride + x * pixelStride]
      }
    }
    return cleanData
  }


  companion object {
    val reader = MultiFormatReader()
  }
}

private fun invert(src: Bitmap): Bitmap {
  val height = src.height
  val width = src.width

  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  val paint = Paint()

  val matrixGrayscale = ColorMatrix()
  matrixGrayscale.setSaturation(0f)

  val matrixInvert = ColorMatrix()
  matrixInvert.set(
    floatArrayOf(
      -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
      0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
      0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
      0.0f, 0.0f, 0.0f, 1.0f, 0.0f
    )
  )
  matrixInvert.preConcat(matrixGrayscale)

  val filter = ColorMatrixColorFilter(matrixInvert)
  paint.setColorFilter(filter)

  canvas.drawBitmap(src, 0f, 0f, paint)
  return bitmap
}

private data class RotatedImage(var byteArray: ByteArray, var width: Int, var height: Int) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RotatedImage

    if (!byteArray.contentEquals(other.byteArray)) return false
    if (width != other.width) return false
    if (height != other.height) return false

    return true
  }

  override fun hashCode(): Int {
    var result = byteArray.contentHashCode()
    result = 31 * result + width
    result = 31 * result + height
    return result
  }
}