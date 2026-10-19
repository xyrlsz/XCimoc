package com.xyrlsz.xcimocob.ui.fragment.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.DialogCaller;

import java.util.Objects;

/**
 * Created by Hiroshi on 2016/10/15.
 */

public class EditorDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {

    private static final String EXTRA_DIALOG_MULTILINE = "cimoc.intent.extra.EXTRA_DIALOG_MULTILINE";

    protected EditText mEditText;

    public static EditorDialogFragment newInstance(int title, String content, int requestCode) {
        return newInstance(title, content, requestCode, false);
    }

    public static EditorDialogFragment newInstance(int title, String content, int requestCode,
                                                   boolean multiline) {
        EditorDialogFragment fragment = new EditorDialogFragment();
        Bundle bundle = createBundle(title, content, requestCode);
        bundle.putBoolean(EXTRA_DIALOG_MULTILINE, multiline);
        fragment.setArguments(bundle);
        return fragment;
    }

    protected static Bundle createBundle(int title, String content, int requestCode) {
        Bundle bundle = new Bundle();
        bundle.putInt(DialogCaller.EXTRA_DIALOG_TITLE, title);
        bundle.putString(DialogCaller.EXTRA_DIALOG_CONTENT, content);
        bundle.putInt(DialogCaller.EXTRA_DIALOG_REQUEST_CODE, requestCode);
        return bundle;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = requireActivity().getLayoutInflater().inflate(R.layout.dialog_editor, null);
        mEditText = view.findViewById(R.id.dialog_editor_text);
        String s = requireArguments().getString(DialogCaller.EXTRA_DIALOG_CONTENT);
        mEditText.setText(s);
        if (requireArguments().getBoolean(EXTRA_DIALOG_MULTILINE, false)) {
            mEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            mEditText.setMinLines(2);
            mEditText.setMaxLines(5);
            mEditText.setGravity(Gravity.TOP | Gravity.START);
        } else {
            mEditText.setSingleLine(true);
        }
        mEditText.setSelection(mEditText.getText().length());
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
        bundle.putString(DialogCaller.EXTRA_DIALOG_RESULT_VALUE, mEditText.getText().toString());
        DialogCaller target = (DialogCaller) (getTargetFragment() != null ? getTargetFragment() : getActivity());
        Objects.requireNonNull(target).onDialogResult(requestCode, bundle);
    }

}
