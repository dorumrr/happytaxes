package io.github.dorumrr.happytaxes.di

import android.content.Context
import androidx.room.Room
import io.github.dorumrr.happytaxes.data.local.DatabaseMigrations
import io.github.dorumrr.happytaxes.data.local.HappyTaxesDatabase
import io.github.dorumrr.happytaxes.data.local.dao.CategoryDao
import io.github.dorumrr.happytaxes.data.local.dao.ProfileDao
import io.github.dorumrr.happytaxes.data.local.dao.SearchHistoryDao
import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.initializer.DatabaseInitializer
import io.github.dorumrr.happytaxes.data.repository.CategoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies.
 *
 * Provides:
 * - Standard Room database (unencrypted)
 * - DAOs for data access
 * - Repositories for business logic
 * - Database initializer
 *
 * Security Note: Database is not encrypted. For a bookkeeping app, device-level
 * security (PIN/biometric lock) provides adequate protection. Encryption would
 * add unnecessary complexity and potential compatibility issues with backups.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HappyTaxesDatabase {
        return Room.databaseBuilder(
            context,
            HappyTaxesDatabase::class.java,
            HappyTaxesDatabase.DATABASE_NAME
        )
            .addMigrations(*DatabaseMigrations.getAllMigrations())
            .build()
    }

    @Provides
    fun provideTransactionDao(database: HappyTaxesDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideCategoryDao(database: HappyTaxesDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideSearchHistoryDao(database: HappyTaxesDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    fun provideProfileDao(database: HappyTaxesDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideDatabaseInitializer(
        @ApplicationContext context: Context,
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        categoryRepository: CategoryRepository,
        transactionRepository: io.github.dorumrr.happytaxes.data.repository.TransactionRepository,
        integrityChecker: io.github.dorumrr.happytaxes.util.DatabaseIntegrityChecker,
        transactionDao: io.github.dorumrr.happytaxes.data.local.dao.TransactionDao,
        preferencesRepository: io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
    ): DatabaseInitializer {
        return DatabaseInitializer(context, dataStore, categoryRepository, transactionRepository, integrityChecker, transactionDao, preferencesRepository)
    }
}

