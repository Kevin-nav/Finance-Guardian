package com.kevin.financeguardian.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.DefaultCategories
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FinanceGuardianDatabaseTest {
    private lateinit var database: FinanceGuardianDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            context,
            FinanceGuardianDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedIfEmptyInsertsDefaultCategories() = runTest {
        DefaultCategorySeeder(database.categoryDao(), FixedClock()).seedIfEmpty()

        val ids = database.categoryDao().getAllOnce().map { it.id }.toSet()

        assertEquals(DefaultCategories.values.map { it.id }.toSet(), ids)
    }

    @Test
    fun seedIfEmptyBackfillsMissingDefaultCategoriesWhenSomeCategoriesExist() = runTest {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        database.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = "custom",
                    name = "Custom",
                    type = CategoryType.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )

        DefaultCategorySeeder(database.categoryDao(), FixedClock(now.plusSeconds(60))).seedIfEmpty()

        val categories = database.categoryDao().getAllOnce()
        val ids = categories.map { it.id }.toSet()
        assertTrue(ids.contains("custom"))
        assertTrue(ids.containsAll(DefaultCategories.values.map { it.id }))
    }

    @Test
    fun categoryDaoCanUpsertLookupAndArchiveCategory() = runTest {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        val category = CategoryEntity(
            id = "custom-rent",
            name = "Rent",
            type = CategoryType.EXPENSE,
            createdAt = now,
            updatedAt = now,
        )

        database.categoryDao().upsert(category)

        assertEquals(category, database.categoryDao().getById("custom-rent"))

        database.categoryDao().archive(
            categoryId = "custom-rent",
            updatedAt = now.plusSeconds(60),
        )

        val archived = database.categoryDao().getById("custom-rent")
        assertTrue(archived?.isArchived == true)
        assertEquals(now.plusSeconds(60), archived?.updatedAt)
        database.categoryDao().observeAll().test {
            assertEquals(emptyList<CategoryEntity>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun insertTransactionCanBeObserved() = runTest {
        val transaction = sampleTransaction()

        database.transactionDao().insert(transaction)

        database.transactionDao().observeAll().test {
            val transactions = awaitItem()
            assertEquals(listOf(transaction), transactions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateCategoryChangesTransactionCategory() = runTest {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        database.categoryDao().upsertAll(
            listOf(
                CategoryEntity(
                    id = "food",
                    name = "Food",
                    type = CategoryType.EXPENSE,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        database.transactionDao().insert(sampleTransaction())

        database.transactionDao().updateCategory(
            transactionId = "transaction-1",
            categoryId = "food",
            updatedAt = now.plusSeconds(60),
        )

        assertEquals("food", database.transactionDao().getById("transaction-1")?.categoryId)
    }

    @Test
    fun duplicateSmsRecordIsRejected() = runTest {
        val record = sampleSmsRecord()

        database.smsMessageRecordDao().insert(record)

        try {
            database.smsMessageRecordDao().insert(record.copy(id = "sms-2"))
            fail("Expected duplicate SMS record to violate unique index")
        } catch (expected: SQLiteConstraintException) {
            assertNotNull(expected.message)
        }

        val duplicate = database.smsMessageRecordDao().findDuplicate(
            sender = record.sender,
            bodyHash = record.bodyHash,
            receivedAt = record.receivedAt,
        )
        assertEquals(record.id, duplicate?.id)
    }

    @Test
    fun seedingTwiceDoesNotDuplicateCategories() = runTest {
        val seeder = DefaultCategorySeeder(database.categoryDao(), FixedClock())

        seeder.seedIfEmpty()
        seeder.seedIfEmpty()

        assertTrue(database.categoryDao().getAllOnce().size == DefaultCategories.values.size)
    }

    @Test
    fun updateMerchantDefaultCategoryChangesMerchant() = runTest {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        database.merchantDao().upsert(
            MerchantEntity(
                id = "merchant-1",
                displayName = "Shoprite",
                normalizedName = "shoprite",
                phone = null,
                defaultCategoryId = null,
                createdFromTransactionId = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        database.merchantDao().updateDefaultCategory(
            id = "merchant-1",
            defaultCategoryId = "food",
            updatedAt = now.plusSeconds(60),
        )

        assertEquals("food", database.merchantDao().getById("merchant-1")?.defaultCategoryId)
    }

    @Test
    fun learningSignalDaoCanInsertAndQueryBySignalKey() = runTest {
        val signal = sampleLearningSignal()

        database.learningSignalDao().upsert(signal)

        assertEquals(signal, database.learningSignalDao().getBySignalKey(signal.signalKey))
    }

    @Test
    fun learningSignalDaoUpsertBySignalKeyUpdatesExistingSignal() = runTest {
        val original = sampleLearningSignal()
        val updated = original.copy(
            id = "signal-2",
            transactionId = "transaction-2",
            categoryId = "transport",
            weight = 4.0f,
            updatedAt = original.updatedAt.plusSeconds(60),
        )

        database.learningSignalDao().upsert(original)
        database.learningSignalDao().upsert(updated)

        val stored = database.learningSignalDao().getBySignalKey(original.signalKey)
        assertEquals("signal-1", stored?.id)
        assertEquals("transaction-2", stored?.transactionId)
        assertEquals("transport", stored?.categoryId)
        assertEquals(4.0f, stored?.weight)
        assertEquals(updated.updatedAt, stored?.updatedAt)
        assertEquals(1, database.learningSignalDao().getAllOnce().size)
    }

    @Test
    fun learningSignalDaoCanQueryByNormalizedMerchantPhoneAndReference() = runTest {
        val merchantSignal = sampleLearningSignal(
            id = "signal-merchant",
            signalKey = "merchant|mtn|sample merchant",
            normalizedPhone = null,
            normalizedReference = null,
        )
        val phoneSignal = sampleLearningSignal(
            id = "signal-phone",
            signalKey = "phone|mtn|233244000111",
            normalizedMerchantName = null,
            normalizedPhone = "233244000111",
            normalizedReference = null,
        )
        val referenceSignal = sampleLearningSignal(
            id = "signal-reference",
            signalKey = "reference|mtn|snacks",
            normalizedMerchantName = null,
            normalizedPhone = null,
            normalizedReference = "snacks",
        )

        database.learningSignalDao().upsert(merchantSignal)
        database.learningSignalDao().upsert(phoneSignal)
        database.learningSignalDao().upsert(referenceSignal)

        assertEquals(
            listOf(merchantSignal),
            database.learningSignalDao().findByNormalizedMerchantName("sample merchant"),
        )
        assertEquals(
            listOf(phoneSignal),
            database.learningSignalDao().findByNormalizedPhone("233244000111"),
        )
        assertEquals(
            listOf(referenceSignal),
            database.learningSignalDao().findByNormalizedReference("snacks"),
        )
    }

    @Test
    fun migrationsUpgradeVersion1DatabaseAndPreserveTransactions() = runTest {
        database.close()

        val dbName = "migration-test-${System.nanoTime()}.db"
        createVersion1Database(dbName)

        val migratedDatabase = Room.databaseBuilder(
            context,
            FinanceGuardianDatabase::class.java,
            dbName,
        )
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
            )
            .allowMainThreadQueries()
            .build()

        try {
            val transaction = migratedDatabase.transactionDao().getById("transaction-1")
            assertNotNull(transaction)
            assertEquals("legacy-sms-1", transaction?.sourceMessageId)
            assertEquals(null, transaction?.providerTransactionId)
            assertEquals(null, transaction?.dedupeKey)
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun sampleTransaction(): TransactionEntity {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        return TransactionEntity(
            id = "transaction-1",
            sourceMessageId = "sms-1",
            provider = Provider.MTN_MOMO,
            rawSender = "MTN MoMo",
            rawBodyHash = "hash-1",
            occurredAt = now,
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            amountMinor = 1850,
            currency = "GHS",
            counterpartyName = "Sample Merchant",
            counterpartyPhone = null,
            reference = "snacks",
            balanceAfterMinor = 44961,
            categoryId = null,
            confidence = 0.95f,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun createVersion1Database(dbName: String) {
        context.deleteDatabase(dbName)
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceMessageId TEXT,
                    provider TEXT NOT NULL,
                    rawSender TEXT NOT NULL,
                    rawBodyHash TEXT NOT NULL,
                    occurredAt INTEGER NOT NULL,
                    direction TEXT NOT NULL,
                    moneyMovementType TEXT NOT NULL,
                    amountMinor INTEGER NOT NULL,
                    currency TEXT NOT NULL,
                    counterpartyName TEXT,
                    counterpartyPhone TEXT,
                    reference TEXT,
                    balanceAfterMinor INTEGER,
                    categoryId TEXT,
                    confidence REAL NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sourceMessageId ON transactions(sourceMessageId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_transactions_occurredAt ON transactions(occurredAt)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions(categoryId)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS merchants (
                    id TEXT NOT NULL PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    normalizedName TEXT NOT NULL,
                    phone TEXT,
                    defaultCategoryId TEXT,
                    createdFromTransactionId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_merchants_normalizedName ON merchants(normalizedName)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_merchants_phone ON merchants(phone)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    isArchived INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_categories_type ON categories(type)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sms_message_records (
                    id TEXT NOT NULL PRIMARY KEY,
                    sender TEXT NOT NULL,
                    bodyHash TEXT NOT NULL,
                    receivedAt INTEGER NOT NULL,
                    processedAt INTEGER,
                    parseStatus TEXT NOT NULL,
                    parseReason TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_sms_message_records_sender_bodyHash_receivedAt
                ON sms_message_records(sender, bodyHash, receivedAt)
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sms_message_records_parseStatus ON sms_message_records(parseStatus)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS parser_rules (
                    id TEXT NOT NULL PRIMARY KEY,
                    provider TEXT NOT NULL,
                    name TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_parser_rules_provider ON parser_rules(provider)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_parser_rules_enabled ON parser_rules(enabled)",
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, sourceMessageId, provider, rawSender, rawBodyHash, occurredAt, direction,
                    moneyMovementType, amountMinor, currency, counterpartyName, counterpartyPhone,
                    reference, balanceAfterMinor, categoryId, confidence, createdAt, updatedAt
                ) VALUES (
                    'transaction-1', 'legacy-sms-1', 'MTN_MOMO', 'MTN MoMo', 'hash-legacy',
                    1713787200000, 'DEBIT', 'EXPENSE', 1850, 'GHS', 'Legacy Merchant', NULL,
                    'legacy ref', 44961, NULL, 0.95, 1713787200000, 1713787200000
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, 'e2a27de4ddabb71307b9bd19442dfeec')",
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private fun sampleSmsRecord(): SmsMessageRecordEntity {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        return SmsMessageRecordEntity(
            id = "sms-1",
            sender = "MTN MoMo",
            bodyHash = "hash-1",
            receivedAt = now,
            processedAt = now,
            parseStatus = ParseStatus.PARSED,
            parseReason = null,
        )
    }

    private fun sampleLearningSignal(
        id: String = "signal-1",
        signalKey: String = "merchant|mtn|sample merchant|snacks",
        normalizedMerchantName: String? = "sample merchant",
        normalizedPhone: String? = "233244000000",
        normalizedReference: String? = "snacks",
    ): LearningSignalEntity {
        val now = Instant.parse("2026-04-21T12:00:00Z")
        return LearningSignalEntity(
            id = id,
            signalKey = signalKey,
            transactionId = "transaction-1",
            provider = Provider.MTN_MOMO,
            normalizedMerchantName = normalizedMerchantName,
            normalizedPhone = normalizedPhone,
            normalizedReference = normalizedReference,
            amountBucket = "small",
            direction = TransactionDirection.DEBIT,
            moneyMovementType = MoneyMovementType.EXPENSE,
            categoryId = "food",
            signalType = "USER_CORRECTION",
            weight = 2.0f,
            createdAt = now,
            updatedAt = now,
        )
    }

    private class FixedClock(
        private val instant: Instant = Instant.parse("2026-04-21T12:00:00Z"),
    ) : AppClock {
        override fun now(): Instant = instant
    }
}
