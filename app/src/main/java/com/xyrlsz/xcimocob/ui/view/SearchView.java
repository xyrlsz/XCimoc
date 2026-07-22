package com.xyrlsz.xcimocob.ui.view;

import com.xyrlsz.xcimocob.component.DialogCaller;
import com.xyrlsz.xcimocob.model.SearchHistory;
import com.xyrlsz.xcimocob.model.Source;

import java.util.List;

/**
 * Created by Hiroshi on 2016/10/11.
 */

public interface SearchView extends BaseView, DialogCaller {

    void onSourceLoadSuccess(List<Source> list);

    void onSourceLoadFail();

    void onAutoCompleteLoadSuccess(List<String> list);

    void onSearchHistoryLoadSuccess(List<SearchHistory> list);

    void onSearchHistoryDeleteSuccess();

    void onSearchHistoryClearSuccess();

}
