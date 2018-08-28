package com.climbtheworld.app.storage.views;

import android.app.Activity;
import android.support.annotation.LayoutRes;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.AsyncDataManager;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RemoteDataFragment extends DataFragment implements IDataViewFragment, View.OnClickListener {

    private String filterString = "";

    public RemoteDataFragment(Activity parent, @LayoutRes int viewID) {
        super(parent, viewID);

        downloadManager = new AsyncDataManager(false);
        downloadManager.addObserver(this);
    }

    @Override
    public int getViewId() {
        return this.viewID;
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;

        EditText filter = findViewById(R.id.EditFilter);
        filter.addTextChangedListener(createTextChangeListener());

        downloadsTab();
    }

    @Override
    public void onViewSelected() {

    }

    private void downloadsTab() {
        if (displayCountryMap.size() == 0) {
            showLoadingProgress(R.id.remoteLoadDialog,true);
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.showPathToDownload, false);

            final ViewGroup tab = findViewById(R.id.countryView);
            tab.removeAllViews();

            (new Thread() {
                public void run() {
                    List<String> installedCountries = new ArrayList<>();
                    installedCountries = Globals.appDB.nodeDao().loadCountriesIso();
                    for (String countryIso: sortedCountryList) {
                        String[] country = countryMap.get(countryIso);
                        displayCountryMap.put(country[0], buildCountriesView(tab, country, getCountryVisibility(country), installedCountries, RemoteDataFragment.this));
                    }
                    showLoadingProgress(R.id.remoteLoadDialog,false);
                }
            }).start();
        }
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {

    }

    private TextWatcher createTextChangeListener () {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                updateFilter(s.toString().toUpperCase());
            }
        };
    }

    private void updateFilter(String filter) {
        if (displayCountryMap.isEmpty()) {
            return;
        }

        filterString = filter;

        for (String countryIso: sortedCountryList) {
            if (displayCountryMap.containsKey(countryIso)) {
                String[] country = countryMap.get(countryIso);
                displayCountryMap.get(countryIso).setVisibility(getCountryVisibility(country));
            }
        }
    }

    private int getCountryVisibility(String[] country) {
        if (country[1].toUpperCase().contains(filterString)
                || country[0].toUpperCase().contains(filterString)) {
            return View.VISIBLE;
        } else {
            return View.GONE;
        }
    }

    @Override
    public void onClick(View v) {
        countryClick(v);
    }
}
