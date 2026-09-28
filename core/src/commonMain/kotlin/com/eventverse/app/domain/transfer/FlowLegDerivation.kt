package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import kotlin.jvm.JvmName

/**
 * Menurunkan daftar perpindahan barang ([FlowTransferLeg]) dari susunan alur dan konfigurasi
 * lokasi tenant. Murni: tidak menyentuh repository, jam, maupun jaringan.
 *
 * ## Cara kerjanya
 *
 * Alur diratakan menjadi barisan simpul, tiap simpul dipetakan ke [LegEndpoint], lalu leg
 * muncul di setiap **pergantian ujung** pada barisan itu. Tiga perilaku yang tampak rumit
 * sebenarnya jatuh sendiri dari aturan tunggal tersebut:
 *
 * - **Makloon menghasilkan dua leg, bukan satu.** Barisan `Gedung A → Vendor → Gedung A`
 *   berganti ujung dua kali, jadi berangkat dan pulang keduanya lahir. Perjalanan pulang bukan
 *   detail administratif: di situlah kuantitas direkonsiliasi dan tanggung jawab cacat
 *   ditentukan.
 * - **Dua proses berurutan di vendor yang sama digabung.** Ujungnya identik, jadi tidak ada
 *   pergantian di antaranya — dan itu memang benar secara fisik, barangnya dikirim sekali.
 * - **Simpul tanpa pemetaan transparan.** Ia tidak melahirkan leg dan tidak memutus barisan,
 *   sehingga `Gedung A → (belum dipetakan) → Gedung B` tetap menghasilkan satu leg yang benar.
 *
 * ## Yang sengaja tidak menghasilkan leg
 *
 * Perpindahan meja di dalam satu gedung. Itu sudah tercatat sebagai setoran kerja
 * (`WorkDeposit`); mencatatnya dua kali membuat operator berhenti mengisi keduanya.
 */
object FlowLegDerivation {

    /**
     * Meratakan tahap wajib dan proses opsional menjadi satu barisan berurutan.
     *
     * Proses disisipkan tepat setelah tahap jangkarnya, dengan urutan relatif sesuai urutan
     * daftar — [TenantOptionalProcess] belum punya nomor urut di dalam satu jangkar, jadi
     * urutan daftar adalah satu-satunya yang kita punya.
     *
     * Tahap di [skipped] (tag Sampling di-×) tidak menjadi simpul — barangnya tidak pernah ke sana,
     * jadi leg menuju gedungnya pun tidak ada. Proses yang berjangkar padanya **tetap** disisipkan:
     * jangkar adalah posisi, bukan syarat bahwa tahapnya dikerjakan.
     */
    fun resolveNodes(
        stages: List<StageCode>,
        processes: List<TenantOptionalProcess>,
        skipped: Set<StageCode> = emptySet()
    ): List<FlowNodeRef> = buildList {
        stages.forEach { stage ->
            if (stage !in skipped) add(FlowNodeRef.Stage(stage))
            processes.filter { it.samplingAnchorAfter == stage }
                .forEach { add(FlowNodeRef.Process(it.code)) }
        }
    }

    /** Jembatan TRD-FLOW-001 Tahap 2 untuk pemanggil yang masih memegang enum. */
    @JvmName("resolveNodesFromLegacyStages")
    fun resolveNodes(
        stages: List<SamplingPipelineStage>,
        processes: List<TenantOptionalProcess>,
        skipped: Set<SamplingPipelineStage> = emptySet()
    ): List<FlowNodeRef> = resolveNodes(
        stages.map { it.toStageCode() },
        processes,
        skipped.mapTo(mutableSetOf()) { it.toStageCode() }
    )

