package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.jsonOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuilderRunRegistryTest {

    private val a = TenantId("ten-aaa")
    private val b = TenantId("ten-bbb")

    @Test
    fun `satu run aktif per tenant, tenant lain bebas, setelah selesai boleh mulai lagi`() = runBlocking<Unit> {
        val registry = BuilderRunRegistry()
        val gate = CompletableDeferred<Unit>()
        val first = registry.start(a) { gate.await() }
        assertFailsWith<BuilderRunRegistry.RunAlreadyActiveException> { registry.start(a) { } }
        registry.start(b) { }                                   // tenant lain tak terpengaruh
        gate.complete(Unit)
        withTimeout(5_000) { first.stream().toList() }
        registry.start(a) { }                                   // sudah selesai → boleh
    }

    @Test
    fun `run tenant lain tidak terlihat dan peristiwa diputar ulang dari posisi mana pun`() = runBlocking<Unit> {
        val registry = BuilderRunRegistry()
        val run = registry.start(a) { it.emit("status", "phase" to jsonOf("drafting")) }
        withTimeout(5_000) { run.stream().toList() }
        assertNotNull(registry.find(run.id, a))
        assertNull(registry.find(run.id, b), "run tenant lain = tidak ada")
        assertEquals(listOf("status", "done"), run.stream(0).toList().map { it.type })
        assertEquals(listOf("done"), run.stream(1).toList().map { it.type })
        assertEquals(emptyList(), run.stream(2).toList())
    }

    @Test
    fun `galat pekerjaan menjadi peristiwa error yang jujur dan run tetap tertutup`() = runBlocking<Unit> {
        val registry = BuilderRunRegistry()
        val run = registry.start(a) { error("Agent gagal menjawab") }
        val events = withTimeout(5_000) { run.stream().toList() }
        assertEquals(listOf("error"), events.map { it.type })
        assertTrue(events.single().data.contains("Agent gagal menjawab"))
        assertTrue(run.finished)
    }

    @Test
    fun `run selesai yang kedaluwarsa dibersihkan saat run baru dimulai`() = runBlocking<Unit> {
        var clock = 0L
        val registry = BuilderRunRegistry(retainMillis = 1_000, now = { clock })
        val old = registry.start(a) { }
        withTimeout(5_000) { old.stream().toList() }
        clock = 5_000
        registry.start(b) { }
        assertNull(registry.find(old.id, a))
    }
}
