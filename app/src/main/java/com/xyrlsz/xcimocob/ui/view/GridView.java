package com.xyrlsz.xcimocob.ui.view;

import com.xyrlsz.xcimocob.component.DialogCaller;
import com.xyrlsz.xcimocob.component.ThemeResponsive;

import java.util.List;

/**
 * Created by Hiroshi on 2016/9/30.
 */

public interface GridView extends BaseView, DialogCaller, ThemeResponsive {

    void onComicLoadSuccess(List<Object> list);

    void onComicLoadFail();

    void onExecuteFail();

    void filterByKeyword(String keyword);

    void filterByKeyword(String keyword, boolean isCompleted, boolean isNotCompleted);

    void filterByKeyword(String keyword, boolean isCompleted, boolean isNotCompleted, boolean hasUpdate);

    void cancelFilter();
}
