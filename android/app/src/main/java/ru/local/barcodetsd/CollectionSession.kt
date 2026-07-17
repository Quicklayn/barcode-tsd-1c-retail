package ru.local.barcodetsd

import java.math.BigDecimal

internal enum class CollectionState {
    DRAFT,
    COMPLETED,
    SENT
}

internal data class CollectionLine(
    val itemRef: String,
    val name: String,
    val barcode: String,
    val quantity: CollectionQuantity
)

internal class CollectionQuantity private constructor(
    val milliUnits: Long
) {

    fun plusOne(): CollectionQuantity {
        if (milliUnits > MAX_MILLI_UNITS - ONE_MILLI_UNITS) {
            throw CollectionValidationException("Количество превышает допустимый максимум.")
        }
        return fromMilliUnits(milliUnits + ONE_MILLI_UNITS)
    }

    fun toBigDecimal(): BigDecimal =
        BigDecimal.valueOf(milliUnits, DECIMAL_PLACES)

    fun toDisplayString(): String =
        toBigDecimal().stripTrailingZeros().toPlainString()

    companion object {
        val ONE: CollectionQuantity = fromMilliUnits(ONE_MILLI_UNITS)

        fun parse(value: String): CollectionQuantity? {
            val normalized = value.trim().replace(',', '.')
            if (normalized.isEmpty()) {
                return null
            }

            val decimal = normalized.toBigDecimalOrNull() ?: return null
            if (decimal.signum() <= 0 || decimal.scale() > DECIMAL_PLACES) {
                return null
            }

            val milliUnits = try {
                decimal.movePointRight(DECIMAL_PLACES).longValueExact()
            } catch (_: ArithmeticException) {
                return null
            }
            if (milliUnits !in 1..MAX_MILLI_UNITS) {
                return null
            }
            return CollectionQuantity(milliUnits)
        }

        fun fromMilliUnits(milliUnits: Long): CollectionQuantity {
            require(milliUnits in 1..MAX_MILLI_UNITS) {
                "Quantity milli-units are outside the API contract."
            }
            return CollectionQuantity(milliUnits)
        }

        private const val DECIMAL_PLACES = 3
        private const val ONE_MILLI_UNITS = 1_000L
        private const val MAX_MILLI_UNITS = 999_999_999_999_999L
    }
}

internal class CollectionSession private constructor(
    val sessionId: String,
    val state: CollectionState,
    lines: List<CollectionLine>,
    val documentRef: String?
) {
    val lines: List<CollectionLine> = lines.toList()

    fun aggregate(found: LookupResult.Found): CollectionSession {
        requireDraft()
        validateResolvedProduct(found)

        val existingIndex = lines.indexOfFirst { it.itemRef == found.itemRef }
        val updatedLines = if (existingIndex >= 0) {
            lines.mapIndexed { index, line ->
                if (index == existingIndex) {
                    line.copy(quantity = line.quantity.plusOne())
                } else {
                    line
                }
            }
        } else {
            if (lines.size >= MAX_LINES) {
                throw CollectionValidationException("В сессии уже $MAX_LINES уникальных товаров.")
            }
            lines + CollectionLine(
                itemRef = found.itemRef,
                name = found.name,
                barcode = found.barcode,
                quantity = CollectionQuantity.ONE
            )
        }
        return copy(lines = updatedLines)
    }

    fun changeQuantity(itemRef: String, value: String): CollectionSession {
        requireDraft()
        val quantity = CollectionQuantity.parse(value)
            ?: throw CollectionValidationException(
                "Введите положительное количество максимум с тремя знаками после запятой."
            )
        val lineIndex = lines.indexOfFirst { it.itemRef == itemRef }
        if (lineIndex < 0) {
            throw CollectionValidationException("Строка коллекции не найдена.")
        }
        return copy(
            lines = lines.mapIndexed { index, line ->
                if (index == lineIndex) line.copy(quantity = quantity) else line
            }
        )
    }

    fun deleteLine(itemRef: String): CollectionSession {
        requireDraft()
        if (lines.none { it.itemRef == itemRef }) {
            throw CollectionValidationException("Строка коллекции не найдена.")
        }
        return copy(lines = lines.filterNot { it.itemRef == itemRef })
    }

    fun complete(): CollectionSession {
        requireDraft()
        if (lines.isEmpty()) {
            throw CollectionValidationException("Нельзя завершить пустую сессию.")
        }
        return copy(state = CollectionState.COMPLETED)
    }

    fun markSent(documentRef: String): CollectionSession {
        if (state != CollectionState.COMPLETED) {
            throw CollectionValidationException("Отправить можно только завершённую сессию.")
        }
        if (documentRef.isBlank()) {
            throw CollectionValidationException("1С не вернула ссылку на документ.")
        }
        return copy(state = CollectionState.SENT, documentRef = documentRef)
    }

    fun startNewDraft(newSessionId: String): CollectionSession {
        if (state != CollectionState.SENT) {
            throw CollectionValidationException("Новая сессия доступна только после успешной отправки.")
        }
        if (newSessionId == sessionId) {
            throw CollectionValidationException("Новая сессия должна иметь другой идентификатор.")
        }
        return draft(newSessionId)
    }

    private fun copy(
        state: CollectionState = this.state,
        lines: List<CollectionLine> = this.lines,
        documentRef: String? = this.documentRef
    ): CollectionSession = CollectionSession(sessionId, state, lines, documentRef)

    private fun requireDraft() {
        if (state != CollectionState.DRAFT) {
            throw CollectionValidationException("Завершённую сессию нельзя изменять.")
        }
    }

    private fun validateResolvedProduct(found: LookupResult.Found) {
        if (found.itemRef.isBlank() || found.name.isBlank() || found.barcode.isBlank()) {
            throw CollectionValidationException("1С вернула неполные данные товара.")
        }
        if (found.barcode.length > MAX_BARCODE_LENGTH) {
            throw CollectionValidationException("Штрихкод длиннее $MAX_BARCODE_LENGTH символов.")
        }
    }

    companion object {
        fun draft(sessionId: String): CollectionSession {
            require(sessionId.isNotBlank()) { "Session id must not be blank." }
            return CollectionSession(sessionId, CollectionState.DRAFT, emptyList(), null)
        }

        fun restore(
            sessionId: String,
            state: CollectionState,
            lines: List<CollectionLine>,
            documentRef: String?
        ): CollectionSession {
            require(sessionId.isNotBlank()) { "Session id must not be blank." }
            require(lines.size <= MAX_LINES) { "Too many collection lines." }
            require(lines.map { it.itemRef }.distinct().size == lines.size) {
                "Collection lines must be unique by itemRef."
            }
            require(state != CollectionState.SENT || !documentRef.isNullOrBlank()) {
                "Sent session must retain documentRef."
            }
            require(state == CollectionState.SENT || documentRef == null) {
                "Only sent session can retain documentRef."
            }
            return CollectionSession(sessionId, state, lines, documentRef)
        }

        private const val MAX_LINES = 1_000
        private const val MAX_BARCODE_LENGTH = 200
    }
}

internal class CollectionValidationException(message: String) : IllegalStateException(message)

internal fun CollectionSession.applyLookupResult(result: LookupResult): CollectionSession =
    if (result is LookupResult.Found) aggregate(result) else this
