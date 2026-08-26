package com.tubetune.downloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tubetune.downloader.data.Prefs
import com.tubetune.downloader.ui.AppRoot
import com.tubetune.downloader.ui.theme.TubeTuneTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeOverride by remember { mutableStateOf<String?>(null) }
            TubeTuneTheme(
                darkThemeOverride = when (themeOverride) {
                    "dark" -> true
                    "light" -> false
                    else -> null
                }
            ) {
                StoragePermission()
                CrashReportDialog()
                AppRoot(vm = vm, onThemeChange = { t -> themeOverride = t })
                LaunchedEffect(Unit) {
                    vm.handleSharedText(intent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 從背景回到前景：同步播放器狀態、確保控制有效
        try {
            vm.resync()
        } catch (t: Throwable) {
        }
    }
}

@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    val crashFile = remember { File(context.filesDir, "crash.log") }
    if (crashFile.exists()) {
        val content = crashFile.readText().take(1600)
        AlertDialog(
            onDismissRequest = { crashFile.delete() },
            title = { Text("上次發生錯誤（請回報這段內容）") },
            text = { Text(content, fontSize = 10.sp) },
            confirmButton = {
                TextButton(onClick = { crashFile.delete() }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun StoragePermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                context.applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        // 背景播放的通知列控制（Android 13+）
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context.applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
