//still requires checking 3.24
//返回逻辑好像又错了
package com.bbblllllocck.canned_emotions.ui.features.drawerMenu

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bbblllllocck.canned_emotions.ui.components.BackNavButton
import com.bbblllllocck.canned_emotions.ui.features.apiScreen.APIScreen
import com.bbblllllocck.canned_emotions.ui.features.databaseScreen.DatabaseScreen
import com.bbblllllocck.canned_emotions.ui.features.scanScreen.ScanScreen
import com.bbblllllocck.canned_emotions.ui.features.start.StartScreen
import kotlinx.coroutines.launch

private data class DrawerDestination(val route: String, val title: String)

private val drawerDestinations = listOf(
    DrawerDestination("start", "开始"),
    DrawerDestination("database", "数据库"),
    DrawerDestination("api", "API"),
    DrawerDestination("scan", "扫描"),
    DrawerDestination("about", "关于")
)

private const val BACK_DEBUG_TAG = "DrawerBackDebug"

@Composable
fun DrawerMenu() {

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isStartRoute = currentRoute == "start"
    val isDrawerVisible =
        drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed // treat in-flight animation as visible

    if (isDrawerVisible) {
        Log.d(BACK_DEBUG_TAG, "BackHandler created")
        BackHandler {
            Log.d(BACK_DEBUG_TAG, "Back action: close drawer")
            scope.launch { drawerState.close() }
        }
    }

    LaunchedEffect(drawerState.currentValue, drawerState.targetValue, currentRoute) {
        Log.d(
            BACK_DEBUG_TAG,
            "state route=$currentRoute current=${drawerState.currentValue} target=${drawerState.targetValue} visible=$isDrawerVisible"
        )
    }

    DismissibleNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DismissibleDrawerSheet(modifier = Modifier.width(150.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("主菜单", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)

                drawerDestinations.forEach { destination ->
                    Spacer(Modifier.height(12.dp))
                    NavigationDrawerItem(
                        label = { Text(destination.title) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                Log.d(BACK_DEBUG_TAG, "popped")
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                            Log.d(BACK_DEBUG_TAG, "closed")
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold { innerPadding ->//where the innerPadding parameter came from
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isStartRoute) {
                    Button(
                        onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("菜单")
                    }
                } else {
                    BackNavButton(
                        onClick = {
                            navController.navigate("start") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "start",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("start") { StartScreen() }
                    composable("database") { DatabaseScreen() }
                    // Keep route registered for safety, but hide it from drawer UI.
                    composable("player") { ScreenPlaceholder("播放器") }
                    composable("api") { APIScreen() }
                    composable("scan") { ScanScreen() }
                    composable("about") { ScreenPlaceholder("关于") }
                }
            }
        }
    }
}

@Composable
private fun ScreenPlaceholder(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "这里先放占位内容，后续可接二级页面或底部菜单。")
        }
    }
}