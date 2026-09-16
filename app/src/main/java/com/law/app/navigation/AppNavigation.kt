package com.law.app.navigation

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.law.app.ui.detail.LawDetailActivity
import com.law.app.ui.favorites.FavoritesScreen
import com.law.app.ui.home.HomeScreen
import com.law.app.ui.search.SearchScreen

/**
 * 底部导航路由
 */
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Home : BottomNavItem("home", "首页", Icons.Default.Book)
    data object Search : BottomNavItem("search", "搜索", Icons.Default.Search)
    data object Favorites : BottomNavItem("favorites", "收藏", Icons.Default.Favorite)
}

/**
 * 全应用导航
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context: Context = LocalContext.current
    val bottomItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Favorites
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route?.startsWith(item.route) == true
                        } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onLawClick = { lawId, title -> LawDetailActivity.start(context, lawId, title) },
                    onSearchClick = { navController.navigate(BottomNavItem.Search.route) },
                    onCategoryClick = { category ->
                        // 点击大类跳转到搜索页，搜索该大类关键词
                        navController.navigate("search?keyword=${category.name}")
                    }
                )
            }
            composable(
                route = "search?keyword={keyword}",
                arguments = listOf(
                    navArgument("keyword") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val keyword = backStackEntry.arguments?.getString("keyword") ?: ""
                SearchScreen(
                    onLawClick = { lawId, title -> LawDetailActivity.start(context, lawId, title) },
                    initialKeyword = keyword
                )
            }
            composable(BottomNavItem.Favorites.route) {
                FavoritesScreen(
                    onLawClick = { lawId, title -> LawDetailActivity.start(context, lawId, title) }
                )
            }
        }
    }
}
