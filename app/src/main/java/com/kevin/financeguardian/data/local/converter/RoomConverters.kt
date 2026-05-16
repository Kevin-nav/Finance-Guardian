package com.kevin.financeguardian.data.local.converter

import androidx.room.TypeConverter
import com.kevin.financeguardian.domain.model.CategoryType
import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.model.TransactionDirection
import com.kevin.financeguardian.domain.parser.BalanceReliability
import com.kevin.financeguardian.domain.parser.MoneyMovementChannel
import com.kevin.financeguardian.domain.parser.TransactionFlowStatus
import com.kevin.financeguardian.domain.parser.TransactionFlowType
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

    @TypeConverter
    fun balanceReliabilityToString(value: BalanceReliability?): String? = value?.name

    @TypeConverter
    fun stringToBalanceReliability(value: String?): BalanceReliability? = value?.let(BalanceReliability::valueOf)

    @TypeConverter
    fun transactionFlowTypeToString(value: TransactionFlowType?): String? = value?.name

    @TypeConverter
    fun stringToTransactionFlowType(value: String?): TransactionFlowType? = value?.let(TransactionFlowType::valueOf)

    @TypeConverter
    fun transactionFlowStatusToString(value: TransactionFlowStatus?): String? = value?.name

    @TypeConverter
    fun stringToTransactionFlowStatus(value: String?): TransactionFlowStatus? = value?.let(TransactionFlowStatus::valueOf)

    @TypeConverter
    fun moneyMovementChannelToString(value: MoneyMovementChannel?): String? = value?.name

    @TypeConverter
    fun stringToMoneyMovementChannel(value: String?): MoneyMovementChannel? = value?.let(MoneyMovementChannel::valueOf)

    @TypeConverter
    fun instrumentTypeToString(value: InstrumentType?): String? = value?.name

    @TypeConverter
    fun stringToInstrumentType(value: String?): InstrumentType? = value?.let(InstrumentType::valueOf)

    @TypeConverter
    fun instrumentProviderToString(value: InstrumentProvider?): String? = value?.name

    @TypeConverter
    fun stringToInstrumentProvider(value: String?): InstrumentProvider? = value?.let(InstrumentProvider::valueOf)
}
