package com.xyrlsz.xcimocob.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.source.Baozi;
import com.xyrlsz.xcimocob.source.BuKa;
import com.xyrlsz.xcimocob.source.CopyMH;
import com.xyrlsz.xcimocob.source.DM5;
import com.xyrlsz.xcimocob.source.DongManHi;
import com.xyrlsz.xcimocob.source.DongManManHua;
import com.xyrlsz.xcimocob.source.DuManWu;
import com.xyrlsz.xcimocob.source.DuManWuApp;
import com.xyrlsz.xcimocob.source.GFMH;
import com.xyrlsz.xcimocob.source.GoDaManHua;
import com.xyrlsz.xcimocob.source.HotManga;
import com.xyrlsz.xcimocob.source.Komiic;
import com.xyrlsz.xcimocob.source.MH5;
import com.xyrlsz.xcimocob.source.MYCOMIC;
import com.xyrlsz.xcimocob.source.ManBen;
import com.xyrlsz.xcimocob.source.ManHuaGui;
import com.xyrlsz.xcimocob.source.ManWa;
import com.xyrlsz.xcimocob.source.MangaBZ;
import com.xyrlsz.xcimocob.source.Manhuatai;
import com.xyrlsz.xcimocob.source.Manhuayu;
import com.xyrlsz.xcimocob.source.Tencent;
import com.xyrlsz.xcimocob.source.Vomicmh;
import com.xyrlsz.xcimocob.source.YKMH;
import com.xyrlsz.xcimocob.source.YYManHua;
import com.xyrlsz.xcimocob.source.ZaiManhua;
import com.xyrlsz.xcimocob.utils.HintUtils;

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
        List<Integer> list = new ArrayList<>();
//        list.add(Animx2.TYPE);
        list.add(Baozi.TYPE);
        list.add(BuKa.TYPE);
//        list.add(Cartoonmad.TYPE);
        list.add(CopyMH.TYPE);
//        list.add(CopyMHWeb.TYPE);
        list.add(DM5.TYPE);
        list.add(HotManga.TYPE);
        list.add(ManHuaGui.TYPE);
//        list.add(Mangakakalot.TYPE);
        list.add(MangaBZ.TYPE);
        list.add(Manhuatai.TYPE);
        list.add(MYCOMIC.TYPE);
        list.add(Tencent.TYPE);
        list.add(DongManManHua.TYPE);
        list.add(YKMH.TYPE);
        list.add(DuManWu.TYPE);
//        list.add(DuManWuOrg.TYPE);
        list.add(Komiic.TYPE);
        list.add(Manhuayu.TYPE);
        list.add(GoDaManHua.TYPE);
//        list.add(TTKMH.TYPE);
        list.add(Vomicmh.TYPE);
        list.add(YYManHua.TYPE);
//        list.add(DmzjV4.TYPE);
        list.add(ZaiManhua.TYPE);
        list.add(ManBen.TYPE);
        list.add(GFMH.TYPE);
        list.add(ManWa.TYPE);
        list.add(MH5.TYPE);
        list.add(DuManWuApp.TYPE);
        list.add(DongManHi.TYPE);
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
