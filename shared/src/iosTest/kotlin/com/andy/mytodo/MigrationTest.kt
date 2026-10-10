package com.andy.mytodo

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.andy.mytodo.data.local.SyncState
import com.andy.mytodo.data.local.TodoDatabase
import com.andy.mytodo.data.local.buildTodoDatabase
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs on iOS because Room's file-based builder needs no Android Context
 * there. Builds a database exactly like version 1 of the app did (SQL copied
 * from schemas/.../1.json), then opens it with the current Room setup. Room
 * validates the migrated schema against version 2 and throws on any mismatch.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class MigrationTest {
    @Test
    fun migrates1To2KeepingRowsAndMarkingThemDirty() = runTest {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".db"
        try {
            BundledSQLiteDriver().open(path).run {
                execSQL("CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT NOT NULL, `priority` INTEGER NOT NULL, `isDone` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER)")
                execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '87ecdbebebc2add9e6627bb851e65149')")
                execSQL("INSERT INTO todos (title, notes, priority, isDone, createdAt, updatedAt, completedAt) VALUES ('Old one', 'n', 2, 0, 1, 2, NULL)")
                execSQL("INSERT INTO todos (title, notes, priority, isDone, createdAt, updatedAt, completedAt) VALUES ('Old done', '', 1, 1, 3, 4, 5)")
                execSQL("PRAGMA user_version = 1")
                close()
            }

            val db = buildTodoDatabase(Room.databaseBuilder<TodoDatabase>(name = path))
            val rows = db.todoDao().getAll().sortedBy { it.createdAt }
            db.close()

            assertEquals(listOf("Old one", "Old done"), rows.map { it.title })
            assertTrue(rows.all { it.syncState == SyncState.Dirty })
            assertTrue(rows.all { Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(it.id) })
            assertEquals(2, rows.map { it.id }.toSet().size)
            assertEquals(listOf(false, true), rows.map { it.isDone })
            assertEquals(5L, rows[1].completedAt)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }
}
