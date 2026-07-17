package ru.local.barcodetsd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionRepositoryTest {

    private lateinit var database: BarcodeDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BarcodeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun draftAndExactFractionalQuantityAreRestoredAfterRepositoryRestart() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val draft = repository.loadOrCreate()
            .aggregate(
                LookupResult.Found(
                    barcode = "4600000000011",
                    itemRef = ITEM_REF,
                    name = "Товар"
                )
            )
            .changeQuantity(ITEM_REF, "1.001")
        repository.save(draft)

        val restored = CollectionRepository(database.collectionSessionDao()) { OTHER_SESSION_ID }
            .loadOrCreate()

        assertEquals(SESSION_ID, restored.sessionId)
        assertEquals(CollectionState.DRAFT, restored.state)
        assertEquals(1, restored.lines.size)
        assertEquals(ITEM_REF, restored.lines.single().itemRef)
        assertEquals(1_001L, restored.lines.single().quantity.milliUnits)
    }

    @Test
    fun completedAndSentTransitionsRetainDocumentReference() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val sent = repository.loadOrCreate()
            .aggregate(
                LookupResult.Found(
                    barcode = "1",
                    itemRef = ITEM_REF,
                    name = "Товар"
                )
            )
            .complete()
            .markSent(DOCUMENT_REF)
        repository.save(sent)

        val restored = CollectionRepository(database.collectionSessionDao()).loadOrCreate()

        assertEquals(CollectionState.SENT, restored.state)
        assertEquals(DOCUMENT_REF, restored.documentRef)
        assertEquals(1, restored.lines.size)
    }

    @Test
    fun newDraftAtomicallyReplacesSentSession() {
        val ids = ArrayDeque(listOf(SESSION_ID, OTHER_SESSION_ID))
        val repository = CollectionRepository(database.collectionSessionDao()) { ids.removeFirst() }
        val sent = repository.loadOrCreate()
            .aggregate(
                LookupResult.Found(
                    barcode = "1",
                    itemRef = ITEM_REF,
                    name = "Товар"
                )
            )
            .complete()
            .markSent(DOCUMENT_REF)
        repository.save(sent)

        val newDraft = repository.startNewDraft(sent.sessionId)
        val restored = CollectionRepository(database.collectionSessionDao()).loadOrCreate()

        assertEquals(OTHER_SESSION_ID, newDraft.sessionId)
        assertEquals(OTHER_SESSION_ID, restored.sessionId)
        assertEquals(CollectionState.DRAFT, restored.state)
        assertEquals(0, restored.lines.size)
    }

    @Test
    fun lateLookupMutatesLatestStoredDraftInsteadOfReplacingIt() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val capturedDraft = repository.loadOrCreate()

        repository.addResolvedProduct(
            capturedDraft.sessionId,
            LookupResult.Found(
                barcode = "4600000000011",
                itemRef = ITEM_REF,
                name = "Первый товар"
            )
        )
        repository.addResolvedProduct(
            capturedDraft.sessionId,
            LookupResult.Found(
                barcode = "4600000000028",
                itemRef = OTHER_ITEM_REF,
                name = "Второй товар"
            )
        )

        val restored = repository.loadOrCreate()
        assertEquals(listOf(ITEM_REF, OTHER_ITEM_REF), restored.lines.map { it.itemRef })
    }

    @Test
    fun lateAcceptedResponseCannotReplaceANewDraft() {
        val ids = ArrayDeque(listOf(SESSION_ID, OTHER_SESSION_ID))
        val repository = CollectionRepository(database.collectionSessionDao()) { ids.removeFirst() }
        val completed = repository.loadOrCreate()
            .aggregate(
                LookupResult.Found(
                    barcode = "1",
                    itemRef = ITEM_REF,
                    name = "Товар"
                )
            )
            .complete()
        repository.save(completed)
        val sent = repository.markSent(completed.sessionId, DOCUMENT_REF)
        repository.startNewDraft(sent.sessionId)

        try {
            repository.markSent(sent.sessionId, DOCUMENT_REF)
            fail("A stale accepted response must not replace the active draft.")
        } catch (_: CollectionValidationException) {
            // Expected: the active session id changed before the late response arrived.
        }

        val restored = repository.loadOrCreate()
        assertEquals(OTHER_SESSION_ID, restored.sessionId)
        assertEquals(CollectionState.DRAFT, restored.state)
        assertEquals(0, restored.lines.size)
    }

    private companion object {
        private const val SESSION_ID = "52af8363-48d3-4e7b-82b4-239760470f41"
        private const val OTHER_SESSION_ID = "2f7a5520-ac39-4c06-a069-89db6421a7fb"
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val OTHER_ITEM_REF = "99241fb2-b926-494c-b4e2-a0da243a2cc0"
        private const val DOCUMENT_REF = "8c85bdb8-5905-4869-b152-8b0fe2d5b413"
    }
}
