package com.eventverse.app.domain.pack

import com.eventverse.app.domain.contracts.PortDataTypeRegistry
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.pipeline.PipelineStage

/**
 * Pack konveksi — **dibangun dari enum lama**, bukan disalin tangan, sehingga identik secara
 * konstruksi selama Strangler Fig berjalan (tenant-variability-rules Kontrak 8). Saat pembaca enum
 * sudah dipindah (B1–B3), isi objek ini menjadi data literal dan enum dihapus.
 */
object GarmentDomainPack {

    val CODE = DomainPackCode("garment")

    val pack: DomainPack by lazy {
        val slots = ModuleArchetype.entries.map { a ->
            SlotDefinition(
                code = SlotCode(a.code),
                displayName = a.displayName,
                phase = PhaseCode(a.defaultStage.name),
                defaultInput = PortType(a.defaultExpectedInputType),
                defaultOutput = PortType(a.defaultProducedOutputType)
            )
        }
        val wired = PortDataTypeRegistry.KNOWN_TYPED_LABELS.map(::PortType).toSet()
        DomainPack(
            code = CODE,
            displayName = "Konveksi & Garmen",
            phases = PipelineStage.entries.map { s ->
                PhaseDefinition(PhaseCode(s.name), s.stepOrder, s.displayName, s.subtitle, s.colorHex)
            },
            slots = slots,
            portTypes = wired + slots.flatMap { listOf(it.defaultInput, it.defaultOutput) },
            wiredPortTypes = wired
        )
    }
}
