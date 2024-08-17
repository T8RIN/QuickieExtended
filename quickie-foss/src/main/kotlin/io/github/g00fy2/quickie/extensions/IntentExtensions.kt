package io.github.g00fy2.quickie.extensions

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import androidx.core.content.IntentCompat
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_BYTES
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_EXCEPTION
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_VALUE
import io.github.g00fy2.quickie.content.QRContent
import io.github.g00fy2.quickie.content.QRContent.Plain

internal fun Intent?.toQuickieContentType(): QRContent {
  val rawBytes = this?.getByteArrayExtra(EXTRA_RESULT_BYTES)
  val rawValue = this?.getStringExtra(EXTRA_RESULT_VALUE)
  return Plain(rawBytes, rawValue)
}

internal fun Intent?.getRootException(): Exception {
  return this?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_EXCEPTION, Exception::class.java) }
    ?: IllegalStateException("Could retrieve root exception")
}

inline fun <reified T : Parcelable> Intent.parcelable(
  key: String
): T? = runCatching {
  when {
    Build.VERSION.SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
  }
}.getOrNull()

inline fun <reified T : Parcelable> Intent.parcelableArrayList(
  key: String
): ArrayList<T>? = runCatching {
  when {
    Build.VERSION.SDK_INT >= 33 -> getParcelableArrayListExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayListExtra(key)
  }
}.getOrNull()