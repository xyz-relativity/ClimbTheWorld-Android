package com.climbtheworld.app.storage.views;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

import needle.Needle;

public class RemoteDataFragment extends DataFragment implements IDataViewFragment, View.OnClickListener {

    private String filterString = "";

    public RemoteDataFragment(AppCompatActivity parent, @LayoutRes int viewID) {
        super(parent, viewID);

        downloadManager = new DataManager(false);
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

            Constants.WEB_EXECUTOR
                    .execute(new Runnable() {
                        @Override
                        public void run() {
                            List<String> installedCountries = Globals.appDB.nodeDao().loadCountriesIso();
                            for (String countryIso: sortedCountryList) {
                                final CountryViewState country = countryMap.get(countryIso);
                                View ctView = buildCountriesView(tab, country.countryInfo, getCountryVisibility(country.countryInfo), RemoteDataFragment.this);
                                displayCountryMap.put(country.countryInfo[CountryViewState.COUNTRY_ISO_ID], ctView);
                                country.views.add(ctView);
                                if (installedCountries.contains(countryIso)) {
                                    country.state = CountryState.REMOVE_UPDATE;
                                } else {
                                    country.state = CountryState.ADD;
                                }

                                Needle.onMainThread().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        setViewState(country);
                                    }
                                });
                            }
                            showLoadingProgress(R.id.remoteLoadDialog,false);
                        }
            });
        }
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
                String[] country = countryMap.get(countryIso).countryInfo;
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
