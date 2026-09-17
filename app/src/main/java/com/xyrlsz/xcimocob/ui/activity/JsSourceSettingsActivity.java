package com.xyrlsz.xcimocob.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.source.js.JsMangaParser;
import com.xyrlsz.xcimocob.ui.widget.LoginDialog;
import com.xyrlsz.xcimocob.ui.widget.MaterialOptionRow;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.ThreadPoolManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 按「JS 配置」自动生成登录与设置界面的页面（替代硬编码的逐源固定页面）。
 * <p>
 * 登录区：脚本声明 {@code getLoginState()/login(params)/logout()} 时渲染账号/密码框与登录/登出按钮；
 * 设置区：脚本声明 {@code getSettings()} 返回字段描述数组时，按类型（text/EditText/select/bool）动态渲染并持久化。
 */
public class JsSourceSettingsActivity extends BackActivity {

    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_TITLE = "title";

    private LinearLayout mContainer;
    private JsMangaParser mParser;

    public static Intent createIntent(Context context, int type, String title) {
        Intent intent = new Intent(context, JsSourceSettingsActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_TITLE, title);
        return intent;
    }

    @Override
    protected String getDefaultTitle() {
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        return title == null || title.isEmpty() ? getString(R.string.comic_source_js_settings) : title;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_js_source_settings;
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mContainer = findViewById(R.id.js_settings_container);
    }

    @Override
    protected void initView() {
        super.initView();
        MangaParser parser = SourceManager.getInstance(this).getParser(getIntent().getIntExtra(EXTRA_TYPE, -1));
        if (!(parser instanceof JsMangaParser)) {
            HintUtils.showToast(this, R.string.comic_source_js_settings);
            finish();
            return;
        }
        mParser = (JsMangaParser) parser;
        boolean hasLogin = mParser.hasLogin();
        JSONArray settings = mParser.getSettings();
        int settingCount = (settings == null) ? 0 : settings.length();
        if (!hasLogin && settingCount == 0) {
            HintUtils.showToast(this, "该源未声明登录或设置项");
            finish();
            return;
        }
        buildAll();
    }

    private void buildAll() {
        mContainer.removeAllViews();
        boolean hasLogin = mParser.hasLogin();
        JSONArray settings = mParser.getSettings();
        int settingCount = (settings == null) ? 0 : settings.length();
        android.util.Log.i("JsSource", "[settings] type="
                + mParser.getType() + " hasLogin=" + hasLogin
                + " settings=" + settingCount
                + (settings != null ? " " + settings : ""));
        // 打印当前登录态，确认是否真的保存了
        try {
            String stored = com.xyrlsz.xcimocob.source.js.JsHost.INSTANCE
                    .getLogin(mParser.getType());
            android.util.Log.i("JsSource", "[settings] type=" + mParser.getType()
                    + " loginState(js)=" + mParser.getLoginState().toString()
                    + " stored(host)=" + (stored == null ? "null" : stored));
        } catch (Exception ignore) {
        }
        if (hasLogin) {
            mContainer.addView(buildLoginSection());
        }
        if (settingCount > 0) {
            mContainer.addView(buildSettingsSection(settings));
        }
    }

    /* ---------------- 登录区（显示为一个可点击 option，点击弹登录对话框） ---------------- */

    private View buildLoginSection() {
        JSONObject state = mParser.getLoginState();
        boolean loggedIn = state != null && state.optBoolean("loggedIn", false);
        String status = loggedIn ? getString(R.string.comic_source_js_logged_in)
                : getString(R.string.comic_source_js_not_logged_in);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        addSectionTitle(box, getString(R.string.comic_source_js_login));
        View row = addOptionRow(box, getString(R.string.comic_source_js_login), status);
        row.setOnClickListener(v -> showLoginDialog());
        return box;
    }

