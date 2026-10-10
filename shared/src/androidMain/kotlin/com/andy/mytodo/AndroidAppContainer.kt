package com.andy.mytodo

import android.content.Context
import androidx.room.Room
import com.andy.mytodo.data.TodoDatabase
import com.andy.mytodo.data.buildTodoDatabase
import com.andy.mytodo.di.AppContainer

fun createAppContainer(context: Context): AppContainer {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(TodoDatabase.FILE_NAME)
    val builder = Room.databaseBuilder<TodoDatabase>(appContext, dbFile.absolutePath)
    return AppContainer(buildTodoDatabase(builder))
}
