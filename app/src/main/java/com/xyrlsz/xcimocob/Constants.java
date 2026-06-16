package com.xyrlsz.xcimocob;

public final class Constants {

    public static final String DMZJ_SHARED = "dmzj";
    public static final String DMZJ_SHARED_COOKIES = "cookies";
    public static final String DMZJ_SHARED_USERNAME = "username";
    public static final String DMZJ_SHARED_UID = "uid";

    public static final String ZAI_SHARED = "zai";
    public static final String ZAI_SHARED_USERNAME = "username";
    public static final String ZAI_SHARED_UID = "uid";
    public static final String ZAI_SHARED_TOKEN = "token";
    public static final String ZAI_SHARED_AUTO_SIGN = "auto_sign";
    public static final String ZAI_SHARED_PASSWD_MD5 = "passwd_md5";
    public static final String ZAI_SHARED_EXP = "exp";

    public static final String KOMIIC_SHARED = "komiic";
    public static final String KOMIIC_SHARED_COOKIES = "cookies";
    public static final String KOMIIC_SHARED_USERNAME = "username";
    public static final String KOMIIC_SHARED_PASSWD = "passwd";
    public static final String KOMIIC_SHARED_EXPIRED = "expired";
    public static final String KOMIIC_SHARED_BASEURL = "baseUrl";
    public static final String KOMIIC_SHARED_IMG_LIMIT = "img_limit";
    public static final String KOMIIC_SHARED_IMG_LIMIT_TIME = "img_limit_time";

    public static final String VOMIC_SHARED = "vomicmh";
    public static final String VOMIC_SHARED_COOKIES = "cookies";
    public static final String VOMIC_SHARED_USERNAME = "username";

    public static final String COPYMG_SHARED = "copyMh";
    public static final String COPYMG_SHARED_REGION = "region";
    public static final String COPYMG_SHARED_VERSION = "version";
    public static final String COPYMG_SHARED_REQID = "request_id";
    public static final String COPYMG_SHARED_REQID_time = "request_id_time";
    public static final String COPYMG_SHARED_IMG_QUALITY = "img_quality";
    public static final String COPYMG_SHARED_SEARCH_API = "web_search_api";
    public static final String HOTMG_SHARED = "hotmg";
    public static final String HOTMG_SHARED_IMG_QUALITY = "img_quality";
    public static final String BAOZI_SHARED = "baozi";
    public static final String BAOZI_SHARED_IMG_QUALITY = "img_quality";
    public static final String WEBDAV_SHARED = "webdav";
    public static final String WEBDAV_SHARED_URL = "url";
    public static final String WEBDAV_SHARED_USERNAME = "username";
    public static final String WEBDAV_SHARED_PASSWORD = "password";

    //    public static final int VERSION = 105006;

    public static final String APP_SHARED = "app";
    public static final String APP_SHARED_TEST_MODE = "test";

    private static final String Repository = BuildConfig.OWNER_NAME + "/" + BuildConfig.REPOSITORIE_NAME;
    public static final String UPDATE_GITHUB_URL = "https://api.github.com/repos/" + Repository + "/releases/latest";
    public static final String UPDATE_GITEE_URL = "https://gitee.com/api/v5/repos/" + Repository + "/releases/latest";
    public static final String GITHUB_HOME_PAGE_URL = "https://github.com/" + Repository;
    public static final String GITHUB_RELEASE_URL = GITHUB_HOME_PAGE_URL + "/releases";
    public static final String GITHUB_ISSUE_URL = GITHUB_HOME_PAGE_URL + "/issues";
    public static final String GITEE_HOME_PAGE_URL = "https://gitee.com/" + Repository;
    public static final String GITEE_RELEASE_URL = GITEE_HOME_PAGE_URL + "/releases";
    public static final String GITEE_ISSUE_URL = GITEE_HOME_PAGE_URL + "/issues";

    // 权限请求码
    public static final int REQUEST_CODE_STORAGE = 0x1001;
    public static final int REQUEST_CODE_MANAGE_STORAGE = 0x1002;

}
