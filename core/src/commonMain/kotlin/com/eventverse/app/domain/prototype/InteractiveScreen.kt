package com.eventverse.app.domain.prototype

/** Layar yang bisa dimainkan: spec + isi awal (seed) + asal datanya ([DataBinding]). Dibuat ulang deterministik dari data pack. */
data class InteractiveScreen(
    val spec: PrototypeSpec,
    val seed: Map<String, List<PrototypeRow>>,
    /** Kontrak v2: memori (bawaan, demo) atau API (pilot). Spec/pack lama tanpa kunci = [DataBinding.Memory]. */
    val binding: DataBinding = DataBinding.Memory,
) {
    fun newStore(): PrototypeStore = PrototypeStore.seeded(spec, seed)
}
