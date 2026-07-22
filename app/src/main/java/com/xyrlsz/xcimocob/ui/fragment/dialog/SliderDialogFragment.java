package com.xyrlsz.xcimocob.ui.fragment.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import android.view.View;

import com.google.android.material.slider.Slider;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.DialogCaller;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

import java.util.Objects;


/**
 * Created by Hiroshi on 2016/10/16.
 * Replaced DiscreteSeekBar with Material Slider.
 */

public class SliderDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {

    private Slider mSeekBar;

    public static SliderDialogFragment newInstance(int title, int min, int max, int progress, int requestCode) {
        SliderDialogFragment fragment = new SliderDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(DialogCaller.EXTRA_DIALOG_TITLE, title);
        bundle.putIntArray(DialogCaller.EXTRA_DIALOG_ITEMS, new int[]{min, max, progress});
        bundle.putInt(DialogCaller.EXTRA_DIALOG_REQUEST_CODE, requestCode);
        fragment.setArguments(bundle);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = requireActivity().getLayoutInflater().inflate(R.layout.dialog_slider, null);
        int[] item = requireArguments().getIntArray(DialogCaller.EXTRA_DIALOG_ITEMS);
        mSeekBar = view.findViewById(R.id.dialog_slider_bar);
        mSeekBar.setValueFrom(Objects.requireNonNull(item)[0]);
        mSeekBar.setValueTo(item[1]);
        mSeekBar.setValue(item[2]);
        int primaryDarkResId = ThemeUtils.getResourceId(requireActivity(), R.attr.colorPrimaryDark);
        int primaryResId = ThemeUtils.getResourceId(requireActivity(), R.attr.colorPrimary);
        if (primaryDarkResId != 0) {
            int primaryDarkColor = requireActivity().getColor(primaryDarkResId);
            mSeekBar.setTrackActiveTintList(ColorStateList.valueOf(primaryDarkColor));
            mSeekBar.setThumbTintList(ColorStateList.valueOf(primaryDarkColor));
            mSeekBar.setHaloTintList(ColorStateList.valueOf(primaryDarkColor));
        }
        if (primaryResId != 0) {
            int primaryColor = requireActivity().getColor(primaryResId);
            mSeekBar.setTrackInactiveTintList(ColorStateList.valueOf(primaryColor));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getArguments().getInt(DialogCaller.EXTRA_DIALOG_TITLE))
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, this);
        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int which) {
        int requestCode = requireArguments().getInt(DialogCaller.EXTRA_DIALOG_REQUEST_CODE);
        Bundle bundle = new Bundle();
        bundle.putInt(DialogCaller.EXTRA_DIALOG_RESULT_VALUE, Math.round(mSeekBar.getValue()));
        DialogCaller target = (DialogCaller) (getTargetFragment() != null ? getTargetFragment() : getActivity());
        Objects.requireNonNull(target).onDialogResult(requestCode, bundle);
    }

}
