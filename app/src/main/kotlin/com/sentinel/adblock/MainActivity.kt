package com.sentinel.adblock

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.adblock.cosmetic.CosmeticStatistics
import com.sentinel.adblock.monitoring.ProtectionState
import com.sentinel.adblock.ui.AppRulesScreen
import com.sentinel.adblock.ui.BrandMark
import com.sentinel.adblock.ui.DashboardScreen
import com.sentinel.adblock.ui.SentinelViewModel
import com.sentinel.adblock.ui.SettingsScreen
import com.sentinel.adblock.ui.StatisticsScreen
import com.sentinel.adblock.ui.theme.SentinelTheme
import com.sentinel.adblock.vpn.VpnController

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SentinelTheme {
                SentinelRoot()
            }
        }
    }
}

private enum class MainTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Home),
    RULES("Rules", Icons.AutoMirrored.Filled.List),
    STATS("Stats", Icons.Filled.CheckCircle),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SentinelRoot(viewModel: SentinelViewModel = viewModel()) {
    val context = LocalContext.current

    val status by viewModel.status.collectAsStateWithLifecycle()
    val counters by viewModel.counters.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val blocklist by viewModel.blocklist.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val cosmeticHidden by CosmeticStatistics.hidden.collectAsStateWithLifecycle()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    // VpnService.prepare() must be launched as an activity result: the system owns
    // the consent dialog and returns RESULT_OK only if the user accepted it.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VpnController.start(context)
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The tunnel works either way; a denied notification is only cosmetic. */ }

    LaunchedEffect(Unit) {
        // Ask once, on first launch, and never nag again: the system dialog handles that.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    val onToggle: (Boolean) -> Unit = { wantProtection ->
        if (!wantProtection) {
            VpnController.stop(context)
        } else {
            val consent = VpnController.prepareIntent(context)
            if (consent != null) consentLauncher.launch(consent) else VpnController.start(context)
        }
    }

    BackHandler(enabled = tabIndex != 0) { tabIndex = 0 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark()
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Shieldly",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = MainTab.entries[tabIndex].label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            bottomBar = {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        MainTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = tabIndex == index,
                                onClick = { tabIndex = index },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (MainTab.entries[tabIndex]) {
                    MainTab.DASHBOARD -> DashboardScreen(
                        status = status,
                        counters = counters,
                        recent = recent,
                        blocklist = blocklist,
                        cosmeticHidden = cosmeticHidden,
                        onToggle = onToggle,
                    )

                    MainTab.RULES -> AppRulesScreen(
                        settings = settings,
                        blocklist = blocklist,
                        onAddAllow = viewModel::addAllow,
                        onAddDeny = viewModel::addDeny,
                        onRemoveAllow = viewModel::removeAllow,
                        onRemoveDeny = viewModel::removeDeny,
                    )

                    MainTab.STATS -> StatisticsScreen(
                        counters = counters,
                        status = status,
                        blocklist = blocklist,
                    )

                    MainTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        blocklist = blocklist,
                        status = status,
                        onUpstream = viewModel::setUpstream,
                onCoverTheme = viewModel::setCoverTheme,
                    )
                }
            }
        }
    }
}
