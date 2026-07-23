package com.xyrlsz.xcimocob.ui.fragment;

import static com.xyrlsz.xcimocob.ui.activity.BrowserFilter.URL_KEY;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.ThemeResponsive;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.manager.TagManager;
import com.xyrlsz.xcimocob.model.Tag;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.ComicPresenter;
import com.xyrlsz.xcimocob.ui.activity.BrowserFilter;
import com.xyrlsz.xcimocob.ui.activity.PartFavoriteActivity;
import com.xyrlsz.xcimocob.ui.activity.SearchActivity;
import com.xyrlsz.xcimocob.ui.adapter.TabPagerAdapter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.ItemDialogFragment;
import com.xyrlsz.xcimocob.ui.fragment.recyclerview.grid.DownloadFragment;
import com.xyrlsz.xcimocob.ui.fragment.recyclerview.grid.FavoriteFragment;
import com.xyrlsz.xcimocob.ui.fragment.recyclerview.grid.GridFragment;
import com.xyrlsz.xcimocob.ui.fragment.recyclerview.grid.HistoryFragment;
import com.xyrlsz.xcimocob.ui.fragment.recyclerview.grid.LocalFragment;
import com.xyrlsz.xcimocob.ui.view.ComicView;
import com.xyrlsz.xcimocob.ui.widget.ComicFilterDialog;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Hiroshi on 2016/10/11.
 */

public class ComicFragment extends BaseFragment implements ComicView {

    private static final int DIALOG_REQUEST_FILTER = 0;

    BottomNavigationView mBottomNavigationView;
    ViewPager mViewPager;
    TextView mToolbarTitle;
    private ComicPresenter mPresenter;
    private TabPagerAdapter mTabAdapter;
    private List<Tag> mTagList;
    private String currTitle;
    private Context mContext;

    public ComicFragment(Context context) {
        mContext = context;
    }

    public ComicFragment() {

    }

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new ComicPresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initView() {
        setHasOptionsMenu(true);
        mBottomNavigationView = mRootView.findViewById(R.id.bottom_navigation);
        mViewPager = mRootView.findViewById(R.id.comic_view_pager);
        mToolbarTitle = requireActivity().findViewById(R.id.toolbar_title);
        mTabAdapter = new TabPagerAdapter(requireActivity().getSupportFragmentManager(),
                new GridFragment[]{new HistoryFragment(), new FavoriteFragment(), new DownloadFragment(), new LocalFragment()},
                new String[]{getString(R.string.comic_tab_history), getString(R.string.comic_tab_favorite), getString(R.string.comic_tab_download), getString(R.string.comic_tab_local)});
        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(mTabAdapter);
        mBottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int __id = item.getItemId();
            if (__id == R.id.navigation_history) {
                    mViewPager.setCurrentItem(0);
                    return true;
            } else if (__id == R.id.navigation_favorite) {
                    mViewPager.setCurrentItem(1);
                    return true;
            } else if (__id == R.id.navigation_download) {
                    mViewPager.setCurrentItem(2);
                    return true;
            } else if (__id == R.id.navigation_local) {
                    mViewPager.setCurrentItem(3);
                    return true;
            }
            return false;
        });
        int home = mPreference.getNumber(PreferenceManager.PREF_OTHER_LAUNCH, PreferenceManager.HOME_FAVORITE).intValue();
        switch (home) {
            default:
            case PreferenceManager.HOME_FAVORITE:
                mViewPager.setCurrentItem(1);
                currTitle = getString(R.string.comic_tab_favorite);
                mBottomNavigationView.setSelectedItemId(R.id.navigation_favorite);
                break;
            case PreferenceManager.HOME_HISTORY:
                mViewPager.setCurrentItem(0);
                currTitle = getString(R.string.comic_tab_history);
                mBottomNavigationView.setSelectedItemId(R.id.navigation_history);
                break;
            case PreferenceManager.HOME_DOWNLOAD:
                mViewPager.setCurrentItem(2);
                currTitle = getString(R.string.comic_tab_download);
                mBottomNavigationView.setSelectedItemId(R.id.navigation_download);
                break;
            case PreferenceManager.HOME_LOCAL:
                mViewPager.setCurrentItem(3);
                currTitle = getString(R.string.comic_tab_local);
                mBottomNavigationView.setSelectedItemId(R.id.navigation_local);
                break;
        }

        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        mBottomNavigationView.setSelectedItemId(R.id.navigation_history);
                        currTitle = getString(R.string.comic_tab_history);
                        setToolbarTitle(currTitle);
                        break;
                    case 1:
                        mBottomNavigationView.setSelectedItemId(R.id.navigation_favorite);
                        currTitle = getString(R.string.comic_tab_favorite);
                        setToolbarTitle(currTitle);
                        break;
                    case 2:
                        mBottomNavigationView.setSelectedItemId(R.id.navigation_download);
                        currTitle = getString(R.string.comic_tab_download);
                        setToolbarTitle(currTitle);
                        break;
                    case 3:
                        mBottomNavigationView.setSelectedItemId(R.id.navigation_local);
                        currTitle = getString(R.string.comic_tab_local);
                        setToolbarTitle(currTitle);
                        break;
                }
            }
        });

        mTagList = new ArrayList<>();
        hideProgressBar();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_comic, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int __id = item.getItemId();
        if (__id == R.id.comic_filter) {
//                showProgressDialog();
//                mTagList.clear();
//                mPresenter.loadTag();
                ComicFilterDialog comicFilterDialog = getComicFilterDialog();
                comicFilterDialog.show();
        } else if (__id == R.id.comic_search) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