    /**
     * Menurunkan leg dari barisan simpul.
     *
     * @param customerName nama pembeli untuk leg ekor. `null` berarti tidak ada penyerahan ke
     *   pembeli yang perlu didokumentasikan pada alur ini.
     */
    fun deriveLegs(
        nodes: List<FlowNodeRef>,
        processes: List<TenantOptionalProcess>,
        config: TenantLocationConfig,
        customerName: String? = null
    ): List<FlowTransferLeg> {
        val processByCode = processes.associateBy { it.code }

        // Simpul yang ujungnya tidak diketahui dibuang di sini, bukan dilewati saat iterasi,
        // supaya `A → (tak dipetakan) → B` tetap menghasilkan satu leg A→B.
        val anchored = nodes.mapNotNull { node ->
            val process = (node as? FlowNodeRef.Process)?.let { processByCode[it.code] }
            val endpoint = config.endpointFor(
                node = node,
                executionMode = process?.executionMode ?: WorkExecutionMode.IN_HOUSE,
                vendorRef = process?.vendorRef
            )
            endpoint?.let { node to it }
        }

        val legs = mutableListOf<FlowTransferLeg>()
        for (index in 1 until anchored.size) {
            val (fromNode, origin) = anchored[index - 1]
            val (toNode, destination) = anchored[index]
            if (origin == destination) continue
            legFor(fromNode, toNode, origin, destination, config)?.let(legs::add)
        }

        tailCustomerLeg(anchored.lastOrNull(), customerName, config)?.let(legs::add)
        return legs
    }

    /**
     * Jenis perpindahan untuk sepasang ujung yang berbeda, atau `null` bila perbedaan itu tidak
     * perlu didokumentasikan.
     *
     * Perpindahan antar-gedung hanya dianggap ada ketika tenant memang menyatakan dirinya
     * multi-site. Pemetaan yang tertinggal dari percobaan konfigurasi tidak boleh diam-diam
     * menyalakan gerbang bagi pabrik satu atap.
     */
    private fun legFor(
        fromNode: FlowNodeRef,
        toNode: FlowNodeRef,
        origin: LegEndpoint,
        destination: LegEndpoint,
        config: TenantLocationConfig
    ): FlowTransferLeg? {
        val transferType = when {
            destination is LegEndpoint.Customer -> TransferType.CUSTOMER_DISPATCH
            destination is LegEndpoint.Vendor -> TransferType.SUBCONTRACT_OUTBOUND
            origin is LegEndpoint.Vendor -> TransferType.SUBCONTRACT_INBOUND
            config.isMultiSiteEnabled -> TransferType.INTERNAL_SITE_TRANSFER
            else -> return null
        }
        return FlowTransferLeg(
            legKey = FlowTransferLeg.keyFor(fromNode, toNode, transferType),
            fromNode = fromNode,
            toNode = toNode,
            origin = origin,
            destination = destination,
            transferType = transferType
        )
    }

    /**
     * Penyerahan ke pembeli di ujung alur.
     *
     * Ada juga pada tenant satu atap: barangnya tetap keluar pabrik, dan itu dokumen nyata yang
     * memang diminta. Tenant yang tidak menginginkannya mematikan
     * [TenantLocationConfig.requireCustomerDispatchSj].
     */
    private fun tailCustomerLeg(
        last: Pair<FlowNodeRef, LegEndpoint>?,
        customerName: String?,
        config: TenantLocationConfig
    ): FlowTransferLeg? {
        if (!config.requireCustomerDispatchSj) return null
        val buyer = customerName?.takeIf { it.isNotBlank() } ?: return null
        val (lastNode, lastEndpoint) = last ?: return null
        val destination = LegEndpoint.Customer(buyer)
        if (lastEndpoint == destination) return null
        return FlowTransferLeg(
            legKey = FlowTransferLeg.keyFor(lastNode, lastNode, TransferType.CUSTOMER_DISPATCH),
            fromNode = lastNode,
            toNode = lastNode,
            origin = lastEndpoint,
            destination = destination,
            transferType = TransferType.CUSTOMER_DISPATCH
        )
    }
}
