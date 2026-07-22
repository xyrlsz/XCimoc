package com.xyrlsz.xcimocob.ui.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.misc.Switcher;
import com.xyrlsz.xcimocob.model.SearchHistory;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.SearchPresenter;
import com.xyrlsz.xcimocob.ui.adapter.AutoCompleteAdapter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.MultiAdpaterDialogFragment;
import com.xyrlsz.xcimocob.ui.view.SearchView;
import com.xyrlsz.xcimocob.utils.CollectionUtils;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;



/**
 * Created by Hiroshi on 2016/10/11.
 */

public class SearchActivity extends BackActivity implements SearchView, TextView.OnEditorActionListener {

    public static final int SEARCH_TITLE = 0;
    public static final int SEARCH_AUTHOR = 1;
    private final static int DIALOG_REQUEST_SOURCE = 0;
    AppCompatAutoCompleteTextView mEditText;
    FloatingActionButton mActionButton;
    AppCompatCheckBox mStrictCheckBox;
    AppCompatCheckBox mSTSameCheckBox;
    Spinner mTypeSpinner;
    private View mLayoutView;
    private ArrayAdapter<String> mArrayAdapter;

    @Override
    protected void initViewById() {
        super.initViewById();
        mEditText = findViewById(R.id.search_keyword_input);
        mActionButton = findViewById(R.id.search_action_button);
        mStrictCheckBox = findViewById(R.id.search_strict_checkbox);
        mSTSameCheckBox = findViewById(R.id.search_STSame_checkbox);
        mTypeSpinner = findViewById(R.id.search_type_spinner);
        mLayoutView = findViewById(R.id.search_root);
        mHistoryContainer = findViewById(R.id.search_history_container);
        mHistoryClear = findViewById(R.id.search_history_clear);
        mHistoryLayout = findViewById(R.id.search_history_layout);
        mInputClear = findViewById(R.id.search_input_clear);
    }

    private SearchPresenter mPresenter;
    private List<Switcher<Source>> mSourceList;
    private boolean mAutoComplete;

