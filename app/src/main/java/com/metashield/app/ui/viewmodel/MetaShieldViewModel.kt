package com.metashield.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metashield.app.di.AppContainer
import com.metashield.app.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MetaShieldUiState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val importedImage: ImportedImageInfo? = null,
    val currentScanReport: MetadataScanReport? = null,
    val cleaningOptions: CleaningOptions = CleaningOptions(),
    val isProcessingCleaning: Boolean = false,
    val processingStageIndex: Int = 1,
    val processingStageName: String = "Preparing...",
    val processingProgress: Float = 0f,
    val lastCleaningResult: CleaningAndVerificationResult? = null,
    val lastExportMessage: String? = null
)

class MetaShieldViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetaShieldUiState())
    val uiState: StateFlow<MetaShieldUiState> = _uiState.asStateFlow()

    val recentOperations: StateFlow<List<RecentOperation>> = container.repository
        .getRecentOperations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultOptions: StateFlow<CleaningOptions> = container.repository
        .getDefaultCleaningOptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CleaningOptions())

    init {
        viewModelScope.launch {
            defaultOptions.collect { defaults ->
                if (_uiState.value.currentScanReport == null) {
                    _uiState.update { it.copy(cleaningOptions = defaults) }
                }
            }
        }
    }

    fun onImageUriSelected(uri: Uri, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "Importing image into local sandbox & scanning embedded metadata...",
                    lastCleaningResult = null,
                    lastExportMessage = null
                )
            }
            val result = container.inspectImageUseCase.importAndScan(uri)
            result.onSuccess { report ->
                val defaults = defaultOptions.value
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        importedImage = report.imageInfo,
                        currentScanReport = report,
                        cleaningOptions = defaults.copy(userAuthorizedProvenanceRemoval = false)
                    )
                }
                onComplete()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = "Import failed: ${err.message ?: "Unable to read selected file"}"
                    )
                }
            }
        }
    }

    fun rescanCurrentImage() {
        val currentImg = _uiState.value.importedImage ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Re-scanning container segments & JUMBF boxes...") }
            container.inspectImageUseCase.rescanExisting(currentImg)
                .onSuccess { report ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = null, currentScanReport = report) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = null, errorMessage = err.message) }
                }
        }
    }

    fun updateCleaningOptions(transform: (CleaningOptions) -> CleaningOptions) {
        _uiState.update { state ->
            state.copy(cleaningOptions = transform(state.cleaningOptions))
        }
    }

    fun runMetadataCleaning(onFinished: () -> Unit) {
        val report = _uiState.value.currentScanReport ?: return
        val options = _uiState.value.cleaningOptions
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingCleaning = true,
                    processingStageIndex = 1,
                    processingStageName = "Validating container header",
                    processingProgress = 0.1f,
                    errorMessage = null
                )
            }
            val res = container.cleanMetadataUseCase(
                scanReport = report,
                options = options
            ) { stageIdx, stageName, fraction ->
                _uiState.update { state ->
                    state.copy(
                        processingStageIndex = stageIdx,
                        processingStageName = stageName,
                        processingProgress = fraction
                    )
                }
            }
            res.onSuccess { cleanResult ->
                _uiState.update {
                    it.copy(
                        isProcessingCleaning = false,
                        lastCleaningResult = cleanResult,
                        errorMessage = cleanResult.errorReason
                    )
                }
                onFinished()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessingCleaning = false,
                        errorMessage = err.message ?: "Metadata cleaning failed."
                    )
                }
            }
        }
    }

    fun exportToCustomUri(destinationUri: Uri) {
        val path = _uiState.value.lastCleaningResult?.outputFilePath ?: return
        viewModelScope.launch {
            container.repository.exportCleanedImageToUri(path, destinationUri)
                .onSuccess { bytesWritten ->
                    _uiState.update {
                        it.copy(lastExportMessage = "Saved cleaned copy (${ImportedImageInfo.formatByteCount(bytesWritten)}) to chosen destination. Original file preserved.")
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = "Export failed: ${err.message}")
                    }
                }
        }
    }

    fun exportToDeviceGallery() {
        val res = _uiState.value.lastCleaningResult ?: return
        val path = res.outputFilePath ?: return
        val name = res.outputFileName ?: "metashield_clean.jpg"
        val mime = res.beforeReport.imageInfo.format.mimeType
        viewModelScope.launch {
            container.repository.exportCleanedImageToGallery(path, name, mime)
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(lastExportMessage = "Saved '$name' to Pictures/MetaShield ($uri). Original file preserved.")
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = "Gallery save failed: ${err.message}")
                    }
                }
        }
    }

    fun saveDefaultCleaningOptions(options: CleaningOptions) {
        viewModelScope.launch {
            container.repository.updateDefaultCleaningOptions(options)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.repository.clearRecentOperations()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null, lastExportMessage = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MetaShieldViewModel(container) as T
                }
            }
    }
}