//            case R.id.comic_bbs:
//                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.home_page_gitter_url)));
//                try {
//                    startActivity(intent);
//                } catch (Exception e) {
//                }
//                break;
        } else if (__id == R.id.comic_cancel_highlight) {
                ((FavoriteFragment) mTabAdapter.getItem(1)).cancelAllHighlight();
        } else if (__id == R.id.comic_open_use_url) {
                AlertDialog dialog = getAlertDialog();
                dialog.show();
//            case R.id.comic_filter_by_keyword:
//                AlertDialog dialog1 = InputDialog.getInputDialog(getContext(), getString(R.string.comic_filter_by_keyword), getString(R.string.search_keyword_input), getString(R.string.dialog_positive), getString(R.string.dialog_negative), new InputDialog.OnItemClickListener() {
//
//                    @Override
//                    public void onPositiveClick(DialogInterface d, int which, String input) {
//                        GridFragment item = (GridFragment) mTabAdapter.getItem(mViewPager.getCurrentItem());
//                        item.filterByKeyword(input);
//                    }
//
//                    @Override
//                    public void onNegativeClick(DialogInterface d, int which) {
//                        d.cancel();
//                    }
//                });
//                dialog1.show();
//
//                break;
//
//            case R.id.comic_filter_by_keyword_cancel:
//                GridFragment item2 = (GridFragment) mTabAdapter.getItem(mViewPager.getCurrentItem());
//                item2.cancelFilter();
//                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    private ComicFilterDialog getComicFilterDialog() {
        int theme = ThemeUtils.getThemeId();
        ComicFilterDialog comicFilterDialog = new ComicFilterDialog(getContext(), ThemeUtils.getDialogThemeById(theme), new ComicFilterDialog.SubmitCallBack() {
            @Override
            public void OnClickCommit(String keyword, boolean isCompleted, boolean isNotCompleted) {
                GridFragment item = (GridFragment) mTabAdapter.getItem(mViewPager.getCurrentItem());
                item.filterByKeyword(keyword, isCompleted, isNotCompleted);
            }

            @Override
            public void OnClickCancel() {
                GridFragment item2 = (GridFragment) mTabAdapter.getItem(mViewPager.getCurrentItem());
                item2.cancelFilter();
            }
        });
        return comicFilterDialog;
    }

    private AlertDialog getAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.comic_open_use_url_dialog_title));
        final EditText input = new EditText(getContext());
        input.setHint(getString(R.string.comic_open_use_url_dialog_content));
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.dialog_positive), (dialog, which) -> {
            String userInput = input.getText().toString();
            Intent intent = new Intent(getActivity(), BrowserFilter.class);
            intent.putExtra(URL_KEY, userInput);
            startActivity(intent);
        });
        builder.setNegativeButton(getString(R.string.dialog_negative), (dialog, which) -> dialog.cancel());
        return builder.create();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        for (int i = 0; i < mTabAdapter.getCount(); ++i) {
            mTabAdapter.getItem(i).onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        switch (requestCode) {
            case DIALOG_REQUEST_FILTER:
                int index = bundle.getInt(EXTRA_DIALOG_RESULT_INDEX);
                Intent intent = PartFavoriteActivity.createIntent(getActivity(),
                        mTagList.get(index).getId(), mTagList.get(index).getTitle());
                startActivity(intent);
                break;
        }
    }

    @Override
    public void onTagLoadSuccess(List<Tag> list) {
        hideProgressDialog();
        mTagList.add(new Tag(TagManager.TAG_FINISH, getString(R.string.comic_status_finish)));
        mTagList.add(new Tag(TagManager.TAG_CONTINUE, getString(R.string.comic_status_continue)));
        mTagList.addAll(list);
        int size = mTagList.size();
        String[] item = new String[size];
        for (int i = 0; i < size; ++i) {
            item[i] = mTagList.get(i).getTitle();
        }
        ItemDialogFragment fragment = ItemDialogFragment.newInstance(R.string.comic_tag_select, item, DIALOG_REQUEST_FILTER);
        fragment.setTargetFragment(this, 0);
        fragment.show(requireActivity().getSupportFragmentManager(), null);
    }

    @Override
    public void onTagLoadFail() {
        hideProgressDialog();
        HintUtils.showToast(getActivity(), R.string.comic_load_tag_fail);
    }

    @Override
    public void onThemeChange(@ColorRes int primary, @ColorRes int accent) {
        for (int i = 0; i < mTabAdapter.getCount(); ++i) {
            ((ThemeResponsive) mTabAdapter.getItem(i)).onThemeChange(primary, accent);
        }
        ColorStateList stateList = new ColorStateList(new int[][]{{-android.R.attr.state_checked}, {android.R.attr.state_checked}},
                new int[]{0x8A000000, ContextCompat.getColor(App.getAppContext(), accent)});
        mBottomNavigationView.setItemTextColor(stateList);
        mBottomNavigationView.setItemIconTintList(stateList);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_comic;
    }

    public String getCurrTitle() {
//        return currTitle;
        if (StringUtils.isEmpty(currTitle) && mContext != null) {
            return mContext.getResources().getString(R.string.comic_tab_favorite);
        }
        return currTitle;
    }

    private void setToolbarTitle(CharSequence title) {
        if (mToolbarTitle != null) {
            mToolbarTitle.setText(title);
        }
    }
}
