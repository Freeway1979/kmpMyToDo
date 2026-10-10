package com.andy.mytodo.android

import android.app.Application
import com.andy.mytodo.createAppContainer
import com.andy.mytodo.di.AppConfig
import com.andy.mytodo.di.AppContainer

class TodoApplication : Application() {
    val container: AppContainer by lazy {
        createAppContainer(
            context = this,
            config = AppConfig(apiBaseUrl = BuildConfig.API_BASE_URL, enableNetworkLogging = BuildConfig.DEBUG),
        )
    }
}
