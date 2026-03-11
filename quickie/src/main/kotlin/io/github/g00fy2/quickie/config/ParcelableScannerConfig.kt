package io.github.g00fy2.quickie.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal class ParcelableScannerConfig(
  val formats: IntArray,
  val stringRes: Int,
  val drawableRes: Int?,
  val hapticFeedback: Boolean,
  val showTorchToggle: Boolean,
  val horizontalFrameRatio: Float,
  val useFrontCamera: Boolean,
  val showCloseButton: Boolean,
  val keepScreenOn: Boolean,
  val buttonsTint: Int?,
  val buttonsBackground: Int?,
  val frameHighlighted: Int?,
  val frame: Int?,
  val topIcon: Int?,
  val topText: Int?,
) : Parcelable

internal fun ScannerConfig.toParcelableConfig() =
  ParcelableScannerConfig(
    formats = formats,
    stringRes = stringRes,
    drawableRes = drawableRes,
    hapticFeedback = hapticFeedback,
    showTorchToggle = showTorchToggle,
    horizontalFrameRatio = horizontalFrameRatio,
    useFrontCamera = useFrontCamera,
    showCloseButton = showCloseButton,
    keepScreenOn = keepScreenOn,
    buttonsTint = buttonsTint,
    buttonsBackground = buttonsBackground,
    frameHighlighted = frameHighlighted,
    frame = frame,
    topIcon = topIcon,
    topText = topText,
  )