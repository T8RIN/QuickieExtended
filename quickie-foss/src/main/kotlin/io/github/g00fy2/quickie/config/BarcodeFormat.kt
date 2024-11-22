package io.github.g00fy2.quickie.config

import com.google.zxing.BarcodeFormat as BarcodeFormatImpl

/**
 * Wrapper class to access the ML Kit BarcodeFormat constants.
 */
enum class BarcodeFormat(val value: BarcodeFormatImpl?) {
  ALL_FORMATS(null),
  CODE_128(BarcodeFormatImpl.CODE_128),
  CODE_39(BarcodeFormatImpl.CODE_39),
  CODE_93(BarcodeFormatImpl.CODE_93),
  CODABAR(BarcodeFormatImpl.CODABAR),
  DATA_MATRIX(BarcodeFormatImpl.DATA_MATRIX),
  EAN_13(BarcodeFormatImpl.EAN_13),
  EAN_8(BarcodeFormatImpl.EAN_8),
  ITF(BarcodeFormatImpl.ITF),
  QR_CODE(BarcodeFormatImpl.QR_CODE),
  UPC_A(BarcodeFormatImpl.UPC_A),
  UPC_E(BarcodeFormatImpl.UPC_E),
  PDF417(BarcodeFormatImpl.PDF_417),
  AZTEC(BarcodeFormatImpl.AZTEC)
}