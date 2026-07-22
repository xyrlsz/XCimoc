package com.xyrlsz.xcimocob.presenter;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.core.Update;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.ui.view.MainView;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/9/21.
 */

public class MainPresenter extends BasePresenter<MainView> {

    private ComicManager mComicManager;
    private static final String APP_VERSIONNAME = "versionName";
    private static final String APP_VERSIONCODE = "versionCode";
    private static final String APP_CONTENT = "content";
    private static final String APP_MD5 = "md5";
    private static final String APP_URL= "url";

    private static final String SOURCE_URL = "https://raw.githubusercontent.com/Haleydu/update/master/sourceBaseUrl.json";

    @Override
    protected void onViewAttach() {
        mComicManager = ComicManager.getInstance(mBaseView);
    }

    @Override
    protected void initSubscription() {
        addSubscription(RxEvent.EVENT_COMIC_READ, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                MiniComic comic = (MiniComic) rxEvent.getData();
                mBaseView.onLastChange(comic.getId(), comic.getSource(), comic.getCid(),
                        comic.getTitle(), comic.getCover());
            }
        });
    }

    public boolean checkLocal(long id) {
        Comic comic = mComicManager.load(id);
        return comic != null && comic.getLocal();
    }

    public void loadLast() {
        mCompositeSubscription.add(mComicManager.loadLast()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Comic>() {
                    @Override
                    public void accept(Comic comic) {
                        if (comic != null) {
                            mBaseView.onLastLoadSuccess(comic.getId(), comic.getSource(), comic.getCid(), comic.getTitle(), comic.getCover());
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onLastLoadFail();
                    }
                }));
    }

    public void checkUpdate(final String version) {
        mCompositeSubscription.add(Update.check()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        if(s.compareTo(version)>0){
                            mBaseView.onUpdateReady();
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                    }
                }));
    }

//    public void checkGiteeUpdate(final int appVersionCode) {
//        mCompositeSubscription.add(Update.checkGitee()
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(new Action1<String>() {
//                    @Override
//                    public void call(String json) {
//                        try {
//                            String versionName = new JSONObject(json).getString(APP_VERSIONNAME);
//                            String versionCodeString = new JSONObject(json).getString(APP_VERSIONCODE);
//                            int ServerAppVersionCode = Integer.parseInt(versionCodeString);
//                            String content = new JSONObject(json).getString(APP_CONTENT);
//                            String md5 = new JSONObject(json).getString(APP_MD5);
//                            String url = new JSONObject(json).getString(APP_URL);
//                            if (appVersionCode < ServerAppVersionCode) {
//                                mBaseView.onUpdateReady(versionName,content,url,ServerAppVersionCode,md5);
//                            }
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }, new Action1<Throwable>() {
//                    @Override
//                    public void call(Throwable throwable) {
//                    }
//                }));
//    }


    public void getSourceBaseUrl() {
        mCompositeSubscription.add(
                Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<String>) emitter -> {
                        OkHttpClient client = App.getHttpClient();
                        Request request = new Request.Builder().url(SOURCE_URL).build();
                        Response response = null;
                        boolean checkSuccess = false;
                        try {
                            response = client.newCall(request).execute();
                            if (response.isSuccessful()) {
                                String json = response.body().string();
                                emitter.onNext(json);
                                emitter.onComplete();
                                checkSuccess = true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (response != null) {
                                response.close();
                            }
                        }
                        if (!checkSuccess && !emitter.isDisposed()) {
                            emitter.tryOnError(new Exception("Main check update failed"));
                        }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Consumer<String>() {
                            @Override
                            public void accept(String json) {
                                try {
                                    JSONObject obj = new JSONObject(json);
                                    String HHAAZZ = obj.getString("HHAAZZ");
                                    if (!HHAAZZ.equals(App.getPreferenceManager().getString(PreferenceManager.PREF_HHAAZZ_BASEURL, ""))){
                                        App.getPreferenceManager().putString(PreferenceManager.PREF_HHAAZZ_BASEURL, HHAAZZ);
                                    }
                                    if (obj.has("sw")) {
                                        String sw = obj.getString("sw");
                                        if (!sw.equals(App.getPreferenceManager().getString(PreferenceManager.PREF_HHAAZZ_SW, ""))){
                                            App.getPreferenceManager().putString(PreferenceManager.PREF_HHAAZZ_SW, sw);
                                        }
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) {
                            }
                        }));
    }
}
