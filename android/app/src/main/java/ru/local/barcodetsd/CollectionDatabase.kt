package ru.local.barcodetsd

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "cached_products")
internal data class CachedProductEntity(
    @PrimaryKey val normalizedBarcode: String,
    val itemRef: String,
    val name: String
)

internal data class StoredCollectionSession(
    val header: CollectionSessionEntity,
    val lines: List<CollectionLineEntity>
)

internal data class CachedProductResolution(
    val found: LookupResult.Found,
    val session: CollectionSession
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun upsertCachedProduct(product: CachedProductEntity)

    @Query("SELECT * FROM cached_products WHERE normalizedBarcode = :normalizedBarcode")
    protected abstract fun loadCachedProduct(normalizedBarcode: String): CachedProductEntity?

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

    @Transaction
    open fun cacheAndUpdateSession(
        expectedSessionId: String,
        found: LookupResult.Found
    ): CollectionSession? {
        val stored = loadStoredSession() ?: return null
        if (stored.header.sessionId != expectedSessionId) {
            return null
        }

        val updated = stored.toDomain().aggregate(found)
        upsertCachedProduct(
            CachedProductEntity(
                normalizedBarcode = found.barcode,
                itemRef = found.itemRef,
                name = found.name
            )
        )
        replaceSessionRows(updated)
        return updated
    }

    @Transaction
    open fun resolveCachedProductAndUpdateSession(
        expectedSessionId: String,
        normalizedBarcode: String
    ): CachedProductResolution? {
        val stored = loadStoredSession()
            ?: throw CollectionValidationException("Активная сессия уже изменилась.")
        if (stored.header.sessionId != expectedSessionId) {
            throw CollectionValidationException("Активная сессия уже изменилась.")
        }

        val cached = loadCachedProduct(normalizedBarcode) ?: return null
        val found = LookupResult.Found(
            barcode = normalizedBarcode,
            itemRef = cached.itemRef,
            name = cached.name,
            source = LookupSource.CACHED
        )
        val updated = stored.toDomain().aggregate(found)
        replaceSessionRows(updated)
        return CachedProductResolution(found, updated)
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

internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `cached_products` (`normalizedBarcode` TEXT NOT NULL, `itemRef` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`normalizedBarcode`))"""
        )
    }
}

@Database(
    entities = [
        CollectionSessionEntity::class,
        CollectionLineEntity::class,
        CachedProductEntity::class
    ],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
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
    ): CollectionSession {
        if (found.source != LookupSource.ONLINE) {
            throw CollectionValidationException("Кешированный результат требует кеш-пути.")
        }
        return dao.cacheAndUpdateSession(sessionId, found)
            ?: throw CollectionValidationException("Активная сессия уже изменилась.")
    }

    fun addSelectedAmbiguousCandidate(
        sessionId: String,
        barcode: String,
        candidate: ProductCandidate
    ): CollectionSession = update(sessionId) { current ->
        current.aggregate(
            LookupResult.Found(
                barcode = barcode,
                itemRef = candidate.itemRef,
                name = candidate.name
            )
        )
    }

    fun resolveCachedProduct(
        sessionId: String,
        normalizedBarcode: String
    ): CachedProductResolution? =
        dao.resolveCachedProductAndUpdateSession(sessionId, normalizedBarcode)

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
