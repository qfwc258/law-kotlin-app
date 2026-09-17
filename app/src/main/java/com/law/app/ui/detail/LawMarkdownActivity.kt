package com.law.app.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.law.app.util.LawMarkdownConverter

/**
 * 法条 Markdown 渲染页面
 *
 * 展示从 OFD 阅读器提取的法条文本，支持源码/渲染预览切换
 */
class LawMarkdownActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var lawTitle = ""
    private var rawText = ""
    private var markdownText = ""
    private var showSource = false

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TEXT = "extra_text"

        fun start(context: Context, title: String, text: String) {
            val intent = Intent(context, LawMarkdownActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lawTitle = intent.getStringExtra(EXTRA_TITLE) ?: "法规详情"
        rawText = intent.getStringExtra(EXTRA_TEXT) ?: ""
        markdownText = LawMarkdownConverter.convertToMarkdown(lawTitle, rawText)

        // 设置 ActionBar
        supportActionBar?.apply {
            title = lawTitle
            setDisplayHomeAsUpEnabled(true)
            subtitle = "文本模式"
        }

        // 创建 WebView
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        setContentView(webView)

        // 初始显示渲染预览
        loadMarkdownPreview()
    }

    /**
     * 加载 Markdown 渲染预览
     */
    private fun loadMarkdownPreview() {
        val html = buildMarkdownHtml(markdownText, showSource)
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    /**
     * 构建 Markdown 渲染 HTML
     */
    private fun buildMarkdownHtml(markdown: String, isSource: Boolean): String {
        val escapedMarkdown = markdown
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val modeLabel = if (isSource) "源码" else "预览"
        val toggleLabel = if (isSource) "切换到预览" else "切换到源码"

        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$lawTitle</title>
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
    line-height: 1.8;
    color: #333;
    background: #fff;
    padding: 16px;
    padding-bottom: 80px;
}
.toolbar {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    background: #fff;
    padding: 12px 16px;
    border-bottom: 1px solid #eee;
    display: flex;
    justify-content: space-between;
    align-items: center;
    z-index: 100;
}
.toolbar span { font-size: 14px; color: #666; }
.toolbar button {
    padding: 6px 16px;
    border: 1px solid #1976d2;
    background: #1976d2;
    color: #fff;
    border-radius: 4px;
    font-size: 14px;
    cursor: pointer;
}
.content { margin-top: 60px; }
.content h1 { font-size: 22px; font-weight: bold; margin-bottom: 16px; color: #1a1a1a; text-align: center; }
.content h2 { font-size: 19px; font-weight: bold; margin: 24px 0 12px; color: #1976d2; border-left: 4px solid #1976d2; padding-left: 12px; }
.content h3 { font-size: 17px; font-weight: bold; margin: 20px 0 10px; color: #333; }
.content h4 { font-size: 16px; font-weight: bold; margin: 16px 0 8px; color: #555; }
.content p { margin-bottom: 12px; text-align: justify; }
.content strong { color: #c62828; }
.content ul, .content ol { margin: 8px 0 12px 24px; }
.content li { margin-bottom: 6px; }
.content hr { border: none; border-top: 1px solid #eee; margin: 24px 0; }
pre {
    background: #f5f5f5;
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-wrap: break-word;
    margin-top: 60px;
}
</style>
</head>
<body>
<div class="toolbar">
    <span>当前模式：$modeLabel</span>
    <button onclick="toggleMode()">$toggleLabel</button>
</div>
<div id="content" class="content"></div>
<pre id="source" style="display:none;"></pre>
<script>
var markdownText = "$escapedMarkdown";
var showSource = $isSource;

function render() {
    if (showSource) {
        document.getElementById('content').style.display = 'none';
        document.getElementById('source').style.display = 'block';
        document.getElementById('source').textContent = markdownText;
    } else {
        document.getElementById('content').style.display = 'block';
        document.getElementById('source').style.display = 'none';
        document.getElementById('content').innerHTML = marked.parse(markdownText);
    }
}

function toggleMode() {
    showSource = !showSource;
    render();
}

render();
</script>
</body>
</html>
        """.trimIndent()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
