package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.BlockDataPort
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.InMemoryBlockDataPort
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.infrastructure.api.apiBlockDataPortFor

/**
 * Fabrik pembuat [BlockDataPort] berdasarkan [DataBinding] layar (TRD-PLAT-003, butir A2).
 * Untuk [DataBinding.Memory], menggunakan [InMemoryBlockDataPort].
 * Untuk [DataBinding.Api], memakai klien HTTP sungguhan ([apiBlockDataPortFor]); [apiPortProvider]
 * hanya untuk menimpa (tes). **Tidak ada fallback diam-diam ke memori**: layar berbinding Api yang
 * tampak bekerja padahal datanya tidak tersimpan menipu pengguna dan verifikasi.
 */
fun interface BlockDataPortFactory {
    fun createPort(screen: InteractiveScreen, entityId: String): BlockDataPort

    companion object {
        /** Penimpa port Api (tes/pratinjau). Null = klien HTTP sungguhan. */
        var apiPortProvider: ((basePath: String, screen: InteractiveScreen, entityId: String) -> BlockDataPort)? = null

        val defaultFactory: BlockDataPortFactory = BlockDataPortFactory { screen, entityId ->
            when (val b = screen.binding) {
                is DataBinding.Memory -> {
                    val seedRows = screen.seed[entityId].orEmpty()
                    InMemoryBlockDataPort(screen.spec, entityId, seedRows)
                }
                is DataBinding.Api -> {
                    apiPortProvider?.invoke(b.basePath, screen, entityId) ?: apiBlockDataPortFor(b)
                }
            }
        }
    }
}
