package com.eventverse.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.auth.LoginScreen
import com.eventverse.app.presentation.orgchart.OrgChartScreen
import com.eventverse.app.presentation.rbac.DynamicRbacScreen
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.theme.WeMadeTheme

enum class AppNavScreen {
    DYNAMIC_RBAC,
    ORG_CHART,
    LOGIN
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(AppNavScreen.ORG_CHART) }

    WeMadeTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Switcher Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WeMadeColors.Surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "WeMade ERP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = WeMadeColors.Primary
                        )
                        Text(
                            text = "•",
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = "Multi-Tenant Garment Platform",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentScreen == AppNavScreen.ORG_CHART,
                            onClick = { currentScreen = AppNavScreen.ORG_CHART },
                            label = { Text("Bagan Organisasi", fontSize = 12.sp, fontWeight = if (currentScreen == AppNavScreen.ORG_CHART) FontWeight.Bold else FontWeight.Medium) }
                        )
                        FilterChip(
                            selected = currentScreen == AppNavScreen.DYNAMIC_RBAC,
                            onClick = { currentScreen = AppNavScreen.DYNAMIC_RBAC },
                            label = { Text("Hak Akses (RBAC)", fontSize = 12.sp, fontWeight = if (currentScreen == AppNavScreen.DYNAMIC_RBAC) FontWeight.Bold else FontWeight.Medium) }
                        )
                        FilterChip(
                            selected = currentScreen == AppNavScreen.LOGIN,
                            onClick = { currentScreen = AppNavScreen.LOGIN },
                            label = { Text("Login Akun", fontSize = 12.sp, fontWeight = if (currentScreen == AppNavScreen.LOGIN) FontWeight.Bold else FontWeight.Medium) }
                        )
                    }
                }
            }

            Crossfade(targetState = currentScreen, modifier = Modifier.weight(1f)) { screen ->
                when (screen) {
                    AppNavScreen.ORG_CHART -> {
                        OrgChartScreen()
                    }
                    AppNavScreen.DYNAMIC_RBAC -> {
                        DynamicRbacScreen(
                            onBackToLogin = { currentScreen = AppNavScreen.LOGIN }
                        )
                    }
                    AppNavScreen.LOGIN -> {
                        LoginScreen()
                    }
                }
            }
        }
    }
}