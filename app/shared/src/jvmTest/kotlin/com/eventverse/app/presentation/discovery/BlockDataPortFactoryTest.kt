package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.InMemoryBlockDataPort
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class BlockDataPortFactoryTest {
    private val memory = assertNotNull(InteractiveScreenFactory.kanban("k", "K", listOf(mapOf("Kolom" to "A", "Judul" to "x"))))
    private val entity = InteractiveScreenFactory.ENTITY_ID

    // Jalur klien HTTP sungguhan tak bisa dibangun di jvmTest (`HttpClient()` tanpa engine — konvensi
    // semua klien API di app ini); yang diuji: penimpa dihormati. Fallback memori sudah dihapus dari kode.
    @Test
    fun apiBinding_honorsTheOverride() {
        val screen = memory.copy(binding = DataBinding.Api("/api/x"))
        val fake = InMemoryBlockDataPort(screen.spec, entity, emptyList())
        BlockDataPortFactory.apiPortProvider = { path, _, _ -> assertEquals("/api/x", path); fake }
        try {
            assertSame(fake, BlockDataPortFactory.defaultFactory.createPort(screen, entity))
        } finally {
            BlockDataPortFactory.apiPortProvider = null
        }
    }

    @Test
    fun memoryBinding_staysInMemory() {
        assertIs<InMemoryBlockDataPort>(BlockDataPortFactory.defaultFactory.createPort(memory, entity))
    }
}
