package com.metashield.app.di

import android.content.Context
import com.metashield.app.data.cleaner.MetadataCleaningEngine
import com.metashield.app.data.cleaner.PostCleaningVerifier
import com.metashield.app.data.parser.MetadataScannerEngine
import com.metashield.app.data.repository.MetaShieldRepositoryImpl
import com.metashield.app.data.storage.PreferencesAndHistoryStore
import com.metashield.app.data.storage.SafFileHandler
import com.metashield.app.domain.repository.MetaShieldRepository
import com.metashield.app.domain.usecase.CleanMetadataUseCase
import com.metashield.app.domain.usecase.InspectImageUseCase

class AppContainer(context: Context) {
    val safFileHandler = SafFileHandler(context.applicationContext)
    val scannerEngine = MetadataScannerEngine()
    val postVerifier = PostCleaningVerifier(scannerEngine)
    val cleaningEngine = MetadataCleaningEngine(scannerEngine, postVerifier)
    val preferencesStore = PreferencesAndHistoryStore(context.applicationContext)

    val repository: MetaShieldRepository = MetaShieldRepositoryImpl(
        safFileHandler = safFileHandler,
        scannerEngine = scannerEngine,
        cleaningEngine = cleaningEngine,
        preferencesStore = preferencesStore
    )

    val inspectImageUseCase = InspectImageUseCase(repository)
    val cleanMetadataUseCase = CleanMetadataUseCase(repository)
}
