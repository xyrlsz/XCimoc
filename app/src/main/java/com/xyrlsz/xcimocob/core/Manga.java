package com.xyrlsz.xcimocob.core;

import static com.xyrlsz.xcimocob.ui.activity.SearchActivity.SEARCH_AUTHOR;
import static com.xyrlsz.xcimocob.ui.activity.SearchActivity.SEARCH_TITLE;

import android.util.Log;
import android.util.Pair;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.manager.ChapterManager;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.Parser;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.parser.WebParser;
import com.xyrlsz.xcimocob.parser.WebParserConfig;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.source.js.JsMangaParser;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.STConvertUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InterruptedIOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Hiroshi on 2016/8/20.
 */
public class Manga {

    /**
     * URL 级别强制刷新集合：只有匹配 URL 的请求才跳过缓存（精准失效，无竞态）
     */
    private static final Set<String> sForceRefreshUrls = ConcurrentHashMap.newKeySet();
    /**
     * 全局强制刷新标志，用于下拉刷新时跳过所有 OkHttp 缓存（一次性）
     */
    private static volatile boolean sForceRefresh = false;
    /**
     * 检查漫画更新时每个漫画的单本超时时间（秒）
     */
    private static final long CHECK_UPDATE_TIMEOUT_SECONDS = 10;

    /**
     * 标记指定 URL 需要强制从网络获取，精准失效（不被其他无关请求消费）
     */
    public static void setForceRefreshUrl(String url) {
        if (url != null) {
            sForceRefreshUrls.add(url);
        }
    }

    private static boolean indexOfIgnoreCase(String str, String search) {
        // 显式使用 Locale.ROOT 执行大小写无关比较：搜索属于语义操作，
        // 不应受土耳其语/i 等区域性大小写映射影响。
        return str.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }


