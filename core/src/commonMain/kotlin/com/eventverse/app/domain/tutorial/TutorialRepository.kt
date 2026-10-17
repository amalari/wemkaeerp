package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.ModuleId

/**
 * Repository for persisting tutorials to enable RAG and future admin editing.
 *
 * Tutorials are platform-level (pack-level), stored per pack in the database.
 * This allows:
 * 1. Vector search for RAG-based help system
 * 2. Future admin UI to edit tutorials
 * 3. Better performance for large tutorial catalogs
 */
interface TutorialRepository {
    /**
     * Get all tutorials for a specific pack.
     */
    suspend fun findByPack(packCode: DomainPackCode): List<ModuleTutorial>

    /**
     * Get all tutorials for a specific module.
     */
    suspend fun findByModule(moduleId: ModuleId): List<ModuleTutorial>

    /**
     * Get all tutorials (for system-wide operations like seeding).
     */
    suspend fun findAll(): List<ModuleTutorial>

    /**
     * Save a tutorial (insert or update).
     */
    suspend fun save(tutorial: ModuleTutorial)

    /**
     * Save multiple tutorials at once (batch operation for seeding).
     */
    suspend fun saveAll(tutorials: List<ModuleTutorial>)

    /**
     * Delete a tutorial by ID.
     */
    suspend fun deleteById(id: TutorialId)

    /**
     * Check if tutorials exist for a pack.
     */
    suspend fun existsByPack(packCode: DomainPackCode): Boolean
}
