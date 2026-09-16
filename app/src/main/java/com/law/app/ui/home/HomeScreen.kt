package com.law.app.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.law.app.ui.webview.LawWebViewScreen
import com.law.app.util.Constants

/**
 * 首页 - WebView 加载国家法律法规数据库首页
 *
 * 网站本身已适配手机屏幕，包含：
 * - 新法速递
 * - 热门搜索
 * - 分类浏览
 * - 搜索功能
 */
@Composable
fun HomeScreen(
    onLawClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    LawWebViewScreen(
        url = Constants.OFFICIAL_URL,
        modifier = Modifier.fillMaxSize()
    )
}
