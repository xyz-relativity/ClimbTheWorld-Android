package com.climbtheworld.app.storage.views;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;

import needle.UiRelatedTask;

public class LocalDataFragment extends DataFragment implements IDataViewFragment {

    public LocalDataFragment(AppCompatActivity parent, @LayoutRes int viewID) {
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
        final ListView tab = findViewById(R.id.localCountryView);
        findViewById(R.id.noLocalDataText).setVisibility(View.GONE);

        Constants.WEB_EXECUTOR
                .execute(new UiRelatedTask() {

                    @Override
                    protected Object doWork() {
                        return Globals.appDB.nodeDao().loadCountriesIso();
                    }

                    @Override
                    protected void thenDoUiRelatedWork(Object o) {
                        List<String> unsortedInstalledCountries = (List<String>)o;
                        unsortedInstalledCountries.remove("");

                        final List<String> installedCountries = new ArrayList<>();

                        for (final String countryIso: sortedCountryList) {
                            if (unsortedInstalledCountries.contains(countryIso)) {
                                installedCountries.add(countryIso);
                            }
                        }

                        if (installedCountries.size() <= 0) {
                            findViewById(R.id.noLocalDataText).setVisibility(View.VISIBLE);
                        } else {
                            findViewById(R.id.noLocalDataText).setVisibility(View.GONE);
                        }

                        tab.setAdapter(new BaseAdapter() {
                            @Override
                            public int getCount() {
                                return installedCountries.size();
                            }

                            @Override
                            public Object getItem(int i) {
                                return installedCountries.get(i);
                            }

                            @Override
                            public long getItemId(int i) {
                                return i;
                            }

                            @Override
                            public View getView(int i, View view, ViewGroup viewGroup) {
                                String countryIso = installedCountries.get(i);
                                final CountryViewState country = countryMap.get(countryIso);

                                View countryView = buildCountriesView(viewGroup, country.countryInfo, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        countryClick(view);
                                    }
                                });
                                country.views.add(countryView);
                                country.setCountryState(CountryState.REMOVE_UPDATE);
                                setViewState(country);
                                return countryView;
                            }
                        });
                    }
                });
    }
}
