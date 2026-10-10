package com.andy.mytodo

import android.content.Context
import androidx.room.Room
import com.andy.mytodo.data.local.TodoDatabase
import com.andy.mytodo.data.local.buildTodoDatabase
import com.andy.mytodo.di.AppConfig
import com.andy.mytodo.di.AppContainer
import io.ktor.client.engine.okhttp.OkHttp

fun createAppContainer(context: Context, config: AppConfig): AppContainer {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(TodoDatabase.FILE_NAME)
    val builder = Room.databaseBuilder<TodoDatabase>(appContext, dbFile.absolutePath)
    return AppContainer(buildTodoDatabase(builder), OkHttp.create(), config)
}
