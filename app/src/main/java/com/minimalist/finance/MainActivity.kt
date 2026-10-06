package com.minimalist.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.minimalist.finance.ui.screen.*
import com.minimalist.finance.ui.theme.MinimalistFinanceTheme
import com.minimalist.finance.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.currentThemeMode.collectAsState()

            MinimalistFinanceTheme(themeMode = themeMode) {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navController = rememberNavController()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            DrawerMenu(
                                viewModel = viewModel,
                                onNavigateToBooks = { navController.navigate("books") },
                                onNavigateToDashboard = { navController.navigate("dashboard") },
                                onNavigateToSearch = { navController.navigate("search") },
                                onNavigateToPeriodic = { navController.navigate("periodic") },
                                onNavigateToAutoRecord = { navController.navigate("auto_record") },
                                onNavigateToBackup = { navController.navigate("backup") },
                                onNavigateToAbout = { navController.navigate("about") },
                                onCloseDrawer = { scope.launch { drawerState.close() } }
                            )
                        }
                    }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "record",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("record") {
                            RecordScreen(
                                viewModel = viewModel,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onNavigateToBooks = { navController.navigate("books") }
                            )
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
                }
            }
        }
    }
}
