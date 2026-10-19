package com.xyrlsz.xcimocob.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.utils.StringUtils;



public class ComicFilterDialog extends Dialog {
    EditText keywordEditText;
    CheckBox isCompletedCheckBox;
    CheckBox isNotCompletedCheckBox;
    CheckBox hasUpdateCheckBox;
    Button commitButton;
    Button cancelButton;

    public ComicFilterDialog(Context context, int themeResId, SubmitCallBack callBack) {
        super(context, themeResId);
        init(context, callBack);
    }

    private void init(Context context, SubmitCallBack callBack) {
        this.setContentView(R.layout.dialog_comic_filter);
        // Find views by ID
        keywordEditText = findViewById(R.id.et_keyword);
        isCompletedCheckBox = findViewById(R.id.cb_is_completed);
        isNotCompletedCheckBox = findViewById(R.id.cb_is_not_completed);
        hasUpdateCheckBox = findViewById(R.id.cb_has_update);
        commitButton = findViewById(R.id.btn_commit);
        cancelButton = findViewById(R.id.btn_cancel);
        isCompletedCheckBox.setChecked(true);
        isNotCompletedCheckBox.setChecked(true);
        // Set up click listeners
        commitButton.setOnClickListener(v -> {
            String keyword = keywordEditText.getText().toString().trim();
            boolean isCompleted = isCompletedCheckBox.isChecked();
            boolean isNotCompleted = isNotCompletedCheckBox.isChecked();
            boolean hasUpdate = hasUpdateCheckBox.isChecked();
            if (StringUtils.isEmpty(keyword) && !isCompleted && !isNotCompleted && !hasUpdate) {
                callBack.OnClickCancel();
                dismiss();
            } else if (!isCompleted && !isNotCompleted) {
                isCompleted = true;
                isNotCompleted = true;
            }
            callBack.OnClickCommit(keyword, isCompleted, isNotCompleted, hasUpdate);
            dismiss();
        });
        cancelButton.setOnClickListener(v -> {
            callBack.OnClickCancel();
            dismiss();
        });
    }


    public interface SubmitCallBack {
        void OnClickCommit(String keyword, boolean isCompleted, boolean isNotCompleted, boolean hasUpdate);

        void OnClickCancel();
    }

}