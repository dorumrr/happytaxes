package io.github.dorumrr.happytaxes.data.mapper

import io.github.dorumrr.happytaxes.data.local.entity.ProfileEntity
import io.github.dorumrr.happytaxes.domain.model.Profile

/**
 * Mapper between ProfileEntity (database) and Profile (domain).
 */
object ProfileMapper {

    /**
     * Convert entity to domain model.
     */
    fun toDomain(entity: ProfileEntity): Profile {
        return Profile(
            id = entity.id,
            name = entity.name,
            icon = entity.icon,
            color = entity.color,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Convert domain model to entity.
     */
    fun toEntity(domain: Profile): ProfileEntity {
        return ProfileEntity(
            id = domain.id,
            name = domain.name,
            icon = domain.icon,
            color = domain.color,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

