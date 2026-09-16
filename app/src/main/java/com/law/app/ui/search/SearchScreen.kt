package com.law.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.law.app.ui.webview.LawWebViewScreen
import com.law.app.util.Constants
import java.net.URLEncoder

/**
 * 搜索页 - 原生搜索框 + WebView 显示搜索结果
 *
 * 顶部保留原生搜索输入框，下方用 WebView 加载国家法律法规数据库的搜索结果。
 * 网站本身已适配手机屏幕，搜索结果和法条详情都在 WebView 内展示。
 */
@Composable
fun SearchScreen(
    onLawClick: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var currentUrl by remember { mutableStateOf(Constants.OFFICIAL_URL) }
    var hasSearched by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 顶部搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { },
                placeholder = {
                    Text(
                        text = "搜索法律法规…",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.text.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = TextFieldValue("")
                            currentUrl = Constants.OFFICIAL_URL
                            hasSearched = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            // 搜索按钮
            IconButton(
                onClick = {
                    if (searchQuery.text.isNotBlank()) {
                        val encoded = URLEncoder.encode(searchQuery.text.trim(), "UTF-8")
                        // 构造搜索 URL（SPA 网站，加载后由前端 JS 处理搜索）
                        currentUrl = "${Constants.OFFICIAL_URL}search?keyword=$encoded"
                        hasSearched = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 热门搜索提示（未搜索时显示）
        if (!hasSearched) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = "热门搜索",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("民法典", "刑法", "劳动合同法", "宪法").forEach { keyword ->
                            androidx.compose.material3.AssistChip(
                                onClick = {
                                    searchQuery = TextFieldValue(keyword)
                                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                                    currentUrl = "${Constants.OFFICIAL_URL}search?keyword=$encoded"
                                    hasSearched = true
                                },
                                label = {
                                    Text(
                                        text = keyword,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "输入关键词后点击搜索，或直接在下方网页中使用网站搜索功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // WebView 显示搜索结果或网站首页
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LawWebViewScreen(
                url = currentUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
