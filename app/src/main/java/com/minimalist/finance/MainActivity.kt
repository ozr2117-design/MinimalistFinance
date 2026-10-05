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
import com.minimalist.finance.ui.screen.BooksScreen
import com.minimalist.finance.ui.screen.DrawerMenu
import com.minimalist.finance.ui.screen.RecordScreen
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
                                onNavigateToBooks = {
                                    navController.navigate("books")
                                },
                                onCloseDrawer = {
                                    scope.launch { drawerState.close() }
                                }
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
                                onOpenDrawer = {
                                    scope.launch { drawerState.open() }
                                },
                                onNavigateToBooks = {
                                    navController.navigate("books")
                                }
                            )
                        }
                        composable("books") {
                            BooksScreen(
                                viewModel = viewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
