@file:Suppress("unused")

package io.github.g00fy2.quickie

import android.Manifest.permission.CAMERA
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.g00fy2.quickie.config.ParcelableScannerConfig
import io.github.g00fy2.quickie.databinding.QuickieScannerActivityBinding
import io.github.g00fy2.quickie.extensions.parcelable
import io.github.g00fy2.quickie.extensions.parcelableArrayList
import io.github.g00fy2.quickie.extensions.readQrCodeIntent
import io.github.g00fy2.quickie.extensions.toIntent
import io.github.g00fy2.quickie.utils.MlKitErrorHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private fun ClipData.clipList() = List(
  size = itemCount,
  init = {
    getItemAt(it).uri
  }
).filterNotNull()

internal class QRScannerActivity : AppCompatActivity() {

  private val pickImageLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val intent = result.data
    val clipData = intent?.clipData

    if (clipData != null) {
      clipData.clipList().firstOrNull()
    } else {
      intent?.data ?: when (intent?.action) {
        Intent.ACTION_SEND_MULTIPLE -> {
          intent.parcelableArrayList<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
        }
        Intent.ACTION_SEND -> {
          intent.parcelable<Uri>(Intent.EXTRA_STREAM)
        }
        else -> null
      }
    }?.let { uri ->
      imageLoader.enqueue(
        ImageRequest.Builder(this)
          .data(uri)
          .size(1500, 1500)
          .allowHardware(false)
          .target { drawable ->
            drawable.toBitmap().readQrCodeIntent(
              barcodeFormats = barcodeFormats,
              onSuccess = ::onSuccess,
              onFailure = {
                if (it is Exception) onFailure(it)
                else onFailure(Exception(it))
              }
            )
          }.build()
      )
    }
  }

  private fun pickImage() {
    val intent = Intent(
      Intent.ACTION_PICK,
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    ).apply {
      type = "image/*"
      putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
    }
    pickImageLauncher.launch(
      Intent.createChooser(
        intent,
        getString(R.string.quickie_scan_qr_code)
      )
    )
  }

  private lateinit var binding: QuickieScannerActivityBinding
  private lateinit var analysisExecutor: ExecutorService
  private var barcodeFormats = intArrayOf(Barcode.FORMAT_QR_CODE)
  private var hapticFeedback = true
  private var showTorchToggle = false
  private var showCloseButton = false
  private var useFrontCamera = false
  internal var errorDialog: Dialog? = null
    set(value) {
      field = value
      value?.show()
      value?.setOnKeyListener { dialog, keyCode, _ ->
        if (keyCode == KeyEvent.KEYCODE_BACK) {
          finish()
          dialog.dismiss()
          true
        } else {
          false
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appThemeLayoutInflater = applicationInfo.theme.let { appThemeRes ->
      if (appThemeRes != 0) layoutInflater.cloneInContext(ContextThemeWrapper(this, appThemeRes)) else layoutInflater
    }
    binding = QuickieScannerActivityBinding.inflate(appThemeLayoutInflater)
    setContentView(binding.root)

    setupEdgeToEdgeUI()
    applyScannerConfig()

    analysisExecutor = Executors.newSingleThreadExecutor()

    requestCameraPermissionIfMissing { granted ->
      if (granted) {
        startCamera()
      } else {
        setResult(RESULT_MISSING_PERMISSION, null)
        finish()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    analysisExecutor.shutdown()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun startCamera() {
    val cameraProviderFuture = try {
      ProcessCameraProvider.getInstance(this)
    } catch (e: Exception) {
      onFailure(e)
      return
    }

    cameraProviderFuture.addListener({
      val cameraProvider = try {
        cameraProviderFuture.get()
      } catch (e: Exception) {
        onFailure(e)
        return@addListener
      }

      val preview = Preview.Builder().build().also { it.surfaceProvider = binding.previewView.surfaceProvider }
      val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
          ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(
              Size(1280, 720),
              ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
          ).build()
        )
        .build()
        .also {
          it.setAnalyzer(
            analysisExecutor,
            QRCodeAnalyzer(
              barcodeFormats = barcodeFormats,
              onSuccess = { barcode ->
                it.clearAnalyzer()
                onSuccess(barcode.toIntent())
              },
              onFailure = { exception -> onFailure(exception) },
              onPassCompleted = { failureOccurred -> onPassCompleted(failureOccurred) }
            )
          )
        }

      cameraProvider.unbindAll()

      val cameraSelector =
        if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

      try {
        val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
          override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentZoomRatio: Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1F

            val delta = detector.scaleFactor

            camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
            return true
          }
        }
        val scaleGestureDetector = ScaleGestureDetector(this, listener)

        binding.overlayView.setOnTouchListener { _, event ->
          scaleGestureDetector.onTouchEvent(event)
          return@setOnTouchListener true
        }

        binding.overlayView.visibility = View.VISIBLE
        binding.overlayView.setCloseVisibilityAndOnClick(showCloseButton) { finish() }
        if (showTorchToggle && camera.cameraInfo.hasFlashUnit()) {
          binding.overlayView.setTorchVisibilityAndOnClick(true) { camera.cameraControl.enableTorch(it) }
          camera.cameraInfo.torchState.observe(this) { binding.overlayView.setTorchState(it == TorchState.ON) }
        } else {
          binding.overlayView.setTorchVisibilityAndOnClick(false)
        }
        binding.overlayView.setGalleryVisibilityAndOnClick(true) { pickImage() }
      } catch (e: Exception) {
        binding.overlayView.visibility = View.INVISIBLE
        onFailure(e)
      }
    }, ContextCompat.getMainExecutor(this))
  }

  private fun onSuccess(result: Intent) {
    binding.overlayView.isHighlighted = true
    if (hapticFeedback) {
      binding.overlayView.reallyPerformHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    setResult(
      RESULT_OK,
      result
    )
    finish()
  }

  @Suppress("DEPRECATION")
  private fun View.reallyPerformHapticFeedback(feedbackConstant: Int) {
    runCatching {
      if (context.isTouchExplorationEnabled()) return

      isHapticFeedbackEnabled = true

      performHapticFeedback(feedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }
  }

  private fun Context.isTouchExplorationEnabled(): Boolean =
    getSystemService<AccessibilityManager>()?.isTouchExplorationEnabled == true

  private fun onFailure(exception: Exception) {
    setResult(RESULT_ERROR, Intent().putExtra(EXTRA_RESULT_EXCEPTION, exception))
    if (!MlKitErrorHandler.isResolvableError(this, exception)) finish()
  }

  private fun onPassCompleted(failureOccurred: Boolean) {
    if (!isFinishing) binding.overlayView.isLoading = failureOccurred
  }

  private fun setupEdgeToEdgeUI() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(binding.overlayView) { v, insets ->
      insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { v.setPadding(it.left, it.top, it.right, it.bottom) }
      WindowInsetsCompat.CONSUMED
    }
  }

  private fun applyScannerConfig() {
    intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_CONFIG, ParcelableScannerConfig::class.java) }?.let {
      barcodeFormats = it.formats
      binding.overlayView.setCustomText(it.stringRes)
      binding.overlayView.setCustomIcon(it.drawableRes)
      binding.overlayView.setHorizontalFrameRatio(it.horizontalFrameRatio)
      hapticFeedback = it.hapticFeedback
      showTorchToggle = it.showTorchToggle
      useFrontCamera = it.useFrontCamera
      showCloseButton = it.showCloseButton

      if (it.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  private fun requestCameraPermissionIfMissing(onResult: ((Boolean) -> Unit)) {
    if (ContextCompat.checkSelfPermission(this, CAMERA) == PackageManager.PERMISSION_GRANTED) {
      onResult(true)
    } else {
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }.launch(CAMERA)
    }
  }

  companion object {
    const val EXTRA_CONFIG = "quickie-config"
    const val EXTRA_RESULT_BYTES = "quickie-bytes"
    const val EXTRA_RESULT_VALUE = "quickie-value"
    const val EXTRA_RESULT_TYPE = "quickie-type"
    const val EXTRA_RESULT_PARCELABLE = "quickie-parcelable"
    const val EXTRA_RESULT_EXCEPTION = "quickie-exception"
    const val RESULT_MISSING_PERMISSION = RESULT_FIRST_USER + 1
    const val RESULT_ERROR = RESULT_FIRST_USER + 2
  }
}