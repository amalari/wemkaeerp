package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.tutorial.CalloutPlacement
import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.SurfaceCode
import com.eventverse.app.domain.tutorial.TutorialAnchorId
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialRepository
import com.eventverse.app.domain.tutorial.TutorialScope
import com.eventverse.app.domain.tutorial.TutorialStep
import com.eventverse.app.infrastructure.tables.TutorialsTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PostgresTutorialRepository : TutorialRepository {

    override suspend fun findByPack(packCode: DomainPackCode): List<ModuleTutorial> = DatabaseFactory.dbQuery {
        TutorialsTable.selectAll()
            .where { TutorialsTable.packCode eq packCode.value }
            .map { toTutorial(it) }
    }

    override suspend fun findByModule(moduleId: ModuleId): List<ModuleTutorial> = DatabaseFactory.dbQuery {
        TutorialsTable.selectAll()
            .where { (TutorialsTable.scopeType eq "module") and (TutorialsTable.scopeCode eq moduleId.value) }
            .map { toTutorial(it) }
    }

    override suspend fun findAll(): List<ModuleTutorial> = DatabaseFactory.dbQuery {
        TutorialsTable.selectAll().map { toTutorial(it) }
    }

    override suspend fun save(tutorial: ModuleTutorial): Unit = DatabaseFactory.dbQuery {
        val scope = tutorial.scope
        val scopeType = when (scope) {
            is TutorialScope.Module -> "module"
            is TutorialScope.Surface -> "surface"
        }
        val scopeCode = when (scope) {
            is TutorialScope.Module -> scope.moduleId.value
            is TutorialScope.Surface -> scope.code.value
        }

        val existing = TutorialsTable.selectAll()
            .where { TutorialsTable.id eq tutorial.id.value }
            .singleOrNull()

        if (existing != null) {
            TutorialsTable.update({ TutorialsTable.id eq tutorial.id.value }) {
                it[TutorialsTable.packCode] = tutorial.id.value.substringBefore("_")
                it[TutorialsTable.scopeType] = scopeType
                it[TutorialsTable.scopeCode] = scopeCode
                it[TutorialsTable.title] = tutorial.title
                it[TutorialsTable.summary] = tutorial.summary
                it[TutorialsTable.requiredLevel] = tutorial.requiredLevel.name
                it[TutorialsTable.keywords] = tutorial.keywords.joinToString(",")
                it[TutorialsTable.steps] = serializeSteps(tutorial.steps)
                it[TutorialsTable.sampleQuestions] = tutorial.sampleQuestions.joinToString(",")
                it[TutorialsTable.updatedAt] = Clock.System.now()
            }
        } else {
            TutorialsTable.insert {
                it[TutorialsTable.id] = tutorial.id.value
                it[TutorialsTable.packCode] = tutorial.id.value.substringBefore("_")
                it[TutorialsTable.scopeType] = scopeType
                it[TutorialsTable.scopeCode] = scopeCode
                it[TutorialsTable.title] = tutorial.title
                it[TutorialsTable.summary] = tutorial.summary
                it[TutorialsTable.requiredLevel] = tutorial.requiredLevel.name
                it[TutorialsTable.keywords] = tutorial.keywords.joinToString(",")
                it[TutorialsTable.steps] = serializeSteps(tutorial.steps)
                it[TutorialsTable.sampleQuestions] = tutorial.sampleQuestions.joinToString(",")
            }
        }
    }

    override suspend fun saveAll(tutorials: List<ModuleTutorial>) {
        tutorials.forEach { save(it) }
    }

    override suspend fun deleteById(id: TutorialId) {
        DatabaseFactory.dbQuery {
            TutorialsTable.deleteWhere { TutorialsTable.id eq id.value }
        }
    }

    override suspend fun existsByPack(packCode: DomainPackCode): Boolean = DatabaseFactory.dbQuery {
        TutorialsTable.selectAll()
            .where { TutorialsTable.packCode eq packCode.value }
            .count() > 0
    }

    private fun toTutorial(row: ResultRow): ModuleTutorial {
        val scopeType = row[TutorialsTable.scopeType]
        val scopeCode = row[TutorialsTable.scopeCode] ?: ""
        val scope = when (scopeType) {
            "module" -> TutorialScope.Module(ModuleId(scopeCode))
            "surface" -> TutorialScope.Surface(SurfaceCode(scopeCode))
            else -> TutorialScope.Module(ModuleId(scopeCode)) // fallback
        }

        val steps = deserializeSteps(row[TutorialsTable.steps])
        val keywordsStr = row[TutorialsTable.keywords] ?: ""
        val questionsStr = row[TutorialsTable.sampleQuestions] ?: ""

        return ModuleTutorial(
            id = TutorialId(row[TutorialsTable.id]),
            scope = scope,
            title = row[TutorialsTable.title],
            summary = row[TutorialsTable.summary],
            steps = steps,
            sampleQuestions = if (questionsStr.isBlank()) emptyList() else questionsStr.split(","),
            keywords = if (keywordsStr.isBlank()) emptyList() else keywordsStr.split(","),
            requiredLevel = AccessLevel.valueOf(row[TutorialsTable.requiredLevel]),
        )
    }

    private fun serializeSteps(steps: List<TutorialStep>): String {
        return steps.joinToString("|||") { step ->
            listOfNotNull(
                step.title,
                step.body,
                step.anchor?.value,
                step.screen?.value,
                step.placement.name
            ).joinToString(":::")
        }
    }

    private fun deserializeSteps(data: String): List<TutorialStep> {
        if (data.isBlank()) return emptyList()
        return data.split("|||").mapNotNull { stepStr ->
            val parts = stepStr.split(":::")
            if (parts.size >= 2) {
                TutorialStep(
                    title = parts[0],
                    body = parts[1],
                    anchor = parts.getOrNull(2)?.let { TutorialAnchorId(it) },
                    screen = parts.getOrNull(3)?.let { ModuleId(it) },
                    placement = parts.getOrNull(4)?.let { CalloutPlacement.valueOf(it) } ?: CalloutPlacement.BOTTOM,
                )
            } else null
        }
    }
}
