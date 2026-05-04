package com.xyrlsz.xcimocob.ui.activity;

import static com.xyrlsz.xcimocob.Constants.DMZJ_SHARED;
import static com.xyrlsz.xcimocob.Constants.DMZJ_SHARED_COOKIES;
import static com.xyrlsz.xcimocob.Constants.DMZJ_SHARED_UID;
import static com.xyrlsz.xcimocob.Constants.DMZJ_SHARED_USERNAME;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED_COOKIES;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED_EXPIRED;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED_USERNAME;
import static com.xyrlsz.xcimocob.Constants.VOMIC_SHARED;
import static com.xyrlsz.xcimocob.Constants.VOMIC_SHARED_COOKIES;
import static com.xyrlsz.xcimocob.Constants.VOMIC_SHARED_USERNAME;
import static com.xyrlsz.xcimocob.Constants.ZAI_SHARED;
import static com.xyrlsz.xcimocob.Constants.ZAI_SHARED_AUTO_SIGN;
import static com.xyrlsz.xcimocob.Constants.ZAI_SHARED_EXP;
import static com.xyrlsz.xcimocob.Constants.ZAI_SHARED_PASSWD_MD5;
import static com.xyrlsz.xcimocob.Constants.ZAI_SHARED_TOKEN;
import static com.xyrlsz.xcimocob.Constants.ZAI_SHARED_UID;
import static com.xyrlsz.xcimocob.Constants.ZAI_SHARED_USERNAME;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.Constants;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.ui.view.ComicSourceLoginView;
import com.xyrlsz.xcimocob.ui.widget.LoginDialog;
import com.xyrlsz.xcimocob.ui.widget.Option;
import com.xyrlsz.xcimocob.ui.widget.preference.CheckBoxPreference;
import com.xyrlsz.xcimocob.ui.widget.preference.ChoicePreference;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.KomiicUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;
import com.xyrlsz.xcimocob.utils.ThreadRunUtils;
import com.xyrlsz.xcimocob.utils.ZaiManhuaSignUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ComicSourceLoginActivity extends BackActivity implements ComicSourceLoginView {
    private final int DIALOG_COPY_REGION = 1000;
    private final int DIALOG_COPY_IMG_QUALITY = 1001;
    private final int DIALOG_HOT_IMG_QUALITY = 2001;
    private final int DIALOG_BAOZI_IMG_QUALITY = 3001;
    View mComicSourceLoginLayout;
    Option mDmzjLogin;
    ImageButton mDmzjLogout;
    Option mkomiicLogin;
    ImageButton mKomiicLogout;
    Option mVoMiCMHLogin;
    ImageButton mVoMiCMHLogout;
    Option mZaiLogin;
    ImageButton mZaiLogout;
    CheckBoxPreference mZaiAutoSign;
    //    ChoicePreference mCopyRegion;
    ChoicePreference mCopyImgQuality;
    ChoicePreference mHotImageQuality;
    ChoicePreference mBaoZiImageQuality;

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.settings_comic_login_title);
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        // 实现对话框结果的回调处理
        // 根据 requestCode 处理不同的对话框结果
        switch (requestCode) {
            case DIALOG_COPY_REGION:
//                int res_copy_region = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
//                mCopyRegion.setValue(res_copy_region);
                break;
            case DIALOG_COPY_IMG_QUALITY:
                int res_copy_img_quality = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
                mCopyImgQuality.setValue(res_copy_img_quality);
                break;
            case DIALOG_HOT_IMG_QUALITY:
                int res_hot_img_quality = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
                mHotImageQuality.setValue(res_hot_img_quality);
                break;
            case DIALOG_BAOZI_IMG_QUALITY:
                int res_baozi_img_quality = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
                mBaoZiImageQuality.setValue(res_baozi_img_quality);
        }
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mComicSourceLoginLayout = findViewById(R.id.comic_login_layout);
        mDmzjLogin = findViewById(R.id.comic_login_dmzj_login);
        mDmzjLogout = findViewById(R.id.comic_login_dmzj_logout);
        mkomiicLogin = findViewById(R.id.comic_login_komiic_login);
        mKomiicLogout = findViewById(R.id.comic_login_komiic_logout);
        mVoMiCMHLogin = findViewById(R.id.comic_login_vomicmh_login);
        mVoMiCMHLogout = findViewById(R.id.comic_login_vomicmh_logout);
        mZaiLogin = findViewById(R.id.comic_login_zai_login);
        mZaiLogout = findViewById(R.id.comic_login_zai_logout);
        mZaiAutoSign = findViewById(R.id.comic_login_zai_auto_sign);
        mCopyImgQuality = findViewById(R.id.comic_login_copy_image_quality);
        mHotImageQuality = findViewById(R.id.comic_login_hot_image_quality);
        mBaoZiImageQuality = findViewById(R.id.comic_login_baozi_image_quality);
    }

    @Override
    protected void initView() {
        super.initView();
        boolean isDarkMod = ThemeUtils.isDarkMode(getAppInstance());
        if (isDarkMod) {
            mDmzjLogout.setImageResource(R.drawable.ic_logout_white);
            mKomiicLogout.setImageResource(R.drawable.ic_logout_white);
            mVoMiCMHLogout.setImageResource(R.drawable.ic_logout_white);
            mZaiLogout.setImageResource(R.drawable.ic_logout_white);
        } else {
            mDmzjLogout.setImageResource(R.drawable.ic_logout);
            mKomiicLogout.setImageResource(R.drawable.ic_logout);
            mVoMiCMHLogout.setImageResource(R.drawable.ic_logout);
            mZaiLogout.setImageResource(R.drawable.ic_logout);
        }

        String dmzjUsername = getSharedPreferences(DMZJ_SHARED, MODE_PRIVATE).getString(DMZJ_SHARED_USERNAME, "");
        if (!dmzjUsername.isEmpty()) {
            mDmzjLogin.setSummary(dmzjUsername);
            mDmzjLogin.setTitle(getString(R.string.logined));
            mDmzjLogout.setVisibility(View.VISIBLE);
        }

        String komiicUsername = getSharedPreferences(KOMIIC_SHARED, MODE_PRIVATE).getString(KOMIIC_SHARED_USERNAME, "");
        if (!komiicUsername.isEmpty()) {
            mkomiicLogin.setSummary(komiicUsername);
//            KomiicUtils.getImageLimit(result -> mKomiicLogout.post(() -> {
//                mkomiicLogin.setSummary(komiicUsername + "\n" + getString(R.string.settings_komiic_img_limit_summary) + result);
//                CharSequence tmp = mkomiicLogin.getSummary();
//                KomiicUtils.getImageLimit("", res -> mKomiicLogout.post(() -> {
//                    mkomiicLogin.setSummary(tmp + "\n" + getString(R.string.empty_account_limit) + res);
//                }));
//            }));
            mkomiicLogin.setTitle(getString(R.string.logined));
            mKomiicLogout.setVisibility(View.VISIBLE);
        }
//        else {
//            CharSequence tmp = mkomiicLogin.getSummary();
//            KomiicUtils.getImageLimit(result -> mKomiicLogout.post(() -> {
//                mkomiicLogin.setSummary(tmp + "\n" + getString(R.string.settings_komiic_img_limit_summary) + result);
//            }));
//        }

        String vomicmhUsername = getSharedPreferences(VOMIC_SHARED, MODE_PRIVATE).getString(VOMIC_SHARED_USERNAME, "");
        if (!vomicmhUsername.isEmpty()) {
            mVoMiCMHLogin.setSummary(vomicmhUsername);
            mVoMiCMHLogin.setTitle(getString(R.string.logined));
            mVoMiCMHLogout.setVisibility(View.VISIBLE);
        }

        String zaiUsername = getSharedPreferences(ZAI_SHARED, MODE_PRIVATE).getString(ZAI_SHARED_USERNAME, "");
        if (!zaiUsername.isEmpty()) {
            mZaiLogin.setSummary(zaiUsername);
            mZaiLogin.setTitle(getString(R.string.logined));
            mZaiLogout.setVisibility(View.VISIBLE);

            boolean autoSign = getSharedPreferences(ZAI_SHARED, MODE_PRIVATE).getBoolean(ZAI_SHARED_AUTO_SIGN, false);
            mZaiAutoSign.setChecked(autoSign);
            mZaiAutoSign.setVisibility(View.VISIBLE);

            ZaiManhuaSignUtils.CheckSigned(getApplicationContext(), isSigned -> {
                if (isSigned) {
                    ThreadRunUtils.runOnMainThread(() -> mZaiAutoSign.setSummary(getString(R.string.is_sign)));
                }
            });

        }

        findViewById(R.id.comic_login_dmzj_login).setOnClickListener(v -> onDmzjLoginClick());
        findViewById(R.id.comic_login_dmzj_logout).setOnClickListener(v -> onDmzjLogoutClick());
        findViewById(R.id.comic_login_komiic_login).setOnClickListener(v -> onKomiicLoginClick());
        findViewById(R.id.comic_login_komiic_logout).setOnClickListener(v -> onKomiicLogoutClick());
        findViewById(R.id.comic_login_vomicmh_login).setOnClickListener(v -> onVoMiCMHLoginClick());
        findViewById(R.id.comic_login_vomicmh_logout).setOnClickListener(v -> onVoMiCMHLogoutClick());
        findViewById(R.id.comic_login_zai_login).setOnClickListener(v -> onZaiLoginClick());
        findViewById(R.id.comic_login_zai_logout).setOnClickListener(v -> onZaiLogoutClick());
        findViewById(R.id.comic_login_zai_auto_sign).setOnClickListener(v -> onZaiAutoSignClick());
        findViewById(R.id.comic_login_zai_auto_sign).setOnLongClickListener(v -> {
            onZaiAutoSignLongClick();
            return true;
        });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences copySharedPreferences = getSharedPreferences(Constants.COPYMG_SHARED, MODE_PRIVATE);
//        mCopyRegion.bindPreference(getSupportFragmentManager(), copySharedPreferences, Constants.COPYMG_SHARED_REGION, 0, R.array.copy_region_items, DIALOG_COPY_REGION);
        mCopyImgQuality.bindPreference(getSupportFragmentManager(), copySharedPreferences, Constants.COPYMG_SHARED_IMG_QUALITY, 2, R.array.copy_img_quality_items, DIALOG_COPY_IMG_QUALITY);

        SharedPreferences hotSharedPreferences = getSharedPreferences(Constants.HOTMG_SHARED, MODE_PRIVATE);
        mHotImageQuality.bindPreference(getSupportFragmentManager(), hotSharedPreferences, Constants.HOTMG_SHARED_IMG_QUALITY, 2, R.array.hot_img_quality_items, DIALOG_HOT_IMG_QUALITY);

        SharedPreferences baoZiSharedPreferences = getSharedPreferences(Constants.BAOZI_SHARED, MODE_PRIVATE);
        mBaoZiImageQuality.bindPreference(getSupportFragmentManager(), baoZiSharedPreferences, Constants.BAOZI_SHARED_IMG_QUALITY, 0, R.array.baozi_img_quality_items, DIALOG_BAOZI_IMG_QUALITY);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_comic_source_login;
    }

    @Override
    public void onLoginSuccess() {
        hideProgressDialog();
        showSnackbar(getString(R.string.user_login_sucess));
    }

    @Override
    public void onLoginFail() {
        hideProgressDialog();
        showSnackbar(getString(R.string.user_login_failed));
    }

    @Override
    public void onStartLogin() {
        showProgressDialog();
    }

    @Override
    protected View getLayoutView() {
        return mComicSourceLoginLayout;
    }

    // 动漫之家
    void onDmzjLoginClick() {

        int theme = ThemeUtils.getThemeId();
        LoginDialog loginDialog = new LoginDialog(this, ThemeUtils.getDialogThemeById(theme));
        loginDialog.setOnLoginListener((username, password) -> {
            if (username.isEmpty() || password.isEmpty()) {
                loginDialog.dismiss();
                showSnackbar(getString(R.string.user_login_empty));
                return;
            }
            onStartLogin();
            RequestBody formBody = new FormBody.Builder()
                    .add("nickname", username)
                    .add("password", password)
                    .add("type", "1")
                    .add("to", "https://i.dmzj.com")
                    .build();

            Request request = new Request.Builder()
                    .url("https://i.dmzj.com/doLogin")
                    .addHeader("referer", "https://i.dmzj.com/login")
                    .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
                    .post(formBody)
                    .build();

            Objects.requireNonNull(App.getHttpClient()).newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    onLoginFail();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                    List<String> cookies = response.headers("Set-Cookie");
                    if (response.isSuccessful() && !cookies.isEmpty()) {
                        String json = response.body().string();
                        String uid = StringUtils.match("m=(\\d+)\\|", json, 1);
                        Set<String> set = new HashSet<>();
                        for (String s : cookies) {
                            List<String> tmp = Arrays.asList(s.split("; "));
                            set.addAll(tmp);
                        }
                        String cookieStr = String.join("; ", set);
                        SharedPreferences sharedPreferences = getSharedPreferences(DMZJ_SHARED, MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(DMZJ_SHARED_COOKIES, cookieStr);
                        editor.putString(DMZJ_SHARED_USERNAME, username);
                        editor.putString(DMZJ_SHARED_UID, uid);
                        editor.apply();
                        runOnUiThread(() -> {
                            mDmzjLogin.setSummary(username);
                            mDmzjLogin.setTitle(getString(R.string.logined));
                            mDmzjLogout.setVisibility(View.VISIBLE);
                        });
                        onLoginSuccess();
                    } else {
                        onLoginFail();
                    }
                    loginDialog.dismiss();
                }
            });


        });
        loginDialog.setOnRegisterListener(() -> {
            String url = "https://m.idmzj.com/register.html";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        });
        loginDialog.show();
    }


    void onDmzjLogoutClick() {
        SharedPreferences sharedPreferences = getSharedPreferences(DMZJ_SHARED, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(DMZJ_SHARED_COOKIES);
        editor.remove(DMZJ_SHARED_USERNAME);
        editor.remove(DMZJ_SHARED_UID);
        mDmzjLogin.setSummary(getString(R.string.no_login));
        mDmzjLogin.setTitle(getString(R.string.login));
        mDmzjLogout.setVisibility(View.GONE);
        editor.apply();
        showSnackbar(getString(R.string.user_login_logout_sucess));
    }

    // komiic
    void onKomiicLoginClick() {

        int theme = ThemeUtils.getThemeId();
        LoginDialog loginDialog = new LoginDialog(this, ThemeUtils.getDialogThemeById(theme));
        loginDialog.setOnLoginListener((username, password) -> {
            if (username.isEmpty() || password.isEmpty()) {
                loginDialog.dismiss();
                showSnackbar(getString(R.string.user_login_empty));
                return;
            }
            onStartLogin();
//            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
//            String json = "{\"email\":\"" + username + "\", \"password\":\"" + password + "\"}";
//            RequestBody body = RequestBody.create(mediaType, json);
//            Request request = new Request.Builder()
//                    .url("https://komiic.com/api/login")
//                    .addHeader("referer", "https://komiic.com/login")
//                    .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
//                    .post(body)
//                    .build();
//
//            Objects.requireNonNull(App.getHttpClient()).newCall(request).enqueue(new Callback() {
//                @Override
//                public void onFailure(Call call, IOException e) {
//                    onLoginFail();
//                }
//
//                @Override
//                public void onResponse(Call call, Response response) throws IOException {
//                    List<String> cookies = response.headers("Set-Cookie");
//                    if (response.isSuccessful() && !cookies.isEmpty()) {
//                        Set<String> set = new HashSet<>();
//                        for (String s : cookies) {
//                            List<String> tmp = Arrays.asList(s.split("; "));
//                            set.addAll(tmp);
//                        }
//                        Long expired = -1L;
//                        try {
//                            JSONObject data = new JSONObject(response.body().string());
//                            String date = data.getString("expire");
//                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
//                            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
//                            expired = KomiicUtils.toTimestamp(date);
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                        String cookieStr = String.join("; ", set);
//                        SharedPreferences sharedPreferences = getSharedPreferences(KOMIIC_SHARED, MODE_PRIVATE);
//                        SharedPreferences.Editor editor = sharedPreferences.edit();
//                        editor.putString(KOMIIC_SHARED_COOKIES, cookieStr);
//                        editor.putString(KOMIIC_SHARED_USERNAME, username);
//                        editor.putString(KOMIIC_SHARED_PASSWD, password);
//                        editor.putLong(KOMIIC_SHARED_EXPIRED, expired);
//                        editor.apply();
//                        runOnUiThread(() -> {
//                            mkomiicLogin.setSummary(username);
//                            mkomiicLogin.setTitle(getString(R.string.logined));
//                            KomiicUtils.getImageLimit(result -> mKomiicLogout.post(() -> {
//                                mkomiicLogin.setSummary(username + "\n" + getString(R.string.settings_komiic_img_limit_summary) + result);
//                                CharSequence tmp = mkomiicLogin.getSummary();
//                                KomiicUtils.getImageLimit("", res -> mKomiicLogout.post(() -> {
//                                    mkomiicLogin.setSummary(tmp + "\n" + getString(R.string.empty_account_limit) + res);
//                                }));
//                            }));
//                            mKomiicLogout.setVisibility(View.VISIBLE);
//                        });
//                        onLoginSuccess();
//                    } else {
//                        onLoginFail();
//                    }
//                    loginDialog.dismiss();
//                }
//            });

            KomiicUtils.login(getApplicationContext(), username, password, new KomiicUtils.OnLoginListener() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        mkomiicLogin.setSummary(username);
                        mkomiicLogin.setTitle(getString(R.string.logined));
//                        KomiicUtils.getImageLimit(result -> mKomiicLogout.post(() -> {
//                            mkomiicLogin.setSummary(username + "\n" + getString(R.string.settings_komiic_img_limit_summary) + result);
//                            CharSequence tmp = mkomiicLogin.getSummary();
//                            KomiicUtils.getImageLimit("", res -> mKomiicLogout.post(() -> {
//                                mkomiicLogin.setSummary(tmp + "\n" + getString(R.string.empty_account_limit) + res);
//                            }));
//                        }));
                        mKomiicLogout.setVisibility(View.VISIBLE);
                    });
                    onLoginSuccess();
                    loginDialog.dismiss();
                }

                @Override
                public void onFail() {
                    onLoginFail();
                    loginDialog.dismiss();
                }
            });
        });
        loginDialog.setOnRegisterListener(() -> {
            String url = "https://komiic.com/register";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        });
        loginDialog.show();
    }

    void onKomiicLogoutClick() {
        SharedPreferences sharedPreferences = getSharedPreferences(KOMIIC_SHARED, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KOMIIC_SHARED_COOKIES);
        editor.remove(KOMIIC_SHARED_USERNAME);
        editor.remove(KOMIIC_SHARED_EXPIRED);
        editor.apply();
        mkomiicLogin.setSummary(getString(R.string.no_login));
        mkomiicLogin.setTitle(getString(R.string.login));
        mKomiicLogout.setVisibility(View.GONE);
    }

    // vomicmh漫
    void onVoMiCMHLoginClick() {
        int theme = ThemeUtils.getThemeId();
        LoginDialog loginDialog = new LoginDialog(this, ThemeUtils.getDialogThemeById(theme));
        loginDialog.setOnLoginListener((username, password) -> {
            if (username.isEmpty() || password.isEmpty()) {
                loginDialog.dismiss();
                showSnackbar(getString(R.string.user_login_empty));
                return;
            }
            onStartLogin();
            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            String json = "{\"email\":\""
                    + username
                    + "\",\"password\":\""
                    + password
                    + "\"}";
            RequestBody body = RequestBody.create(mediaType, json);
            Request request = new Request.Builder().url("https://api.vomicmh.com/pics/login")
                    .post(body)
                    .addHeader("referer", "https://www.vomicmh.com/")
                    .build();
            Objects.requireNonNull(App.getHttpClient()).newCall(request).enqueue(new Callback() {

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonObject = new JSONObject(response.body().string());
                            String token = jsonObject.getString("token");
                            SharedPreferences sharedPreferences = getSharedPreferences(VOMIC_SHARED, MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(VOMIC_SHARED_USERNAME, username);
                            editor.putString(VOMIC_SHARED_COOKIES, "_token=" + token);
                            editor.apply();
                            runOnUiThread(() -> {
                                mVoMiCMHLogin.setSummary(username);
                                mVoMiCMHLogin.setTitle(getString(R.string.logined));
                                mVoMiCMHLogout.setVisibility(View.VISIBLE);
                            });
                            onLoginSuccess();
                            loginDialog.dismiss();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            onLoginFail();
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    onLoginFail();
                }
            });
        });
        loginDialog.setOnRegisterListener(() -> {
            String url = "https://www.vomicmh.com/";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        });
        loginDialog.show();
    }

    void onVoMiCMHLogoutClick() {
        SharedPreferences sharedPreferences = getSharedPreferences(VOMIC_SHARED, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(VOMIC_SHARED_COOKIES);
        editor.remove(VOMIC_SHARED_USERNAME);
        mVoMiCMHLogin.setSummary(getString(R.string.no_login));
        mVoMiCMHLogin.setTitle(getString(R.string.login));
        mVoMiCMHLogout.setVisibility(View.GONE);
        editor.apply();
        showSnackbar(getString(R.string.user_login_logout_sucess));
    }

    // 再漫画
    void onZaiLoginClick() {
        int theme = ThemeUtils.getThemeId();
        LoginDialog loginDialog = new LoginDialog(this, ThemeUtils.getDialogThemeById(theme));
        loginDialog.setOnLoginListener((username, password) -> {
            if (username.isEmpty() || password.isEmpty()) {
                loginDialog.dismiss();
                showSnackbar(getString(R.string.user_login_empty));
                return;
            }
            onStartLogin();

            ZaiManhuaSignUtils.Login(this, new ZaiManhuaSignUtils.LoginCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        mZaiLogin.setSummary(username);
                        mZaiLogin.setTitle(getString(R.string.logined));
                        mZaiLogout.setVisibility(View.VISIBLE);
                        boolean autoSign = getSharedPreferences(ZAI_SHARED, MODE_PRIVATE).getBoolean(ZAI_SHARED_AUTO_SIGN, false);
                        mZaiAutoSign.setChecked(autoSign);
                        mZaiAutoSign.setVisibility(View.VISIBLE);
                    });
                    onLoginSuccess();
                    loginDialog.dismiss();
                }

                @Override
                public void onFail() {
                    onLoginFail();
                }
            }, username, password);
        });
        loginDialog.setOnRegisterListener(() -> {
            String url = "https://i.zaimanhua.com/login";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        });
        loginDialog.show();
    }

    void onZaiLogoutClick() {
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.ZAI_SHARED, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(ZAI_SHARED_UID);
        editor.remove(ZAI_SHARED_TOKEN);
        editor.remove(ZAI_SHARED_USERNAME);
        editor.remove(ZAI_SHARED_PASSWD_MD5);
        editor.remove(ZAI_SHARED_EXP);
        mZaiLogin.setSummary(getString(R.string.no_login));
        mZaiLogin.setTitle(getString(R.string.login));
        mZaiLogout.setVisibility(View.GONE);
        editor.apply();
        mZaiAutoSign.setVisibility(View.GONE);
    }

    void onZaiAutoSignClick() {
        boolean isChecked = mZaiAutoSign.isChecked();
        mZaiAutoSign.setChecked(!isChecked);
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.ZAI_SHARED, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(ZAI_SHARED_AUTO_SIGN, mZaiAutoSign.isChecked());
        editor.apply();
    }

    void onZaiAutoSignLongClick() {
        ZaiManhuaSignUtils.CheckSigned(getApplicationContext(), isSigned -> {
            if (isSigned) {
                HintUtils.showToast(getApplicationContext(), "再漫画已签到");
            } else {
                ZaiManhuaSignUtils.SignIn(getApplicationContext());
            }
        });
    }
}