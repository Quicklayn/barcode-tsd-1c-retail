package ru.local.barcodetsd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun onlineResultCanBeResolvedAndAggregatedFromCache() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val draft = repository.loadOrCreate()
        repository.addResolvedProduct(
            draft.sessionId,
            LookupResult.Found(BARCODE, ITEM_REF, "Товар")
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(OTHER_SESSION_ID))

        val cached = repository.resolveCachedProduct(OTHER_SESSION_ID, BARCODE)

        assertNotNull(cached)
        cached ?: return
        assertEquals(LookupSource.CACHED, cached.found.source)
        assertEquals(ITEM_REF, cached.found.itemRef)
        assertEquals("Товар", cached.found.name)
        assertEquals(1_000L, cached.session.lines.single().quantity.milliUnits)
    }

    @Test
    fun cacheMissDoesNotMutateDraft() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val draft = repository.loadOrCreate()

        val cached = repository.resolveCachedProduct(draft.sessionId, BARCODE)

        assertNull(cached)
        assertEquals(0, repository.loadOrCreate().lines.size)
    }

    @Test
    fun laterOnlineResultRefreshesCachedProduct() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val firstDraft = repository.loadOrCreate()
        repository.addResolvedProduct(
            firstDraft.sessionId,
            LookupResult.Found(BARCODE, ITEM_REF, "Старое наименование")
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(OTHER_SESSION_ID))
        repository.addResolvedProduct(
            OTHER_SESSION_ID,
            LookupResult.Found(BARCODE, OTHER_ITEM_REF, "Новое наименование")
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(THIRD_SESSION_ID))

        val cached = repository.resolveCachedProduct(THIRD_SESSION_ID, BARCODE)

        assertNotNull(cached)
        cached ?: return
        assertEquals(OTHER_ITEM_REF, cached.found.itemRef)
        assertEquals("Новое наименование", cached.found.name)
        assertEquals(OTHER_ITEM_REF, cached.session.lines.single().itemRef)
    }

    @Test
    fun repeatedCachedResultIncrementsQuantity() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val draft = repository.loadOrCreate()
        repository.addResolvedProduct(
            draft.sessionId,
            LookupResult.Found(BARCODE, ITEM_REF, "Товар")
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(OTHER_SESSION_ID))

        repository.resolveCachedProduct(OTHER_SESSION_ID, BARCODE)
        val repeated = repository.resolveCachedProduct(OTHER_SESSION_ID, BARCODE)

        assertNotNull(repeated)
        assertEquals(2_000L, repeated?.session?.lines?.single()?.quantity?.milliUnits)
    }

    @Test
    fun selectedAmbiguousCandidateAddsExactValuesAndRepeatedSelectionIncrements() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val draft = repository.loadOrCreate()
        val candidate = ProductCandidate(ITEM_REF, "Выбранный товар")

        val selected = repository.addSelectedAmbiguousCandidate(
            draft.sessionId,
            BARCODE,
            candidate
        )
        val repeated = repository.addSelectedAmbiguousCandidate(
            draft.sessionId,
            BARCODE,
            candidate
        )

        assertEquals(ITEM_REF, selected.lines.single().itemRef)
        assertEquals("Выбранный товар", selected.lines.single().name)
        assertEquals(BARCODE, selected.lines.single().barcode)
        assertEquals(1_000L, selected.lines.single().quantity.milliUnits)
        assertEquals(1, repeated.lines.size)
        assertEquals(2_000L, repeated.lines.single().quantity.milliUnits)
    }

    @Test
    fun selectedAmbiguousCandidateDoesNotCreateCachedProduct() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val draft = repository.loadOrCreate()
        repository.addSelectedAmbiguousCandidate(
            draft.sessionId,
            BARCODE,
            ProductCandidate(ITEM_REF, "Выбранный товар")
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(OTHER_SESSION_ID))

        assertNull(repository.resolveCachedProduct(OTHER_SESSION_ID, BARCODE))
    }

    @Test
    fun selectedAmbiguousCandidateDoesNotReplaceExistingCachedProduct() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val draft = repository.loadOrCreate()
        repository.addResolvedProduct(
            draft.sessionId,
            LookupResult.Found(BARCODE, ITEM_REF, "Кешированный товар")
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(OTHER_SESSION_ID))
        repository.addSelectedAmbiguousCandidate(
            OTHER_SESSION_ID,
            BARCODE,
            ProductCandidate(OTHER_ITEM_REF, "Выбранный товар")
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(THIRD_SESSION_ID))

        val cached = repository.resolveCachedProduct(THIRD_SESSION_ID, BARCODE)

        assertNotNull(cached)
        assertEquals(ITEM_REF, cached?.found?.itemRef)
        assertEquals("Кешированный товар", cached?.found?.name)
    }

    @Test
    fun staleSessionRejectsSelectedAmbiguousCandidate() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val staleDraft = repository.loadOrCreate()
        database.collectionSessionDao().replaceSession(CollectionSession.draft(OTHER_SESSION_ID))

        expectSessionChanged {
            repository.addSelectedAmbiguousCandidate(
                staleDraft.sessionId,
                BARCODE,
                ProductCandidate(ITEM_REF, "Выбранный товар")
            )
        }

        val restored = repository.loadOrCreate()
        assertEquals(OTHER_SESSION_ID, restored.sessionId)
        assertEquals(0, restored.lines.size)
    }

    @Test
    fun completedSessionRejectsSelectedAmbiguousCandidate() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val completed = repository.loadOrCreate()
            .aggregate(LookupResult.Found(BARCODE, ITEM_REF, "Товар"))
            .complete()
        repository.save(completed)

        expectSessionChangedOrInvalidDraft {
            repository.addSelectedAmbiguousCandidate(
                completed.sessionId,
                OTHER_BARCODE,
                ProductCandidate(OTHER_ITEM_REF, "Выбранный товар")
            )
        }

        val restored = repository.loadOrCreate()
        assertEquals(CollectionState.COMPLETED, restored.state)
        assertEquals(listOf(ITEM_REF), restored.lines.map { it.itemRef })
    }

    @Test
    fun staleOnlineAndCachedLookupsCannotMutateNewDraft() {
        val ids = ArrayDeque(listOf(SESSION_ID, OTHER_SESSION_ID))
        val repository = CollectionRepository(database.collectionSessionDao()) { ids.removeFirst() }
        val draft = repository.loadOrCreate()
        repository.addResolvedProduct(
            draft.sessionId,
            LookupResult.Found(BARCODE, ITEM_REF, "Товар")
        )
        val completed = repository.complete(draft.sessionId)
        val sent = repository.markSent(completed.sessionId, DOCUMENT_REF)
        val newDraft = repository.startNewDraft(sent.sessionId)

        expectSessionChanged {
            repository.addResolvedProduct(
                sent.sessionId,
                LookupResult.Found(OTHER_BARCODE, OTHER_ITEM_REF, "Другой товар")
            )
        }
        expectSessionChanged {
            repository.resolveCachedProduct(sent.sessionId, BARCODE)
        }

        val restored = repository.loadOrCreate()
        assertEquals(newDraft.sessionId, restored.sessionId)
        assertEquals(0, restored.lines.size)
        assertNull(repository.resolveCachedProduct(newDraft.sessionId, OTHER_BARCODE))
    }

    @Test
    fun failedAggregationRollsBackOnlineCacheWrite() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        val completed = repository.loadOrCreate()
            .aggregate(LookupResult.Found(BARCODE, ITEM_REF, "Товар"))
            .complete()
        repository.save(completed)

        expectSessionChangedOrInvalidDraft {
            repository.addResolvedProduct(
                completed.sessionId,
                LookupResult.Found(OTHER_BARCODE, OTHER_ITEM_REF, "Другой товар")
            )
        }
        database.collectionSessionDao().replaceSession(CollectionSession.draft(OTHER_SESSION_ID))

        assertNull(repository.resolveCachedProduct(OTHER_SESSION_ID, OTHER_BARCODE))
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

    private fun expectSessionChanged(operation: () -> Unit) {
        try {
            operation()
            fail("A stale lookup must be rejected.")
        } catch (_: CollectionValidationException) {
            // Expected: the active session id changed before the lookup completed.
        }
    }

    private fun expectSessionChangedOrInvalidDraft(operation: () -> Unit) {
        try {
            operation()
            fail("A completed draft must reject lookup aggregation.")
        } catch (_: CollectionValidationException) {
            // Expected: the transaction must roll back when aggregation is rejected.
        }
    }

    private companion object {
        private const val SESSION_ID = "52af8363-48d3-4e7b-82b4-239760470f41"
        private const val OTHER_SESSION_ID = "2f7a5520-ac39-4c06-a069-89db6421a7fb"
        private const val THIRD_SESSION_ID = "168f538e-e294-4817-a20f-6ce9d8ea863c"
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val OTHER_ITEM_REF = "99241fb2-b926-494c-b4e2-a0da243a2cc0"
        private const val DOCUMENT_REF = "8c85bdb8-5905-4869-b152-8b0fe2d5b413"
        private const val BARCODE = "4600000000011"
        private const val OTHER_BARCODE = "4600000000028"
    }
}
