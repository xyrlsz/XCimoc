package com.xyrlsz.xcimocob.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

public class LoginDialog extends Dialog {
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private Button registerButton;
    private Button logoutButton;
    private ImageButton togglePasswordButton;
    private boolean isPasswordInvisible = true;
    // 设置登录和注册的监听器
    private OnLoginListener loginListener;
    private OnRegisterListener registerListener;
    private OnLogoutListener logoutListener;
    private Context mContext;

    public LoginDialog(Context context) {
        super(context);
        init(context);
    }

    public LoginDialog(Context context, int themeResId) {
        super(context, themeResId);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
        this.setContentView(R.layout.dialog_login);
        setupWindowSize();
        // Find views by ID
        usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        loginButton = findViewById(R.id.login_button);
        registerButton = findViewById(R.id.register_button);
        logoutButton = findViewById(R.id.logout_button);
        togglePasswordButton = findViewById(R.id.ib_is_show_passwd);
        changeTogglePassword();
        togglePasswordButton.setOnClickListener(
                view -> togglePasswordVisibility()
        );

        // 可以通过提供接口来处理登录和注册按钮的点击事件
        loginButton.setOnClickListener(view -> {
                    if (loginListener != null) {
                        loginListener.onLogin(usernameEditText.getText().toString(), passwordEditText.getText().toString());
                    }
                    dismiss();
                }
        );

        registerButton.setOnClickListener(v -> {

            // 注册逻辑
            if (registerListener != null) {
                registerListener.onRegister();
            }
            dismiss();

        });

        logoutButton.setOnClickListener(v -> {
            if (logoutListener != null) {
                logoutListener.onLogout();
            }
            dismiss();
        });
    }

    /**
     * 控制注册按钮显隐：源未声明注册链接时传入 false 隐藏。
     */
    public void setRegisterButtonVisible(boolean visible) {
        registerButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * 控制登出按钮显隐：默认隐藏。
     */
    public void setLogoutButtonVisible(boolean visible) {
        logoutButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * 设置对话框窗口宽度为屏幕宽度的约 85%（不超过 dialog_max_width），避免过宽/贴边显示异常。
     * 同时清除宿主 Activity 继承来的沉浸式 flag（windowTranslucentStatus），
     * 否则 SOFT_INPUT_ADJUST_RESIZE 不生效，键盘会遮挡底部按钮。
     */
    @SuppressWarnings("deprecation")
    private void setupWindowSize() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        // 清除沉浸式状态栏 flag，让 adjustResize 正常生效
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // 窗口随软键盘弹出而缩放，避免底部登录/注册按钮被键盘遮挡
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        Point size = new Point();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                size.set(bounds.width(), bounds.height());
            } else {
                wm.getDefaultDisplay().getSize(size);
            }
            window.setLayout(Math.min((int) (size.x * 0.85f),
                    mContext.getResources().getDimensionPixelSize(R.dimen.dialog_max_width)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void changeTogglePassword() {
        if (isPasswordInvisible) {
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (ThemeUtils.isDarkMode(mContext)) {
                togglePasswordButton.setImageResource(R.drawable.eye_close_white);
            } else {
                togglePasswordButton.setImageResource(R.drawable.eye_close);
            }
        } else {
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            if (ThemeUtils.isDarkMode(mContext)) {
                togglePasswordButton.setImageResource(R.drawable.eye_white);
            } else {
                togglePasswordButton.setImageResource(R.drawable.eye);
            }
        }
    }

    private void togglePasswordVisibility() {
        isPasswordInvisible = !isPasswordInvisible;
        changeTogglePassword();
        passwordEditText.setSelection(passwordEditText.getText().length());
    }

    public void setOnLoginListener(OnLoginListener listener) {
        this.loginListener = listener;
    }

    public void setOnRegisterListener(OnRegisterListener listener) {
        this.registerListener = listener;
    }

    public void setOnLogoutListener(OnLogoutListener listener) {
        this.logoutListener = listener;
    }

    // 定义监听器接口
    public interface OnLoginListener {
        void onLogin(String username, String password);
    }

    public interface OnRegisterListener {
        void onRegister();
    }

    public interface OnLogoutListener {
        void onLogout();
    }
}
