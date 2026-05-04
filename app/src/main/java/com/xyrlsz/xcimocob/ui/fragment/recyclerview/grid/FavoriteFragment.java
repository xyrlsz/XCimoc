package com.xyrlsz.xcimocob.ui.fragment.recyclerview.grid;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.FavoritePresenter;
import com.xyrlsz.xcimocob.service.CheckUpdateService;
import com.xyrlsz.xcimocob.ui.fragment.dialog.MessageDialogFragment;
import com.xyrlsz.xcimocob.ui.view.FavoriteView;
import com.xyrlsz.xcimocob.utils.HintUtils;

import java.util.Calendar;
import java.util.List;

/**
 * Created by Hiroshi on 2016/7/1.
 */
public class FavoriteFragment extends GridFragment implements FavoriteView {

    private static final int DIALOG_REQUEST_UPDATE = 1;
    private static final int DIALOG_REQUEST_INFO = 2;
    private static final int DIALOG_REQUEST_DELETE = 3;

    private static final int OPERATION_INFO = 0;
    private static final int OPERATION_DELETE = 1;

    private FavoritePresenter mPresenter;

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new FavoritePresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initView() {
        super.initView();
        mGridAdapter.setSymbol(true);
    }

    @Override
    protected void initData() {
        mPresenter.load();
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        // 从其他界面（如详情页）返回后刷新收藏列表数据
        if (mPresenter != null && isAdded()) {
            mPresenter.load();
        }
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        switch (requestCode) {
            case DIALOG_REQUEST_OPERATION:
                int index = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
                switch (index) {
                    case OPERATION_INFO:
                        showComicInfo(mPresenter.load(mSavedId), DIALOG_REQUEST_INFO);
                        break;
                    case OPERATION_DELETE:
                        MessageDialogFragment fragment = MessageDialogFragment.newInstance(R.string.dialog_confirm,
                                R.string.favorite_delete_confirm, true, DIALOG_REQUEST_DELETE);
                        fragment.setTargetFragment(this, 0);
                        fragment.show(requireActivity().getSupportFragmentManager(), null);
                        break;
                }
                break;
            case DIALOG_REQUEST_UPDATE:
                checkUpdate();
                HintUtils.showToast(getActivity(), R.string.favorite_check_update_doing);
                break;
            case DIALOG_REQUEST_DELETE:
                mPresenter.unfavoriteComic(mSavedId);
                HintUtils.showToast(getActivity(), R.string.common_execute_success);
                break;
        }
    }

    public void cancelAllHighlight() {
        mPresenter.cancelAllHighlight();
        mGridAdapter.cancelAllHighlight();
    }

    private void checkUpdate() {
        Intent intent = new Intent(requireActivity(), CheckUpdateService.class);
        requireActivity().startService(intent);
    }

    @Override
    protected void performActionButtonClick() {
        if (mGridAdapter.getDateSet().isEmpty()) {
            return;
        }
        MessageDialogFragment fragment = MessageDialogFragment.newInstance(R.string.dialog_confirm,
                R.string.favorite_check_update_confirm, true, DIALOG_REQUEST_UPDATE);
        fragment.setTargetFragment(this, 0);
        fragment.show(requireActivity().getSupportFragmentManager(), null);
    }

    @Override
    public void onComicLoadSuccess(List<Object> list) {
        super.onComicLoadSuccess(list);
        WifiManager manager =
                (WifiManager) requireActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager != null) {
            if (manager.isWifiEnabled() &&
                    mPreference.getBoolean(PreferenceManager.PREF_OTHER_CHECK_UPDATE, false)) {
                Calendar calendar = Calendar.getInstance();
                int day = calendar.get(Calendar.DAY_OF_YEAR);
                calendar.setTimeInMillis(mPreference.getNumber(PreferenceManager.PREF_OTHER_CHECK_UPDATE_LAST, 0).longValue());
                if (day != calendar.get(Calendar.DAY_OF_YEAR)) {
                    mPreference.putNumber(PreferenceManager.PREF_OTHER_CHECK_UPDATE_LAST, System.currentTimeMillis());
                    checkUpdate();
                }
            }
        }
    }

    @Override
    public void OnComicFavorite(MiniComic comic) {
        mGridAdapter.add(mGridAdapter.findFirstNotHighlight(), comic);
    }

    @Override
    public void OnComicRestore(List<Object> list) {
        mGridAdapter.addAll(mGridAdapter.findFirstNotHighlight(), list);
    }

    @Override
    public void OnComicUnFavorite(long id) {
        mGridAdapter.removeItemById(id);
    }

    @Override
    public void onComicCheckSuccess(MiniComic comic, int progress, int max) {
        if (comic != null) {
            mGridAdapter.remove(comic);
            mGridAdapter.add(0, comic);
        }
    }

    @Override
    public void onComicCheckFail() {
        HintUtils.showToast(getActivity(), R.string.favorite_check_update_fail);
    }

    @Override
    public void onComicCheckComplete() {
        HintUtils.showToast(getActivity(), R.string.favorite_check_update_done);
    }

    @Override
    public void onHighlightCancel(MiniComic comic) {
        mGridAdapter.moveItemTop(comic);
    }

    @Override
    public void onComicRead(MiniComic comic) {
        mGridAdapter.moveItemTop(comic);
    }

    @Override
    protected int getActionButtonRes() {
        return R.drawable.ic_sync_white_24dp;
    }

    @Override
    protected String[] getOperationItems() {
        return new String[]{getString(R.string.comic_info), getString(R.string.favorite_delete)};
    }
}
