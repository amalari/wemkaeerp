package com.eventverse.app.presentation.discovery.studio

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.DiscoveryModuleUi
import com.eventverse.app.presentation.discovery.DiscoveryScreenUi
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Satu isian dalam satu baris pola: `<kolom>` → `<contoh isi>`. */
data class PrototypeFieldUi(val label: String, val value: String)

/**
 * Satu baris pola. Bentuknya **disengaja sama** dengan satu elemen `sampleRows` yang dibawa draf,
 * sehingga pola bisa digambar `PrototypeRenderer` tanpa penerjemahan — dan apa yang dilihat di
 * Studio persis apa yang dilihat prospek di pratinjau draf.
 */
data class PrototypeRowUi(val fields: List<PrototypeFieldUi>) {

    /**
     * Bentuk yang diminta `PrototypeRenderer`. Urutan isian dipertahankan (`LinkedHashMap`, sama
     * seperti `JsonParser` menyimpan objek): pada `TABLE` urutan kunci = urutan baris label–isi.
     */
    fun toSampleRow(): Map<String, String> = LinkedHashMap<String, String>(fields.size).apply {
        fields.forEach { (label, value) -> put(label, value) }
    }
}

/**
 * Pola layar Studio (plan §4, Fase C) — resep susun widget yang dipakai ulang antar draf, tersimpan
 * di `ops.prototype_patterns` (V79).
 *
 * Isi pola adalah `rows`, dan itu satu-satunya yang diklaimnya: **bentuk** layar (kolom apa, blok apa,
 * selebar apa). Contoh isinya pun tetap contoh — yang dinilai prospek adalah susunannya. Kerangka
 * awal baris **dipanen dari `WidgetRegistry`** ([skeletonFrom]), bukan dikarang di layar Studio,
 * supaya pola baru tidak mulai dari nol dan tetap sejalan dengan sampel yang digambar draf.
 *
 * Modul asal tidak disimpan: yang tersimpan adalah baris konkretnya. Modul hanya dipakai sekali saat
 * memanen kerangka; menyimpannya akan membuat pola mengklaim terikat pada modul yang mungkin tidak
 * ada di pack tenant lain.
 */
data class PrototypePatternUi(
    val id: String = "",
    val name: String = "",
    val widgetCode: String = WidgetKind.TABLE.code,
    val packCode: String? = null,
    val rows: List<PrototypeRowUi> = emptyList()
) {
    val widget: WidgetKind? get() = WidgetKind.fromCode(widgetCode)
    val widgetLabel: String get() = widget?.displayName ?: widgetCode
    val isNew: Boolean get() = id.isBlank()

    /** Label pack untuk tampilan; pack yang tidak dikenal registry ditampilkan apa adanya. */
    val packLabel: String
        get() = packCode?.let { code -> DomainPackRegistry.find(DomainPackCode(code))?.displayName ?: code }
            ?: "tanpa pack"

    /**
     * JSON yang dikirim ke `POST /api/discovery/patterns`: `{"rows":[{"<kolom>":"<contoh>"}]}`.
     *
     * Bentuk ini milik Studio, tapi sengaja datar dan urut supaya bisa dibaca manusia di kolom
     * `pattern_json` tanpa perkakas — pola yang rusak biasanya ketahuan dari membaca isinya.
     */
    fun patternJson(): JsonValue.Obj = jsonObjectOf(
        "rows" to jsonArrayOf(
            rows.map { row ->
                jsonObjectOf(*row.fields.map { it.label to jsonOf(it.value) }.toTypedArray())
            }
        )
    )

    /**
     * Bungkus pola menjadi draf sekali-layar agar digambar `PrototypeRenderer` **yang sama** dengan
     * pratinjau draf (plan D2: satu renderer generik). Modul asal kerangka dipakai sebagai nama modul
     * di kartu pratinjau; kalau pola disusun tanpa modul, penanda netral dipakai.
     */
    fun toPreviewDraft(moduleId: String, moduleName: String, section: String): DiscoveryDraftUi =
        DiscoveryDraftUi(
            id = "pattern-preview",
            status = "DRAFT",
            packCode = packCode.orEmpty(),
            packDisplayName = packLabel,
            blueprintCode = "",
            blueprintDescription = "Pratinjau pola Studio",
            modules = listOf(
                DiscoveryModuleUi(
                    id = moduleId,
                    displayName = moduleName,
                    section = section,
                    kind = widgetCode,
                    slot = null,
                    slotInput = null,
                    slotOutput = null,
                    active = true
                )
            ),
            activeModuleCodes = listOf(moduleId),
            screens = listOf(
                DiscoveryScreenUi(
                    screenId = "pattern-preview",
                    moduleId = moduleId,
                    title = name.ifBlank { "Pola tanpa nama" },
                    widget = widgetCode,
                    sampleRows = rows.map { it.toSampleRow() }
                )
            )
        )

    companion object {

        /**
         * Kerangka baris dipanen dari `WidgetRegistry` — sumber yang **sama** dengan sampel yang
         * digambar pratinjau draf, jadi pola tidak perlahan berbeda dari yang sudah dipakai sistem.
         *
         * Kosong berarti `WidgetRegistry` tidak punya bentuk untuk kombinasi modul × widget itu
         * (modul asing, atau widget di luar kosakata); layar Studio yang menjelaskannya ke pengguna.
         */
        fun skeletonFrom(pack: DomainPack, moduleId: String, widgetCode: String): List<PrototypeRowUi> {
            if (WidgetKind.fromCode(widgetCode) == null) return emptyList()
            if (pack.modules.none { it.id.value == moduleId }) return emptyList()
            return WidgetRegistry.sampleRowsFor(
                PrototypeScreen(
                    screenId = "pattern-skeleton",
                    moduleId = ModuleId(moduleId),
                    title = "Kerangka pola",
                    widget = widgetCode
                ),
                pack
            ).map { row -> PrototypeRowUi(row.map { (label, value) -> PrototypeFieldUi(label, value) }) }
        }

        /** Parse satu pola (`GET /api/discovery/patterns` item atau respons `POST`). */
        fun fromJson(o: JsonValue.Obj): PrototypePatternUi {
            val pattern = o["pattern"] as? JsonValue.Obj
            val rows = (pattern?.entries?.get("rows") as? JsonValue.Arr)?.items ?: emptyList()
            return PrototypePatternUi(
                id = o.string("id").orEmpty(),
                name = o.string("name").orEmpty(),
                widgetCode = o.string("widget").orEmpty(),
                packCode = o.string("packCode")?.takeIf { it.isNotBlank() },
                rows = rows.mapNotNull { it as? JsonValue.Obj }.map { row ->
                    PrototypeRowUi(row.entries.map { (label, value) -> PrototypeFieldUi(label, value.plainText()) })
                }
            )
        }

        /**
         * Daftar pola. Baris yang bentuknya tidak dikenali **dibuang, tidak menggagalkan seluruh
         * daftar**: satu pola tua yang ditulis tangan (`{"kolom":["a"]}`) tidak boleh menyembunyikan
         * pola lain yang masih sehat.
         */
        fun listFromJson(value: JsonValue): List<PrototypePatternUi> =
            (value as? JsonValue.Arr)?.items.orEmpty().mapNotNull { it as? JsonValue.Obj }.map(::fromJson)

        /** Nilai JSON apa pun → teks yang bisa ditampilkan & disunting; objek/array tidak punya kolom. */
        private fun JsonValue.plainText(): String = when (this) {
            is JsonValue.Str -> value
            is JsonValue.Num -> raw
            is JsonValue.Bool -> if (value) "ya" else "tidak"
            else -> ""
        }
    }
}
