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
                downstreamModuleCodes = listOf(BusinessModule.SAMPLING_ORDER.code)
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
                downstreamModuleCodes = listOf(BusinessModule.TECH_PACK_BOM.code)
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
                downstreamModuleCodes = listOf(BusinessModule.INVENTORY.code, BusinessModule.COSTING_HPP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.COSTING_HPP.code, BusinessModule.PRODUCTION_MRP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.OPERATOR_EXEC.code)
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
                downstreamModuleCodes = listOf(BusinessModule.QUALITY_CONTROL.code)
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
                downstreamModuleCodes = listOf(BusinessModule.FULFILLMENT.code)
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
                downstreamModuleCodes = emptyList()
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
                downstreamModuleCodes = listOf(BusinessModule.SAMPLING_ORDER.code)
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
                downstreamModuleCodes = listOf(BusinessModule.COSTING_HPP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.OPERATOR_EXEC.code)
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
                downstreamModuleCodes = listOf(BusinessModule.QUALITY_CONTROL.code)
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
                downstreamModuleCodes = listOf(BusinessModule.FULFILLMENT.code)
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
                downstreamModuleCodes = emptyList()
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
                downstreamModuleCodes = listOf(BusinessModule.SAMPLING_ORDER.code)
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
                downstreamModuleCodes = listOf(BusinessModule.TECH_PACK_BOM.code)
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
                downstreamModuleCodes = listOf(BusinessModule.INVENTORY.code, BusinessModule.COSTING_HPP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.PRODUCTION_MRP.code)
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
                downstreamModuleCodes = listOf(BusinessModule.OPERATOR_EXEC.code)
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
                downstreamModuleCodes = listOf(BusinessModule.QUALITY_CONTROL.code)
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
                downstreamModuleCodes = listOf(BusinessModule.FULFILLMENT.code)
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
                downstreamModuleCodes = emptyList()
            )
        )
    }
}
