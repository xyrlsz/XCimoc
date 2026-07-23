package com.xyrlsz.xcimocob.ui.widget;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

public class InputDialog {
    public static AlertDialog getInputDialog(Context context, String title, String hint, String positive, String negative, OnItemClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        final EditText input = new EditText(context);
        input.setHint(hint);
        builder.setView(input);
        builder.setPositiveButton(positive, (dialog, which) -> {
            String userInput = input.getText().toString();
            listener.onPositiveClick(dialog, which, userInput);
        });
        builder.setNegativeButton(negative, (dialog, which) -> listener.onNegativeClick(dialog, which));
        return builder.create();
    }

    public interface OnItemClickListener {
        void onPositiveClick(DialogInterface d, int which, String input);

        void onNegativeClick(DialogInterface d, int which);
    }


}
