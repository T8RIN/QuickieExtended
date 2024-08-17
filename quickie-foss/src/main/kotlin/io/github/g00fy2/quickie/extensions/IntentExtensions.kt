package io.github.g00fy2.quickie.extensions

import android.content.Intent
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