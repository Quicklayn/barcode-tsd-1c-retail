package ru.local.barcodetsd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CollectionSessionTest {

    @Test
    fun aggregateRetainsItemRefAndAddsNewLine() {
        val session = CollectionSession.draft(SESSION_ID)

        val updated = session.aggregate(found(ITEM_REF, "4600000000011"))

        assertEquals(1, updated.lines.size)
        assertEquals(ITEM_REF, updated.lines.single().itemRef)
        assertEquals("4600000000011", updated.lines.single().barcode)
        assertEquals("Товар", updated.lines.single().name)
        assertEquals("1", updated.lines.single().quantity.toDisplayString())
    }

    @Test
    fun repeatedItemRefAddsExactlyOneToFractionalQuantity() {
        val session = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "first"))
            .changeQuantity(ITEM_REF, "1.001")

        val updated = session.aggregate(found(ITEM_REF, "second"))

        assertEquals(1, updated.lines.size)
        assertEquals("2.001", updated.lines.single().quantity.toDisplayString())
        assertEquals("first", updated.lines.single().barcode)
    }

    @Test
    fun aggregateUsesItemRefInsteadOfBarcode() {
        val session = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "same"))

        val updated = session.aggregate(found(OTHER_ITEM_REF, "same"))

        assertEquals(2, updated.lines.size)
    }

    @Test
    fun unsuccessfulLookupResultsDoNotMutateDraft() {
        val session = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "1"))
        val failures = listOf(
            LookupResult.NotFound("2", null),
            LookupResult.Ambiguous("2", listOf("A", "B")),
            LookupResult.InvalidInput("invalid"),
            LookupResult.AuthError("auth"),
            LookupResult.ServerError("server"),
            LookupResult.ConnectionError("connection")
        )

        failures.forEach { result ->
            assertSame(session, session.applyLookupResult(result))
        }
    }

    @Test
    fun positiveQuantityWithThreeDecimalPlacesIsAcceptedExactly() {
        val session = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "1"))

        val updated = session.changeQuantity(ITEM_REF, "1.001")

        assertEquals(1_001L, updated.lines.single().quantity.milliUnits)
        assertEquals("1.001", updated.lines.single().quantity.toBigDecimal().toPlainString())
    }

    @Test
    fun invalidQuantityDoesNotChangeOriginalSession() {
        val session = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "1"))

        listOf("0", "-1", "1.0001", "not-a-number").forEach { invalidValue ->
            assertThrows(CollectionValidationException::class.java) {
                session.changeQuantity(ITEM_REF, invalidValue)
            }
        }
        assertEquals("1", session.lines.single().quantity.toDisplayString())
    }

    @Test
    fun draftLineCanBeDeleted() {
        val session = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "1"))

        val updated = session.deleteLine(ITEM_REF)

        assertEquals(emptyList<CollectionLine>(), updated.lines)
    }

    @Test
    fun emptyDraftCannotBeCompleted() {
        assertThrows(CollectionValidationException::class.java) {
            CollectionSession.draft(SESSION_ID).complete()
        }
    }

    @Test
    fun completedSessionIsImmutableAndCanBecomeSent() {
        val completed = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "1"))
            .complete()

        assertEquals(CollectionState.COMPLETED, completed.state)
        assertThrows(CollectionValidationException::class.java) {
            completed.aggregate(found(OTHER_ITEM_REF, "2"))
        }
        assertThrows(CollectionValidationException::class.java) {
            completed.changeQuantity(ITEM_REF, "2")
        }
        assertThrows(CollectionValidationException::class.java) {
            completed.deleteLine(ITEM_REF)
        }

        val sent = completed.markSent(DOCUMENT_REF)

        assertEquals(CollectionState.SENT, sent.state)
        assertEquals(DOCUMENT_REF, sent.documentRef)
    }

    @Test
    fun sentSessionStartsDifferentEmptyDraft() {
        val sent = CollectionSession.draft(SESSION_ID)
            .aggregate(found(ITEM_REF, "1"))
            .complete()
            .markSent(DOCUMENT_REF)

        val draft = sent.startNewDraft(NEW_SESSION_ID)

        assertNotEquals(sent.sessionId, draft.sessionId)
        assertEquals(CollectionState.DRAFT, draft.state)
        assertEquals(emptyList<CollectionLine>(), draft.lines)
        assertEquals(null, draft.documentRef)
    }

    private fun found(itemRef: String, barcode: String): LookupResult.Found =
        LookupResult.Found(barcode = barcode, itemRef = itemRef, name = "Товар")

    private companion object {
        private const val SESSION_ID = "52af8363-48d3-4e7b-82b4-239760470f41"
        private const val NEW_SESSION_ID = "2f7a5520-ac39-4c06-a069-89db6421a7fb"
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val OTHER_ITEM_REF = "a3fe213b-334b-48ed-ae7a-f55cc3eac9f5"
        private const val DOCUMENT_REF = "8c85bdb8-5905-4869-b152-8b0fe2d5b413"
    }
}
