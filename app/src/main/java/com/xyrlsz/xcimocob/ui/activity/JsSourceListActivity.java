package com.xyrlsz.xcimocob.ui.activity;

import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.JsSourceManager;
import com.xyrlsz.xcimocob.model.JsSource;
import com.xyrlsz.xcimocob.utils.ThreadPoolManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 已安装的 JS 源列表。点击某个源进入其「按 JS 配置自动生成」的登录/设置页
 * （{@link JsSourceSettingsActivity}），无需逐源硬编码页面。
 * <p>
 * 只展示「声明了登录或设置项」的源；既无登录也无设置项的源不会出现在列表中，
 * 避免点击后进入一个空白的「漫画源设置」页。
 * <p>
 * 登录/设置元信息（hasLogin/settingCount）已缓存到 JsSource 表，列表加载直接读取，
 * 无需逐源执行 JS 引擎；仅对少数 metaReady=false 的存量源做即时回填。
 */
public class JsSourceListActivity extends BackActivity {

    private final List<JsSource> mFilteredSources = new ArrayList<>();
    private ListView mList;
    private TextView mEmptyView;

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.comic_source_js_settings);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_js_source_list;
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mList = findViewById(R.id.js_source_list);
        mEmptyView = findViewById(android.R.id.empty);
    }

    @Override
    protected void initView() {
        super.initView();
        mList.setEmptyView(mEmptyView);
        loadSources();
    }

    private void loadSources() {
        // 登录/设置元信息已缓存到 DB，直接读 hasLogin/settingCount 过滤，无需逐源跑 JS。
        JsSourceManager manager = JsSourceManager.getInstance(this);
        List<JsSource> all = manager.listEnabled();
        List<JsSource> filtered = new ArrayList<>();
        List<JsSource> needBackfill = new ArrayList<>();
        for (JsSource s : all) {
            if (!s.isMetaReady()) {
                needBackfill.add(s);
                continue;
            }
            if (s.isHasLogin() || s.getSettingCount() > 0) {
                filtered.add(s);
            }
        }
        bindList(filtered);

        // 对少数 metaReady=false 的存量源在后台回填（App 启动时 backfillMeta 通常已处理完）
        if (!needBackfill.isEmpty()) {
            ThreadPoolManager.getInstance().getIoExecutor().execute(() -> {
                for (JsSource s : needBackfill) {
                    try {
                        JSONObject meta = manager.validateScript(s.getScript());
                        s.setHasLogin(meta.optBoolean("__hasLogin", false));
                        s.setSettingCount(meta.optInt("__settingCount", 0));
                        s.setSettingsJson(meta.optString("__settingsJson", ""));
                        s.setHasCategory(meta.optBoolean("__hasCategory", false));
                        s.setMetaReady(true);
                        manager.put(s);
                    } catch (Exception e) {
                        android.util.Log.w("JsSourceList", "backfill meta failed type="
                                + s.getType() + ": " + e);
                    }
                }
                // 回填完成后刷新列表（用回填后的真实值重算过滤）
                runOnUiThread(() -> {
                    List<JsSource> refreshed = new ArrayList<>();
                    for (JsSource s : manager.listEnabled()) {
                        if (s.isHasLogin() || s.getSettingCount() > 0) {
                            refreshed.add(s);
                        }
                    }
                    bindList(refreshed);
                });
            });
        }
    }

    private void bindList(List<JsSource> sources) {
        mFilteredSources.clear();
        mFilteredSources.addAll(sources);
        List<String> titles = new ArrayList<>();
        for (JsSource s : sources) {
            titles.add(s.getTitle());
        }
        mList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles));
        mList.setOnItemClickListener((parent, view, position, id) -> {
            JsSource s = mFilteredSources.get(position);
            startActivity(JsSourceSettingsActivity.createIntent(this, s.getType(), s.getTitle()));
        });
    }
}
