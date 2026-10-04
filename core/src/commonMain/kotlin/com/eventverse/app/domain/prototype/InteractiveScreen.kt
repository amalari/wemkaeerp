package com.eventverse.app.domain.prototype

/** Layar yang bisa dimainkan: spec + isi awal (seed). Dibuat ulang deterministik dari data pack. */
data class InteractiveScreen(val spec: PrototypeSpec, val seed: Map<String, List<PrototypeRow>>) {
    fun newStore(): PrototypeStore = PrototypeStore.seeded(spec, seed)
}
