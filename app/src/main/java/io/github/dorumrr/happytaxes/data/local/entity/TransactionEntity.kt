package io.github.dorumrr.happytaxes.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Transaction entity representing income or expense entries.
 *
 * Business Rules:
 * - Cash basis accounting: transaction date = payment date
 * - Expenses MUST have receipts (isDraft=true until receipt added)
 * - Income can be logged without proof
 * - Soft delete: isDeleted=true, hard delete only on app reset
 * - Edit history tracks from/to values for audit trail
 * - Multi-profile: Each transaction belongs to exactly one profile
 *
 * Indices:
 * - profileId: For profile-based filtering (all queries)
 * - date: For date range queries (reports, filtering)
 * - type, category: For filtering by type and category
 * - isDeleted, deletedAt: For soft delete queries (Recently Deleted screen)
 * - isDraft: For draft filtering (Draft banner, reports exclusion)
 * - profileId, isDeleted, category: Composite index for filtered search queries (Section 14.5)
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["date"]),
        Index(value = ["type", "category"]),
        Index(value = ["isDeleted", "deletedAt"]),
        Index(value = ["isDraft"]),
        Index(value = ["profileId", "isDeleted", "category"]) // For search performance
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,

    // Profile association (multi-profile support)
    val profileId: String,

    // Core fields
    val date: LocalDate,
    val type: TransactionType,
    val category: String,
    val description: String?,
    val notes: String?,

    // Amount field (single currency - user's base currency)
    // For cash-basis accounting, this is the amount that actually hit the bank account
    val amount: BigDecimal,

    // Receipt management (JSON array of paths for multiple receipts)
    // Example: ["path1.jpg", "path2.jpg"] or null if no receipts
    val receiptPaths: String?,

    // Draft system (expenses without receipts)
    val isDraft: Boolean = false,

    // Demo data flag (for onboarding sample transactions)
    val isDemoData: Boolean = false,

    // Soft delete
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,

    // Timestamps
    val createdAt: Instant,
    val updatedAt: Instant,

    // Edit history (JSON array with from/to values)
    val editHistory: String? = null
)

enum class TransactionType {
    INCOME,
    EXPENSE
}

