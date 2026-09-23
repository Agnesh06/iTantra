package com.itantra.app

import android.app.Application
import com.itantra.app.di.AppContainer
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import kotlinx.coroutines.launch

class iTantraApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("iTantraApp", "Initializing iTantra application", category = LogCategory.APP)

        container = AppContainer(this)

        container.appScope.launch {
            container.modelManager.initializeDefaultPacks()
            try {
                container.transport.bindSocket()
            } catch (e: Exception) {
                AppLogger.e("iTantraApp", "Error binding UDP socket on startup: ${e.message}", e, category = LogCategory.NETWORK)
            }
        }
    }
}
