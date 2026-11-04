package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchSpace

/** 任天堂账号登录弹窗：全屏 WebView 打开官方登录页，拦截回调拿 session_token_code */
@Composable
internal fun SwitchLoginDialog(
    onDismiss: () -> Unit,
    onCode: (code: String, verifier: String) -> Unit,
) {
    val pkce = dev.cao.finch.data.SwitchClient.createPkce()
    var codeFired by remember { mutableStateOf(false) }
    val fireOnce: (String, String) -> Unit = { code, verifier ->
        if (!codeFired) {
            codeFired = true
            onCode(code, verifier)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FinchSpace.lg, vertical = FinchSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "登录任天堂账号",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "登录页由任天堂官方提供，授权 Finch 读取家长监护游玩记录。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FinchButton(text = "关闭", variant = FinchButtonVariant.Ghost, onClick = onDismiss)
                }
                AndroidView(
                    factory = { ctx ->
                        val container = android.widget.FrameLayout(ctx)
                        container.layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                        android.webkit.WebView(ctx).apply {
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            webViewClient = object : android.webkit.WebViewClient() {
                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                                    if (url?.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME) == true) {
                                        handleNintendoDeepLink(url, pkce, fireOnce)
                                        return true
                                    }
                                    return false
                                }
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    url?.let { handleNintendoDeepLink(it, pkce, fireOnce) }
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onCreateWindow(
                                    view: android.webkit.WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?,
                                ): Boolean {
                                    val host = view ?: return false
                                    val container = host.parent as? android.widget.FrameLayout ?: return false
                                    val newView = android.webkit.WebView(host.context)
                                    newView.layoutParams = android.widget.FrameLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    newView.settings.javaScriptEnabled = true
                                    newView.settings.domStorageEnabled = true
                                    newView.settings.setSupportMultipleWindows(true)
                                    newView.settings.javaScriptCanOpenWindowsAutomatically = true
                                    newView.webViewClient = object : android.webkit.WebViewClient() {
                                        @Deprecated("Deprecated in Java")
                                        override fun shouldOverrideUrlLoading(v: android.webkit.WebView?, url: String?): Boolean {
                                            if (url?.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME) == true) {
                                                handleNintendoDeepLink(url, pkce, fireOnce)
                                                return true
                                            }
                                            return false
                                        }
                                    }
                                    container.addView(newView)
                                    (resultMsg?.obj as? android.webkit.WebView.WebViewTransport)?.webView = newView
                                    resultMsg?.sendToTarget()
                                    return true
                                }
                                override fun onCloseWindow(window: android.webkit.WebView?) {
                                    (window?.parent as? android.view.ViewGroup)?.removeView(window)
                                    window?.destroy()
                                }
                            }
                            loadUrl(dev.cao.finch.data.SwitchClient.buildAuthorizeUrl(pkce))
                            container.addView(this)
                        }
                        container
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = FinchSpace.xs),
                )
            }
        }
    }
}

/** 解析任天堂回调 URL：npf<clientid>://auth#session_token_code=...&state=... */
private fun handleNintendoDeepLink(url: String, pkce: dev.cao.finch.data.SwitchClient.Pkce, onCode: (String, String) -> Unit) {
    if (!url.startsWith(dev.cao.finch.data.SwitchClient.REDIRECT_SCHEME)) return
    val uri = android.net.Uri.parse(url)
    var code = uri.getQueryParameter("session_token_code")
    if (code == null) {
        val frag = uri.getFragment()
        if (frag != null) {
            code = android.net.Uri.parse("npf://f?$frag").getQueryParameter("session_token_code")
        }
    }
    if (code != null) {
        onCode(code, pkce.verifier)
    }
}
