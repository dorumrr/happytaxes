package io.github.dorumrr.happytaxes.data.mapper

import io.github.dorumrr.happytaxes.data.local.ProfileConstants
import io.github.dorumrr.happytaxes.data.local.entity.TransactionEntity
import io.github.dorumrr.happytaxes.domain.model.EditHistoryEntry
import io.github.dorumrr.happytaxes.domain.model.FieldChange
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mapper between TransactionEntity (database) and Transaction (domain).
 * 
 * Handles conversion of edit history JSON to/from domain objects.
 */
object TransactionMapper {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Convert entity to domain model.
     */
    fun toDomain(entity: TransactionEntity): Transaction {
        return Transaction(
            id = entity.id,
            date = entity.date,
            type = when (entity.type) {
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.INCOME ->
                    TransactionType.INCOME
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.EXPENSE ->
                    TransactionType.EXPENSE
            },
            category = entity.category,
            description = entity.description,
            notes = entity.notes,
            amount = entity.amount,
            receiptPaths = parseReceiptPaths(entity.receiptPaths),
            isDraft = entity.isDraft,
            isDemoData = entity.isDemoData,
            isDeleted = entity.isDeleted,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            editHistory = parseEditHistory(entity.editHistory)
        )
    }

    /**
     * Convert domain model to entity.
     *
     * TEMPORARY: Uses default profile ID. Will be replaced with proper profile context in Phase 2-3.
     */
    fun toEntity(domain: Transaction, profileId: String = ProfileConstants.DEFAULT_PROFILE_ID): TransactionEntity {
        return TransactionEntity(
            id = domain.id,
            profileId = profileId,
            date = domain.date,
            type = when (domain.type) {
                TransactionType.INCOME ->
                    io.github.dorumrr.happytaxes.data.local.entity.TransactionType.INCOME
                TransactionType.EXPENSE ->
                    io.github.dorumrr.happytaxes.data.local.entity.TransactionType.EXPENSE
            },
            category = domain.category,
            description = domain.description,
            notes = domain.notes,
            amount = domain.amount,
            receiptPaths = serializeReceiptPaths(domain.receiptPaths),
            isDraft = domain.isDraft,
            isDemoData = domain.isDemoData,
            isDeleted = domain.isDeleted,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            editHistory = serializeEditHistory(domain.editHistory)
        )
    }
    
    /**
     * Parse receipt paths JSON array to list.
     * Example: ["path1.jpg", "path2.jpg"] -> List("path1.jpg", "path2.jpg")
     */
    private fun parseReceiptPaths(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()

        return try {
            this.json.decodeFromString<List<String>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize receipt paths list to JSON array.
     * Example: List("path1.jpg", "path2.jpg") -> ["path1.jpg", "path2.jpg"]
     */
    private fun serializeReceiptPaths(paths: List<String>): String? {
        if (paths.isEmpty()) return null

        return try {
            json.encodeToString(paths)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse edit history JSON to domain objects.
     */
    private fun parseEditHistory(json: String?): List<EditHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()

        return try {
            this.json.decodeFromString<List<EditHistoryEntryJson>>(json).map { entry ->
                EditHistoryEntry(
                    editedAt = java.time.Instant.ofEpochMilli(entry.editedAt),
                    changes = entry.changes.map { change ->
                        FieldChange(
                            field = change.field,
                            from = change.from,
                            to = change.to
                        )
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Serialize edit history to JSON.
     */
    private fun serializeEditHistory(history: List<EditHistoryEntry>): String? {
        if (history.isEmpty()) return null

        return try {
            val jsonHistory = history.map { entry ->
                EditHistoryEntryJson(
                    editedAt = entry.editedAt.toEpochMilli(),
                    changes = entry.changes.map { change ->
                        FieldChangeJson(
                            field = change.field,
                            from = change.from,
                            to = change.to
                        )
                    }
                )
            }
            json.encodeToString(jsonHistory)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * JSON serializable edit history entry.
 */
@kotlinx.serialization.Serializable
private data class EditHistoryEntryJson(
    val editedAt: Long,
    val changes: List<FieldChangeJson>
)

/**
 * JSON serializable field change.
 */
@kotlinx.serialization.Serializable
private data class FieldChangeJson(
    val field: String,
    val from: String,
    val to: String
)

