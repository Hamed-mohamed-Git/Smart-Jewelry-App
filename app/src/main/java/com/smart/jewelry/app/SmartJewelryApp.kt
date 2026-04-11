package com.smart.jewelry.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

@KoinApplication
@ComponentScan("com.smart.jewelry.app")
class SmartJewelryKoinApp

class SmartJewelryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin<SmartJewelryKoinApp> {
            androidLogger()
            androidContext(this@SmartJewelryApp)
        }
    }
}
