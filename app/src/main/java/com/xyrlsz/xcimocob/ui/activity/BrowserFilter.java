package com.xyrlsz.xcimocob.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.JsSourceManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.JsSource;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserFilter extends BaseActivity {
    public static final String URL_KEY = "url";

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_browser_filter;
    }

    @Override
    protected String getDefaultTitle() {
        return "jumping...";
    }

//    private Parser parser;
//    private SourceManager mSourceManager;

    public void openDetailActivity(int source, String comicId) {
        Intent intent = DetailActivity.createIntent(this, null, source, comicId);
        startActivity(intent);
    }

//    public void openReaderActivity(int source,String comicId) {
//        Intent intent = DetailActivity.createIntent(this, null, source, comicId);
//        startActivity(intent);
//    }

    private List<Integer> registUrlListener() {
        // 网络漫画源统一由 JS 源提供，按已安装的 JS 源注册 URL 监听
        List<Integer> list = new ArrayList<>();
        for (JsSource js : JsSourceManager.getInstance(this).listEnabled()) {
            if (!StringUtils.isEmpty(js.getHosts())
                    && !js.getHosts().equals("null")
                    && !js.getHosts().replaceAll("\\s+", "").equals("[]")) {
                list.add(js.getType());
            }
        }
        return list;
    }

    private void openReader(Uri uri) {
        try {
            SourceManager mSourceManager = SourceManager.getInstance(this);
            String comicId;

            for (int i : registUrlListener()) {
                boolean isHere = mSourceManager.getParser(i).isHere(uri);
                comicId = mSourceManager.getParser(i).getComicId(uri);
                if (isHere && comicId != null) {
                    openDetailActivity(i, comicId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void openReaderByIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        //来自url
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                openReader(uri);
            } else {
                HintUtils.showToast(this, "url不合法");
            }
        }

        // 来自输入链接
        else if (intent.hasExtra(URL_KEY)) {
            String url = intent.getStringExtra(URL_KEY);
            openReader(Uri.parse(url));
        }

        //来自分享
        else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            try {
                // 使用正则表达式匹配URL
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                Pattern pattern = Pattern.compile("(https?://[\\w\\-./?#&=]+)");
                Matcher matcher = null;
                if (sharedText != null) {
                    matcher = pattern.matcher(sharedText);
                }
                if (matcher != null && matcher.find()) {
                    String url = matcher.group(1);
                    openReader(Uri.parse(url));
                }
            } catch (Exception ex) {
                HintUtils.showToast(this, "url不合法");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser_filter);

        openReaderByIntent(getIntent());

        finish();
    }
}
