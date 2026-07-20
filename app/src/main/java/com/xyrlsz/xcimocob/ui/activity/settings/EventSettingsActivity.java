package com.xyrlsz.xcimocob.ui.activity.settings;

import static com.xyrlsz.xcimocob.manager.PreferenceManager.READER_ORIENTATION_AUTO;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.DialogCaller;
import com.xyrlsz.xcimocob.global.ClickEvents;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.ui.activity.BaseActivity;
import com.xyrlsz.xcimocob.ui.fragment.dialog.ChoiceDialogFragment;

import java.util.ArrayList;
import java.util.List;



/**
 * Created by Hiroshi on 2016/10/9.
 */

public class EventSettingsActivity extends BaseActivity implements DialogCaller {

    private final float thredhold = 0.3f;
    List<Button> mButtonList;
    FrameLayout mLayoutView;
    private int[] mChoiceArray;

    @Override
    protected void initViewById() {
        super.initViewById();
        mLayoutView = findViewById(R.id.event_root);
        mButtonList = new ArrayList<>();
        mButtonList.add(findViewById(R.id.event_left));
        mButtonList.add(findViewById(R.id.event_top));
        mButtonList.add(findViewById(R.id.event_middle));
        mButtonList.add(findViewById(R.id.event_bottom));
        mButtonList.add(findViewById(R.id.event_right));
    }
    private String[] mKeyArray;
    private boolean isLong;
    private boolean JoyLock[] = {false, false};
    private int JoyEvent[] = {7, 8};

    public static Intent createIntent(Context context, boolean isLong, int orientation, boolean isStream) {
        Intent intent = new Intent(context, EventSettingsActivity.class);
        intent.putExtra(Extra.EXTRA_IS_LONG, isLong);
        intent.putExtra(Extra.EXTRA_IS_PORTRAIT, orientation);
        intent.putExtra(Extra.EXTRA_IS_STREAM, isStream);
        return intent;
    }

    @Override
    protected void initTheme() {
        super.initTheme();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
        final int oArray[] = {ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED};
        int value = oArray[getIntent().getIntExtra(Extra.EXTRA_IS_PORTRAIT, READER_ORIENTATION_AUTO)];
        setRequestedOrientation(value);
    }

    @Override
    protected void initView() {
        boolean isStream = getIntent().getBooleanExtra(Extra.EXTRA_IS_STREAM, false);
        isLong = getIntent().getBooleanExtra(Extra.EXTRA_IS_LONG, false);
        if (isStream) {
            mKeyArray = isLong ? ClickEvents.getStreamLongClickEvents() : ClickEvents.getStreamClickEvents();
            mChoiceArray = isLong ? ClickEvents.getStreamLongClickEventChoice(mPreference) :
                    ClickEvents.getStreamClickEventChoice(mPreference);
        } else {
            mKeyArray = isLong ? ClickEvents.getPageLongClickEvents() : ClickEvents.getPageClickEvents();
            mChoiceArray = isLong ? ClickEvents.getPageLongClickEventChoice(mPreference) :
                    ClickEvents.getPageClickEventChoice(mPreference);
        }

        for (int i = 0; i != 5; ++i) {
            final int index = i;
            mButtonList.get(i).setText(ClickEvents.getEventTitle(this, mChoiceArray[i]));
            mButtonList.get(i).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEventList(index);
                }
            });
        }
        ViewCompat.setOnApplyWindowInsetsListener(mLayoutView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    systemBars.bottom
            );
            return insets;
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isLong) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    showEventList(5);
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    showEventList(6);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                case KeyEvent.KEYCODE_BUTTON_L2:
                    showEventList(7);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                case KeyEvent.KEYCODE_BUTTON_R2:
                    showEventList(8);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                    showEventList(14);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                    showEventList(13);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_X:
                    showEventList(15);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    showEventList(16);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    showEventList(9);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    showEventList(10);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    showEventList(11);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    showEventList(12);
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {

        // Check that the event came from a game controller
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            // Process all historical movement samples in the batch
            final int historySize = event.getHistorySize();

            // Process the movements starting from the
            // earliest historical position in the batch
            for (int i = 0; i < historySize; i++) {
                // Process the event at historical position i
                processJoystickInput(event, i);
            }

            // Process the current movement sample in the batch (position -1)
            processJoystickInput(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void checkKey(float val, ClickEvents.JoyLocks joy) {
        //unlock
        if (JoyLock[joy.ordinal()] && val < this.thredhold) {
            JoyLock[joy.ordinal()] = false;
        }
        //lock
        if (!JoyLock[joy.ordinal()] && val > this.thredhold) {
            JoyLock[joy.ordinal()] = true;
            showEventList(JoyEvent[joy.ordinal()]);
        }
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {
        checkKey(event.getAxisValue(MotionEvent.AXIS_GAS), ClickEvents.JoyLocks.RT);
        checkKey(event.getAxisValue(MotionEvent.AXIS_BRAKE), ClickEvents.JoyLocks.LT);
    }

    private void showEventList(int index) {
        ChoiceDialogFragment fragment = ChoiceDialogFragment.newInstance(R.string.event_select,
                ClickEvents.getEventTitleArray(this), mChoiceArray[index], index);
        fragment.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        int index = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
        mChoiceArray[requestCode] = index;
        mPreference.putNumber(mKeyArray[requestCode], index);
        if (requestCode < 5) {
            mButtonList.get(requestCode).setText(ClickEvents.getEventTitle(this, index));
        }
    }

    @Override
    protected View getLayoutView() {
        return mLayoutView;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_event;
    }

}
