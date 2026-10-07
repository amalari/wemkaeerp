package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Satu peristiwa progres run. [id] urut dari 1 — dipakai `Last-Event-ID` agar sambung ulang tak kehilangan peristiwa. */
data class RunEvent(val id: Long, val type: String, val data: String)

/**
 * Satu run chat Builder (PLAN-builder-interview-chat §3.3): urutan peristiwa **progres** yang bisa diputar ulang dari
 * posisi mana pun. Riwayat chat di DB tetap sumber kebenaran — run hanya memberi tahu "sedang apa", jadi run yang
 * hilang (restart server) tidak menghilangkan data apa pun.
 */
class BuilderRun(val id: String, val tenantId: TenantId) {

    private data class State(val events: List<RunEvent> = emptyList(), val finished: Boolean = false)

    private val state = MutableStateFlow(State())

    val finished: Boolean get() = state.value.finished

    fun emit(type: String, vararg fields: Pair<String, JsonValue>) {
        val data = jsonObjectOf("type" to jsonOf(type), *fields).encode()
        state.update { it.copy(events = it.events + RunEvent(it.events.size + 1L, type, data)) }
    }

    fun finish() = state.update { it.copy(finished = true) }

    /** Peristiwa setelah [afterId] (0 = dari awal); aliran selesai setelah run selesai dan semua peristiwa terkirim. */
    fun stream(afterId: Long = 0): Flow<RunEvent> = flow {
        var next = afterId.coerceAtLeast(0).toInt()
        while (true) {
            val snapshot = state.first { it.events.size > next || it.finished }
            while (next < snapshot.events.size) emit(snapshot.events[next++])
            if (snapshot.finished && next >= snapshot.events.size) break
        }
    }
}

/**
 * Registri run per tenant: **satu run aktif per tenant** ([RunAlreadyActiveException] bila ada), run milik tenant
 * lain tidak pernah terlihat ([find] mensyaratkan tenant yang sama). Run selesai disimpan sebentar ([retainMillis])
 * supaya klien yang terlambat sambung masih bisa memutar ulang peristiwanya.
 */
class BuilderRunRegistry(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val retainMillis: Long = 10 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() }
) {
    class RunAlreadyActiveException(message: String) : IllegalStateException(message)

    private val runs = ConcurrentHashMap<String, BuilderRun>()
    private val finishedAt = ConcurrentHashMap<String, Long>()

    /**
     * Memulai run: [work] berjalan di latar; galat apa pun menjadi peristiwa `error` (pesan jujur), bukan hilang.
     * Peristiwa `done` selalu menutup run yang berhasil.
     */
    fun start(tenantId: TenantId, work: suspend (BuilderRun) -> Unit): BuilderRun {
        purgeExpired()
        synchronized(this) {
            if (runs.values.any { it.tenantId == tenantId && !it.finished }) {
                throw RunAlreadyActiveException("Masih ada proses yang berjalan untuk project ini; tunggu selesai dulu")
            }
            val run = BuilderRun(newId(), tenantId)
            runs[run.id] = run
            scope.launch {
                try {
                    work(run)
                    run.emit("done")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    run.emit("error", "message" to jsonOf(e.message ?: "Proses gagal"))
                } finally {
                    run.finish()
                    finishedAt[run.id] = now()
                }
            }
            return run
        }
    }

    /** Run milik [tenantId] atau null — run tenant lain diperlakukan seperti tidak ada. */
    fun find(runId: String, tenantId: TenantId): BuilderRun? = runs[runId]?.takeIf { it.tenantId == tenantId }

    private fun purgeExpired() {
        val limit = now() - retainMillis
        finishedAt.filterValues { it < limit }.keys.forEach { runs.remove(it); finishedAt.remove(it) }
    }
}