    public static boolean indexOfIgnoreCase(String str, String search, boolean stSame) {
        if (stSame) {
            try {
                String s1 = STConvertUtils.T2S(str);
                String s2 = STConvertUtils.T2S(search);
                return s1.toLowerCase(Locale.ROOT).contains(s2.toLowerCase(Locale.ROOT));
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return str.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
        }
    }


    public static Observable<Comic> getSearchResult(final MangaParser parser, final String keyword, final int page, final boolean strictSearch, final boolean stSame) {
        return Observable.defer(() -> {
            Request request = parser.getSearchRequest(keyword, page);
            if (request == null) {
                return Observable.error(new Exception("Search request returned null"));
            }
            String url = request.url().toString();
            WebParserConfig searchConfig = parser.getSearchConfig();
            Observable<String> htmlObs = searchConfig.isUseWebParser()
                    ? new WebParser(App.getAppContext(), url, request.headers(), "", searchConfig).getHtmlObservable()
                    : Observable.fromCallable(() -> getResponseBody(App.getHttpClient(), request));
            return htmlObs
                    .flatMap(html -> {
                        SearchIterator iterator = parser.getSearchIterator(html, page);
                        if (iterator == null || iterator.empty()) {
                            return Observable.error(new Exception("Search returned no results"));
                        }

                        Random random = new Random();
                        List<Comic> result = new ArrayList<>();
                        while (iterator.hasNext()) {
                            Comic comic = iterator.next();
                            if (comic != null
                                    && (indexOfIgnoreCase(comic.getTitle(), keyword, stSame)
                                    || indexOfIgnoreCase(comic.getAuthor(), keyword, stSame)
                                    || (!strictSearch))) {
                                result.add(comic);
//                        Thread.sleep(random.nextInt(200));
                            }
                        }
                        return Observable.fromIterable(result);
                    })
                    .onErrorResumeNext(e -> {

                        setForceRefreshUrl(url);
                        return Observable.error(e);
                    });
        }).subscribeOn(Schedulers.io());
    }

    public static Observable<Comic> getSearchResult(final MangaParser parser, final String keyword, final int page, final boolean strictSearch, final boolean stSame, final int searchType) {
        return Observable.defer(() -> {
            Request request = parser.getSearchRequest(keyword, page);
            if (request == null) {
                return Observable.error(new Exception("Search request returned null"));
            }
            String url = request.url().toString();
            WebParserConfig searchConfig = parser.getSearchConfig();
            Observable<String> htmlObs = searchConfig.isUseWebParser()
                    ? new WebParser(App.getAppContext(), url, request.headers(), "", searchConfig).getHtmlObservable()
                    : Observable.fromCallable(() -> getResponseBody(App.getHttpClient(), request));
            return htmlObs
                    .flatMap(html -> {
                        SearchIterator iterator = parser.getSearchIterator(html, page);
                        if (iterator == null || iterator.empty()) {
                            return Observable.error(new Exception("Search returned no results"));
                        }

                        Random random = new Random();
                        List<Comic> result = new ArrayList<>();
                        while (iterator.hasNext()) {
                            Comic comic = iterator.next();

                            if (searchType == SEARCH_TITLE) {
                                if (comic != null
                                        && (indexOfIgnoreCase(comic.getTitle().strip(), keyword.strip(), stSame)
                                        || (!strictSearch))) {
                                    result.add(comic);
//                            Thread.sleep(random.nextInt(200));
                                }
                            } else if (searchType == SEARCH_AUTHOR) {
                                if (comic != null) {
                                    String[] separators = {",", ";", "、", "，", "；", " ", "/"};
                                    boolean findAuthor = false;
                                    for (String separator : separators) {
                                        String[] keywords = keyword.strip().split(separator);
                                        for (String key : keywords) {
                                            if (indexOfIgnoreCase(comic.getAuthor().strip(), key.strip(), stSame)) {
                                                findAuthor = true;
                                                break;
                                            }
                                        }
                                    }

                                    if (findAuthor || (!strictSearch)) {
                                        result.add(comic);
//                                Thread.sleep(random.nextInt(200));
                                    }
                                }
                            }
                        }
                        return Observable.fromIterable(result);
                    })
                    .onErrorResumeNext(e -> {

                        setForceRefreshUrl(url);
                        return Observable.error(e);
                    });
        }).subscribeOn(Schedulers.io());
    }

    public static Observable<List<Chapter>> getComicInfo(final MangaParser parser, final Comic comic) {
        // JS 源：在单次详情流程内复用同一 QuickJS 引擎，避免多次重复编译体积较大的 SDK+脚本
        // （见 JsMangaParser.openSession）。复用范围严格限定本流程、同线程串行、用完即弃，
        // 不污染其它漫画/任务；非 JS 源走空会话，行为不变。
        return Observable.using(
                () -> (parser instanceof JsMangaParser)
                        ? ((JsMangaParser) parser).openSession()
                        : (java.io.Closeable) () -> {
                },
                session -> Observable.defer(() -> {
            String cid = comic.getCid();
            if (cid == null) {
                return Observable.error(new Exception("Comic CID is null"));
            }
            String comicUrl = parser.getUrl(cid);
            if (comicUrl == null) {
                return Observable.error(new Exception("Comic URL is null"));
            }
            comic.setUrl(comicUrl);
            Request infoRequest = parser.getInfoRequest(cid);
            if (infoRequest == null) {
                return Observable.error(new Exception("Info request returned null"));
            }
            String infoUrl = infoRequest.url().toString();
            WebParserConfig infoConfig = parser.getInfoConfig();
            Observable<String> infoHtmlObs = infoConfig.isUseWebParser()
                    ? new WebParser(App.getAppContext(), infoUrl, infoRequest.headers(), "", infoConfig).getHtmlObservable()
                    : Observable.fromCallable(() -> getResponseBody(App.getHttpClient(), infoRequest));
            return infoHtmlObs
                    .flatMap(html -> {
                        if (html == null) {
                            return Observable.error(new Exception("Info HTML is null"));
                        }
                        Comic newComic = parser.parseInfo(html, comic);
                        if (newComic == null) {
                            return Observable.error(new Exception("Parse info returned null"));
                        }
                        // 解析漫画标签（题材），与详情同源 HTML，仅在解析器支持且非空时覆盖
                        List<String> tags = parser.parseTags(html);
                        if (tags != null && !tags.isEmpty()) {
                            newComic.setTags(tags);
                        }
                        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_UPDATE_INFO, newComic));
                        Request chapterReq = parser.getChapterRequest(html, comic.getCid());
                        if (chapterReq != null) {
                            String chapterUrl = chapterReq.url().toString();
                            WebParserConfig chapterConfig = parser.getChapterConfig();
                            Observable<String> chapterHtmlObs = chapterConfig.isUseWebParser()
                                    ? new WebParser(App.getAppContext(), chapterUrl, chapterReq.headers(), "", chapterConfig).getHtmlObservable()
                                    : Observable.fromCallable(() -> getResponseBody(App.getHttpClient(), chapterReq));
                            return chapterHtmlObs.flatMap(chapterHtml -> {
                                if (chapterHtml == null) {

                                    setForceRefreshUrl(infoUrl);
                                    setForceRefreshUrl(chapterUrl);
                                    return Observable.error(new Exception("Chapter HTML is null"));
                                }
                                Long sourceComic = IdCreator.createSourceComic(comic);
                                List<Chapter> list = parser.parseChapter(chapterHtml, comic, sourceComic);
                                if (list == null) {
                                    list = parser.parseChapter(chapterHtml);
                                }
                                if (list == null || list.isEmpty()) {
                                    // 解析失败 → 清除缓存，下次重试走网络

                                    setForceRefreshUrl(infoUrl);
                                    setForceRefreshUrl(chapterUrl);
                                    return Observable.error(new Exception("Parse chapter list empty"));
                                }
                                return Observable.just(list);
                            });
                        } else {
                            Long sourceComic = IdCreator.createSourceComic(comic);
                            List<Chapter> list = parser.parseChapter(html, comic, sourceComic);
                            if (list == null) {
                                list = parser.parseChapter(html);
                            }
                            if (list == null || list.isEmpty()) {
                                // 解析失败 → 清除缓存，下次重试走网络

                                setForceRefreshUrl(infoUrl);
                                return Observable.error(new Exception("Parse chapter list empty"));
                            }
                            return Observable.just(list);
                        }
                    })
                    // infoHtmlObs 或 chapterHtmlObs 出错时（包括 WebParser 超时/错误），清除缓存确保重试走网络
                    .onErrorResumeNext(e -> {

                        setForceRefreshUrl(infoUrl);
                        return Observable.error(e);
                    });
                }),
                session -> {
                    try {
                        session.close();
                    } catch (Throwable ignore) {
                    }
                })
                .subscribeOn(Schedulers.io());
    }

    public static Observable<List<Comic>> getCategoryComic(final Parser parser, final String format,
                                                           final int page) {
        return Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<List<Comic>>) emitter -> {
            String url = null;
            try {
                Request request = parser.getCategoryRequest(format, page);
                if (request == null) {
                    emitter.onError(new Exception("Category request returned null"));
                    return;
                }
                url = request.url().toString();
                String html = getResponseBody(App.getHttpClient(), request);
                List<Comic> list = parser.parseCategory(html, page);
                if (!list.isEmpty()) {
                    emitter.onNext(list);
                    emitter.onComplete();
                } else {
                    // 解析失败 → 清除缓存，下次重试走网络

                    setForceRefreshUrl(url);
                    throw new Exception("Parse category list empty");
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public static Observable<List<ImageUrl>> getChapterImage(final Chapter chapter,
                                                             final MangaParser parser,
                                                             final String cid,
                                                             final String path) {
        return Observable.defer(() -> {
            Request request = parser.getImagesRequest(cid, path);
            if (request == null) {
                // 偶发：getImagesRequest 内部引擎/JS 调用静默失败返回 null（无 JsParser/JS exception 日志）。
                // 每次调用都会新建 QuickJS 引擎，重试一次大概率成功，避免整个章节加载失败。
                Log.w("Manga", "[Manga] getImagesRequest null, retrying once cid=" + cid + " path=" + path);
                request = parser.getImagesRequest(cid, path);
                if (request == null) {
                    return Observable.error(new Exception("Images request returned null"));
                }
            }
            String url = request.url().toString();
            WebParserConfig imagesConfig = parser.getImagesConfig();
            Request finalRequest = request;
            Observable<String> htmlObs = imagesConfig.isUseWebParser()
                    ? new WebParser(App.getAppContext(), url, request.headers(), "", imagesConfig).getHtmlObservable()
                    : Observable.fromCallable(() -> getResponseBody(App.getHttpClient(), finalRequest));
            return htmlObs
                    .flatMap(html -> {
                        List<ImageUrl> list = parser.parseImages(html, chapter);
                        if (list == null || list.isEmpty()) {
                            list = parser.parseImages(html);
                        }
                        if (list == null || list.isEmpty()) {
                            // 解析失败 → 清除该 URL 的 WebParser 内存缓存 + 跳过 OkHttp 磁盘缓存

                            setForceRefreshUrl(url);
                            return Observable.error(new Exception("Parse images list empty"));
                        }
                        for (ImageUrl imageUrl : list) {
                            imageUrl.setChapter(path);
                        }
                        return Observable.just(list);
                    })
                    // WebParser 超时/错误 或 OkHttp 请求失败时，也清除缓存确保下次重试走网络
                    .onErrorResumeNext(e -> {

                        setForceRefreshUrl(url);
                        return Observable.error(e);
                    });
        }).subscribeOn(Schedulers.io());
    }

    public static List<ImageUrl> getImageUrls(MangaParser parser, int source, String cid, String path, String title, ChapterManager mChapterManager) throws InterruptedIOException {
        List<ImageUrl> list = new ArrayList<>();
        Response response = null;
        try {

            if (!list.isEmpty()) {
                return list;
            }
            Request request = parser.getImagesRequest(cid, path);
            if (request == null) {
                return list;
            }
            if (!parser.getImagesConfig().isUseWebParser()) {
                response = Objects.requireNonNull(App.getHttpClient()).newCall(request).execute();
                if (response.isSuccessful()) {
                    Comic comic = ComicManager.getInstance(parser).load(source, cid);
                    long sourceComic = IdCreator.createSourceComic(comic);
                    List<Chapter> chapter = mChapterManager.getChapter(sourceComic, path);
                    if (chapter != null && !chapter.isEmpty()) {
                        list.addAll(parser.parseImages(response.body().string(), chapter.get(0)));
                    }
                    if (list.isEmpty()) {
                        list.addAll(parser.parseImages(response.body().string()));
                    }

                } else {
                    throw new NetworkErrorException();
                }
            } else {
                WebParser webParser = new WebParser(App.getAppContext(), request.url().toString(), request.headers(), "", parser.getImagesConfig());

                String html = webParser.getHtmlObservable().blockingFirst();
                Comic comic = ComicManager.getInstance(parser).load(source, cid);
                long sourceComic = IdCreator.createSourceComic(comic);
                List<Chapter> chapter = mChapterManager.getChapter(sourceComic, path);
                if (chapter != null && !chapter.isEmpty()) {
                    list.addAll(parser.parseImages(html, chapter.get(0)));
                }
                if (list.isEmpty()) {
                    list.addAll(parser.parseImages(html));
                }
            }

        } catch (InterruptedIOException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return list;
    }

    public static String getLazyUrl(MangaParser parser, String url) throws InterruptedIOException {
        Response response = null;
        try {
            Request request = parser.getLazyRequest(url);
            response = Objects.requireNonNull(App.getHttpClient()).newCall(request).execute();
            if (response.isSuccessful()) {
                if (parser.getImagesLazyConfig().isUseWebParser()) {
                    WebParser webParser = new WebParser(App.getAppContext(), request.url().toString(), request.headers(), "", parser.getImagesLazyConfig());
                    return parser.parseLazy(webParser.getHtmlObservable().blockingFirst(), url);
                }
                return parser.parseLazy(response.body().string(), url);
            } else {
                throw new NetworkErrorException();
            }
        } catch (InterruptedIOException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return null;
    }

    public static Observable<String> loadLazyUrl(final MangaParser parser, final String url) {
        return Observable.defer(() -> {
            Request request = parser.getLazyRequest(url);
            String reqUrl = request.url().toString();
            WebParserConfig imagesLazyConfig = parser.getImagesLazyConfig();
            Observable<String> htmlObs = imagesLazyConfig.isUseWebParser()
                    ? new WebParser(App.getAppContext(), reqUrl, request.headers(), "", imagesLazyConfig).getHtmlObservable()
                    : Observable.fromCallable(() -> getResponseBody(App.getHttpClient(), request));
            return htmlObs
                    .flatMap(html -> {
                        String newUrl = parser.parseLazy(html, url);
                        if (newUrl != null) {
                            return Observable.just(newUrl);
                        } else {
                            return Observable.error(new Exception("loadLazyUrl returned null"));
                        }
                    })
                    .onErrorResumeNext(e -> {

                        setForceRefreshUrl(reqUrl);
                        return Observable.error(e);
                    });
        }).subscribeOn(Schedulers.io());
    }

    public static Observable<List<String>> loadAutoComplete(final String keyword) {
        return Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<List<String>>) emitter -> {
            Request request = new Request.Builder()
                    .url("https://m.ac.qq.com/search/smart?word=" + keyword)
                    .addHeader("referer", "https://m.ac.qq.com/search/index")
                    .addHeader("user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                    .build();
            try {
                String jsonString = getResponseBody(App.getHttpClient(), request);
                JSONObject jsonObject = new JSONObject(jsonString);
                JSONArray array = jsonObject.getJSONArray("data");
                List<String> list = new ArrayList<>();
                for (int i = 0; i != array.length(); ++i) {
                    list.add(array.getJSONObject(i).getString("title"));
                }
                emitter.onNext(list);
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public static Observable<CheckUpdateEvent> checkUpdate(
            final SourceManager manager, final List<Comic> list) {
        // 复用共享连接池的 OkHttpClient，通过 per-request timeout 控制超时
        OkHttpClient sharedClient = App.getHttpClient();
        return Observable.fromIterable(list)
                .flatMap(comic -> Observable.just(comic)
                        .subscribeOn(Schedulers.io())  // 每个 Comic 分配到不同的 IO 线程
                        .flatMap(c -> {
                            try {
                                MangaParser parser = manager.getParser(c.getSource());
                                Request request = parser.getCheckRequest(c.getCid());
                                if (request == null) {
                                    request = parser.getInfoRequest(c.getCid());
                                }
                                if (request == null) {
                                    return Observable.just(new CheckUpdateEvent(c, false));
                                }

                                // 使用 WebView 渲染的源（info 环节配置了 useWebParser，如全站
                                // Cloudflare 或需执行 JS 才能拿到内容的站点）走 WebParser 抓取
                                // 渲染后的 HTML，否则 OkHttp 直连（带 per-request 超时）
                                WebParserConfig infoConfig = parser.getInfoConfig();
                                String reqUrl = request.url().toString();
                                Observable<String> htmlObs;
                                if (infoConfig.isUseWebParser()) {
                                    htmlObs = new WebParser(App.getAppContext(), reqUrl,
                                            request.headers(), "", infoConfig).getHtmlObservable();
                                } else {
                                    // 使用 per-request 超时控制（复用共享连接池）
                                    okhttp3.Call call = sharedClient.newCall(request);
                                    call.timeout().timeout(CHECK_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                                    htmlObs = Observable.fromCallable(() -> getResponseBody(call));
                                }

                                // 每个漫画检查更新整体限时（覆盖 OkHttp 直连与 WebParser 渲染路径）
                                return htmlObs
                                        .timeout(CHECK_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                        .map(html -> {
                                    String update = parser.parseCheck(html);
                                    Pair<Boolean, Integer> checkRes = new Pair<>(false, 0);
                                    if (update == null || update.isEmpty()) {
                                        checkRes = parser.checkUpdateByChapterCount(html, c);
                                    }
                                    if ((c.getUpdate() != null && update != null && !update.isEmpty() && !c.getUpdate().equals(update))
                                            || checkRes.first) {
                                        c.setFavorite(System.currentTimeMillis());
                                        c.setUpdate(update);
                                        if (checkRes.first) {
                                            c.setChapterCount(checkRes.second);
                                        }
                                        c.setHighlight(true);
                                        return new CheckUpdateEvent(c, true);  // 有更新
                                    }
                                    return new CheckUpdateEvent(c, false);  // 无更新
                                }).onErrorReturn(e -> new CheckUpdateEvent(c, false));  // 单本失败不中断整体，确保进度计数正确
                            } catch (Exception e) {
                                e.printStackTrace();
                                return Observable.just(new CheckUpdateEvent(c, false));  // 无更新，确保进度计数正确
                            }
                        }), 8  // 并发数：与 OkHttp maxRequestsPerHost(8) 对齐，避免线程空转与 WebView OOM
                );
    }

    /**
     * 使用已经构建好的 okhttp3.Call 来获取响应体（支持 per-request timeout）
     */
    public static String getResponseBody(okhttp3.Call call) throws NetworkErrorException {
        try {
            okhttp3.Response response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                byte[] bodybytes = response.body().bytes();
                String body = new String(bodybytes);
                java.util.regex.Matcher m = Pattern.compile("charset=([\\w\\-]+)").matcher(body);
                if (m.find()) {
                    body = new String(bodybytes, Objects.requireNonNull(m.group(1)));
                }
                return body;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new NetworkErrorException();
    }


    public static String getResponseBody(OkHttpClient client, Request request) throws NetworkErrorException {
        return getResponseBody(client, request, true);
    }

    public static void setForceRefresh(boolean force) {
        sForceRefresh = force;
    }

    private static String getResponseBody(OkHttpClient client, Request request, boolean retry) throws NetworkErrorException {
        String reqUrl = request.url().toString();
        // 优先检查 URL 级别的精准失效（不会被其他无关请求消费）
        if (sForceRefreshUrls.remove(reqUrl)) {
            request = request.newBuilder()
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build();
        }
        // 再检查全局标志（一次性，下拉刷新用）
        else if (sForceRefresh) {
            request = request.newBuilder()
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build();
            sForceRefresh = false;
        }
        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                byte[] bodybytes = response.body().bytes();
                String body = new String(bodybytes);
                Matcher m = Pattern.compile("charset=([\\w\\-]+)").matcher(body);
                if (m.find()) {
                    body = new String(bodybytes, Objects.requireNonNull(m.group(1)));
                }
                return body;
            } else if (retry)
                return getResponseBody(client, request, false);
        } catch (Exception e) {
            e.printStackTrace();
            if (retry)
                return getResponseBody(client, request, false);
        } catch (Error e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
        }
        throw new NetworkErrorException();
    }

    /**
     * 漫画检查更新结果包装类
     */
    public static class CheckUpdateEvent {
        public final Comic comic;
        public final boolean hasUpdate;

        public CheckUpdateEvent(Comic comic, boolean hasUpdate) {
            this.comic = comic;
            this.hasUpdate = hasUpdate;
        }
    }


    /**
     * 漫画抓取网络异常（如第三方源加载失败）。
     * 显式加 serialVersionUID：异常在 RxJava/RxBus 事件中流转，
     * 若对象被落盘/跨组件序列化后反序列化，缺少该字段会抛 InvalidClassException。
     */
    public static class NetworkErrorException extends Exception implements Serializable {
        private static final long serialVersionUID = 1L;
    }

}
