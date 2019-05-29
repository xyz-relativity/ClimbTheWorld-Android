package com.climbtheworld.app.storage.views;

import android.app.Activity;
import android.support.annotation.LayoutRes;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.IPagerViewFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import needle.UiRelatedTask;

public class RemotePagerFragment extends DataFragment implements IPagerViewFragment {

    class CountryAdapter extends BaseAdapter {
        private List<String> arrayList = new ArrayList<>(sortedCountryList);
        private List<String> myList = new ArrayList<>(arrayList);
        private List<String> installedCountries;

        View.OnClickListener onClick;

        public CountryAdapter(List<String> installedCountries, View.OnClickListener onClick) {
            this.installedCountries = installedCountries;
            this.onClick = onClick;
        }

        // put below code (method) in Adapter class
        public void filter(String charText) {
            charText = charText.toLowerCase(Locale.getDefault());
            myList.clear();
            if (charText.length() == 0) {
                myList.addAll(arrayList);
            }
            else
            {
                for (String countryIso : arrayList) {
                    String[] country = countryMap.get(countryIso).countryInfo;

                    if (getCountryVisibility(country, charText.toUpperCase())) {
                        myList.add(countryIso);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return myList.size();
        }

        @Override
        public Object getItem(int i) {
            return myList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            String countryIso = myList.get(i);
            final CountryViewState country = countryMap.get(countryIso);

            view = buildCountriesView(view, viewGroup, country.countryInfo, onClick);
            country.views.add(view);
            if (installedCountries.contains(countryIso)) {
                country.setCountryState(CountryState.REMOVE_UPDATE);
            } else if (!displayCountryMap.containsKey(countryIso)) {
                country.setCountryState(CountryState.ADD);
            }
            setViewState(country);

            return view;
        }

        private boolean getCountryVisibility(String[] country, String filter) {
            return country[1].toUpperCase().contains(filter)
                    || country[0].toUpperCase().contains(filter);
        }
    }

    public RemotePagerFragment(Activity parent, @LayoutRes int viewID) {
        super(parent, viewID);

        downloadManager = new DataManager(parent,false);
    }

    @Override
    public int getViewId() {
        return this.viewID;
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;

        downloadsTab();
    }

    @Override
    public void onViewSelected() {
        if (needRefresh) {
            downloadsTab();
            needRefresh = false;
        }
    }

    private void downloadsTab() {
        Globals.globalConfigs.setBoolean(Configs.ConfigKey.showPathToDownload, false);

        final ListView tab = findViewById(R.id.countryView);

        Constants.WEB_EXECUTOR
                .execute(new UiRelatedTask() {
                    @Override
                    protected Object doWork() {
                        return Globals.appDB.nodeDao().loadCountriesIso();
                    }

                    @Override
                    protected void thenDoUiRelatedWork(Object o) {
                        final List<String> installedCountries = (List<String>)o;

                        final CountryAdapter viewAdaptor = new CountryAdapter(installedCountries, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                countryClick(view);
                            }
                        });

                        EditText filter = findViewById(R.id.EditFilter);
                        viewAdaptor.filter(filter.getText().toString());
                        filter.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {

                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                viewAdaptor.filter(s.toString());
                            }
                        });

                        tab.setAdapter(viewAdaptor);
                    }
                });
    }
}
