package com.metashield.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.metashield.app.ui.navigation.MetaShieldNavHost
import com.metashield.app.ui.navigation.Screen
import com.metashield.app.ui.theme.MetaShieldTheme
import com.metashield.app.ui.viewmodel.MetaShieldViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as MetaShieldApp).container
        val sharedImageUri: Uri? = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else null

        setContent {
            MetaShieldTheme {
                val navController = rememberNavController()
                val vm: MetaShieldViewModel = viewModel(
                    factory = MetaShieldViewModel.factory(appContainer)
                )

                var pendingExportMime by remember { mutableStateOf("image/jpeg") }

                // 1. Native Android Photo Picker
                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri: Uri? ->
                    if (uri != null) {
                        vm.onImageUriSelected(uri) {
                            navController.navigate(Screen.ImagePreview.route)
                        }
                    }
                }

                // 2. Storage Access Framework (SAF) OpenDocument Picker (JPG, JPEG, PNG, WebP, TIFF)
                val documentPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        vm.onImageUriSelected(uri) {
                            navController.navigate(Screen.ImagePreview.route)
                        }
                    }
                }

                // 3. Storage Access Framework (SAF) CreateDocument Exporter
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("*/*")
                ) { destUri: Uri? ->
                    if (destUri != null) {
                        vm.exportToCustomUri(destUri)
                    }
                }

                // Handle incoming SEND intent if user shared an image to MetaShield
                LaunchedEffect(sharedImageUri) {
                    if (sharedImageUri != null) {
                        vm.onImageUriSelected(sharedImageUri) {
                            navController.navigate(Screen.ImagePreview.route)
                        }
                    }
                }

                MetaShieldNavHost(
                    navController = navController,
                    viewModel = vm,
                    onLaunchPhotoPicker = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onLaunchDocumentPicker = {
                        documentPickerLauncher.launch(
                            arrayOf("image/jpeg", "image/png", "image/webp", "image/tiff", "image/*")
                        )
                    },
                    onLaunchCreateDocument = { suggestedName, mimeType ->
                        pendingExportMime = mimeType
                        createDocumentLauncher.launch(suggestedName)
                    },
                    onShareFile = { filePath, mimeType ->
                        shareCleanedFile(filePath, mimeType)
                    }
                )
            }
        }
    }

    private fun shareCleanedFile(filePath: String, mimeType: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val contentUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Sanitized Image"))
    }
}
