package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.pipeline.OperationalModuleCatalog
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.SlotCode

/**
 * Model aliran data antar modul aktif untuk pane Data Flow (Builder & Discovery).
 *
 * Sumber kebenaran port adalah [OperationalModuleCatalog] — kontrak domain yang sama yang dipakai
 * kanvas Factory Flow untuk menyambung node (keluar A ∩ masuk B). Modul yang tidak ada di katalog
 * (plugin kustom) jatuh ke port slot bawaan pada draf (`slotInput`/`slotOutput`).
 *
 * Nama tampilan (label manusiawi port & slot) diambil dari kosakata domain pack milik draf
 * (`portLabels` / `SlotDefinition.displayName`); kode tetap identitas, label hanya untuk layar.
 *
 * Murni perhitungan — tanpa Compose — agar bisa diuji tanpa UI.
 */

/** Satu serah-terima data antar port: [payload] mengalir dari [from] ke [to]. */
data class PortHandoff(
    /** Kode tipe port (identitas kontrak domain, mis. `ProductionOrderDraft`). */
    val payload: String,
    /** Label tampilan [payload] menurut kosakata pack; tanpa label = kode itu sendiri. */
    val payloadLabel: String,
    /** Modul penghasil; null = dari luar sistem (PO pelanggan, permintaan manual, dsb.). */
    val from: DiscoveryModuleUi?,
    /** Modul penerima; null = keluaran akhir (belum dipakai modul aktif lain). */
    val to: DiscoveryModuleUi?,
    /** Masukan rujukan: dibaca sebagai acuan, tidak dialirkan sebagai barang (QC membaca tech pack). */
    val isReference: Boolean = false
)

/** Port masuk/keluar satu modul aktif, sudah dihitung sambungannya. */
data class ModuleDataFlow(
    val module: DiscoveryModuleUi,
    /** Nama tampilan slot modul menurut pack (`order_ingestion` → "Penerimaan Pesanan…"); null = bukan slot pack. */
    val slotLabel: String?,
    val incoming: List<PortHandoff>,
    val outgoing: List<PortHandoff>
)

/** Peta aliran data seluruh modul operasional aktif pada satu draf. */
data class DataFlowMap(val flows: List<ModuleDataFlow>) {

    /** Semua sambungan unik, terurut: antar-modul per modul sumber, lalu input eksternal, lalu ujung alur. */
    val handoffs: List<PortHandoff> by lazy {
        val interModule = flows.flatMap { it.outgoing }.filter { it.to != null && it.to != it.from }
        val external = flows.flatMap { it.incoming }.filter { it.from == null }
        val ends = flows.flatMap { it.outgoing }.filter { it.to == null }
        (interModule + external + ends).distinct()
    }

    /** Sambungan sungguhan antar dua modul berbeda. */
    val connectionCount: Int get() = handoffs.count { it.from != null && it.to != null }

    /** Port masuk yang tidak dihasilkan modul aktif mana pun (dipenuhi di luar sistem). */
    val externalInputCount: Int get() = handoffs.count { it.from == null }

    /** Port keluar yang belum dipakai modul aktif lain (keluaran akhir alur). */
    val endOutputCount: Int get() = handoffs.count { it.to == null }

    /**
     * Urutan alur stasiun kerja dari hulu ke hilir (topological pipeline stream).
     * Mengikuti urutan kanonik kanvas Factory Flow [OperationalModuleCatalog.all]
     * yang sudah terbukti mencerminkan aliran alami data manufaktur.
     */
    fun pipelineOrder(): List<ModuleDataFlow> {
        if (flows.isEmpty()) return emptyList()
        val catalogOrder: List<String> = OperationalModuleCatalog.all.map { it.module.value }
        val catalogModules = flows.filter { it.module.id in catalogOrder }
            .sortedBy { catalogOrder.indexOf(it.module.id) }
        val otherModules = flows.filter { it.module.id !in catalogOrder }

        return catalogModules + otherModules
    }
}

private class Ports(val inputs: List<String>, val references: List<String>, val outputs: List<String>)

/**
 * Menghitung peta sambungan port dari modul-modul operasional aktif pada [draft].
 * Modul governance/foundation tidak disertakan — mereka bukan stasiun aliran data.
 */
fun buildDataFlowMap(draft: DiscoveryDraftUi): DataFlowMap {
    val ops = draft.activeModules.filter { it.kind == "OPERATIONAL" }

    // Kosakata tampilan: utama dari payload draf (ikut ringkasan — bekerja juga untuk draf
    // pra-handoff yang pack-nya belum terdaftar), registry sebagai fallback, kode sebagai
    // fallback terakhir (Kontrak 4: tanpa label = tampil kode, bukan jatuh ke pack lain).
    val pack = DomainPackRegistry.all.firstOrNull { it.code.value == draft.packCode }
    fun labelOf(type: String) = draft.portLabels[type] ?: pack?.portLabel(type) ?: type
    fun slotLabelOf(slot: String?) = slot?.let { code ->
        draft.slotLabels[code] ?: pack?.slot(SlotCode(code))?.displayName
    }

    val portsByModule: Map<DiscoveryModuleUi, Ports> = ops.associateWith { m ->
        OperationalModuleCatalog.specificationForCode(m.id)?.let {
            Ports(it.upstreamPrerequisites, it.referenceInputs, it.downstreamHandoffs)
        } ?: Ports(listOfNotNull(m.slotInput), emptyList(), listOfNotNull(m.slotOutput))
    }

    fun producersOf(type: String, self: DiscoveryModuleUi): List<DiscoveryModuleUi> =
        portsByModule.filterValues { type in it.outputs }.keys.filter { it.id != self.id }

    fun consumersOf(type: String, self: DiscoveryModuleUi): List<DiscoveryModuleUi> =
        portsByModule.filterValues { type in it.inputs || type in it.references }.keys.filter { it.id != self.id }

    val flows = ops.map { m ->
        val ports = portsByModule.getValue(m)

        val incoming = buildList {
            (ports.inputs + ports.references).forEach { type ->
                val isRef = type !in ports.inputs
                val producers = producersOf(type, m)
                if (producers.isEmpty()) {
                    add(PortHandoff(type, labelOf(type), from = null, to = m, isReference = isRef))
                } else {
                    producers.forEach { add(PortHandoff(type, labelOf(type), from = it, to = m, isReference = isRef)) }
                }
            }
        }

        val outgoing = buildList {
            ports.outputs.forEach { type ->
                val consumers = consumersOf(type, m)
                if (consumers.isEmpty()) {
                    // Tidak ada modul lain yang memakai; bila modul ini sendiri memakai tipenya,
                    // tandai sebagai pemakaian internal (from == to), selain itu keluaran akhir.
                    val selfConsumes = type in ports.inputs || type in ports.references
                    add(
                        if (selfConsumes) PortHandoff(type, labelOf(type), from = m, to = m)
                        else PortHandoff(type, labelOf(type), from = m, to = null)
                    )
                } else {
                    consumers.forEach { add(PortHandoff(type, labelOf(type), from = m, to = it)) }
                }
            }
        }

        ModuleDataFlow(m, slotLabelOf(m.slot), incoming, outgoing)
    }

    return DataFlowMap(flows)
}