    private ChipGroup mHistoryContainer;
    private TextView mHistoryClear;
    private View mHistoryLayout;
    private android.widget.ImageButton mInputClear;

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new SearchPresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initView() {
        mAutoComplete = mPreference.getBoolean(PreferenceManager.PREF_SEARCH_AUTO_COMPLETE, false);
        mEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (mActionButton != null && !mActionButton.isShown()) {
                    mActionButton.show();
                }
            }
        });
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                mEditText.setError(null);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 控制清空按钮显隐
                mInputClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (mAutoComplete) {
                    String keyword = mEditText.getText().toString();
                    if (!StringUtils.isEmpty(keyword)) {
                        mPresenter.loadAutoComplete(keyword);
                    }
                }
            }
        });
        mEditText.setOnEditorActionListener(this);
        if (mAutoComplete) {
            mArrayAdapter = new AutoCompleteAdapter(this);
            mEditText.setAdapter(mArrayAdapter);
        }
        String[] searchTypes = getResources().getStringArray(R.array.search_type_items);
        mTypeSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.custom_spinner_item, searchTypes));
        mTypeSpinner.setSelection(SEARCH_TITLE);
        findViewById(R.id.search_action_button).setOnClickListener(v -> onSearchButtonClick());
        mInputClear.setOnClickListener(v -> mEditText.setText(""));
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

        // 初始化搜索历史 — 清空按钮
        mHistoryClear.setOnClickListener(v -> mPresenter.clearSearchHistory());
    }

    @Override
    protected void initData() {
        mSourceList = new ArrayList<>();
        mPresenter.loadSource();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.loadSearchHistory();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int __id = item.getItemId();
        if (__id == R.id.search_menu_source) {
                if (!mSourceList.isEmpty()) {
                    int size = mSourceList.size();
                    String[] arr1 = new String[size];
                    boolean[] arr2 = new boolean[size];
                    for (int i = 0; i < size; ++i) {
                        arr1[i] = mSourceList.get(i).getElement().getTitle();
                        arr2[i] = mSourceList.get(i).isEnable();
                    }
                    MultiAdpaterDialogFragment fragment =
                            MultiAdpaterDialogFragment.newInstance(R.string.search_source_select, arr1, arr2, DIALOG_REQUEST_SOURCE);
                    fragment.show(getSupportFragmentManager(), null);
                }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        switch (requestCode) {
            case DIALOG_REQUEST_SOURCE:
                boolean[] check = bundle.getBooleanArray(EXTRA_DIALOG_RESULT_VALUE);
                if (check != null) {
                    int size = mSourceList.size();
                    for (int i = 0; i < size; ++i) {
                        mSourceList.get(i).setEnable(check[i]);
                    }
                }
                break;
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            mActionButton.performClick();
            return true;
        }
        return false;
    }

    void onSearchButtonClick() {
        String keyword = mEditText.getText().toString().trim();
        boolean strictSearch = mStrictCheckBox.isChecked();
        boolean stSame = mSTSameCheckBox.isChecked();
        if (StringUtils.isEmpty(keyword)) {
            mEditText.setError(getString(R.string.search_keyword_empty));
        } else {
            ArrayList<Integer> list = new ArrayList<>();
            for (Switcher<Source> switcher : mSourceList) {
                if (switcher.isEnable()) {
                    list.add(switcher.getElement().getType());
                }
            }
            if (list.isEmpty()) {
                HintUtils.showToast(this, R.string.search_source_none);
            } else {
                // 保存搜索历史
                mPresenter.saveSearchHistory(keyword);
                // 重新加载历史列表
                mPresenter.loadSearchHistory();
                startActivity(ResultActivity.createIntent(this, keyword, strictSearch, stSame,
                        CollectionUtils.unbox(list), ResultActivity.LAUNCH_MODE_SEARCH, mTypeSpinner.getSelectedItemPosition()));
            }
        }
    }

    @Override
    public void onAutoCompleteLoadSuccess(List<String> list) {
        if (mArrayAdapter != null) {
            mArrayAdapter.clear();
            mArrayAdapter.addAll(list);
        }
    }

    @Override
    public void onSourceLoadSuccess(List<Source> list) {
        hideProgressBar();
        for (Source source : list) {
            mSourceList.add(new Switcher<>(source, true));
        }
    }

    @Override
    public void onSourceLoadFail() {
        hideProgressBar();
        HintUtils.showToast(this, R.string.search_source_load_fail);
    }

    @Override
    public void onSearchHistoryLoadSuccess(List<SearchHistory> list) {
        mHistoryContainer.removeAllViews();
        if (list == null || list.isEmpty()) {
            mHistoryContainer.setVisibility(View.GONE);
            mHistoryClear.setVisibility(View.GONE);
        } else {
            for (SearchHistory history : list) {
                mHistoryContainer.addView(createHistoryChip(history));
            }
            mHistoryContainer.setVisibility(View.VISIBLE);
            mHistoryClear.setVisibility(View.VISIBLE);
        }
    }

    private Chip createHistoryChip(SearchHistory history) {
        Chip chip = new Chip(this);
        chip.setText(history.getKeyword());
        chip.setTextSize(13);
        chip.setCheckable(false);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setCloseIconVisible(true);
        chip.setCloseIconSize(16 * getResources().getDisplayMetrics().density);
        chip.setChipCornerRadius(16 * getResources().getDisplayMetrics().density);
        chip.setChipMinHeight(32 * getResources().getDisplayMetrics().density);
        chip.setChipStrokeWidth(0);
        // 点击关键词 → 填入搜索框并搜索
        chip.setOnClickListener(v -> {
            mEditText.setText(history.getKeyword());
            mEditText.setSelection(history.getKeyword().length());
            mActionButton.performClick();
        });
        // 点击 × → 删除单条历史
        chip.setOnCloseIconClickListener(v -> {
            mPresenter.deleteSearchHistory(history);
        });
        return chip;
    }

    @Override
    public void onSearchHistoryDeleteSuccess() {
        mPresenter.loadSearchHistory();
    }

    @Override
    public void onSearchHistoryClearSuccess() {
        mHistoryContainer.removeAllViews();
        mHistoryContainer.setVisibility(View.GONE);
        mHistoryClear.setVisibility(View.GONE);
        HintUtils.showToast(this, R.string.search_history_empty);
    }

    @Override
    protected View getLayoutView() {
        return mLayoutView;
    }

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.comic_search);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_search;
    }

    @Override
    protected boolean isNavTranslation() {
        return true;
    }

}
