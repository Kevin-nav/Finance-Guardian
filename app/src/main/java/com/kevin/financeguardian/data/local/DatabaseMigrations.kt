package com.kevin.financeguardian.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN providerTransactionId TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN dedupeKey TEXT")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS learning_signals (
                    id TEXT NOT NULL,
                    signalKey TEXT NOT NULL,
                    transactionId TEXT,
                    provider TEXT NOT NULL,
                    normalizedMerchantName TEXT,
                    normalizedPhone TEXT,
                    normalizedReference TEXT,
                    amountBucket TEXT,
                    direction TEXT NOT NULL,
                    moneyMovementType TEXT NOT NULL,
                    categoryId TEXT,
                    signalType TEXT NOT NULL,
                    weight REAL NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_learning_signals_signalKey ON learning_signals(signalKey)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_learning_signals_normalizedMerchantName ON learning_signals(normalizedMerchantName)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_learning_signals_normalizedPhone ON learning_signals(normalizedPhone)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_learning_signals_normalizedReference ON learning_signals(normalizedReference)",
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN balanceReliability TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE transactions ADD COLUMN flowId TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN flowType TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN flowStatus TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN plannedUse TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN includedInSpendingTotals INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN includedInIncomeTotals INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                UPDATE transactions
                SET includedInSpendingTotals = CASE
                    WHEN moneyMovementType IN ('EXPENSE', 'SUBSCRIPTION_CANDIDATE')
                        OR (moneyMovementType = 'UNKNOWN' AND direction = 'DEBIT')
                    THEN 1 ELSE 0 END,
                    includedInIncomeTotals = CASE
                    WHEN moneyMovementType = 'INCOME'
                        OR (moneyMovementType = 'UNKNOWN' AND direction = 'CREDIT')
                    THEN 1 ELSE 0 END
                """.trimIndent(),
            )
        }
    }
}
