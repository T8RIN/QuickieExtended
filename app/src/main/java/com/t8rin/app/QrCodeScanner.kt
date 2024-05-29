/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2024 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.app

/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2024 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.zxing.BarcodeFormat
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig

class QrCodeScanner internal constructor(
  private val scannerLauncher: ManagedActivityResultLauncher<ScannerConfig, QRResult>
) {

  fun scan() {
    val config = ScannerConfig.build {
      setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
      setHapticSuccessFeedback(true)
      setShowTorchToggle(true)
      setShowCloseButton(true)
      setKeepScreenOn(true)
    }

    scannerLauncher.launch(config)
  }

}


@Composable
fun rememberQrCodeScanner(
  onSuccess: (String) -> Unit
): QrCodeScanner {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current as ComponentActivity

  val scannerLauncher = rememberLauncherForActivityResult(ScanCustomCode()) { result ->
    when (result) {
      is QRResult.QRError -> {

      }

      QRResult.QRMissingPermission -> {

      }

      is QRResult.QRSuccess -> {
        onSuccess(result.content.rawValue ?: "")
      }

      QRResult.QRUserCanceled -> Unit
    }
  }


  return remember(scannerLauncher) {
    QrCodeScanner(scannerLauncher)
  }
}