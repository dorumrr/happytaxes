package io.github.dorumrr.happytaxes.data.local.converter

import androidx.room.TypeConverter
import io.github.dorumrr.happytaxes.data.local.entity.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters for custom types.
 *
 * Converts between Kotlin types and SQLite-compatible types.
 */
class Converters {
    
    // ========== LocalDate ==========
    
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }
    
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }
    
    // ========== Instant ==========
    
    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilli()
    }
    
    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }
    
    // ========== BigDecimal ==========
    
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }
    
    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }
    
    // ========== TransactionType ==========

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? {
        return value?.let { TransactionType.valueOf(it) }
    }
}

