package io.github.dorumrr.happytaxes.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileRepository

/**
 * Entry point for accessing repositories from non-Hilt contexts.
 * Used when repositories need to be accessed from Composables that don't have ViewModels.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun preferencesRepository(): PreferencesRepository
    fun profileRepository(): ProfileRepository
}

