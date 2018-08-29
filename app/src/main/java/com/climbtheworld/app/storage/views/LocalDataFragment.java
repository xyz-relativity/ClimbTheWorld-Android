package com.climbtheworld.app.storage.views;

import android.app.Activity;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.AsyncDataManager;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LocalDataFragment extends DataFragment implements IDataViewFragment, View.OnClickListener{

    public LocalDataFragment(Activity parent, @LayoutRes int viewID) {
        super(parent, viewID);

        downloadManager = new AsyncDataManager(false);
        downloadManager.addObserver(this);
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

    }

    public void localTab() {
        showLoadingProgress(R.id.localLoadDialog,true);

        final ViewGroup tab = findViewById(R.id.localCountryView);
        tab.removeAllViews();
        findViewById(R.id.noLocalDataText).setVisibility(View.GONE);

        (new Thread() {
            public void run() {
                List<String> installedCountries = new ArrayList<>();
                installedCountries = Globals.appDB.nodeDao().loadCountriesIso();
                boolean foundOne = false;
                if (!installedCountries.isEmpty()) {
                    for (final String countryIso: sortedCountryList) {
                        CountryViewState country = countryMap.get(countryIso);
                        if (installedCountries.contains(countryIso)) {
                            foundOne = true;
                            country.views.add(buildCountriesView(tab, country.countryInfo, View.VISIBLE, LocalDataFragment.this));
                            country.state = CountryState.REMOVE_UPDATE;
                            setViewState(country);
                        }
                    }
                }
                if (!foundOne) {
                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            findViewById(R.id.noLocalDataText).setVisibility(View.VISIBLE);
                        }
                    });
                }

                showLoadingProgress(R.id.localLoadDialog, false);
            }
        }).start();
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {

    }

    @Override
    public void onClick(View v) {
        countryClick(v);
    }
}
