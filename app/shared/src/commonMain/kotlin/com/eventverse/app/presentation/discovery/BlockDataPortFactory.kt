package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.BlockDataPort
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.InMemoryBlockDataPort
import com.eventverse.app.domain.prototype.InteractiveScreen

/**
 * Fabrik pembuat [BlockDataPort] berdasarkan [DataBinding] layar (TRD-PLAT-003, butir A2).
 * Untuk [DataBinding.Memory], menggunakan [InMemoryBlockDataPort].
 * Untuk [DataBinding.Api], memanggil provider API (disediakan jalur C); bila belum terpasang
 * menggunakan fallback in-memory dengan seed agar pengujian dan demo tetap berjalan.
 */
fun interface BlockDataPortFactory {
    fun createPort(screen: InteractiveScreen, entityId: String): BlockDataPort

    companion object {
        /**
         * Provider klien HTTP yang diterbitkan oleh jalur C di infrastructure/api.
         */
        var apiPortProvider: ((basePath: String, screen: InteractiveScreen, entityId: String) -> BlockDataPort)? = null

        val defaultFactory: BlockDataPortFactory = BlockDataPortFactory { screen, entityId ->
            when (val b = screen.binding) {
                is DataBinding.Memory -> {
                    val seedRows = screen.seed[entityId].orEmpty()
                    InMemoryBlockDataPort(screen.spec, entityId, seedRows)
                }
                is DataBinding.Api -> {
                    apiPortProvider?.invoke(b.basePath, screen, entityId)
                        ?: run {
                            // Fallback jika ApiBlockDataPort belum di-inject
                            val seedRows = screen.seed[entityId].orEmpty()
                            InMemoryBlockDataPort(screen.spec, entityId, seedRows)
                        }
                }
            }
        }
    }
}
