package com.andy.mytodo.android

import android.app.Application
import com.andy.mytodo.createAppContainer
import com.andy.mytodo.di.AppContainer

class TodoApplication : Application() {
    val container: AppContainer by lazy { createAppContainer(this) }
}
