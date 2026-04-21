package com.kevin.financeguardian.data.local.converter

import androidx.room.TypeConverter
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import java.time.Instant

class RoomConverters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun providerToString(value: Provider?): String? = value?.name

    @TypeConverter
    fun stringToProvider(value: String?): Provider? = value?.let(Provider::valueOf)

    @TypeConverter
    fun transactionDirectionToString(value: TransactionDirection?): String? = value?.name

    @TypeConverter
    fun stringToTransactionDirection(value: String?): TransactionDirection? =
        value?.let(TransactionDirection::valueOf)

    @TypeConverter
    fun moneyMovementTypeToString(value: MoneyMovementType?): String? = value?.name

    @TypeConverter
    fun stringToMoneyMovementType(value: String?): MoneyMovementType? =
        value?.let(MoneyMovementType::valueOf)

    @TypeConverter
    fun categoryTypeToString(value: CategoryType?): String? = value?.name

    @TypeConverter
    fun stringToCategoryType(value: String?): CategoryType? = value?.let(CategoryType::valueOf)

    @TypeConverter
    fun parseStatusToString(value: ParseStatus?): String? = value?.name

    @TypeConverter
    fun stringToParseStatus(value: String?): ParseStatus? = value?.let(ParseStatus::valueOf)
}
