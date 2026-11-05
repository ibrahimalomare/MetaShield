package com.metashield.app

import android.app.Application
import com.metashield.app.di.AppContainer

class MetaShieldApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
