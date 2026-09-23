package com.eventverse.app.shared.fulfillment

import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kontrak JSON tunggal konfigurasi rute — dipakai server dan client.
 *
 * Seluruh rute selalu dipancarkan, termasuk yang belum pernah disetel, berikut mode efektifnya.
 * UI jadi tidak perlu mengulang aturan *fallback*: kalau client dan server masing-masing
 * menyimpulkan mode untuk rute yang kosong, cepat atau lambat keduanya berbeda dan tidak ada
 * yang tahu mana yang benar.
 */
object FulfillmentRouteConfigCodec {

    fun encode(config: FulfillmentRouteConfig): JsonValue.Obj = jsonObjectOf(
        "tenantId" to jsonOf(config.tenantId.value),
        "hasAdminHubRoute" to jsonOf(config.hasAdminHubRoute),
        "routes" to jsonArrayOf(
            SackRoute.entries.map { route ->
                val mode = config.modeFor(route)
                jsonObjectOf(
                    "route" to jsonOf(route.name),
                    "routeLabel" to jsonOf(route.displayName),
                    "mode" to jsonOf(mode.name),
                    "modeLabel" to jsonOf(mode.displayName),
                    // Membedakan "sengaja disetel ADMIN_HUB" dari "belum pernah disentuh".
                    // Keduanya berperilaku sama, tapi hanya yang kedua yang layak ditawari
                    // pengaturan awal di layar konfigurasi nanti.
                    "isExplicit" to jsonOf(route in config.modes)
                )
            }
        )
    )

    /**
     * Membaca mode tiap rute dari payload — **tanpa** menyentuh `tenantId`.
     *
     * Dipisah justru karena pemiliknya tidak pernah berasal dari payload: di server ia datang
     * dari sesi, di client dari slug yang sedang aktif. Mengurainya dari JSON berarti membuat
     * `TenantId("")` saat field-nya absen, dan value class itu melempar seketika.
     */
    fun decodeModes(obj: JsonValue.Obj): Map<SackRoute, HandoverMode> {
        val modes = obj.objectArray("routes")
            .mapNotNull { row ->
                // Baris yang rutenya tidak dikenali dilewati satu per satu, bukan menjatuhkan
                // seluruh konfigurasi: enum rute bisa menyusut antar rilis, dan layar kerja
                // harus tetap terbuka meski satu baris usang.
                val route = enumOrNull<SackRoute>(row.string("route")) ?: return@mapNotNull null
                val mode = enumOrNull<HandoverMode>(row.string("mode")) ?: return@mapNotNull null
                if (row.boolean("isExplicit") == false) null else route to mode
            }
            .toMap()

        return modes
    }

    /** Konfigurasi utuh untuk [tenantId] yang sudah diketahui pemanggil. */
    fun decode(obj: JsonValue.Obj, tenantId: TenantId): FulfillmentRouteConfig =
        FulfillmentRouteConfig(tenantId = tenantId, modes = decodeModes(obj))

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } }
}
