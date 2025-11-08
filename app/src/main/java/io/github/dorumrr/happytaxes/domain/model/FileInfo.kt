package io.github.dorumrr.happytaxes.domain.model

import io.github.dorumrr.happytaxes.util.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * File metadata for sync manifest.
 * 
 * Contains path, SHA-256 hash, size, and last modified timestamp
 * for integrity verification during sync operations.
 * 
 * PRD Reference: Section 4.5 - Backup Verification
 */
@Serializable
data class FileInfo(
    val path: String,
    val hash: String,
    val size: Long,
    @Serializable(with = InstantSerializer::class)
    val lastModified: Instant
)

