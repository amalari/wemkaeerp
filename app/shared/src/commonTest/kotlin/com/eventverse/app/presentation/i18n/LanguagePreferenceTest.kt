package com.eventverse.app.presentation.i18n

import com.eventverse.app.domain.i18n.LanguageCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguagePreferenceTest {

    @Test
    fun parseStored_kodeDikenal_dibaca() {
        assertEquals(LanguageCode.EN, LanguagePreference.parseStored("en"))
        assertEquals(LanguageCode.ID, LanguagePreference.parseStored(" ID "))
    }

    @Test
    fun parseStored_kodeTakDikenalAtauKosong_null_bukanBahasaLain() {
        assertNull(LanguagePreference.parseStored("fr"))
        assertNull(LanguagePreference.parseStored(""))
        assertNull(LanguagePreference.parseStored(null))
    }
}
