package com.eventverse.app.domain.invoicing.template

/**
 * Primitif kertas kini tinggal di [com.eventverse.app.domain.printing], karena bukan hanya invoice
 * yang dicetak: kartu telusur dan lembar kerja rajut memakai geometri yang sama persis.
 *
 * Alias ini dipertahankan supaya seluruh kanvas designer, renderer PDF, dan codec template tidak
 * perlu disentuh sama sekali oleh pemindahan itu. Pemindahan dilakukan sekarang, selagi pemakainya
 * masih satu — setelah ada pemakai kedua, memindahkannya berarti satu PR yang menyentuh dua fitur.
 */
typealias Mm10 = com.eventverse.app.domain.printing.Mm10
typealias TemplateRect = com.eventverse.app.domain.printing.TemplateRect
typealias PaperSize = com.eventverse.app.domain.printing.PaperSize
typealias TextAlign = com.eventverse.app.domain.printing.TextAlign
typealias TextStyleSpec = com.eventverse.app.domain.printing.TextStyleSpec