    private void showLoginDialog() {
        final LoginDialog dialog = new LoginDialog(this);

        // 注册链接：源声明 getRegisterUrl 时显示注册按钮，点击打开注册页
        final String registerUrl = mParser.getRegisterUrl();
        if (registerUrl == null || registerUrl.isEmpty()) {
            dialog.setRegisterButtonVisible(false);
        } else {
            dialog.setOnRegisterListener(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(registerUrl)));
                } catch (Exception e) {
                    HintUtils.showToast(this, R.string.comic_source_js_register_open_fail);
                }
            });
        }

        // 已登录时显示登出按钮
        JSONObject state = mParser.getLoginState();
        boolean loggedIn = state != null && state.optBoolean("loggedIn", false);
        if (loggedIn) {
            dialog.setLogoutButtonVisible(true);
            dialog.setOnLogoutListener(() -> {
                mParser.logout();
                HintUtils.showToast(this, R.string.user_login_logout_sucess);
                buildAll();
            });
        }

        dialog.setOnLoginListener((username, password) -> {
            JSONObject params = new JSONObject();
            try {
                params.put("account", username);
                params.put("password", password);
            } catch (Exception ignore) {
            }
            doLogin(params);
        });

        dialog.show();
    }

    private void doLogin(JSONObject params) {
        ThreadPoolManager.getInstance().getIoExecutor().execute(() -> {
            JSONObject result = mParser.login(params);
            android.util.Log.i("JsSource", "[login] type=" + mParser.getType()
                    + " result=" + (result != null ? result.toString() : "null"));
            runOnUiThread(() -> {
                boolean ok = result != null && result.optBoolean("success", false);
                String msg = ok ? getString(R.string.user_login_sucess)
                        : (result != null && !result.optString("message").isEmpty()
                           ? result.optString("message") : getString(R.string.user_login_failed));
                HintUtils.showToast(this, msg);
                buildAll();
            });
        });
    }

    /* ---------------- 通用 option 行 ---------------- */

    /**
     * 添加一行「左标签 + 右侧文字」的 option 行，返回该行（可设置点击）。
     */
    private View addOptionRow(LinearLayout box, String label, String valueText) {
        MaterialOptionRow row = new MaterialOptionRow(this);
        row.setLabel(label);
        row.setValueText(valueText == null ? "" : valueText);
        addRowWithMargin(box, row);
        return row;
    }

    /**
     * 添加一行「左标签 + 右侧控件」的 option 行，返回该行。
     */
    private View addOptionRow(LinearLayout box, String label, View control) {
        MaterialOptionRow row = new MaterialOptionRow(this);
        row.setLabel(label);
        row.setContent(control);
        addRowWithMargin(box, row);
        return row;
    }

    private void addRowWithMargin(LinearLayout box, MaterialOptionRow row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        box.addView(row, lp);
    }

    /* ---------------- 设置区 ---------------- */

    private View buildSettingsSection(JSONArray settings) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        addSectionTitle(box, "设置");

        for (int i = 0; i < settings.length(); i++) {
            JSONObject o = settings.optJSONObject(i);
            if (o == null) continue;
            String key = o.optString("key");
            String label = o.optString("label", key);
            String type = o.optString("type", "text");
            String def = o.optString("default");
            SettingRow row = new SettingRow();
            row.key = key;
            row.type = type;

            String current = mParser.getSetting(key);
            if (current == null) current = def;

            switch (type) {
                case "bool": {
                    // 勾选型 option：右侧 MaterialCheckBox，勾选即保存
                    row.check = new MaterialCheckBox(this);
                    row.check.setChecked("true".equalsIgnoreCase(current) || "1".equals(current));
                    row.check.setOnCheckedChangeListener((b, checked) ->
                            mParser.setSetting(key, Boolean.toString(checked)));
                    addOptionRow(box, label, row.check);
                    break;
                }
                case "select": {
                    // 下拉型 option：点击展开，只读选择（选择即保存 value）
                    List<String> opts = new ArrayList<>();
                    List<String> vals = new ArrayList<>();
                    JSONArray arr = o.optJSONArray("options");
                    if (arr != null) {
                        for (int j = 0; j < arr.length(); j++) {
                            JSONObject op = arr.optJSONObject(j);
                            if (op != null) {
                                opts.add(op.optString("label"));
                                vals.add(op.optString("value"));
                            }
                        }
                    }
                    com.google.android.material.textfield.TextInputLayout selectLayout =
                            new com.google.android.material.textfield.TextInputLayout(this);
                    selectLayout.setBoxBackgroundMode(
                            com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                    selectLayout.setEndIconMode(
                            com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU);
                    row.select = new com.google.android.material.textfield.MaterialAutoCompleteTextView(this);
                    row.select.setAdapter(new ArrayAdapter<>(this,
                            android.R.layout.simple_list_item_1, opts));
                    // 做成只读下拉：禁输入/光标/软键盘，仅点击弹出选项，避免像可编辑文本框
                    row.select.setInputType(android.text.InputType.TYPE_NULL);
                    row.select.setKeyListener(null);
                    row.select.setShowSoftInputOnFocus(false);
                    row.select.setCursorVisible(false);
                    row.select.setClickable(true);
                    row.select.setOnClickListener(v -> {
                        if (!row.select.isPopupShowing()) row.select.showDropDown();
                    });
                    // 紧凑字号 + 右对齐贴近箭头，保证较长选项也能完整显示
                    row.select.setTextSize(14);
                    row.select.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
                    row.select.setMaxLines(1);
                    int idx = vals.indexOf(current);
                    if (idx >= 0) row.select.setText(opts.get(idx), false);
                    row.select.setOnItemClickListener((parent, view, pos, id) -> {
                        String val = (pos >= 0 && pos < vals.size()) ? vals.get(pos) : "";
                        mParser.setSetting(key, val);
                    });
                    selectLayout.addView(row.select);
                    selectLayout.setLayoutParams(new LinearLayout.LayoutParams(
                            dp(165), ViewGroup.LayoutParams.WRAP_CONTENT));
                    addOptionRow(box, label, selectLayout);
                    break;
                }
                case "callback":
                case "button": {
                    // 按钮型 option：点击调用脚本 onSettingsAction(key)（如签到）
                    MaterialButton btn = new MaterialButton(this);
                    btn.setText(o.optString("buttonText", label));
                    btn.setTextColor(getResources().getColor(R.color.white));
                    btn.setOnClickListener(v -> {
                        btn.setEnabled(false);
                        ThreadPoolManager.getInstance().getIoExecutor().execute(() -> {
                            JSONObject r = mParser.settingsCallback(key);
                            runOnUiThread(() -> {
                                btn.setEnabled(true);
                                // 优先显示回调返回的真实 message（如"已登录剩余可看 N 页"），
                                // 不能成功时硬编码"登录成功"——那是给登录按钮用的
                                String msg = (r != null && !r.optString("message").isEmpty())
                                        ? r.optString("message")
                                        : ((r != null && r.optBoolean("success", false))
                                           ? "操作成功" : "操作失败");
                                HintUtils.showToast(this, msg);
                            });
                        });
                    });
                    addOptionRow(box, label, btn);
                    break;
                }
                case "EditText":
                case "edit_text":
                case "edittext": {
                    // 文本型 option：右侧 Material 输入框，失去焦点时保存
                    com.google.android.material.textfield.TextInputLayout inputLayout =
                            new com.google.android.material.textfield.TextInputLayout(this);
                    inputLayout.setBoxBackgroundMode(
                            com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                    inputLayout.setLayoutParams(new LinearLayout.LayoutParams(
                            dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
                    row.inputLayout = inputLayout;
                    row.edit = new TextInputEditText(this);
                    row.edit.setText(current);
                    row.edit.setSingleLine(true);
                    row.edit.setGravity(android.view.Gravity.END);
                    row.edit.setOnFocusChangeListener((v, hasFocus) -> {
                        if (!hasFocus) {
                            Editable textEt = row.edit.getText();
                            String text = "";
                            if (textEt != null) {
                                text = textEt.toString();
                            }
                            mParser.setSetting(key, text);
                        }
                    });
                    inputLayout.addView(row.edit);
                    addOptionRow(box, label, inputLayout);
                    break;
                }
                default: {
                    break;
                }
            }
        }
        return box;
    }

    private void addSectionTitle(LinearLayout box, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                com.xyrlsz.xcimocob.utils.ThemeUtils.getResourceId(this, R.attr.colorAccent)));
        tv.setTextSize(15);
        tv.setPadding(0, dp(4), 0, dp(4));
        box.addView(tv);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static class SettingRow {
        String key;
        String type;
        TextInputLayout inputLayout;
        TextInputEditText edit;
        com.google.android.material.textfield.MaterialAutoCompleteTextView select;
        MaterialCheckBox check;
    }
}
