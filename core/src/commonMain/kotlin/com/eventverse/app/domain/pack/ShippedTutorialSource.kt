package com.eventverse.app.domain.pack

import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialAnchorId
import com.eventverse.app.domain.tutorial.TutorialSource

/** Tutorial pack yang dikirim bersama rilis — padanan tutorial untuk [DomainPackRegistry.shipped]. */
object ShippedTutorialSource : TutorialSource {
    private val tutorials = mapOf(GarmentDomainPack.CODE to { GarmentTutorials.all })
    private val anchors = mapOf(GarmentDomainPack.CODE to { GarmentTutorialAnchors.all })

    override fun tutorialsFor(pack: DomainPackCode): List<ModuleTutorial> = tutorials[pack]?.invoke().orEmpty()
    override fun anchorsFor(pack: DomainPackCode): Set<TutorialAnchorId> = anchors[pack]?.invoke().orEmpty()
}
