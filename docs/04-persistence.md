# 4. Persistence (Room KMP)

Room has supported Kotlin Multiplatform since 2.7. The entity, DAO and
database are written once in `commonMain`.

## Setup

[`TodoDatabase.kt`](../shared/src/commonMain/kotlin/com/andy/mytodo/data/local/TodoDatabase.kt):

```kotlin
@Database(entities = [TodoEntity::class], version = 2)
@ConstructedBy(TodoDatabaseConstructor::class)
abstract class TodoDatabase : RoomDatabase() { … }

@Suppress("KotlinNoActualForExpect")
expect object TodoDatabaseConstructor : RoomDatabaseConstructor<TodoDatabase>  // Room generates the actuals

fun buildTodoDatabase(builder: RoomDatabase.Builder<TodoDatabase>) = builder
    .addMigrations(*ALL_MIGRATIONS)
    .setDriver(BundledSQLiteDriver())       // same SQLite version on every platform
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
```

Each platform supplies only the builder (the file path differs):

- **Android:** `Room.databaseBuilder<TodoDatabase>(context, context.getDatabasePath(…).absolutePath)`
- **iOS:** `Room.databaseBuilder<TodoDatabase>(name = "<Documents>/todos.db")`

**Why `BundledSQLiteDriver`:** it ships its own SQLite build, so SQL behaves
the same on every OS version. The system SQLite on old Android versions lacks
some features.

## DAO rules for KMP

- On non-Android targets, every query must be `suspend` or return a `Flow`.
  Blocking queries aren't allowed.
- `Flow` queries re-emit when the table changes. This is how the UI updates
  without any manual refresh.
- `@Transaction` methods with a body work on interfaces. The two merge
  operations in [`TodoDao`](../shared/src/commonMain/kotlin/com/andy/mytodo/data/local/TodoDao.kt)
  (`markPushed` and `applyRemoteSnapshot`) are written this way, so they run
  atomically *and* a fake DAO in tests inherits the real logic.
- Enums are stored by name (`'Dirty'`), so queries can say
  `WHERE syncState != 'Deleted'`. Renaming an enum constant therefore needs a
  migration.

## Schemas are exported and committed

```kotlin
room { schemaDirectory("$projectDir/schemas") }
```

Every version's schema is written to
[`shared/schemas/`](../shared/schemas) and committed. The JSON shows exactly
what each version looked like, which you need in order to write and test
migrations.

## Migrations

Version 1 used integer IDs; version 2 uses UUIDs plus a `syncState` column.
SQLite can't change a primary key's type in place, so
[`MIGRATION_1_2`](../shared/src/commonMain/kotlin/com/andy/mytodo/data/local/Migrations.kt)
rebuilds the table:

1. Create `todos_new` with the v2 schema.
2. Copy the rows across, generating a v4 UUID per row in SQL and setting
   `syncState = 'Dirty'`, because these rows were never uploaded.
3. Drop the old table, rename the new one, and recreate the index.

The resulting schema must match `2.json` *exactly*, including column order,
defaults and index names. Room checks this when the database opens and throws
on any difference.

### Testing migrations

[`MigrationTest`](../shared/src/iosTest/kotlin/com/andy/mytodo/MigrationTest.kt)
builds a database file the way v1 of the app did (SQL copied from `1.json`,
including Room's `room_master_table`), opens it with the current setup, and
checks that the rows survived. It lives in `iosTest` because Room's file-based
builder needs no Android `Context` there.

**Make sure the test can fail.** Deleting the `CREATE INDEX` line from the
migration makes this test fail with Room's "Migration didn't properly handle"
error. A migration test that can't fail tells you nothing.

The migration was also checked on a real device: a simulator with v1 data was
upgraded in place, and every row arrived on the server with a new UUID.

### Never use destructive migration in production

`fallbackToDestructiveMigration()` deletes the user's data on any schema
change. It's acceptable during early prototyping only.
