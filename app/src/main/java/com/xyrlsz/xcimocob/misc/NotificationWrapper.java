package com.xyrlsz.xcimocob.misc;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import androidx.annotation.DrawableRes;
import androidx.core.app.NotificationCompat;

import com.xyrlsz.xcimocob.R;

/**
 * Created by Hiroshi on 2018/2/11.
 */

public class NotificationWrapper {

    private NotificationManager mManager;
    private NotificationCompat.Builder mBuilder;
    private int mId;

    public NotificationWrapper(Context context, String id, @DrawableRes int icon, boolean ongoing) {
        String title = context.getString(R.string.app_name);
        mManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mManager == null) {
            Log.e("NotificationWrapper", "NotificationManager is null");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(id, id, NotificationManager.IMPORTANCE_MIN);
                channel.setShowBadge(false);
                mManager.createNotificationChannel(channel);
            } catch (Exception e) {
                Log.e("NotificationWrapper", "create channel failed", e);
            }
        }
        mBuilder = new NotificationCompat.Builder(context, id);
        mBuilder.setContentTitle(title)
                .setSmallIcon(icon)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), icon))
                .setOngoing(ongoing);
        mId = id.hashCode();
    }

    public android.app.Notification getNotification() {
        return mBuilder.build();
    }

    public void post(int progress, int max) {
        if (mManager == null) {
            Log.e("NotificationWrapper", "post skipped: mManager is null");
            return;
        }
        try {
            mBuilder.setProgress(max, progress, false);
            mManager.notify(mId, mBuilder.build());
        } catch (Exception e) {
            Log.e("NotificationWrapper", "post error", e);
        }
    }

    public void post(String content, int progress, int max) {
        mBuilder.setContentText(content).setTicker(content);
        post(progress, max);
    }

    public void post(String content, boolean ongoing) {
        mBuilder.setOngoing(ongoing);
        post(content, 0, 0);
    }

    /**
     * 显示不定进度条（循环动画），适用于无法确定具体进度的后台任务
     */
    public void postIndeterminate(String content, boolean ongoing) {
        if (mManager == null) {
            Log.e("NotificationWrapper", "postIndeterminate skipped: mManager is null");
            return;
        }
        try {
            mBuilder.setContentText(content).setTicker(content);
            mBuilder.setOngoing(ongoing);
            mBuilder.setProgress(0, 0, true);
            mManager.notify(mId, mBuilder.build());
        } catch (Exception e) {
            Log.e("NotificationWrapper", "postIndeterminate error", e);
        }
    }

    public void cancel() {
        if (mManager == null) {
            Log.e("NotificationWrapper", "cancel skipped: mManager is null");
            return;
        }
        try {
            mManager.cancel(mId);
        } catch (Exception e) {
            Log.e("NotificationWrapper", "cancel failed", e);
        }
    }

}
