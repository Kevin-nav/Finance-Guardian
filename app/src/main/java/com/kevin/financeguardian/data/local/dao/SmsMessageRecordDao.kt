package com.kevin.financeguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import java.time.Instant

@Dao
interface SmsMessageRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SmsMessageRecordEntity)

    @Query(
        """
        SELECT * FROM sms_message_records
        WHERE sender = :sender AND bodyHash = :bodyHash AND receivedAt = :receivedAt
        LIMIT 1
        """,
    )
    suspend fun findDuplicate(
        sender: String,
        bodyHash: String,
        receivedAt: Instant,
    ): SmsMessageRecordEntity?
}
