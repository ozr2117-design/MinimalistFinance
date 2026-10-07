package com.minimalist.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.minimalist.finance.ui.component.AppUpdateDialog
import com.minimalist.finance.ui.component.SmoothIosDrawer
import com.minimalist.finance.ui.screen.*
import com.minimalist.finance.ui.theme.MinimalistFinanceTheme
import com.minimalist.finance.ui.viewmodel.MainViewModel
import com.minimalist.finance.util.AppUpdateInfo
import com.minimalist.finance.util.AppUpdateManager

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.currentThemeMode.collectAsState()

            MinimalistFinanceTheme(themeMode = themeMode) {
                var isDrawerOpen by remember { mutableStateOf(false) }
                val navController = rememberNavController()
                var autoUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

                // 启动无感静默检查更新（带 24 小时防骚扰频控）
                LaunchedEffect(Unit) {
                    if (AppUpdateManager.shouldCheckAutoUpdate(this@MainActivity)) {
                        val result = AppUpdateManager.checkUpdate(AppUpdateManager.CURRENT_VERSION_CODE)
                        result.onSuccess { info ->
                            if (info != null) {
                                autoUpdateInfo = info
                            }
                        }
                    }
                }

                // 平滑导航：先标记关闭抽屉，随后执行 iOS 式平滑页面转场
                val navigateTo: (String) -> Unit = { route ->
                    isDrawerOpen = false
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "record",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f) // Apple iOS 经典平滑推进曲线
                            )
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 280),
                            initialAlpha = 0.6f
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() }, // Apple iOS 视差深度后退
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                            )
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 250),
                            targetAlpha = 0.7f
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() },
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                            )
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 280),
                            initialAlpha = 0.7f
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                            )
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 250),
                            targetAlpha = 0.6f
                        )
                    }
                ) {
                    composable("record") {
                        SmoothIosDrawer(
                            isOpen = isDrawerOpen,
                            onClose = { isDrawerOpen = false },
                            onOpen = { isDrawerOpen = true },
                            drawerContent = {
                                DrawerMenu(
                                    viewModel = viewModel,
                                    onNavigateToBooks = { navigateTo("books") },
                                    onNavigateToDashboard = { navigateTo("dashboard") },
                                    onNavigateToSearch = { navigateTo("search") },
                                    onNavigateToPeriodic = { navigateTo("periodic") },
                                    onNavigateToAutoRecord = { navigateTo("auto_record") },
                                    onNavigateToBackup = { navigateTo("backup") },
                                    onNavigateToAbout = { navigateTo("about") },
                                    onCloseDrawer = { isDrawerOpen = false }
                                )
                            }
                        ) {
                            RecordScreen(
                                viewModel = viewModel,
                                onOpenDrawer = { isDrawerOpen = true },
                                onNavigateToBooks = { navigateTo("books") }
                            )
                        }
                    }
                    composable("books") {
                        BooksScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("dashboard") {
                        AssetDashboardScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("search") {
                        SearchRecordsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("periodic") {
                        PeriodicScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("auto_record") {
                        AutoRecordScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("backup") {
                        BackupExportScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("about") {
                        AboutScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // 启动发现新版本弹窗（展示版本号、日期、详细更新说明与一键更新）
                if (autoUpdateInfo != null) {
                    AppUpdateDialog(
                        updateInfo = autoUpdateInfo!!,
                        onDismiss = { autoUpdateInfo = null }
                    )
                }
            }
        }
    }
}
