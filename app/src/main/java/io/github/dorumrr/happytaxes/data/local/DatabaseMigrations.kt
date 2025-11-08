package io.github.dorumrr.happytaxes.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for HappyTaxes.
 *
 * Version History:
 * - Version 1: Initial schema (transactions, categories)
 * - Version 2: Added recurrences, currency_rates, sync_queue
 * - Version 3: Added unique constraint on currency_rates (currency, date)
 * - Version 4: Added exchange rate fields to transactions (Phase 5)
 * - Version 5: Added indices to transactions table (Phase 5)
 * - Version 6: Added search_history table (Phase 6 Task 2)
 * - Version 7: Removed sync_queue table (cloud sync removed)
 * - Version 8: Removed recurrences table and recurrenceId from transactions (recurring transactions feature removed)
 * - Version 9: Updated categories table for multi-country support (added countryCode, renamed hmrcBox/hmrcDescription to taxFormReference/taxFormDescription)
 * - Version 10: Renamed amountGbp to amountInBaseCurrency in transactions table
 * - Version 11: Updated currency_rates table (renamed rateToGbp to rateToBaseCurrency, added baseCurrency field)
 * - Version 12: Removed multi-currency support - dropped currency_rates table, simplified transactions to single amount field
 * - Version 13: Added multi-profile support - added profiles table, added profileId to transactions/categories/search_history
 * - Version 14: Added isDemoData flag to transactions table for identifying demo/sample transactions
 * - Version 15: Added unique constraint on categories (profileId, name, type) and deduplicated existing categories
 */
object DatabaseMigrations {
    
