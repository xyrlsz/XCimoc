package com.xyrlsz.xcimocob.ui.activity;

import android.content.Context;
import android.content.Intent;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.ui.fragment.CategoryFragment;

/**
 * Created by Hiroshi on 2016/12/11.
 * 分类浏览页：承载 CategoryFragment，提供独立的分类浏览入口。
 */
public class CategoryActivity extends BackActivity {

    private static final String TAG_FRAGMENT_CATEGORY = "fragment_category";

    public static Intent createIntent(Context context, int source, String title) {
        Intent intent = new Intent(context, CategoryActivity.class);
        intent.putExtra(Extra.EXTRA_SOURCE, source);
        intent.putExtra(Extra.EXTRA_KEYWORD, title);
        return intent;
    }

    @Override
    protected void initView() {
        super.initView();
//        String title = getIntent().getStringExtra(Extra.EXTRA_KEYWORD);
        if (mToolbarTitle != null) {
//            mToolbarTitle.setText(title != null ? title : getString(R.string.category));
            mToolbarTitle.setText(getString(R.string.category));
        }
        addCategoryFragment();
    }

    private void addCategoryFragment() {
        CategoryFragment fragment = (CategoryFragment) getSupportFragmentManager()
                .findFragmentByTag(TAG_FRAGMENT_CATEGORY);
        if (fragment == null) {
            fragment = new CategoryFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.category_fragment_container, fragment, TAG_FRAGMENT_CATEGORY)
                    .commit();
        }
    }

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.category);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_category;
    }

}
