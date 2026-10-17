package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentTutorialAnchors
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialAnchorId
import com.eventverse.app.domain.tutorial.TutorialSource
import kotlinx.coroutines.runBlocking

/**
 * Database-backed TutorialSource that reads tutorials from Postgres.
 * Seeds tutorials from shipped packs on first load if DB is empty.
 */
class DbTutorialSource(
    private val repository: PostgresTutorialRepository
) : TutorialSource {

    private var cachedTutorials: List<ModuleTutorial>? = null
    private val cachedAnchors = mutableMapOf<DomainPackCode, Set<TutorialAnchorId>>()

    override fun tutorialsFor(pack: DomainPackCode): List<ModuleTutorial> {
        // Load and cache on first call (blocking)
        if (cachedTutorials == null) {
            cachedTutorials = runBlocking {
                seedAndLoad(pack)
            }
        }
        return cachedTutorials ?: emptyList()
    }

    private suspend fun seedAndLoad(packCode: DomainPackCode): List<ModuleTutorial> {
        return try {
            // Check if DB has tutorials for this pack
            val existingInDb = repository.findByPack(packCode)
            
            if (existingInDb.isNotEmpty()) {
                // Use DB tutorials
                existingInDb
            } else {
                // Seed from shipped pack and save to DB
                val shippedTutorials = getShippedTutorials(packCode)
                if (shippedTutorials.isNotEmpty()) {
                    repository.saveAll(shippedTutorials)
                }
                shippedTutorials
            }
        } catch (e: Exception) {
            // Fallback to shipped if DB fails
            getShippedTutorials(packCode)
        }
    }

    private fun getShippedTutorials(packCode: DomainPackCode): List<ModuleTutorial> {
        return when (packCode.value) {
            GarmentDomainPack.CODE.value -> GarmentDomainPack.pack.tutorials
            else -> emptyList()
        }
    }

    private val DomainPack.tutorials: List<ModuleTutorial>
        get() {
            // Get tutorials for this pack from the pack's module definitions
            return modules.mapNotNull { module ->
                // Create a default tutorial for each module if not defined
                createDefaultTutorial(module.id, module.displayName)
            }
        }

    private fun createDefaultTutorial(moduleId: com.eventverse.app.domain.pack.ModuleId, displayName: String): ModuleTutorial? {
        // This creates a basic tutorial from module metadata
        // In the future, this could be enhanced to read from ModuleDefinition.tutorial
        return null // Handled by GarmentTutorials for now
    }

    override fun anchorsFor(pack: DomainPackCode): Set<TutorialAnchorId> {
        return cachedAnchors.getOrPut(pack) {
            runBlocking {
                try {
                    val dbTutorials = repository.findAll()
                    if (dbTutorials.isNotEmpty()) {
                        dbTutorials.flatMap { it.steps.mapNotNull { step -> step.anchor } }.toSet()
                    } else {
                        getShippedAnchors(pack)
                    }
                } catch (e: Exception) {
                    getShippedAnchors(pack)
                }
            }
        }
    }

    private fun getShippedAnchors(packCode: DomainPackCode): Set<TutorialAnchorId> {
        return when (packCode.value) {
            GarmentDomainPack.CODE.value -> GarmentTutorialAnchors.all
            else -> emptySet()
        }
    }

    /** Force reload from DB (useful for testing or admin refresh) */
    fun refresh() {
        cachedTutorials = null
        cachedAnchors.clear()
    }
}

/** Tutorial extensions for DomainPack */
private val DomainPack.tutorials: List<ModuleTutorial>
    get() = when (code.value) {
        "garment" -> com.eventverse.app.domain.pack.GarmentTutorials.all
        else -> emptyList()
    }
