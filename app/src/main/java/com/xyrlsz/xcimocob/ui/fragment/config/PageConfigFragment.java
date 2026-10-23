package com.xyrlsz.xcimocob.ui.fragment.config;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import android.view.View;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.DialogCaller;
import com.xyrlsz.xcimocob.global.ClickEvents;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.ui.activity.settings.EventSettingsActivity;
import com.xyrlsz.xcimocob.ui.fragment.BaseFragment;
import com.xyrlsz.xcimocob.ui.fragment.dialog.ChoiceDialogFragment;
import com.xyrlsz.xcimocob.ui.widget.preference.CheckBoxPreference;
import com.xyrlsz.xcimocob.ui.widget.preference.ChoicePreference;
import com.xyrlsz.xcimocob.ui.widget.preference.SliderPreference;



/**
 * Created by Hiroshi on 2016/10/13.
 */

public class PageConfigFragment extends BaseFragment implements DialogCaller {

    private static final int DIALOG_REQUEST_ORIENTATION = 0;
    private static final int DIALOG_REQUEST_TURN = 1;
    private static final int DIALOG_REQUEST_TRIGGER = 2;
    private static final int DIALOG_REQUEST_OPERATION = 3;

    private static final int OPERATION_VOLUME_UP = 0;
    private static final int OPERATION_VOLUME_DOWN = 1;

    CheckBoxPreference mReaderBanTurn;
    CheckBoxPreference mReaderQuickTurn;
    CheckBoxPreference mReaderDualPage;
    CheckBoxPreference mReaderDualPageRtl;
    CheckBoxPreference mReaderDualPageNoGap;
    ChoicePreference mReaderOrientation;
    ChoicePreference mReaderTurn;
    SliderPreference mReaderTrigger;

//    @BindView(R.id.settings_reader_volume_click_event) View mReaderVolumeEvent;

    @Override
    protected void initView() {
        mReaderBanTurn = mRootView.findViewById(R.id.settings_reader_ban_turn);
        mReaderQuickTurn = mRootView.findViewById(R.id.settings_reader_quick_turn);
        mReaderDualPage = mRootView.findViewById(R.id.settings_reader_dual_page);
        mReaderDualPageRtl = mRootView.findViewById(R.id.settings_reader_dual_page_rtl);
        mReaderDualPageNoGap = mRootView.findViewById(R.id.settings_reader_dual_page_no_gap);
        mReaderOrientation = mRootView.findViewById(R.id.settings_reader_orientation);
        mReaderTurn = mRootView.findViewById(R.id.settings_reader_turn);
        mReaderTrigger = mRootView.findViewById(R.id.settings_reader_trigger);
        mRootView.findViewById(R.id.settings_reader_click_event).setOnClickListener(v -> onReaderEventClick(v));
        mRootView.findViewById(R.id.settings_reader_long_click_event).setOnClickListener(v -> onReaderEventClick(v));
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//            mReaderVolumeEvent.setVisibility(View.VISIBLE);
//        } else {
//            mReaderVolumeEvent.setVisibility(View.GONE);
//        }
        mReaderBanTurn.bindPreference(PreferenceManager.PREF_READER_PAGE_BAN_TURN, false);
        mReaderQuickTurn.bindPreference(PreferenceManager.PREF_READER_PAGE_QUICK_TURN, false);
        mReaderDualPage.bindPreference(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE, false);
        mReaderDualPageRtl.bindPreference(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_RTL, false);
        mReaderDualPageNoGap.bindPreference(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_NO_GAP, false);
        // 未开启左右双页时，排列顺序/无间隔设置无意义
        mReaderDualPage.setOnCheckedChangeListener((buttonView, isChecked) -> updateDualPageSubVisibility());
        updateDualPageSubVisibility();
        mReaderOrientation.bindPreference(requireActivity().getSupportFragmentManager(), this, PreferenceManager.PREF_READER_PAGE_ORIENTATION,
                PreferenceManager.READER_ORIENTATION_AUTO, R.array.reader_orientation_items, DIALOG_REQUEST_ORIENTATION);
        mReaderTurn.bindPreference(requireActivity().getSupportFragmentManager(), this, PreferenceManager.PREF_READER_PAGE_TURN,
                PreferenceManager.READER_TURN_LTR, R.array.reader_turn_items, DIALOG_REQUEST_TURN);
        mReaderTrigger.bindPreference(requireActivity().getSupportFragmentManager(), this, PreferenceManager.PREF_READER_PAGE_TRIGGER, 10,
                R.string.settings_reader_trigger, DIALOG_REQUEST_TRIGGER);
    }

    private void updateDualPageSubVisibility() {
        int visibility = mReaderDualPage.isChecked() ? View.VISIBLE : View.GONE;
        mReaderDualPageRtl.setVisibility(visibility);
        mReaderDualPageNoGap.setVisibility(visibility);
    }

    void onReaderEventClick(View view) {
        boolean isLong = view.getId() == R.id.settings_reader_long_click_event;
        Intent intent = EventSettingsActivity.createIntent(getActivity(), isLong,
                mReaderOrientation.getValue(), false);
        startActivity(intent);
    }

//    @OnClick(R.id.settings_reader_volume_click_event)
//    void onReaderVolumeEventClick() {
//        String[] items = {"音量上键", "音量下键"};
//        ItemDialogFragment fragment = ItemDialogFragment.newInstance(R.string.common_operation_select,
//                items, DIALOG_REQUEST_OPERATION);
//        fragment.setTargetFragment(this, 0);
//        fragment.show(getSupportFragmentManager(), null);
//    }

    private void showEventList(int index) {
        int[] mChoiceArray = ClickEvents.getPageClickEventChoice(mPreference);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Context context = this.getContext();
            ChoiceDialogFragment fragment = ChoiceDialogFragment.newInstance(R.string.event_select,
                    ClickEvents.getEventTitleArray(context), mChoiceArray[index], index);
            fragment.show(requireActivity().getSupportFragmentManager(), null);
        }
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        switch (requestCode) {
            case DIALOG_REQUEST_ORIENTATION:
                mReaderOrientation.setValue(bundle.getInt(EXTRA_DIALOG_RESULT_INDEX));
                break;
            case DIALOG_REQUEST_TURN:
                mReaderTurn.setValue(bundle.getInt(EXTRA_DIALOG_RESULT_INDEX));
                break;
            case DIALOG_REQUEST_TRIGGER:
                mReaderTrigger.setValue(bundle.getInt(EXTRA_DIALOG_RESULT_VALUE));
                break;
            case DIALOG_REQUEST_OPERATION:
                int index = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
                switch (index) {
                    case OPERATION_VOLUME_UP:
                        showEventList(5);
                        break;
                    case OPERATION_VOLUME_DOWN:
                        showEventList(6);
                        break;
                }
                break;
        }
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_page_config;
    }

}
