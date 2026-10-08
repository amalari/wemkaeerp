package com.eventverse.app.domain.pack

import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode

/**
 * Template rute serah terima karung pack garment (TRD-FLOW-003 A1): dua rute yang selama ini hidup sebagai
 * enum `SackRoute`.
 *
 * **Data literal, sengaja tidak diturunkan dari `SackRoute.entries`.** Kode, label, dan urutannya dikunci
 * `GarmentHandoverRoutesParityTest` terhadap enum; bila template ini dihitung dari enum, test itu tak
 * membuktikan apa-apa, dan menghapus enum di S3 ikut merusak template.
 *
 * Kode identik dengan nama enum (D3) sehingga nilai yang sudah tersimpan di `fulfillment_transfers.leg` dan
 * `fulfillment_route_settings.route` tetap sah tanpa backfill. `from`/`to` sengaja kosong: rute berfungsi tanpa
 * terhubung ke simpul alur (D2), dan admin mengisinya hanya bila ingin rute dicocokkan ke leg.
 *
 * Template **disalin** ke tenant saat admin pertama kali mengubah daftar; tenant tanpa baris memakai template
 * ini secara efektif. Mengubah template tidak sampai ke tenant yang sudah punya salinan.
 */
object GarmentHandoverRoutes {
    val template: List<HandoverRoute> = listOf(
        HandoverRoute(HandoverRouteCode("QC_RAJUT_TO_FINISHING"), "QC Rajut ke Finishing", sortOrder = 0),
        HandoverRoute(HandoverRouteCode("FINISHING_TO_QC_FINISHING"), "Finishing ke QC Finishing", sortOrder = 1)
    )
}
