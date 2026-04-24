package com.example.eyes

import android.app.Application
import com.example.eyes.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class EyesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@EyesApp)
            modules(appModule)
        }
    }
}
