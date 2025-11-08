package io.github.dorumrr.happytaxes.data.local.dao

import androidx.room.*
import io.github.dorumrr.happytaxes.data.local.entity.CategoryEntity
import io.github.dorumrr.happytaxes.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * DAO for category operations.
 */
@Dao
interface CategoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)
    
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE AND profileId = :profileId LIMIT 1")
    suspend fun getByNameAndProfile(name: String, profileId: String): CategoryEntity?

    @Query("""
        SELECT * FROM categories
        WHERE profileId = :profileId AND isArchived = 0
        ORDER BY displayOrder ASC, name ASC
    """)
    fun getAllActive(profileId: String): Flow<List<CategoryEntity>>

    @Query("""
        SELECT * FROM categories
        WHERE profileId = :profileId AND isArchived = 0 AND type = :type
        ORDER BY displayOrder ASC, name ASC
    """)
    fun getByType(profileId: String, type: TransactionType): Flow<List<CategoryEntity>>
    
    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM categories WHERE profileId = :profileId AND isArchived = 0")
    suspend fun getCategoryCount(profileId: String): Int
}

