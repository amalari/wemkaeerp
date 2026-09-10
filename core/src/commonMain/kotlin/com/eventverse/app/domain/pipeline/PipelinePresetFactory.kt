package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Domain Factory that creates pre-configured pipeline snapshots and live monitoring metrics
 * for various garment factory operational models (FOB, CMT, Brand D2C).
 */
object PipelinePresetFactory {

    fun createSnapshot(preset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT): FactoryPipelineSnapshot {
        val nodes = buildNodesForPreset(preset)
        val activeNodes = nodes.filterNot { it.isBypassed }
        val bypassedCount = nodes.count { it.isBypassed }
        val bottlenecksCount = nodes.count { it.isBottleneck }
        val totalWip = activeNodes.sumOf { it.wipPieces }

        // Average lead time calculated from cumulative active cycle times
        val totalHours = activeNodes.sumOf { it.cycleTimeHours }
        // Assuming 8 operating hours/day
        val avgLeadDays = (totalHours / 8.0 * 10.0).let { kotlin.math.round(it) / 10.0 }

        // Dynamic health score: 100% minus penalties for bottlenecks & critical nodes
        val baseScore = 100
        val bottleneckPenalty = nodes.count { it.healthStatus == FlowHealthStatus.BOTTLENECK } * 7
        val criticalPenalty = nodes.count { it.healthStatus == FlowHealthStatus.CRITICAL } * 15
        val healthScore = (baseScore - bottleneckPenalty - criticalPenalty).coerceIn(40, 100)

        return FactoryPipelineSnapshot(
            preset = preset,
            nodes = nodes,
            overallHealthScore = healthScore,
            totalWipPieces = totalWip,
            activeBottlenecks = bottlenecksCount,
            avgLeadTimeDays = avgLeadDays,
            activeModulesCount = activeNodes.size,
            bypassedModulesCount = bypassedCount
        )
    }

    private fun buildNodesForPreset(preset: GarmentBusinessPreset): List<PipelineNode> {
        return when (preset) {
            GarmentBusinessPreset.FOB_FULL_PACKAGE -> buildFobNodes()
            GarmentBusinessPreset.CMT_MAKLOON -> buildCmtNodes()
            GarmentBusinessPreset.BRAND_D2C -> buildBrandD2cNodes()
        }
    }

