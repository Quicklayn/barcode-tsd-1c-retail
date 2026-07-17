package ru.local.barcodetsd

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import java.util.UUID

@Entity(tableName = "collection_sessions")
internal data class CollectionSessionEntity(
    @PrimaryKey val sessionId: String,
    val state: String,
    val documentRef: String?
)

@Entity(
    tableName = "collection_lines",
    primaryKeys = ["sessionId", "itemRef"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
internal data class CollectionLineEntity(
    val sessionId: String,
    val itemRef: String,
    val name: String,
    val barcode: String,
    val quantityMilliUnits: Long,
    val position: Int
)

internal data class StoredCollectionSession(
    val header: CollectionSessionEntity,
    val lines: List<CollectionLineEntity>
)

private fun StoredCollectionSession.toDomain(): CollectionSession =
    CollectionSession.restore(
        sessionId = header.sessionId,
        state = CollectionState.valueOf(header.state),
        lines = lines.map { line ->
            CollectionLine(
                itemRef = line.itemRef,
                name = line.name,
                barcode = line.barcode,
                quantity = CollectionQuantity.fromMilliUnits(line.quantityMilliUnits)
            )
        },
        documentRef = header.documentRef
    )

@Dao
internal abstract class CollectionSessionDao {

    @Query("SELECT * FROM collection_sessions LIMIT 1")
    protected abstract fun loadHeader(): CollectionSessionEntity?

    @Query("SELECT * FROM collection_lines WHERE sessionId = :sessionId ORDER BY position")
    protected abstract fun loadLines(sessionId: String): List<CollectionLineEntity>

    @Insert
    protected abstract fun insertHeader(header: CollectionSessionEntity)

    @Insert
    protected abstract fun insertLines(lines: List<CollectionLineEntity>)

    @Query("DELETE FROM collection_sessions")
    protected abstract fun deleteAllSessions()

    @Transaction
    open fun loadSession(): StoredCollectionSession? = loadStoredSession()

    @Transaction
    open fun replaceSession(session: CollectionSession) {
        replaceSessionRows(session)
    }

    @Transaction
    open fun updateSession(
        expectedSessionId: String,
        update: (CollectionSession) -> CollectionSession
    ): CollectionSession? {
        val stored = loadStoredSession() ?: return null
        if (stored.header.sessionId != expectedSessionId) {
            return null
        }

        val updated = update(stored.toDomain())
        replaceSessionRows(updated)
        return updated
    }

    private fun loadStoredSession(): StoredCollectionSession? {
        val header = loadHeader() ?: return null
        return StoredCollectionSession(header, loadLines(header.sessionId))
    }

    private fun replaceSessionRows(session: CollectionSession) {
        deleteAllSessions()
        insertHeader(
            CollectionSessionEntity(
                sessionId = session.sessionId,
                state = session.state.name,
                documentRef = session.documentRef
            )
        )
        if (session.lines.isNotEmpty()) {
            insertLines(
                session.lines.mapIndexed { index, line ->
                    CollectionLineEntity(
                        sessionId = session.sessionId,
                        itemRef = line.itemRef,
                        name = line.name,
                        barcode = line.barcode,
                        quantityMilliUnits = line.quantity.milliUnits,
                        position = index
                    )
                }
            )
        }
    }
}

@Database(
    entities = [CollectionSessionEntity::class, CollectionLineEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class BarcodeDatabase : RoomDatabase() {
    abstract fun collectionSessionDao(): CollectionSessionDao

    companion object {
        @Volatile
        private var instance: BarcodeDatabase? = null

        fun getInstance(context: Context): BarcodeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BarcodeDatabase::class.java,
                    "barcode-collection.db"
                ).build().also { instance = it }
            }
    }
}

internal class CollectionRepository(
    private val dao: CollectionSessionDao,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun loadOrCreate(): CollectionSession {
        val stored = dao.loadSession()
        if (stored != null) {
            return stored.toDomain()
        }

        val draft = CollectionSession.draft(sessionIdFactory())
        save(draft)
        return draft
    }

    fun save(session: CollectionSession) {
        dao.replaceSession(session)
    }

    fun addResolvedProduct(
        sessionId: String,
        found: LookupResult.Found
    ): CollectionSession = update(sessionId) { current ->
        current.aggregate(found)
    }

    fun changeQuantity(
        sessionId: String,
        itemRef: String,
        value: String
    ): CollectionSession = update(sessionId) { current ->
        current.changeQuantity(itemRef, value)
    }

    fun deleteLine(sessionId: String, itemRef: String): CollectionSession =
        update(sessionId) { current -> current.deleteLine(itemRef) }

    fun complete(sessionId: String): CollectionSession =
        update(sessionId) { current -> current.complete() }

    fun markSent(sessionId: String, documentRef: String): CollectionSession =
        update(sessionId) { current ->
            if (current.state == CollectionState.SENT && current.documentRef == documentRef) {
                current
            } else {
                current.markSent(documentRef)
            }
        }

    fun startNewDraft(sessionId: String): CollectionSession =
        update(sessionId) { current -> current.startNewDraft(sessionIdFactory()) }

    private fun update(
        sessionId: String,
        operation: (CollectionSession) -> CollectionSession
    ): CollectionSession = dao.updateSession(sessionId, operation)
        ?: throw CollectionValidationException("Активная сессия уже изменилась.")
}
