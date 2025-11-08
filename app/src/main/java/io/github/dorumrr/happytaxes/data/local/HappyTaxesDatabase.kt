package io.github.dorumrr.happytaxes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.dorumrr.happytaxes.data.local.converter.Converters
import io.github.dorumrr.happytaxes.data.local.dao.CategoryDao
import io.github.dorumrr.happytaxes.data.local.dao.ProfileDao
import io.github.dorumrr.happytaxes.data.local.dao.SearchHistoryDao
import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.local.entity.CategoryEntity
import io.github.dorumrr.happytaxes.data.local.entity.ProfileEntity
import io.github.dorumrr.happytaxes.data.local.entity.SearchHistoryEntity
import io.github.dorumrr.happytaxes.data.local.entity.TransactionEntity

/**
 * Main Room database for HappyTaxes.
 *
 * Security:
 * - Standard SQLite database (unencrypted)
 * - Device-level security (PIN/biometric) provides adequate protection
 * - Simplifies backup/restore across devices
 *
 * Version History:
 * - Version 1: Initial schema (transactions, categories)
 * - Version 2: Added recurrences, currency_rates, sync_queue
 * - Version 3: Added unique constraint on currency_rates (currency, date)
 * - Version 4: Added exchange rate fields to transactions (exchangeRate, exchangeRateDate, isManualRate)
 * - Version 5: Added indices to transactions table (date, type+category, isDeleted+deletedAt, isDraft)
 * - Version 6: Added search_history table for storing recent searches (Phase 6 Task 2)
 * - Version 7: Removed sync_queue table (cloud sync removed)
 * - Version 8: Removed recurrences table and recurrenceId from transactions (recurring transactions feature removed)
 * - Version 9: Updated categories table for multi-country support (added countryCode, renamed hmrcBox/hmrcDescription to taxFormReference/taxFormDescription)
 * - Version 10: Renamed amountGbp to amountInBaseCurrency in transactions table
 * - Version 11: Updated currency_rates table (renamed rateToGbp to rateToBaseCurrency, added baseCurrency field)
 * - Version 12: Removed multi-currency support - dropped currency_rates table, simplified transactions to single amount field
 * - Version 13: Added multi-profile support - added profiles table, added profileId to transactions/categories/search_history
 * - Version 14: Added isDemoData flag to transactions table for identifying demo/sample transactions
 * - Version 15: Added unique constraint on categories (profileId, name, type) and deduplicated existing categories
 *
 * Entities: profiles, transactions, categories, search_history
 */
@Database(
    entities = [
        ProfileEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        SearchHistoryEntity::class
    ],
    version = 15,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HappyTaxesDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val DATABASE_NAME = "happytaxes.db"
    }
}