    private fun buildFobNodes(): List<PipelineNode> {
        return listOf(
            PipelineNode(
                id = "fob-crm-sales",
                module = BusinessModule.CRM_SALES,
                stage = PipelineStage.COMMERCIAL,
                stepNumber = 1,
                title = BusinessModule.CRM_SALES.displayName,
                description = "Negosiasi awal kontrak FOB, penentuan kuota minimum order (MOQ), dan kesepakatan lead time pengiriman.",
                assignedDepartment = "Marketing & Sales",
                deptColorHex = 0xFF2563EB,
                inputContract = "Permintaan Penawaran (RFQ), Desain Referensi Klien, & Target Harga",
                outputContract = "Purchase Order (PO) Induk & Kesepakatan Spesifikasi Awal",
                wipPieces = 320,
                cycleTimeHours = 4.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Target prospek berjalan normal, 3 PO menunggu persetujuan sample.",
                downstreamModuleCodes = listOf(BusinessModule.SAMPLING_ORDER.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-crm-1",
                        name = "Form Permintaan Penawaran (RFQ)",
                        isManual = true,
                        operatorRole = "Merchandiser / Sales Lead",
                        inputMethod = "Form Digital ERP",
                        description = "Negosiasi kuota minimum order (MOQ), target harga klien, dan kesepakatan lead time pengiriman."
                    ),
                    PipelineInputPort(
                        id = "in-fob-crm-2",
                        name = "Sketsa & Referensi Desain Buyer",
                        isManual = true,
                        operatorRole = "Klien Buyer / Akun Sales",
                        inputMethod = "Upload File (PDF / JPG)",
                        description = "Moodboard dan panduan spesifikasi model baju yang diinginkan pembeli.",
                        isRequired = false
                    )
                )
            ),
            PipelineNode(
                id = "fob-sampling",
                module = BusinessModule.SAMPLING_ORDER,
                stage = PipelineStage.COMMERCIAL,
                stepNumber = 2,
                title = BusinessModule.SAMPLING_ORDER.displayName,
                description = "Pembuatan 1 pcs prototipe baju (Golden Sample) untuk fitting, uji bahan susut, dan approval buyer.",
                assignedDepartment = "Desain, Pola & Sampling",
                deptColorHex = 0xFF0284C7,
                inputContract = "PO Induk & Lembar Sketsa Desain Awal",
                outputContract = "Golden Sample Terverifikasi & Lembar Komentar Fitting Buyer",
                wipPieces = 45,
                cycleTimeHours = 12.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Sample kemeja & polo batch ini telah lolos fitting buyer.",
                downstreamModuleCodes = listOf(BusinessModule.TECH_PACK_BOM.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-samp-1",
                        name = "Purchase Order (PO) Induk Terverifikasi",
                        isManual = false,
                        sourceModuleCode = BusinessModule.CRM_SALES.code,
                        sourceModuleName = BusinessModule.CRM_SALES.displayName,
                        sourceOutputContract = "Purchase Order (PO) Induk & Kesepakatan Spesifikasi Awal",
                        description = "Data kontrak kuantiti dan spesifikasi dasar yang telah disetujui sales."
                    ),
                    PipelineInputPort(
                        id = "in-fob-samp-2",
                        name = "Konstruksi Pola Dasar & Lembar Fitting",
                        isManual = true,
                        operatorRole = "Pattern Maker (Tukang Pola)",
                        inputMethod = "Upload Pola CAD DXF & Lembar Kerja",
                        description = "Pengembangan pola 1 size percontohan untuk pengujian bahan susut dan fitting buyer."
                    )
                )
            ),
            PipelineNode(
                id = "fob-tech-pack",
                module = BusinessModule.TECH_PACK_BOM,
                stage = PipelineStage.ENGINEERING,
                stepNumber = 3,
                title = BusinessModule.TECH_PACK_BOM.displayName,
                description = "Penyusunan Bill of Materials (BOM) lengkap: konsumsi kain per yard, spesifikasi jarum jahit, dan grade ukuran.",
                assignedDepartment = "Teknikal, Pola & R&D",
                deptColorHex = 0xFF7C3AED,
                inputContract = "Golden Sample Disetujui & Tabel Ukuran Standar (S/M/L/XL)",
                outputContract = "Tech Pack Final, Pola Potong CAD, & Lembar Konsumsi BOM Kain",
                wipPieces = 150,
                cycleTimeHours = 6.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Grading ukuran selesai, konsumsi kain rata-rata 1.45 yard/pcs.",
                downstreamModuleCodes = listOf(BusinessModule.INVENTORY.code, BusinessModule.COSTING_HPP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-tp-1",
                        name = "Golden Sample Disetujui Buyer",
                        isManual = false,
                        sourceModuleCode = BusinessModule.SAMPLING_ORDER.code,
                        sourceModuleName = BusinessModule.SAMPLING_ORDER.displayName,
                        sourceOutputContract = "Golden Sample Terverifikasi & Lembar Komentar Fitting Buyer",
                        description = "Sampel fisik dan lembar revisi fitting yang telah di-ACC oleh pihak pembeli."
                    ),
                    PipelineInputPort(
                        id = "in-fob-tp-2",
                        name = "Tabel Konsumsi BOM & Grading Ukuran",
                        isManual = true,
                        operatorRole = "Spesialis Teknikal & R&D",
                        inputMethod = "Matrix Input BOM Digital",
                        description = "Perhitungan konsumsi kain per yard, aksesoris kancing/zipper, serta toleransi ukuran S/M/L/XL."
                    )
                )
            ),
            PipelineNode(
                id = "fob-inventory",
                module = BusinessModule.INVENTORY,
                stage = PipelineStage.SUPPLY_CHAIN,
                stepNumber = 4,
                title = BusinessModule.INVENTORY.displayName,
                description = "Pengadaan dan penerimaan kain rol dari pabrik tenun, pengecekan lot warna (shading), zipper, kancing, dan benang.",
                assignedDepartment = "Gudang & Logistik Masuk",
                deptColorHex = 0xFF0D9488,
                inputContract = "Lembar Kebutuhan BOM Kain & Surat Jalan Suplier Tekstil",
                outputContract = "Kain Rol Teruji Shading + Aksesoris Siap Alokasi Potong",
                wipPieces = 2100,
                cycleTimeHours = 8.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Stok kain Cotton Combed 24s & 30s aman di rak penerimaan.",
                downstreamModuleCodes = listOf(BusinessModule.COSTING_HPP.code, BusinessModule.PRODUCTION_MRP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-inv-1",
                        name = "Daftar Kebutuhan Rol Kain BOM",
                        isManual = false,
                        sourceModuleCode = BusinessModule.TECH_PACK_BOM.code,
                        sourceModuleName = BusinessModule.TECH_PACK_BOM.displayName,
                        sourceOutputContract = "Tech Pack Final, Pola Potong CAD, & Lembar Konsumsi BOM Kain",
                        description = "Jumlah yard kain dan jenis aksesoris yang dipesan sesuai rincian BOM."
                    ),
                    PipelineInputPort(
                        id = "in-fob-inv-2",
                        name = "Uji Shading Lot & Surat Jalan Tekstil",
                        isManual = true,
                        operatorRole = "Inspektur Gudang & QC Bahan Baku",
                        inputMethod = "Scan Barcode Rol & Form Lot",
                        description = "Pemeriksaan visual konsistensi celupan warna (shading) dan cacat tenun kain masuk."
                    )
                )
            ),
            PipelineNode(
                id = "fob-costing-hpp",
                module = BusinessModule.COSTING_HPP,
                stage = PipelineStage.ENGINEERING,
                stepNumber = 5,
                title = BusinessModule.COSTING_HPP.displayName,
                description = "Perhitungan HPP akurat: biaya kain per kg + ongkos potong + SAM (Standard Allowed Minute) jahit + margin rahasia.",
                assignedDepartment = "Finance & Akuntansi Biaya",
                deptColorHex = 0xFF6366F1,
                inputContract = "Harga Aktual Kain Masuk & Estimasi Waktu Jahit (SAM)",
                outputContract = "Kalkulasi HPP Bersih per Pcs & Batas Margin Laba Pabrik",
                wipPieces = 80,
                cycleTimeHours = 3.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "HPP tervalidasi Rp 48.500/pcs dengan proyeksi margin 24.5%.",
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-cost-1",
                        name = "Spesifikasi BOM & Konsumsi Kain",
                        isManual = false,
                        sourceModuleCode = BusinessModule.TECH_PACK_BOM.code,
                        sourceModuleName = BusinessModule.TECH_PACK_BOM.displayName,
                        sourceOutputContract = "Tech Pack Final, Pola Potong CAD, & Lembar Konsumsi BOM Kain",
                        description = "Total konsumsi material per potong baju untuk dasar biaya pokok."
                    ),
                    PipelineInputPort(
                        id = "in-fob-cost-2",
                        name = "Harga Pembelian Aktual Rol Kain",
                        isManual = false,
                        sourceModuleCode = BusinessModule.INVENTORY.code,
                        sourceModuleName = BusinessModule.INVENTORY.displayName,
                        sourceOutputContract = "Kain Rol Teruji Shading + Aksesoris Siap Alokasi Potong",
                        description = "Faktur harga riil pembelian dari pabrik tekstil."
                    ),
                    PipelineInputPort(
                        id = "in-fob-cost-3",
                        name = "Tarif SAM Jahit & Target Margin",
                        isManual = true,
                        operatorRole = "Akuntan Biaya & Finance Manager",
                        inputMethod = "Form Tarif Upah & Matrix Margin",
                        description = "Standard Allowed Minute jahit, estimasi overhead listrik pabrik, dan target profit margin."
                    )
                )
            ),
            PipelineNode(
                id = "fob-mrp-spk",
                module = BusinessModule.PRODUCTION_MRP,
                stage = PipelineStage.MANUFACTURING,
                stepNumber = 6,
                title = BusinessModule.PRODUCTION_MRP.displayName,
                description = "Penjadwalan 10 mesin jahit, penetapan kapasitas harian per line, dan penerbitan SPK Potong & Jahit massal.",
                assignedDepartment = "PPIC & Manajemen Pabrik",
                deptColorHex = 0xFFD97706,
                inputContract = "BOM Final, Kain Ready di Gudang, & PO Terjadwal",
                outputContract = "Surat Perintah Kerja (SPK) Potong & Matriks Penugasan Line Jahit",
                wipPieces = 850,
                cycleTimeHours = 4.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Line A dan Line B terjadwal penuh untuk pesanan 2.500 pcs.",
                downstreamModuleCodes = listOf(BusinessModule.OPERATOR_EXEC.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-mrp-1",
                        name = "Persetujuan HPP & Kuota Produksi",
                        isManual = false,
                        sourceModuleCode = BusinessModule.COSTING_HPP.code,
                        sourceModuleName = BusinessModule.COSTING_HPP.displayName,
                        sourceOutputContract = "Kalkulasi HPP Bersih per Pcs & Batas Margin Laba Pabrik",
                        description = "Status kelayakan margin biaya sebelum produksi massal dijalankan."
                    ),
                    PipelineInputPort(
                        id = "in-fob-mrp-2",
                        name = "Kain Ready di Gudang Siap Potong",
                        isManual = false,
                        sourceModuleCode = BusinessModule.INVENTORY.code,
                        sourceModuleName = BusinessModule.INVENTORY.displayName,
                        sourceOutputContract = "Kain Rol Teruji Shading + Aksesoris Siap Alokasi Potong",
                        description = "Verifikasi fisik kain sudah berada di rak antrean potong pabrik."
                    ),
                    PipelineInputPort(
                        id = "in-fob-mrp-3",
                        name = "Alokasi Mesin & Kapasitas Line",
                        isManual = true,
                        operatorRole = "PPIC Planner / Kepala Pabrik",
                        inputMethod = "Form Line Balancing & Mesin",
                        description = "Penetapan jadwal giliran operator, line potong, dan target kuota harian."
                    )
                )
            ),
            PipelineNode(
                id = "fob-operator-exec",
                module = BusinessModule.OPERATOR_EXEC,
                stage = PipelineStage.MANUFACTURING,
                stepNumber = 7,
                title = BusinessModule.OPERATOR_EXEC.displayName,
                description = "Gelar kain, pemotongan massal, pembagian bundel jahit (bundling), dan perakitan garmen di mesin jahit jarum 1 & obras.",
                assignedDepartment = "Lantai Produksi (Operator Jahit & Potong)",
                deptColorHex = 0xFFEA580C,
                inputContract = "Kain Tergelar, Bundel Pola Bertiket Barcode, & SPK Line",
                outputContract = "Pakaian Jadi Belum Diinspeksi (Grey Goods) + Catatan Target Harian",
                wipPieces = 1350,
                cycleTimeHours = 18.0,
                healthStatus = FlowHealthStatus.BOTTLENECK,
                healthMessage = "WIP menumpuk 1.350 pcs di Line Jahit B karena pergantian benang warna navy.",
                downstreamModuleCodes = listOf(BusinessModule.QUALITY_CONTROL.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-op-1",
                        name = "Surat Perintah Kerja (SPK) Potong & Jahit",
                        isManual = false,
                        sourceModuleCode = BusinessModule.PRODUCTION_MRP.code,
                        sourceModuleName = BusinessModule.PRODUCTION_MRP.displayName,
                        sourceOutputContract = "Surat Perintah Kerja (SPK) Potong & Matriks Penugasan Line Jahit",
                        description = "Rincian urutan pengerjaan potong dan susunan assembly line penjahit."
                    ),
                    PipelineInputPort(
                        id = "in-fob-op-2",
                        name = "Scan Tiket Bundel Potong & Tally Gelar",
                        isManual = true,
                        operatorRole = "Operator Meja Potong & Mandor Jahit",
                        inputMethod = "Scan Barcode Tiket Bundel",
                        description = "Pencatatan nomor bundel hasil potong sebelum diserahkan ke meja jahit."
                    )
                )
            ),
            PipelineNode(
                id = "fob-qc-defect",
                module = BusinessModule.QUALITY_CONTROL,
                stage = PipelineStage.ASSURANCE_DELIVERY,
                stepNumber = 8,
                title = BusinessModule.QUALITY_CONTROL.displayName,
                description = "Inspeksi jahitan loncat, noda minyak, pengukuran toleransi dimensi baju, dan pemisahan reject/cacat produksi.",
                assignedDepartment = "Quality Control (QC Inspeksi)",
                deptColorHex = 0xFF16A34A,
                inputContract = "Pakaian Jadi dari Line Jahit & Toleransi Ukuran Tech Pack",
                outputContract = "Pakaian Lolos QC Grade A Bertiket + Laporan Cacat (Reject Rate)",
                wipPieces = 280,
                cycleTimeHours = 3.5,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Tingkat cacat terkendali di 1.2% (standar toleransi ekspor < 2.5%).",
                downstreamModuleCodes = listOf(BusinessModule.FULFILLMENT.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-qc-1",
                        name = "Garmen Grey Goods dari Lantai Jahit",
                        isManual = false,
                        sourceModuleCode = BusinessModule.OPERATOR_EXEC.code,
                        sourceModuleName = BusinessModule.OPERATOR_EXEC.displayName,
                        sourceOutputContract = "Pakaian Jadi Belum Diinspeksi (Grey Goods) + Catatan Target Harian",
                        description = "Kumpulan pakaian jadi hasil rakitan operator yang siap diinspeksi."
                    ),
                    PipelineInputPort(
                        id = "in-fob-qc-2",
                        name = "Spesifikasi Toleransi Ukuran Tech Pack",
                        isManual = false,
                        sourceModuleCode = BusinessModule.TECH_PACK_BOM.code,
                        sourceModuleName = BusinessModule.TECH_PACK_BOM.displayName,
                        sourceOutputContract = "Tech Pack Final, Pola Potong CAD, & Lembar Konsumsi BOM Kain",
                        description = "Batas deviasi dimensi ukuran baju yang diperbolehkan buyer."
                    ),
                    PipelineInputPort(
                        id = "in-fob-qc-3",
                        name = "Ceklis Audit Cacat & Pengukuran Dimensi",
                        isManual = true,
                        operatorRole = "Pemeriksa QC Line & Inspector",
                        inputMethod = "Form Ceklis Cacat & Meteran Fisik",
                        description = "Pengujian kerapihan jahitan, deteksi noda minyak mesin, dan sortir reject."
                    )
                )
            ),
            PipelineNode(
                id = "fob-fulfillment",
                module = BusinessModule.FULFILLMENT,
                stage = PipelineStage.ASSURANCE_DELIVERY,
                stepNumber = 9,
                title = BusinessModule.FULFILLMENT.displayName,
                description = "Setrika uap (finishing iron), pasang hangtag merk, pemilahan ukuran per karton ekspor, dan penerbitan Surat Jalan.",
                assignedDepartment = "Finishing, Packing & Ekspedisi",
                deptColorHex = 0xFF059669,
                inputContract = "Garmen Grade A Lolos QC + Karton Box Standar Buyer",
                outputContract = "Karton Tersegel Siap Muat Kontainer + Surat Jalan Ekspedisi Resmi",
                wipPieces = 180,
                cycleTimeHours = 2.5,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "50 karton box siap di-pickup oleh armada logistik besok pagi.",
                downstreamModuleCodes = emptyList(),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-fob-ful-1",
                        name = "Garmen Grade A Lolos QC Bertiket",
                        isManual = false,
                        sourceModuleCode = BusinessModule.QUALITY_CONTROL.code,
                        sourceModuleName = BusinessModule.QUALITY_CONTROL.displayName,
                        sourceOutputContract = "Pakaian Lolos QC Grade A Bertiket + Laporan Cacat (Reject Rate)",
                        description = "Pakaian yang telah tervalidasi bersih dari cacat produksi."
                    ),
                    PipelineInputPort(
                        id = "in-fob-ful-2",
                        name = "Pemeriksaan Karton & Nomor Segel Ekspedisi",
                        isManual = true,
                        operatorRole = "Staff Finishing & Packing",
                        inputMethod = "Packing List & Verifikasi Kontainer",
                        description = "Penyusunan per karton ekspor dan penempelan barcode shipping."
                    )
                )
            )
        )
    }

    private fun buildCmtNodes(): List<PipelineNode> {
        return listOf(
            PipelineNode(
                id = "cmt-crm-sales",
                module = BusinessModule.CRM_SALES,
                stage = PipelineStage.COMMERCIAL,
                stepNumber = 1,
                title = BusinessModule.CRM_SALES.displayName,
                description = "Penerimaan PO jasa jahit makloon dari Brand Klien dengan kuantiti dan tanggal kirim target.",
                assignedDepartment = "Marketing & Akun Makloon",
                deptColorHex = 0xFF2563EB,
                inputContract = "PO Makloon dari Brand Buyer & Jadwal Kirim Bahan dari Buyer",
                outputContract = "Kontrak Kerja Jasa Makloon & Perjanjian Waktu Selesai Jahit",
                wipPieces = 120,
                cycleTimeHours = 2.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Kontrak makloon 3 brand lokal aktif berjalan.",
                downstreamModuleCodes = listOf(BusinessModule.SAMPLING_ORDER.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-crm-1",
                        name = "Purchase Order (PO) Makloon Brand",
                        isManual = true,
                        operatorRole = "Akun Makloon / Merchandiser",
                        inputMethod = "Form Kontrak Jasa Makloon",
                        description = "Target kuantiti jahit, tanggal serah terima baju jadi, dan spesifikasi ongkos makloon."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-crm-2",
                        name = "Konfirmasi Jadwal Drop Kain Buyer",
                        isManual = true,
                        operatorRole = "Perwakilan Brand Buyer",
                        inputMethod = "Surat Jalan Pengiriman Kain",
                        description = "Jadwal truk pengantar bahan kain tiba di bengkel makloon."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-sampling",
                module = BusinessModule.SAMPLING_ORDER,
                stage = PipelineStage.COMMERCIAL,
                stepNumber = 2,
                title = BusinessModule.SAMPLING_ORDER.displayName,
                description = "Uji jahit 1 sample fitting dengan kain yang dikirim oleh brand untuk memastikan kerapihan jarum.",
                assignedDepartment = "Tim Sampling & Approval",
                deptColorHex = 0xFF0284C7,
                inputContract = "Kain Sample dari Buyer & Panduan Jahit Buyer",
                outputContract = "Approval Jahit Percontohan dari Brand",
                wipPieces = 25,
                cycleTimeHours = 6.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Sample fitting disetujui tanpa revisi jahitan.",
                downstreamModuleCodes = listOf(BusinessModule.COSTING_HPP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-samp-1",
                        name = "Kontrak Kerja Jasa Makloon",
                        isManual = false,
                        sourceModuleCode = BusinessModule.CRM_SALES.code,
                        sourceModuleName = BusinessModule.CRM_SALES.displayName,
                        sourceOutputContract = "Kontrak Kerja Jasa Makloon & Perjanjian Waktu Selesai Jahit",
                        description = "PO dan kesepakatan jahit resmi dari klien brand."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-samp-2",
                        name = "Bahan Sample & Lembar Panduan Jahit",
                        isManual = true,
                        operatorRole = "Staff Percontohan / Sample Maker",
                        inputMethod = "Penerimaan Fisik Kain dari Brand",
                        description = "Kain potong sample dan lembar panduan spesifikasi setikan jarum."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-tech-pack",
                module = BusinessModule.TECH_PACK_BOM,
                stage = PipelineStage.ENGINEERING,
                stepNumber = 3,
                title = BusinessModule.TECH_PACK_BOM.displayName,
                description = "Modul Tech Pack di-bypass karena pola potong dan spesifikasi jahitan disediakan 100% oleh Buyer.",
                assignedDepartment = "Buyer / Pihak Brand Eksternal",
                deptColorHex = 0xFF94A3B8,
                inputContract = "Disediakan oleh Pihak Brand Luar",
                outputContract = "Pola Jadi Cetak Plotter dari Brand Buyer",
                wipPieces = 0,
                cycleTimeHours = 0.0,
                healthStatus = FlowHealthStatus.BYPASSED,
                healthMessage = "Tahapan ini di-bypass pada model makloon CMT (Disediakan Brand Buyer).",
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-tp-1",
                        name = "Pola Jadi Cetak Plotter dari Brand",
                        isManual = true,
                        operatorRole = "Pihak Brand Eksternal",
                        inputMethod = "Kertas Pola Fisik / File Plotter",
                        description = "Pola potong yang sudah jadi 100% disediakan oleh pihak luar."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-inventory",
                module = BusinessModule.INVENTORY,
                stage = PipelineStage.SUPPLY_CHAIN,
                stepNumber = 4,
                title = BusinessModule.INVENTORY.displayName,
                description = "Modul pengadaan kain di-bypass. Pabrik hanya menerima kain drop dari buyer tanpa membeli bahan baku sendiri.",
                assignedDepartment = "Buyer / Logistik Pihak Ketiga",
                deptColorHex = 0xFF94A3B8,
                inputContract = "Kain Disediakan Brand Buyer",
                outputContract = "Kain Titipan Buyer Terverifikasi Kuantitasnya",
                wipPieces = 0,
                cycleTimeHours = 0.0,
                healthStatus = FlowHealthStatus.BYPASSED,
                healthMessage = "Tahapan ini di-bypass pada model makloon CMT (Kain tidak dibeli pabrik).",
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-inv-1",
                        name = "Kain Titipan Drop dari Truk Buyer",
                        isManual = true,
                        operatorRole = "Logistik & Ekspedisi Buyer",
                        inputMethod = "Surat Jalan Kirim Titip Kain",
                        description = "Pabrik tidak membeli bahan baku; hanya menerima drop rol kain dari buyer."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-costing-hpp",
                module = BusinessModule.COSTING_HPP,
                stage = PipelineStage.ENGINEERING,
                stepNumber = 5,
                title = BusinessModule.COSTING_HPP.displayName,
                description = "Penetapan tarif ongkos jahit makloon per pcs (hanya biaya tenaga kerja operator potong + jahit + listrik).",
                assignedDepartment = "Finance & Manajemen Makloon",
                deptColorHex = 0xFF6366F1,
                inputContract = "Target Kuantitas Jahit & Kerumitan Model Baju",
                outputContract = "Tarif Ongkos Jahit Bersih (misal Rp 14.000/pcs)",
                wipPieces = 60,
                cycleTimeHours = 2.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Ongkos makloon disepakati Rp 13.500/pcs untuk polo shirt.",
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-cost-1",
                        name = "Approval Jahit Percontohan Brand",
                        isManual = false,
                        sourceModuleCode = BusinessModule.SAMPLING_ORDER.code,
                        sourceModuleName = BusinessModule.SAMPLING_ORDER.displayName,
                        sourceOutputContract = "Approval Jahit Percontohan dari Brand",
                        description = "Hasil uji coba tingkat kesulitan jahit dan kerapihan setikan."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-cost-2",
                        name = "Penetapan Tarif Jasa Makloon per Pcs",
                        isManual = true,
                        operatorRole = "Finance & Akuntan Makloon",
                        inputMethod = "Form Tarif Upah Borongan / Pcs",
                        description = "Perhitungan upah potong + jahit + utilitas listrik per potong baju."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-mrp-spk",
                module = BusinessModule.PRODUCTION_MRP,
                stage = PipelineStage.MANUFACTURING,
                stepNumber = 6,
                title = BusinessModule.PRODUCTION_MRP.displayName,
                description = "Alokasi meja potong dan giliran mesin jahit untuk pesanan makloon brand.",
                assignedDepartment = "Kepala Bengkel / PPIC Makloon",
                deptColorHex = 0xFFD97706,
                inputContract = "Kain Drop dari Buyer Tiba & Jadwal Masuk Antrean",
                outputContract = "SPK Meja Potong & Pembagian Mesin Jahit Makloon",
                wipPieces = 650,
                cycleTimeHours = 3.5,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Kain titipan buyer sudah masuk antrean meja potong.",
                downstreamModuleCodes = listOf(BusinessModule.OPERATOR_EXEC.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-mrp-1",
                        name = "Tarif Ongkos Jahit Disepakati",
                        isManual = false,
                        sourceModuleCode = BusinessModule.COSTING_HPP.code,
                        sourceModuleName = BusinessModule.COSTING_HPP.displayName,
                        sourceOutputContract = "Tarif Ongkos Jahit Bersih (misal Rp 14.000/pcs)",
                        description = "Biaya jasa makloon terverifikasi oleh manajemen."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-mrp-2",
                        name = "Verifikasi Kedatangan Kain Drop Buyer",
                        isManual = true,
                        operatorRole = "Mandor Gudang Makloon",
                        inputMethod = "Ceklis Kuantiti Rol Masuk",
                        description = "Penghitungan fisik jumlah yard kain yang dititipkan oleh buyer."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-mrp-3",
                        name = "Jadwal Meja Potong & Mesin Jahit",
                        isManual = true,
                        operatorRole = "Kepala Bengkel Makloon",
                        inputMethod = "Papan Jadwal Antrean Produksi",
                        description = "Pengaturan giliran jahit sesuai prioritas deadline brand."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-operator-exec",
                module = BusinessModule.OPERATOR_EXEC,
                stage = PipelineStage.MANUFACTURING,
                stepNumber = 7,
                title = BusinessModule.OPERATOR_EXEC.displayName,
                description = "Penjahitan massal oleh para penjahit sesuai instruksi pola buyer.",
                assignedDepartment = "Operator Jahit Makloon",
                deptColorHex = 0xFFEA580C,
                inputContract = "Kain Potongan Siap Jahit + Benang Sesuai Permintaan Buyer",
                outputContract = "Baju Jadi Selesai Jahit Siap Disortir",
                wipPieces = 980,
                cycleTimeHours = 14.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Output jahit harian mencapai 220 pcs/hari dengan 6 operator.",
                downstreamModuleCodes = listOf(BusinessModule.QUALITY_CONTROL.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-op-1",
                        name = "SPK Meja Potong & Pembagian Mesin",
                        isManual = false,
                        sourceModuleCode = BusinessModule.PRODUCTION_MRP.code,
                        sourceModuleName = BusinessModule.PRODUCTION_MRP.displayName,
                        sourceOutputContract = "SPK Meja Potong & Pembagian Mesin Jahit Makloon",
                        description = "Instruksi alokasi potongan bahan per penjahit."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-op-2",
                        name = "Tally Output Jahit Harian Operator",
                        isManual = true,
                        operatorRole = "Operator Jahit Makloon",
                        inputMethod = "Lembar Catatan Tally Fisik",
                        description = "Pencatatan jumlah setelan baju yang selesai dijahit setiap jam."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-qc-defect",
                module = BusinessModule.QUALITY_CONTROL,
                stage = PipelineStage.ASSURANCE_DELIVERY,
                stepNumber = 8,
                title = BusinessModule.QUALITY_CONTROL.displayName,
                description = "Pemeriksaan mutu jahitan sesuai standar toleransi yang disepakati dengan brand.",
                assignedDepartment = "Pemeriksa QC Makloon",
                deptColorHex = 0xFF16A34A,
                inputContract = "Baju Jadi Hasil Jahit",
                outputContract = "Laporan Sortir Lolos & Kain Sisa Potong untuk Dikembalikan",
                wipPieces = 140,
                cycleTimeHours = 2.5,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Kerapihan jahitan lolos audit perwakilan brand.",
                downstreamModuleCodes = listOf(BusinessModule.FULFILLMENT.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-qc-1",
                        name = "Baju Jadi Selesai Jahit dari Operator",
                        isManual = false,
                        sourceModuleCode = BusinessModule.OPERATOR_EXEC.code,
                        sourceModuleName = BusinessModule.OPERATOR_EXEC.displayName,
                        sourceOutputContract = "Baju Jadi Selesai Jahit Siap Disortir",
                        description = "Setelan pakaian siap inspeksi benang dan kerapihan setikan."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-qc-2",
                        name = "Ceklis Standar Mutu Buyer Makloon",
                        isManual = true,
                        operatorRole = "Pemeriksa QC Makloon",
                        inputMethod = "Form Audit Kerapihan Jahit",
                        description = "Pemisahan barang reject dan pembersihan sisa benang jahit."
                    )
                )
            ),
            PipelineNode(
                id = "cmt-fulfillment",
                module = BusinessModule.FULFILLMENT,
                stage = PipelineStage.ASSURANCE_DELIVERY,
                stepNumber = 9,
                title = BusinessModule.FULFILLMENT.displayName,
                description = "Packing plastik bening sederhana per lusin dan pengembalian ke gudang brand buyer.",
                assignedDepartment = "Ekspedisi & Serah Terima",
                deptColorHex = 0xFF059669,
                inputContract = "Baju Jadi Lolos QC + Kain Perca Sisa Buyer",
                outputContract = "Surat Jalan Serah Terima Barang Makloon ke Buyer",
                wipPieces = 90,
                cycleTimeHours = 2.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Siap serah terima dengan tanda tangan berita acara penyerahan.",
                downstreamModuleCodes = emptyList(),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-cmt-ful-1",
                        name = "Baju Jadi Lolos Sortir & Kain Perca Sisa",
                        isManual = false,
                        sourceModuleCode = BusinessModule.QUALITY_CONTROL.code,
                        sourceModuleName = BusinessModule.QUALITY_CONTROL.displayName,
                        sourceOutputContract = "Laporan Sortir Lolos & Kain Sisa Potong untuk Dikembalikan",
                        description = "Pakaian yang siap dipack lusinan beserta sisa kain milik buyer."
                    ),
                    PipelineInputPort(
                        id = "in-cmt-ful-2",
                        name = "Berita Acara Serah Terima Barang Makloon",
                        isManual = true,
                        operatorRole = "Staff Ekspedisi & Serah Terima",
                        inputMethod = "Form Surat Jalan & Tanda Tangan Buyer",
                        description = "Konfirmasi penyerahan barang kembali ke gudang brand pembeli."
                    )
                )
            )
        )
    }

    private fun buildBrandD2cNodes(): List<PipelineNode> {
        return listOf(
            PipelineNode(
                id = "d2c-crm-sales",
                module = BusinessModule.CRM_SALES,
                stage = PipelineStage.COMMERCIAL,
                stepNumber = 1,
                title = BusinessModule.CRM_SALES.displayName,
                description = "Analisis tren penjualan toko online, reseller, dan proyeksi dropship untuk koleksi baru.",
                assignedDepartment = "E-Commerce & Retail Sales",
                deptColorHex = 0xFF2563EB,
                inputContract = "Data Penjualan Marketplace & Saran Pelanggan",
                outputContract = "Rencana Peluncuran Koleksi Baru (Drop Collection)",
                wipPieces = 500,
                cycleTimeHours = 5.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Koleksi Ramadhan siap diproduksi sebanyak 3.000 pcs.",
                downstreamModuleCodes = listOf(BusinessModule.SAMPLING_ORDER.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-crm-1",
                        name = "Data Penjualan Marketplace & Wishlist",
                        isManual = true,
                        operatorRole = "E-Commerce Lead / Brand Strategist",
                        inputMethod = "Dashboard Analytics ERP",
                        description = "Analisis tren konversi toko online dan saran audiens medsos."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-crm-2",
                        name = "Rencana Rilis Drop Collection",
                        isManual = true,
                        operatorRole = "Creative Director",
                        inputMethod = "Form Peluncuran Season Baru",
                        description = "Jadwal peluncuran baju baru untuk teaser media sosial."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-sampling",
                module = BusinessModule.SAMPLING_ORDER,
                stage = PipelineStage.COMMERCIAL,
                stepNumber = 2,
                title = BusinessModule.SAMPLING_ORDER.displayName,
                description = "Desain prototipe in-house, fotoshoot sample untuk pre-order konten media sosial.",
                assignedDepartment = "Kreatif, Desain & Model",
                deptColorHex = 0xFF0284C7,
                inputContract = "Moodboard Desain Kreatif & Sketsa Digital",
                outputContract = "Sample Terpilih untuk Fotoshoot & Pola Produksi Massal",
                wipPieces = 30,
                cycleTimeHours = 10.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Sample disetujui tim kreatif untuk konten TikTok & Reels.",
                downstreamModuleCodes = listOf(BusinessModule.TECH_PACK_BOM.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-samp-1",
                        name = "Rencana Peluncuran Koleksi Baru",
                        isManual = false,
                        sourceModuleCode = BusinessModule.CRM_SALES.code,
                        sourceModuleName = BusinessModule.CRM_SALES.displayName,
                        sourceOutputContract = "Rencana Peluncuran Koleksi Baru (Drop Collection)",
                        description = "Target tema model dan kuota batch drop koleksi."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-samp-2",
                        name = "Moodboard Kreatif & Sketsa 3D Digital",
                        isManual = true,
                        operatorRole = "Fashion Designer & Tim Kreatif",
                        inputMethod = "Upload Sketsa Digital / AI Prompt Asset",
                        description = "Desain visual tampak depan/belakang dan inspirasi warna kain."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-tech-pack",
                module = BusinessModule.TECH_PACK_BOM,
                stage = PipelineStage.ENGINEERING,
                stepNumber = 3,
                title = BusinessModule.TECH_PACK_BOM.displayName,
                description = "Standarisasi fitting brand sendiri (misal: Oversized Streetwear Fit) dan konsumsi kain.",
                assignedDepartment = "Pola Mandiri & Spesifikasi",
                deptColorHex = 0xFF7C3AED,
                inputContract = "Sample Fitting Disetujui",
                outputContract = "Pola Master CAD Brand Sendiri",
                wipPieces = 90,
                cycleTimeHours = 4.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Pola cutting oversize grade S, M, L, XL tersimpan di arsip digital.",
                downstreamModuleCodes = listOf(BusinessModule.INVENTORY.code, BusinessModule.COSTING_HPP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-tp-1",
                        name = "Sample Fitting Disetujui Tim Kreatif",
                        isManual = false,
                        sourceModuleCode = BusinessModule.SAMPLING_ORDER.code,
                        sourceModuleName = BusinessModule.SAMPLING_ORDER.displayName,
                        sourceOutputContract = "Sample Terpilih untuk Fotoshoot & Pola Produksi Massal",
                        description = "Baju contoh yang telah dites kenyamanan jatuhnya di badan model."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-tp-2",
                        name = "Grading Pola Master CAD Brand In-House",
                        isManual = true,
                        operatorRole = "Tukang Pola In-House",
                        inputMethod = "CAD Software & Digital Grading",
                        description = "Pola paten khas brand (drop shoulder, rib leher tebal, panjang lengan)."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-inventory",
                module = BusinessModule.INVENTORY,
                stage = PipelineStage.SUPPLY_CHAIN,
                stepNumber = 4,
                title = BusinessModule.INVENTORY.displayName,
                description = "Penyimpanan stok kain custom wash, label woven brand, polybag bermerk, dan hangtag eksklusif.",
                assignedDepartment = "Gudang Stok Internal",
                deptColorHex = 0xFF0D9488,
                inputContract = "Kain Heavyweight Cotton 16s & Label Woven Masuk",
                outputContract = "Bahan Siap Potong Terverifikasi Kualitasnya",
                wipPieces = 1800,
                cycleTimeHours = 6.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Kain Heavyweight Cotton aman tersedia untuk 2 batch produksi.",
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-inv-1",
                        name = "Kebutuhan Material dari Pola Master CAD",
                        isManual = false,
                        sourceModuleCode = BusinessModule.TECH_PACK_BOM.code,
                        sourceModuleName = BusinessModule.TECH_PACK_BOM.displayName,
                        sourceOutputContract = "Pola Master CAD Brand Sendiri",
                        description = "Kalkulasi rol kain dan aksesoris tag brand."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-inv-2",
                        name = "Penerimaan Kain Heavyweight & Label Woven",
                        isManual = true,
                        operatorRole = "Staff Gudang Internal",
                        inputMethod = "Barcode Penerimaan Bahan",
                        description = "Inspeksi gramasi kain 16s/20s dan kerapihan label satin/woven."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-costing-hpp",
                module = BusinessModule.COSTING_HPP,
                stage = PipelineStage.ENGINEERING,
                stepNumber = 5,
                title = BusinessModule.COSTING_HPP.displayName,
                description = "Penetapan harga jual ritel (MSRP) berdasarkan modal produksi agar margin toko dan diskon promosi tetap untung.",
                assignedDepartment = "Keuangan Bisnis Retail",
                deptColorHex = 0xFF6366F1,
                inputContract = "Biaya Produksi Kotor + Budget Packaging Mewah",
                outputContract = "Harga Retail Resmi & Batas Diskon Promo Flash Sale",
                wipPieces = 40,
                cycleTimeHours = 2.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Margin e-commerce ditetapkan di angka 62% pasca biaya packaging.",
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-cost-1",
                        name = "Konsumsi Kain Master CAD",
                        isManual = false,
                        sourceModuleCode = BusinessModule.TECH_PACK_BOM.code,
                        sourceModuleName = BusinessModule.TECH_PACK_BOM.displayName,
                        sourceOutputContract = "Pola Master CAD Brand Sendiri",
                        description = "Rincian biaya modal kain per kaos."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-cost-2",
                        name = "Biaya Packaging Mewah & Penetapan MSRP",
                        isManual = true,
                        operatorRole = "Retail Finance Specialist",
                        inputMethod = "Form Penetapan Harga Retail & Margin",
                        description = "Kalkulasi margin 60%+ untuk menutup biaya iklan (ROAS) dan diskon payday."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-mrp-spk",
                module = BusinessModule.PRODUCTION_MRP,
                stage = PipelineStage.MANUFACTURING,
                stepNumber = 6,
                title = BusinessModule.PRODUCTION_MRP.displayName,
                description = "Pengaturan jadwal jahit mingguan untuk restock varian ukuran terlaris (fast moving SKU).",
                assignedDepartment = "Perencanaan Produksi Brand",
                deptColorHex = 0xFFD97706,
                inputContract = "Peringatan Stok Tipis dari Marketplace",
                outputContract = "SPK Restock Batch Baru per Warna & Ukuran",
                wipPieces = 700,
                cycleTimeHours = 4.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Batch restock warna Black & Washed Grey berjalan sesuai jadwal.",
                downstreamModuleCodes = listOf(BusinessModule.OPERATOR_EXEC.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-mrp-1",
                        name = "Batas Margin HPP & MSRP Resmi",
                        isManual = false,
                        sourceModuleCode = BusinessModule.COSTING_HPP.code,
                        sourceModuleName = BusinessModule.COSTING_HPP.displayName,
                        sourceOutputContract = "Harga Retail Resmi & Batas Diskon Promo Flash Sale",
                        description = "Target volume produksi batch baru."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-mrp-2",
                        name = "Stok Kain Gudang Internal Ready",
                        isManual = false,
                        sourceModuleCode = BusinessModule.INVENTORY.code,
                        sourceModuleName = BusinessModule.INVENTORY.displayName,
                        sourceOutputContract = "Bahan Siap Potong Terverifikasi Kualitasnya",
                        description = "Ketersediaan kain ready di lantai workshop."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-mrp-3",
                        name = "Trigger Alert Restock SKU Terlaris",
                        isManual = true,
                        operatorRole = "Production Planner Brand",
                        inputMethod = "Matrix Restock Order",
                        description = "Penetapan jadwal pemotongan kain untuk size L & XL yang cepat habis."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-operator-exec",
                module = BusinessModule.OPERATOR_EXEC,
                stage = PipelineStage.MANUFACTURING,
                stepNumber = 7,
                title = BusinessModule.OPERATOR_EXEC.displayName,
                description = "Penjahitan oleh konveksi in-house dengan penekanan detail sablon discharge / bordir komputer.",
                assignedDepartment = "Lantai Jahit Brand",
                deptColorHex = 0xFFEA580C,
                inputContract = "Potongan Kain Berlabel + Sablon / Bordir Jadi",
                outputContract = "Kaos / Hoodie Selesai Jahit",
                wipPieces = 1100,
                cycleTimeHours = 15.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Operator jahit rantai leher bekerja sesuai standar kerapihan brand.",
                downstreamModuleCodes = listOf(BusinessModule.QUALITY_CONTROL.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-op-1",
                        name = "SPK Restock Batch Baru per Warna",
                        isManual = false,
                        sourceModuleCode = BusinessModule.PRODUCTION_MRP.code,
                        sourceModuleName = BusinessModule.PRODUCTION_MRP.displayName,
                        sourceOutputContract = "SPK Restock Batch Baru per Warna & Ukuran",
                        description = "Instruksi potong dan assembly jahit in-house."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-op-2",
                        name = "Verifikasi Hasil Sablon & Jahit Rantai",
                        isManual = true,
                        operatorRole = "Mandor Konveksi Brand",
                        inputMethod = "Checksheet Pengerjaan Sablon/Jahit",
                        description = "Pemeriksaan presisi letak grafis sablon dan jahitan rantai bahu."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-qc-defect",
                module = BusinessModule.QUALITY_CONTROL,
                stage = PipelineStage.ASSURANCE_DELIVERY,
                stepNumber = 8,
                title = BusinessModule.QUALITY_CONTROL.displayName,
                description = "Pengecekan super ketat untuk menghindari ulasan bintang 1 dari pembeli online (Zero defect customer policy).",
                assignedDepartment = "QC Tim Brand",
                deptColorHex = 0xFF16A34A,
                inputContract = "Kaos Hasil Jahit dari Line Produksi",
                outputContract = "Kaos Siap Pasang Tag Barcode SKU Retail",
                wipPieces = 190,
                cycleTimeHours = 3.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Inspeksi teliti, 100% item dipastikan bebas noda dan benang sisa.",
                downstreamModuleCodes = listOf(BusinessModule.FULFILLMENT.code),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-qc-1",
                        name = "Kaos / Hoodie Selesai Jahit",
                        isManual = false,
                        sourceModuleCode = BusinessModule.OPERATOR_EXEC.code,
                        sourceModuleName = BusinessModule.OPERATOR_EXEC.displayName,
                        sourceOutputContract = "Kaos / Hoodie Selesai Jahit",
                        description = "Pakaian jadi dari lantai workshop in-house."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-qc-2",
                        name = "Ceklis Zero-Defect Customer Policy",
                        isManual = true,
                        operatorRole = "Inspector QC Tim Brand",
                        inputMethod = "Form Audit Standar Brand Premium",
                        description = "Pemeriksaan 100% item bebas sisa benang, noda minyak, atau cacat sablon."
                    )
                )
            ),
            PipelineNode(
                id = "d2c-fulfillment",
                module = BusinessModule.FULFILLMENT,
                stage = PipelineStage.ASSURANCE_DELIVERY,
                stepNumber = 9,
                title = BusinessModule.FULFILLMENT.displayName,
                description = "Finishing wangi, kemas ziplock bag bermerk, stiker merchandise, dan serah terima ke kurir marketplace.",
                assignedDepartment = "Packing & Pengiriman Retail",
                deptColorHex = 0xFF059669,
                inputContract = "Kaos Grade A Lolos QC + Ziplock Bag & Stiker",
                outputContract = "Paket Resi Pesanan Siap Kirim ke Konsumen Langsung",
                wipPieces = 220,
                cycleTimeHours = 2.0,
                healthStatus = FlowHealthStatus.HEALTHY,
                healthMessage = "Paket pre-order siap di-pickup ekspedisi harian jam 16:00.",
                downstreamModuleCodes = emptyList(),
                inputs = listOf(
                    PipelineInputPort(
                        id = "in-d2c-ful-1",
                        name = "Kaos Siap Pasang Barcode SKU Retail",
                        isManual = false,
                        sourceModuleCode = BusinessModule.QUALITY_CONTROL.code,
                        sourceModuleName = BusinessModule.QUALITY_CONTROL.displayName,
                        sourceOutputContract = "Kaos Siap Pasang Tag Barcode SKU Retail",
                        description = "Item terverifikasi siap dibungkus kemasan ritel."
                    ),
                    PipelineInputPort(
                        id = "in-d2c-ful-2",
                        name = "Kemas Ziplock, Stiker & Cetak Resi Kurir",
                        isManual = true,
                        operatorRole = "Staff Packing & Pengiriman Retail",
                        inputMethod = "Scan Barcode Resi & Packing Ekspedisi",
                        description = "Penyemprotan pewangi pakaian, penyisipan free gift stiker, dan serah terima kurir."
                    )
                )
            )
        )
    }
}
