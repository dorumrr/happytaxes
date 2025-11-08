package io.github.dorumrr.happytaxes.data.mapper

import io.github.dorumrr.happytaxes.data.local.ProfileConstants
import io.github.dorumrr.happytaxes.data.local.entity.CategoryEntity
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.TransactionType

/**
 * Mapper between CategoryEntity (database) and Category (domain).
 */
object CategoryMapper {

    fun toDomain(entity: CategoryEntity): Category {
        return Category(
            id = entity.id,
            name = entity.name,
            type = when (entity.type) {
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.INCOME ->
                    TransactionType.INCOME
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.EXPENSE ->
                    TransactionType.EXPENSE
            },
            icon = entity.icon,
            color = entity.color,
            taxFormReference = entity.taxFormReference,
            taxFormDescription = entity.taxFormDescription,
            countryCode = entity.countryCode,
            isCustom = entity.isCustom,
            isArchived = entity.isArchived,
            displayOrder = entity.displayOrder,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Convert domain model to entity.
     *
     * TEMPORARY: Uses default profile ID. Will be replaced with proper profile context in Phase 2-3.
     */
    fun toEntity(domain: Category, profileId: String = ProfileConstants.DEFAULT_PROFILE_ID): CategoryEntity {
        return CategoryEntity(
            id = domain.id,
            profileId = profileId,
            name = domain.name,
            type = when (domain.type) {
                TransactionType.INCOME ->
                    io.github.dorumrr.happytaxes.data.local.entity.TransactionType.INCOME
                TransactionType.EXPENSE ->
                    io.github.dorumrr.happytaxes.data.local.entity.TransactionType.EXPENSE
            },
            icon = domain.icon,
            color = domain.color,
            taxFormReference = domain.taxFormReference,
            taxFormDescription = domain.taxFormDescription,
            countryCode = domain.countryCode,
            isCustom = domain.isCustom,
            isArchived = domain.isArchived,
            displayOrder = domain.displayOrder,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

