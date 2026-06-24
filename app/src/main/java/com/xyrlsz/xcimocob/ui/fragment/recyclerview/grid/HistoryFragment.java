package com.xyrlsz.xcimocob.ui.fragment.recyclerview.grid;

import android.os.Bundle;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.HistoryPresenter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.MessageDialogFragment;
import com.xyrlsz.xcimocob.ui.view.HistoryView;
import com.xyrlsz.xcimocob.utils.HintUtils;

import java.util.List;

/**
 * Created by Hiroshi on 2016/7/1.
 */
public class HistoryFragment extends GridFragment implements HistoryView {

    private static final int DIALOG_REQUEST_CLEAR = 1;
    private static final int DIALOG_REQUEST_INFO = 2;
    private static final int DIALOG_REQUEST_DELETE = 3;

    private static final int OPERATION_INFO = 0;
    private static final int OPERATION_DELETE = 1;

    private HistoryPresenter mPresenter;

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new HistoryPresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initData() {
        mPresenter.load();
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        // 从阅读器返回后刷新历史记录
        if (mPresenter != null && isAdded()) {
            mPresenter.load();
        }
    }

    @Override
    protected void performActionButtonClick() {
        if (mGridAdapter.getDateSet().isEmpty()) {
            return;
        }
        MessageDialogFragment fragment = MessageDialogFragment.newInstance(R.string.dialog_confirm,
                R.string.history_clear_confirm, true, DIALOG_REQUEST_CLEAR);
        fragment.setTargetFragment(this, 0);
        fragment.show(requireActivity().getSupportFragmentManager(), null);
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
                                R.string.history_delete_confirm, true, DIALOG_REQUEST_DELETE);
                        fragment.setTargetFragment(this, 0);
                        fragment.show(requireActivity().getSupportFragmentManager(), null);
                        break;
                    default:
                        break;
                }
                break;
            case DIALOG_REQUEST_CLEAR:
                showProgressDialog();
                mPresenter.clear();
                break;
            case DIALOG_REQUEST_DELETE:
                showProgressDialog();
                mPresenter.delete(mSavedId);
                break;
            default:
                break;
        }
    }

    @Override
    public void onHistoryClearSuccess() {
        mGridAdapter.clear();
        HintUtils.showToast(getActivity(), R.string.common_execute_success);
        hideProgressDialog();
    }

    @Override
    public void onHistoryDelete(long id) {
        mGridAdapter.removeItemById(mSavedId);
        HintUtils.showToast(getActivity(), R.string.common_execute_success);
        hideProgressDialog();
    }

    @Override
    public void OnComicRestore(List<Object> list) {
        mGridAdapter.addAll(0, list);
    }

    @Override
    public void onItemUpdate(MiniComic comic) {
        mGridAdapter.remove(comic);
        mGridAdapter.add(0, comic);
    }

    @Override
    protected int getActionButtonRes() {
        return R.drawable.ic_delete_white_24dp;
    }


    @Override
    protected String[] getOperationItems() {
        return new String[]{getString(R.string.comic_info), getString(R.string.history_delete)};
    }
}
