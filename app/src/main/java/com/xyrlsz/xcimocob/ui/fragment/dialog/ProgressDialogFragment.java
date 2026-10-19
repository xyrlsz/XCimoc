package com.xyrlsz.xcimocob.ui.fragment.dialog;

import android.graphics.PorterDuff;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

import java.util.Objects;


import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Created by Hiroshi on 2016/10/14.
 */

public class ProgressDialogFragment extends DialogFragment {

    ProgressBar mProgressBar;
    TextView mTextView;
    private CompositeDisposable mCompositeSubscription;

    public static ProgressDialogFragment newInstance() {
        return new ProgressDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_progress, container, false);
        mProgressBar = view.findViewById(R.id.dialog_progress_bar);
        mTextView = view.findViewById(R.id.dialog_progress_text);
        Objects.requireNonNull(getDialog()).requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(false);
        int resId = ThemeUtils.getResourceId(requireActivity(), R.attr.colorAccent);
        mProgressBar.getIndeterminateDrawable().setColorFilter(ContextCompat.getColor(getActivity(), resId), PorterDuff.Mode.SRC_ATOP);
        mCompositeSubscription = new CompositeDisposable();
        Disposable disposable = RxBus.getInstance().toObservable(RxEvent.EVENT_DIALOG_PROGRESS).subscribe(new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                mTextView.setText((String) rxEvent.getData());
            }
        });
        mCompositeSubscription.add(disposable);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // 进度框内容很少，覆盖主题里的对话框最小宽度，保持紧凑
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onDestroyView() {
        if (mCompositeSubscription != null) {
            mCompositeSubscription.dispose();
        }
        super.onDestroyView();
    }

}
