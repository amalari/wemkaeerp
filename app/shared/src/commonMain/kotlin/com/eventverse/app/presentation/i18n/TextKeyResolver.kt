package com.eventverse.app.presentation.i18n

import androidx.compose.runtime.Composable
import com.eventverse.app.domain.i18n.TextKey

object TextKeyResolver {

    fun resolve(key: TextKey): String {
        return resolve(key.key)
    }

    fun resolve(key: String): String {
        return key
    }
}

@Composable
fun text(key: TextKey): String {
    return TextKeyResolver.resolve(key)
}

@Composable
fun text(key: String): String {
    return TextKeyResolver.resolve(key)
}
