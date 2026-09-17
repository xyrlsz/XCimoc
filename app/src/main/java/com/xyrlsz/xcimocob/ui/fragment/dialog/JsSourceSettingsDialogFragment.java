package com.xyrlsz.xcimocob.ui.fragment.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.source.js.JsMangaParser;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.ThreadPoolManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JS 漫画源的登录与设置对话框。登录态与设置项均按「脚本契约」动态渲染：
 * <ul>
 *   <li>登录：脚本声明 {@code login(params)} / {@code getLoginState()} 时显示账号/密码框与登录/登出按钮；</li>
 *   <li>设置：脚本声明 {@code getSettings()} 返回字段描述数组时，按类型渲染输入控件并持久化到宿主。</li>
 * </ul>
 */
public class JsSourceSettingsDialogFragment extends DialogFragment {

    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_TITLE = "title";

    private int mType;
    private String mTitle;

    public static JsSourceSettingsDialogFragment newInstance(int type, String title) {
        JsSourceSettingsDialogFragment f = new JsSourceSettingsDialogFragment();
        Bundle b = new Bundle();
        b.putInt(EXTRA_TYPE, type);
        b.putString(EXTRA_TITLE, title);
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mType = requireArguments().getInt(EXTRA_TYPE);
        mTitle = requireArguments().getString(EXTRA_TITLE, "");

        // 内容容器（可滚动），避免软键盘弹出时遮挡底部按钮
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(8));

        MangaParser parser = SourceManager.getInstance(App.getApp()).getParser(mType);
        if (!(parser instanceof JsMangaParser)) {
            HintUtils.showToast(requireContext(), R.string.comic_source_js_settings);
            dismiss();
            return new AlertDialog.Builder(requireContext()).setTitle(mTitle).setMessage("非 JS 源").create();
        }
        JsMangaParser jsParser = (JsMangaParser) parser;

        if (jsParser.hasLogin()) {
            content.addView(buildLoginSection(jsParser));
        }
        JSONArray settings = jsParser.getSettings();
        if (settings != null && settings.length() > 0) {
            content.addView(buildSettingsSection(jsParser, settings));
        }
        if (content.getChildCount() == 0) {
            TextView tv = new TextView(requireContext());
            tv.setText("该源未声明登录或设置项");
            content.addView(tv);
        }

        // 包一层 ScrollView，窗口随键盘缩放后内容可滚动，底部按钮不会被键盘挡住
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(mTitle)
                .setView(scrollView)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                })
                .create();
        // 让对话框窗口随软键盘弹出而缩放，配合 ScrollView 使输入区与按钮始终可见
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            // 清除宿主 Activity 继承来的沉浸式 flag（windowTranslucentStatus），
            // 否则 ADJUST_RESIZE 不生效，键盘会遮挡底部按钮
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    /* ---------------- 登录区 ---------------- */

    private View buildLoginSection(JsMangaParser parser) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);

        TextView status = new TextView(requireContext());
        status.setTextSize(14);
        JSONObject state = parser.getLoginState();
        boolean loggedIn = state != null && state.optBoolean("loggedIn", false);
        status.setText(loggedIn
                ? getString(R.string.comic_source_js_logged_in)
                : getString(R.string.comic_source_js_not_logged_in));

        EditText account = new EditText(requireContext());
        account.setHint(R.string.comic_source_js_account);
        account.setSingleLine(true);
        EditText password = new EditText(requireContext());
        password.setHint(R.string.comic_source_js_password);
        password.setSingleLine(true);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Button loginBtn = new Button(requireContext());
        loginBtn.setText(R.string.comic_source_js_login);
        loginBtn.setOnClickListener(v -> {
            JSONObject params = new JSONObject();
            try {
                params.put("account", account.getText().toString());
                params.put("password", password.getText().toString());
            } catch (Exception ignore) {
            }
            loginBtn.setEnabled(false);
            ThreadPoolManager.getInstance().getIoExecutor().execute(() -> {
                JSONObject result = parser.login(params);
                requireActivity().runOnUiThread(() -> {
                    loginBtn.setEnabled(true);
                    String msg = (result != null && result.optBoolean("success", false))
                            ? "登录成功" : (result != null ? result.optString("message", "登录失败") : "登录失败");
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    status.setText(result != null && result.optBoolean("success", false)
                            ? getString(R.string.comic_source_js_logged_in)
                            : getString(R.string.comic_source_js_not_logged_in));
                });
            });
        });

        Button logoutBtn = new Button(requireContext());
        logoutBtn.setText(R.string.comic_source_js_logout);
        logoutBtn.setOnClickListener(v -> {
            parser.logout();
            status.setText(getString(R.string.comic_source_js_not_logged_in));
            HintUtils.showToast(requireContext(), "已退出登录");
        });

        box.addView(status);
        box.addView(account);
        box.addView(password);
        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.addView(loginBtn);
        btnRow.addView(logoutBtn);
        box.addView(btnRow);
        return box;
    }

    /* ---------------- 设置区 ---------------- */

    private View buildSettingsSection(JsMangaParser parser, JSONArray settings) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText("设置");
        title.setTextSize(16);
        box.addView(title);

        List<SettingRow> rows = new ArrayList<>();
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

            TextView lbl = new TextView(requireContext());
            lbl.setText(label);

            String current = parser.getSetting(key);
            if (current == null) current = def;

            switch (type) {
                case "bool": {
                    row.check = new CheckBox(requireContext());
                    row.check.setText(label);
                    row.check.setChecked("true".equalsIgnoreCase(current) || "1".equals(current));
                    box.addView(row.check);
                    break;
                }
                case "select": {
                    row.spinner = new Spinner(requireContext());
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
                    row.spinner.setAdapter(new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_spinner_item, opts));
                    int idx = vals.indexOf(current);
                    if (idx >= 0) row.spinner.setSelection(idx);
                    box.addView(lbl);
                    box.addView(row.spinner);
                    break;
                }
                case "EditText":
                case "edit_text":
                case "edittext":
                default: {
                    row.edit = new EditText(requireContext());
                    row.edit.setText(current);
                    row.edit.setSingleLine(true);
                    box.addView(lbl);
                    box.addView(row.edit);
                    break;
                }
            }
            rows.add(row);
        }

        Button save = new Button(requireContext());
        save.setText("保存");
        save.setOnClickListener(v -> {
            for (SettingRow r : rows) {
                String value;
                if ("bool".equals(r.type)) {
                    value = r.check.isChecked() ? "true" : "false";
                } else if ("select".equals(r.type)) {
                    value = r.spinner.getSelectedItem() == null ? "" : r.spinner.getSelectedItem().toString();
                } else {
                    value = r.edit.getText().toString();
                }
                parser.setSetting(r.key, value);
            }
            HintUtils.showToast(requireContext(), "已保存");
        });
        box.addView(save);
        return box;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static class SettingRow {
        String key;
        String type;
        EditText edit;
        Spinner spinner;
        CheckBox check;
    }
}
