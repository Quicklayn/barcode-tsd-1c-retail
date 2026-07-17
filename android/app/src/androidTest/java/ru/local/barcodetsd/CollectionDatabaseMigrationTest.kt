package ru.local.barcodetsd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionDatabaseMigrationTest {

    private lateinit var context: Context
    private var database: BarcodeDatabase? = null

    @Before
    fun prepareDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFromVersionOnePreservesCollectionAndCreatesUsableCache() {
        createVersionOneDatabase()
        database = Room.databaseBuilder(context, BarcodeDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val migratedDatabase = requireNotNull(database)
        val dao = migratedDatabase.collectionSessionDao()
        val repository = CollectionRepository(dao) { NEXT_SESSION_ID }
        val restored = repository.loadOrCreate()

        assertEquals(SESSION_ID, restored.sessionId)
        assertEquals(CollectionState.SENT, restored.state)
        assertEquals(DOCUMENT_REF, restored.documentRef)
        assertEquals(1, restored.lines.size)
        assertEquals(ITEM_REF, restored.lines.single().itemRef)
        assertEquals(1_001L, restored.lines.single().quantity.milliUnits)

        val nextDraft = repository.startNewDraft(restored.sessionId)
        repository.addResolvedProduct(
            nextDraft.sessionId,
            LookupResult.Found(CACHED_BARCODE, CACHED_ITEM_REF, CACHED_NAME)
        )
        dao.replaceSession(CollectionSession.draft(CACHE_READ_SESSION_ID))

        val cached = repository.resolveCachedProduct(CACHE_READ_SESSION_ID, CACHED_BARCODE)

        assertNotNull(cached)
        cached ?: return
        assertEquals(LookupSource.CACHED, cached.found.source)
        assertEquals(CACHED_ITEM_REF, cached.found.itemRef)
        assertEquals(CACHED_NAME, cached.found.name)
        assertEquals(CACHED_ITEM_REF, cached.session.lines.single().itemRef)
    }

    private fun createVersionOneDatabase() {
        val versionOne = context.openOrCreateDatabase(
            DATABASE_NAME,
            Context.MODE_PRIVATE,
            null
        )
        versionOne.execSQL("PRAGMA foreign_keys = ON")
        versionOne.execSQL(
            """CREATE TABLE IF NOT EXISTS `collection_sessions` (`sessionId` TEXT NOT NULL, `state` TEXT NOT NULL, `documentRef` TEXT, PRIMARY KEY(`sessionId`))"""
        )
        versionOne.execSQL(
            """CREATE TABLE IF NOT EXISTS `collection_lines` (`sessionId` TEXT NOT NULL, `itemRef` TEXT NOT NULL, `name` TEXT NOT NULL, `barcode` TEXT NOT NULL, `quantityMilliUnits` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `itemRef`), FOREIGN KEY(`sessionId`) REFERENCES `collection_sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        versionOne.execSQL(
            """CREATE INDEX IF NOT EXISTS `index_collection_lines_sessionId` ON `collection_lines` (`sessionId`)"""
        )
        versionOne.execSQL(
            "INSERT INTO collection_sessions(sessionId, state, documentRef) VALUES (?, ?, ?)",
            arrayOf<Any>(SESSION_ID, CollectionState.SENT.name, DOCUMENT_REF)
        )
        versionOne.execSQL(
            """INSERT INTO collection_lines(sessionId, itemRef, name, barcode, quantityMilliUnits, position) VALUES (?, ?, ?, ?, ?, ?)""",
            arrayOf<Any>(SESSION_ID, ITEM_REF, "Сохранённый товар", "123", 1_001L, 0)
        )
        versionOne.version = 1
        versionOne.close()
    }

    private companion object {
        private const val DATABASE_NAME = "collection-migration-test.db"
        private const val SESSION_ID = "52af8363-48d3-4e7b-82b4-239760470f41"
        private const val NEXT_SESSION_ID = "2f7a5520-ac39-4c06-a069-89db6421a7fb"
        private const val CACHE_READ_SESSION_ID = "168f538e-e294-4817-a20f-6ce9d8ea863c"
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val DOCUMENT_REF = "8c85bdb8-5905-4869-b152-8b0fe2d5b413"
        private const val CACHED_BARCODE = "4600000000011"
        private const val CACHED_ITEM_REF = "99241fb2-b926-494c-b4e2-a0da243a2cc0"
        private const val CACHED_NAME = "Товар из кеша"
    }
}
