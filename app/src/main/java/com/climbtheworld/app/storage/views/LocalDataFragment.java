package com.climbtheworld.app.storage.views;

import android.app.Activity;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

import needle.Needle;

public class LocalDataFragment extends DataFragment implements IDataViewFragment, View.OnClickListener{

    public LocalDataFragment(Activity parent, @LayoutRes int viewID) {
        super(parent, viewID);

        downloadManager = new DataManager(false);
    }

    @Override
    public @LayoutRes
    int getViewId() {
        return this.viewID;
    }

    @Override
    public void onCreate(final ViewGroup view) {
        this.view = view;

        ((TextView)findViewById(R.id.noLocalDataText))
                .setText(parent.getString(R.string.no_local_data, parent.getString(R.string.download_manager_downloads)));

        localTab();
    }

    @Override
    public void onViewSelected() {
        if (needRefresh) {
            localTab();
            needRefresh = false;
        }
    }

    public void localTab() {
        showLoadingProgress(R.id.localLoadDialog,true);

        final ViewGroup tab = findViewById(R.id.localCountryView);
        tab.removeAllViews();
        findViewById(R.id.noLocalDataText).setVisibility(View.GONE);

        Needle.onBackgroundThread()
                .withThreadPoolSize(Constants.NEEDLE_WEB_POOL)
                .withTaskType(Constants.NEEDLE_WORK_TASK)
                .execute(new Runnable() {
                    @Override
                    public void run() {
                        List<String> installedCountries = new ArrayList<>();
                        installedCountries = Globals.appDB.nodeDao().loadCountriesIso();
                        boolean foundOne = false;
                        if (!installedCountries.isEmpty()) {
                            for (final String countryIso: sortedCountryList) {
                                final CountryViewState country = countryMap.get(countryIso);
                                if (installedCountries.contains(countryIso)) {
                                    foundOne = true;
                                    country.views.add(buildCountriesView(tab, country.countryInfo, View.VISIBLE, LocalDataFragment.this));
                                    country.state = CountryState.REMOVE_UPDATE;
                                    Needle.onMainThread().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            setViewState(country);
                                        }
                                    });
                                }
                            }
                        }
                        if (!foundOne) {
                            Needle.onMainThread().execute(new Runnable() {
                                @Override
                                public void run() {
                                    findViewById(R.id.noLocalDataText).setVisibility(View.VISIBLE);
                                }
                            });
                        }

                        showLoadingProgress(R.id.localLoadDialog, false);
                    }
        });
    }

    @Override
    public void onClick(View v) {
        countryClick(v);
    }
}