    /**
     * Migration from version 3 to version 4.
     * 
     * Changes:
     * 1. Add currency field to transactions (default "GBP")
     * 2. Rename amount to amountOriginal
     * 3. Add amountGbp field (copy from amountOriginal for existing records)
     * 4. Add exchangeRate field (nullable)
     * 5. Add exchangeRateDate field (nullable)
     * 6. Add isManualRate field (default false)
     * 
     * Strategy:
     * - Create new table with new schema
     * - Copy data from old table with transformations
     * - Drop old table
     * - Rename new table to original name
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Create new transactions table with Phase 5 schema
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS transactions_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    date INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    category TEXT NOT NULL,
                    description TEXT,
                    notes TEXT,
                    amountOriginal TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    amountGbp TEXT NOT NULL,
                    exchangeRate TEXT,
                    exchangeRateDate INTEGER,
                    isManualRate INTEGER NOT NULL DEFAULT 0,
                    recurrenceId TEXT,
                    receiptPaths TEXT,
                    isDraft INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    editHistory TEXT
                )
            """.trimIndent())
            
            // Step 2: Copy data from old table to new table
            // For existing records:
            // - currency = "GBP" (default)
            // - amountOriginal = amount (old field)
            // - amountGbp = amount (same as original since currency is GBP)
            // - exchangeRate = NULL (no conversion needed for GBP)
            // - exchangeRateDate = NULL
            // - isManualRate = 0 (false)
            database.execSQL("""
                INSERT INTO transactions_new (
                    id, date, type, category, description, notes,
                    amountOriginal, currency, amountGbp,
                    exchangeRate, exchangeRateDate, isManualRate,
                    recurrenceId, receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                )
                SELECT 
                    id, date, type, category, description, notes,
                    amount, 'GBP', amount,
                    NULL, NULL, 0,
                    recurrenceId, receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                FROM transactions
            """.trimIndent())
            
            // Step 3: Drop old table
            database.execSQL("DROP TABLE transactions")
            
            // Step 4: Rename new table to original name
            database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
        }
    }

    /**
     * Migration from version 4 to version 5.
     *
     * Changes:
     * 1. Add indices to transactions table for performance optimization:
     *    - Index on date (for date range queries)
     *    - Index on type, category (for filtering)
     *    - Index on isDeleted, deletedAt (for soft delete queries)
     *    - Index on isDraft (for draft filtering)
     *
     * Strategy:
     * - Create indices on existing table (no data migration needed)
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create indices for performance optimization
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions(date)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_category ON transactions(type, category)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDeleted_deletedAt ON transactions(isDeleted, deletedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDraft ON transactions(isDraft)")
        }
    }

    /**
     * Migration from version 5 to version 6.
     *
     * Changes:
     * 1. Add search_history table for storing recent searches (Phase 6 Task 2)
     *    - id: Primary key (auto-increment)
     *    - query: Search query text (unique)
     *    - timestamp: When search was performed
     *
     * Strategy:
     * - Create new table (no data migration needed)
     * - Add indices for performance
     *
     * PRD Reference: Section 4.9 - "Store last 20 searches locally"
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create search_history table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS search_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    query TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())

            // Create unique index on query (no duplicate searches)
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)")

            // Create index on timestamp (for sorting by most recent)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history(timestamp)")
        }
    }

    /**
     * Migration from version 6 to version 7.
     *
     * Changes:
     * 1. Drop sync_queue table (cloud sync removed)
     *
     * Strategy:
     * - Drop table (no data migration needed)
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Drop sync_queue table (cloud sync removed)
            database.execSQL("DROP TABLE IF EXISTS sync_queue")
        }
    }

    /**
     * Migration from version 7 to version 8.
     *
     * Changes:
     * 1. Drop recurrences table (recurring transactions feature removed)
     * 2. Remove recurrenceId field from transactions table
     *
     * Strategy:
     * - Drop recurrences table
     * - Create new transactions table without recurrenceId
     * - Copy data from old table
     * - Drop old table and rename new table
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Drop recurrences table
            database.execSQL("DROP TABLE IF EXISTS recurrences")

            // Step 2: Create new transactions table without recurrenceId
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS transactions_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    date INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    category TEXT NOT NULL,
                    description TEXT,
                    notes TEXT,
                    amountOriginal TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    amountGbp TEXT NOT NULL,
                    exchangeRate TEXT,
                    exchangeRateDate INTEGER,
                    isManualRate INTEGER NOT NULL DEFAULT 0,
                    receiptPaths TEXT,
                    isDraft INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    editHistory TEXT
                )
            """.trimIndent())

            // Step 3: Copy data from old table to new table (excluding recurrenceId)
            database.execSQL("""
                INSERT INTO transactions_new (
                    id, date, type, category, description, notes,
                    amountOriginal, currency, amountGbp,
                    exchangeRate, exchangeRateDate, isManualRate,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                )
                SELECT
                    id, date, type, category, description, notes,
                    amountOriginal, currency, amountGbp,
                    exchangeRate, exchangeRateDate, isManualRate,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                FROM transactions
            """.trimIndent())

            // Step 4: Recreate indices on new table
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions_new(date)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_category ON transactions_new(type, category)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDeleted_deletedAt ON transactions_new(isDeleted, deletedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDraft ON transactions_new(isDraft)")

            // Step 5: Drop old table
            database.execSQL("DROP TABLE transactions")

            // Step 6: Rename new table to original name
            database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
        }
    }

    /**
     * Migration from version 8 to version 9.
     *
     * Changes:
     * 1. Add countryCode field to categories table
     * 2. Rename hmrcBox to taxFormReference
     * 3. Rename hmrcDescription to taxFormDescription
     *
     * Strategy:
     * - Create new categories table with updated schema
     * - Copy data from old table with column renames
     * - Drop old table and rename new table
     *
     * Note: This migration supports the multi-country category system.
     * Categories are now country-agnostic with generic tax form references.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Create new categories table with updated schema
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS categories_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT,
                    color TEXT,
                    taxFormReference TEXT,
                    taxFormDescription TEXT,
                    countryCode TEXT,
                    isCustom INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0,
                    displayOrder INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())

            // Step 2: Copy data from old table to new table
            // Rename hmrcBox → taxFormReference, hmrcDescription → taxFormDescription
            // Set countryCode to NULL for existing categories (will be updated by app logic)
            database.execSQL("""
                INSERT INTO categories_new (
                    id, name, type, icon, color,
                    taxFormReference, taxFormDescription, countryCode,
                    isCustom, isArchived, displayOrder,
                    createdAt, updatedAt
                )
                SELECT
                    id, name, type, icon, color,
                    hmrcBox, hmrcDescription, NULL,
                    isCustom, isArchived, displayOrder,
                    createdAt, updatedAt
                FROM categories
            """.trimIndent())

            // Step 3: Drop old table
            database.execSQL("DROP TABLE categories")

            // Step 4: Rename new table to original name
            database.execSQL("ALTER TABLE categories_new RENAME TO categories")
        }
    }

    /**
     * Migration from version 9 to version 10.
     *
     * Changes:
     * 1. Rename amountGbp to amountInBaseCurrency in transactions table
     *
     * Strategy:
     * - Create new transactions table with renamed column
     * - Copy data from old table
     * - Drop old table and rename new table
     * - Recreate indices
     *
     * Note: This migration makes the transactions table base-currency agnostic.
     * The amountInBaseCurrency field now represents the amount in the user's
     * selected base currency (not hardcoded to GBP).
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Create new transactions table with renamed column
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS transactions_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    date INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    category TEXT NOT NULL,
                    description TEXT,
                    notes TEXT,
                    amountOriginal TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    amountInBaseCurrency TEXT NOT NULL,
                    exchangeRate TEXT,
                    exchangeRateDate INTEGER,
                    isManualRate INTEGER NOT NULL DEFAULT 0,
                    receiptPaths TEXT,
                    isDraft INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    editHistory TEXT
                )
            """.trimIndent())

            // Step 2: Copy data from old table to new table
            // Rename amountGbp → amountInBaseCurrency
            database.execSQL("""
                INSERT INTO transactions_new (
                    id, date, type, category, description, notes,
                    amountOriginal, currency, amountInBaseCurrency,
                    exchangeRate, exchangeRateDate, isManualRate,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                )
                SELECT
                    id, date, type, category, description, notes,
                    amountOriginal, currency, amountGbp,
                    exchangeRate, exchangeRateDate, isManualRate,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                FROM transactions
            """.trimIndent())

            // Step 3: Recreate indices on new table
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions_new(date)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_category ON transactions_new(type, category)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDeleted_deletedAt ON transactions_new(isDeleted, deletedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDraft ON transactions_new(isDraft)")

            // Step 4: Drop old table
            database.execSQL("DROP TABLE transactions")

            // Step 5: Rename new table to original name
            database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
        }
    }

    /**
     * Migration from version 10 to version 11.
     *
     * Changes:
     * 1. Rename rateToGbp to rateToBaseCurrency in currency_rates table
     * 2. Add baseCurrency field to track which currency rates are relative to
     *
     * Strategy:
     * - Create new currency_rates table with updated schema
     * - Copy data from old table with column rename
     * - Set baseCurrency to "GBP" for existing rates (backward compatibility)
     * - Drop old table and rename new table
     * - Recreate unique index
     *
     * Note: Existing rates are preserved with baseCurrency="GBP" for backward
     * compatibility. When user changes base currency, new rates will be fetched.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Create new currency_rates table with updated schema
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS currency_rates_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    currency TEXT NOT NULL,
                    rateToBaseCurrency TEXT NOT NULL,
                    baseCurrency TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())

            // Step 2: Copy data from old table to new table
            // Rename rateToGbp → rateToBaseCurrency
            // Set baseCurrency to "GBP" for existing rates (backward compatibility)
            database.execSQL("""
                INSERT INTO currency_rates_new (
                    id, currency, rateToBaseCurrency, baseCurrency,
                    date, source, updatedAt
                )
                SELECT
                    id, currency, rateToGbp, 'GBP',
                    date, source, updatedAt
                FROM currency_rates
            """.trimIndent())

            // Step 3: Recreate unique index on new table
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_currency_rates_currency_date ON currency_rates_new(currency, date)")

            // Step 4: Drop old table
            database.execSQL("DROP TABLE currency_rates")

            // Step 5: Rename new table to original name
            database.execSQL("ALTER TABLE currency_rates_new RENAME TO currency_rates")
        }
    }

    /**
     * Migration from version 11 to version 12.
     *
     * Changes:
     * 1. Drop currency_rates table entirely (multi-currency support removed)
     * 2. Remove multi-currency fields from transactions table:
     *    - Remove amountOriginal
     *    - Remove currency
     *    - Remove exchangeRate
     *    - Remove exchangeRateDate
     *    - Remove isManualRate
     * 3. Rename amountInBaseCurrency to amount (single currency model)
     *
     * Strategy:
     * - Drop currency_rates table
     * - Create new transactions table with simplified schema
     * - Copy data (use amountInBaseCurrency as the new amount)
     * - Drop old table and rename new table
     * - Recreate indices
     *
     * Rationale:
     * For cash-basis accounting, when money hits the bank account, it's already
     * converted to local currency. Multi-currency tracking adds unnecessary
     * complexity. Users record what the bank statement shows.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Drop currency_rates table entirely
            database.execSQL("DROP TABLE IF EXISTS currency_rates")

            // Step 2: Create new transactions table with simplified schema
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS transactions_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    date INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    category TEXT NOT NULL,
                    description TEXT,
                    notes TEXT,
                    amount TEXT NOT NULL,
                    receiptPaths TEXT,
                    isDraft INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    editHistory TEXT
                )
            """.trimIndent())

            // Step 3: Copy data from old table to new table
            // Use amountInBaseCurrency as the new amount field
            database.execSQL("""
                INSERT INTO transactions_new (
                    id, date, type, category, description, notes, amount,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                )
                SELECT
                    id, date, type, category, description, notes, amountInBaseCurrency,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                FROM transactions
            """.trimIndent())

            // Step 4: Recreate indices on new table
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions_new(date)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_category ON transactions_new(type, category)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDeleted_deletedAt ON transactions_new(isDeleted, deletedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDraft ON transactions_new(isDraft)")

            // Step 5: Drop old table
            database.execSQL("DROP TABLE transactions")

            // Step 6: Rename new table to original name
            database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
        }
    }

    /**
     * Migration from version 12 to version 13.
     *
     * Changes:
     * 1. Create profiles table
     * 2. Add default "Business" profile
     * 3. Add profileId column to transactions table
     * 4. Add profileId column to categories table
     * 5. Add profileId column to search_history table
     * 6. Update all existing records to use default profile
     *
     * Strategy:
     * - Create profiles table
     * - Insert default "Business" profile with fixed UUID
     * - Recreate transactions/categories/search_history tables with profileId
     * - Copy data with default profileId
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val defaultProfileId = "business-profile-default-uuid"
            val currentTimestamp = System.currentTimeMillis() * 1000000 // Convert to nanoseconds for Instant

            // Step 1: Create profiles table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS profiles (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    color TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())

            // Step 2: Insert default "Business" profile
            database.execSQL("""
                INSERT INTO profiles (id, name, icon, color, createdAt, updatedAt)
                VALUES ('$defaultProfileId', 'Business', 'business', '#1976D2', $currentTimestamp, $currentTimestamp)
            """.trimIndent())

            // Step 3: Recreate transactions table with profileId
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS transactions_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    profileId TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    category TEXT NOT NULL,
                    description TEXT,
                    notes TEXT,
                    amount TEXT NOT NULL,
                    receiptPaths TEXT,
                    isDraft INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    editHistory TEXT
                )
            """.trimIndent())

            // Copy transactions data with default profileId
            database.execSQL("""
                INSERT INTO transactions_new (
                    id, profileId, date, type, category, description, notes, amount,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                )
                SELECT
                    id, '$defaultProfileId', date, type, category, description, notes, amount,
                    receiptPaths, isDraft, isDeleted, deletedAt,
                    createdAt, updatedAt, editHistory
                FROM transactions
            """.trimIndent())

            // Recreate indices for transactions
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_profileId ON transactions_new(profileId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions_new(date)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_category ON transactions_new(type, category)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDeleted_deletedAt ON transactions_new(isDeleted, deletedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDraft ON transactions_new(isDraft)")

            database.execSQL("DROP TABLE transactions")
            database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

            // Step 4: Recreate categories table with profileId
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS categories_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    profileId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT,
                    color TEXT,
                    taxFormReference TEXT,
                    taxFormDescription TEXT,
                    countryCode TEXT,
                    isCustom INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0,
                    displayOrder INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())

            // Copy categories data with default profileId
            database.execSQL("""
                INSERT INTO categories_new (
                    id, profileId, name, type, icon, color,
                    taxFormReference, taxFormDescription, countryCode,
                    isCustom, isArchived, displayOrder,
                    createdAt, updatedAt
                )
                SELECT
                    id, '$defaultProfileId', name, type, icon, color,
                    taxFormReference, taxFormDescription, countryCode,
                    isCustom, isArchived, displayOrder,
                    createdAt, updatedAt
                FROM categories
            """.trimIndent())

            database.execSQL("DROP TABLE categories")
            database.execSQL("ALTER TABLE categories_new RENAME TO categories")

            // Step 5: Recreate search_history table with profileId
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS search_history_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    profileId TEXT NOT NULL,
                    query TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())

            // Copy search_history data with default profileId
            database.execSQL("""
                INSERT INTO search_history_new (id, profileId, query, timestamp)
                SELECT id, '$defaultProfileId', query, timestamp
                FROM search_history
            """.trimIndent())

            // Recreate indices for search_history
            database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_profileId ON search_history_new(profileId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_profileId_query ON search_history_new(profileId, query)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history_new(timestamp)")

            database.execSQL("DROP TABLE search_history")
            database.execSQL("ALTER TABLE search_history_new RENAME TO search_history")
        }
    }

    /**
     * Migration from version 13 to version 14.
     *
     * Changes:
     * 1. Add isDemoData column to transactions table (default false)
     *
     * Strategy:
     * - Add column with default value (no data migration needed)
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add isDemoData column to transactions table
            database.execSQL("ALTER TABLE transactions ADD COLUMN isDemoData INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Migration from version 14 to version 15.
     *
     * Changes:
     * 1. Deduplicate categories (same profileId + name + type)
     * 2. Add unique index on categories (profileId, name, type)
     *
     * Strategy:
     * - Find duplicate categories (same profileId, name, type)
     * - Keep the oldest category (by createdAt timestamp, then by id for determinism)
     * - Delete duplicate categories
     * - Add unique index to prevent future duplicates
     *
     * Note: Transactions reference categories by NAME (not ID), so duplicate
     * categories with the same name don't affect transaction integrity.
     * We just need to remove the duplicate category records.
     *
     * This fixes the issue where categories were seeded multiple times,
     * causing duplicates to appear in filter dropdowns.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Create a temp table with the IDs of categories to keep
            // For each (profileId, name, type) group, keep the one with MIN(createdAt), MIN(id)
            database.execSQL("""
                CREATE TEMPORARY TABLE categories_to_keep AS
                SELECT c1.id
                FROM categories c1
                WHERE c1.id = (
                    SELECT c2.id
                    FROM categories c2
                    WHERE c2.profileId = c1.profileId
                    AND c2.name = c1.name
                    AND c2.type = c1.type
                    ORDER BY c2.createdAt ASC, c2.id ASC
                    LIMIT 1
                )
            """.trimIndent())

            // Step 2: Delete duplicate categories (keep only the ones in categories_to_keep)
            database.execSQL("""
                DELETE FROM categories
                WHERE id NOT IN (SELECT id FROM categories_to_keep)
            """.trimIndent())

            // Step 3: Drop temporary table
            database.execSQL("DROP TABLE categories_to_keep")

            // Step 4: Add unique index to prevent future duplicates
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS index_categories_profileId_name_type
                ON categories(profileId, name, type)
            """.trimIndent())
        }
    }

    /**
     * Get all migrations for the database.
     * Add new migrations here as the database evolves.
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15
        )
    }
}

