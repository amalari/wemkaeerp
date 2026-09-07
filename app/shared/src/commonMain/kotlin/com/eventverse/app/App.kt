package com.eventverse.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.eventverse.app.presentation.auth.LoginScreen
import com.eventverse.app.presentation.theme.WeMadeTheme

@Composable
@Preview
fun App() {
    WeMadeTheme {
        LoginScreen()
    }
}