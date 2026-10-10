package com.andy.mytodo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2: integer ids become client-generated UUIDs, and rows get a sync
 * state. Existing rows were never uploaded, so they start as Dirty.
 *
 * SQLite can't change a primary key's type in place, so the table is rebuilt.
 * The resulting schema must match `schemas/.../2.json` exactly; Room
 * validates it on open (see MigrationTest).
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE todos_new (
                id TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL,
                priority INTEGER NOT NULL, isDone INTEGER NOT NULL,
                createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, completedAt INTEGER,
                syncState TEXT NOT NULL DEFAULT 'Dirty',
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        // Random version-4 UUID built in SQL.
        val uuid = "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
            "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
            "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"
        connection.execSQL(
            """
            INSERT INTO todos_new (id, title, notes, priority, isDone, createdAt, updatedAt, completedAt, syncState)
            SELECT $uuid, title, notes, priority, isDone, createdAt, updatedAt, completedAt, 'Dirty' FROM todos
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE todos")
        connection.execSQL("ALTER TABLE todos_new RENAME TO todos")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_todos_syncState ON todos (syncState)")
    }
}

internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
