package com.metashield.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.metashield.app.ui.screens.*
import com.metashield.app.ui.viewmodel.MetaShieldViewModel

@Composable
fun MetaShieldNavHost(
    navController: NavHostController,
    viewModel: MetaShieldViewModel,
    onLaunchPhotoPicker: () -> Unit,
    onLaunchDocumentPicker: () -> Unit,
    onLaunchCreateDocument: (suggestedName: String, mimeType: String) -> Unit,
    onShareFile: (filePath: String, mimeType: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentOps by viewModel.recentOperations.collectAsState()
    val defaultOpts by viewModel.defaultOptions.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Screen.HomeDashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.HomeDashboard.route) {
            HomeDashboardScreen(
                currentReport = uiState.currentScanReport,
                recentOperations = recentOps,
                onSelectImageClick = { navController.navigate(Screen.ImageSelection.route) },
                onScanImageClick = { navController.navigate(Screen.MetadataScanner.route) },
                onCleanMetadataClick = { navController.navigate(Screen.MetadataCleaning.route) },
                onInspectC2paClick = { navController.navigate(Screen.C2paInspection.route) },
                onPreviewImageClick = { navController.navigate(Screen.ImagePreview.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenAbout = { navController.navigate(Screen.About.route) }
            )
        }

        composable(Screen.ImageSelection.route) {
            ImageSelectionScreen(
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onLaunchPhotoPicker = onLaunchPhotoPicker,
                onLaunchDocumentPicker = onLaunchDocumentPicker,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImagePreview.route) {
            ImagePreviewScreen(
                scanReport = uiState.currentScanReport,
                onOpenMetadataScanner = { navController.navigate(Screen.MetadataScanner.route) },
                onOpenC2paInspection = { navController.navigate(Screen.C2paInspection.route) },
                onOpenMetadataCleaning = { navController.navigate(Screen.MetadataCleaning.route) },
                onSelectDifferentImage = { navController.navigate(Screen.ImageSelection.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MetadataScanner.route) {
            MetadataScannerScreen(
                scanReport = uiState.currentScanReport,
                onOpenC2paInspection = { navController.navigate(Screen.C2paInspection.route) },
                onOpenCleaning = { navController.navigate(Screen.MetadataCleaning.route) },
                onRescan = { viewModel.rescanCurrentImage() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.C2paInspection.route) {
            C2paInspectionScreen(
                c2paReport = uiState.currentScanReport?.c2paReport,
                onProceedToClean = { navController.navigate(Screen.MetadataCleaning.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MetadataCleaning.route) {
            MetadataCleaningScreen(
                scanReport = uiState.currentScanReport,
                options = uiState.cleaningOptions,
                onUpdateOptions = { viewModel.updateCleaningOptions(it) },
                onStartCleaning = {
                    navController.navigate(Screen.Processing.route)
                    viewModel.runMetadataCleaning {
                        navController.navigate(Screen.BeforeAfterResults.route) {
                            popUpTo(Screen.Processing.route) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Processing.route) {
            ProcessingScreen(
                stageIndex = uiState.processingStageIndex,
                stageName = uiState.processingStageName,
                progressFraction = uiState.processingProgress
            )
        }

        composable(Screen.BeforeAfterResults.route) {
            BeforeAfterResultsScreen(
                result = uiState.lastCleaningResult,
                onProceedToExport = { navController.navigate(Screen.ExportSave.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ExportSave.route) {
            ExportSaveScreen(
                result = uiState.lastCleaningResult,
                exportStatusMessage = uiState.lastExportMessage,
                onSaveToCustomDocumentLocation = onLaunchCreateDocument,
                onSaveToGallery = { viewModel.exportToDeviceGallery() },
                onShareFile = onShareFile,
                onReturnHome = {
                    navController.navigate(Screen.HomeDashboard.route) {
                        popUpTo(Screen.HomeDashboard.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                defaultOptions = defaultOpts,
                onUpdateDefaults = { viewModel.saveDefaultCleaningOptions(it) },
                onClearHistory = { viewModel.clearHistory() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutMetaShieldScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
