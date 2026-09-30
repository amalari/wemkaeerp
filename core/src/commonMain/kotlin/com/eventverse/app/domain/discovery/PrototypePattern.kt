package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.auth.UserId

/**
 * Pola layout Studio (plan §4, Fase C): resep menyusun widget yang dipanen dari layar produksi,
 * dipakai ulang antar draf. Disimpan di `ops.prototype_patterns` (V79) — schema platform,
 * owner-only. [patternJson] adalah objek JSON bebas (posisi/ukuran/konfigurasi widget): isinya
 * milik Studio, bukan domain — yang divalidasi domain hanyalah identitas & widget-nya.
 */
data class PrototypePattern(
    val id: String,
    val name: String,
    val widget: WidgetKind,
    val packCode: String?,
    val patternJson: String,
    val createdByUserId: UserId,
) {
    init {
        require(id.isNotBlank()) { "PrototypePattern.id kosong" }
        require(name.isNotBlank()) { "PrototypePattern.name kosong" }
        require(patternJson.isNotBlank()) { "PrototypePattern.patternJson kosong" }
    }
}

interface PrototypePatternRepository {
    suspend fun findById(id: String): PrototypePattern?
    suspend fun findByName(name: String): PrototypePattern?
    suspend fun findAll(): List<PrototypePattern>
    suspend fun save(pattern: PrototypePattern)
}

/**
 * Simpan pola Studio. Fail-closed: `patternJson` wajib objek JSON sah, widget wajib kosakata
 * tertutup, dan pack (bila disebut) wajib dikenal registry — pola tidak boleh meluncur menggantung
 * pada pack hantu.
 */
class SavePrototypePatternUseCase(private val repository: PrototypePatternRepository) {

    suspend operator fun invoke(
        id: String,
        name: String,
        widgetCode: String,
        patternJson: String,
        createdByUserId: UserId,
        packCode: String? = null
    ): Result<PrototypePattern> = runCatching {
        val widget = WidgetKind.fromCode(widgetCode)
        requireNotNull(widget) { "Widget '$widgetCode' bukan kosakata tertutup" }
        val parsed = com.eventverse.app.shared.json.JsonParser.parseObjectOrNull(patternJson)
        requireNotNull(parsed) { "patternJson wajib objek JSON" }
        packCode?.let {
            requireNotNull(com.eventverse.app.domain.pack.DomainPackRegistry.find(com.eventverse.app.domain.pack.DomainPackCode(it))) {
                "Pack '$it' tidak dikenal registry"
            }
        }
        repository.findByName(name.trim())?.let { existing ->
            require(existing.id == id) { "Nama pola '${name.trim()}' sudah dipakai pola ${existing.id}" }
        }
        val pattern = PrototypePattern(id, name.trim(), widget, packCode, parsed.encode(), createdByUserId)
        repository.save(pattern)
        pattern
    }
}
