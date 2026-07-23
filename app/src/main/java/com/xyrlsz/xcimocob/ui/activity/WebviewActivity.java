package com.xyrlsz.xcimocob.ui.activity;

import static com.xyrlsz.xcimocob.ui.activity.BrowserFilter.URL_KEY;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.utils.HintUtils;

import java.util.HashMap;
import java.util.Map;

public class WebviewActivity extends BackActivity {

    public static final String EXTRA_WEB_URL = "extra_web_url";
    public static final String EXTRA_WEB_HEADERS = "extra_web_headers";
    public static final String EXTRA_WEB_HTML = "extra_web_html";
    public static final String EXTRA_IS_USE_TO_WEB_PARSER = "extra_is_use_to_web_parser";
    private final String htmlStr = "";
    boolean isShowButton = true;
    private WebView webView;
    private LinearLayout buttonPanel;
    private FloatingActionButton loadButton;
    private FloatingActionButton exitButton;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        webView = findViewById(R.id.web);
        loadButton = findViewById(R.id.load_button);
        exitButton = findViewById(R.id.exit_button);
        buttonPanel = findViewById(R.id.button_panel);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

            }


        });


        // 设置 WebChromeClient 以便获取页面中的 JavaScript 输出
        webView.setWebChromeClient(new WebChromeClient());

        // 启用 JavaScript 和 DOM 存储
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // 显示自定义菜单
                showCustomMenu();
                return true; // 返回true表示已处理长按事件
            }
        });

        // 从 Intent 中获取 URL
        String url = getIntent().getStringExtra(EXTRA_WEB_URL);
        Bundle bundle = getIntent().getBundleExtra(EXTRA_WEB_HEADERS);
        Map<String, String> headers = new HashMap<>();
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                headers.put(key, bundle.getString(key));
            }
        }
        if (url == null || url.isEmpty()) {
            finish();
        } else {
            webView.loadUrl(url, headers);
        }
        loadButton.setOnClickListener(v -> {
            Intent intent = new Intent(WebviewActivity.this, BrowserFilter.class);
            intent.putExtra(URL_KEY, webView.getOriginalUrl());
            startActivity(intent);
        });
        exitButton.setOnClickListener(v -> finish());
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 如果 WebView 可以返回上一页，则返回上一页
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    // 否则执行默认的返回操作
                    finish();
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_webview;
    }


    private void showCustomMenu() {
        PopupMenu popup = new PopupMenu(WebviewActivity.this, findViewById(R.id.button_panel));
        popup.getMenuInflater().inflate(R.menu.menu_webview, popup.getMenu());

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int __id = item.getItemId();
                if (__id == R.id.copy_link) {
                        // 复制链接到剪贴板
                        String url = webView.getUrl();
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("URL", url);
                        clipboard.setPrimaryClip(clip);
                        HintUtils.showToast(WebviewActivity.this, "链接已复制到剪贴板");
                        return true;
                } else if (__id == R.id.refresh_page) {
                        // 刷新
                        webView.reload();
                        return true;
                } else if (__id == R.id.edit_url) {
                        // 编辑 URL
                        String currentUrl = webView.getUrl();
                        AlertDialog.Builder builder = new AlertDialog.Builder(WebviewActivity.this);
                        builder.setTitle("编辑链接");
                        final EditText input = new EditText(WebviewActivity.this);
                        input.setText(currentUrl);
                        builder.setView(input);
                        builder.setPositiveButton(getString(R.string.dialog_positive), (dialog, which) -> {
                            String userInput = input.getText().toString();
                            webView.loadUrl(userInput);
                        });
                        builder.setNegativeButton(getString(R.string.dialog_negative), (dialog, which) -> dialog.cancel());
                        builder.create().show();
                        return true;
                } else if (__id == R.id.show_button) {
                        // 显示按钮
                        if (isShowButton) {
                            buttonPanel.setVisibility(View.GONE);
                            isShowButton = false;
                        } else {
                            buttonPanel.setVisibility(View.VISIBLE);
                            isShowButton = true;
                        }
                        return true;
//                    case R.id.change_ua_to_pc:
//                        // 切换 UA 为 PC 版
//                        String pcUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0";
//                        webView.getSettings().setUserAgentString(pcUserAgent);
//                        webView.reload(); // 刷新使UA生效
//                    case R.id.change_ua_to_mobile:
//                        // 切换 UA 为移动端
//                        webView.getSettings().setUserAgentString(WebSettings.getDefaultUserAgent(WebviewActivity.this));
//                        webView.reload(); // 刷新使UA生效
//                        return true;
                } else {
                        return false;
                }
            }
        });

        popup.show();
    }
}
