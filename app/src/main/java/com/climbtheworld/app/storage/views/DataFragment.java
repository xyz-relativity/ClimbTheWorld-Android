package com.climbtheworld.app.storage.views;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.DialogBuilder;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import needle.UiRelatedTask;

public class DataFragment {
    protected final Configs configs;

    public enum CountryState {
        ADD,
        PROGRESS_BAR,
        REMOVE_UPDATE
    }

    //column location in the CSV file.
    public static class CountryViewState {
        public static final int COUNTRY_ISO_ID = 0;
        public static final int COUNTRY_NORTH_COORD = 5;
        public static final int COUNTRY_EAST_COORD = 4;
        public static final int COUNTRY_SOUTH_COORD = 3;
        public static final int COUNTRY_WEST_COORD = 2;

        public String[] countryInfo;
        public List<View> views = new ArrayList<>();
        public CountryViewState(CountryState state, String[] countryInfo) {
            this.countryInfo = countryInfo;
            setCountryState(state);
        }

        public CountryState getCountryState() {
            return displayCountryMap.get(countryInfo[COUNTRY_ISO_ID]);
        }

        public void setCountryState(CountryState state) {
            displayCountryMap.put(countryInfo[COUNTRY_ISO_ID], state);
        }
    }

    final AppCompatActivity parent;
    @LayoutRes
    int viewID;
    ViewGroup view;
    LayoutInflater inflater;
    DataManager downloadManager;
    static Map<String, CountryState> displayCountryMap = new HashMap<>();

    public static Set<String> sortedCountryList = new LinkedHashSet<>();
    public static Map<String, CountryViewState> countryMap = new ConcurrentHashMap<>(); //ConcurrentSkipListMap<>();
    static boolean needRefresh = false;

    DataFragment (AppCompatActivity parent, @LayoutRes int viewID) {
        this.parent = parent;
        this.viewID = viewID;

        inflater = parent.getLayoutInflater();
        configs = Configs.instance(parent);
    }

    View buildCountriesView(View view, final ViewGroup tab, String[] country, View.OnClickListener onClick) {
        final String countryIso = country[0];
        String countryName = country[1];

        if (view == null) {
            view = inflater.inflate(R.layout.list_item_country, tab, false);
        }

        TextView textField = view.findViewById(R.id.itemID);
        textField.setText(countryIso);

        view.findViewById(R.id.countryAddButton).setOnClickListener(onClick);
        view.findViewById(R.id.countryDeleteButton).setOnClickListener(onClick);
        view.findViewById(R.id.countryRefreshButton).setOnClickListener(onClick);

        textField = view.findViewById(R.id.selectText);
        textField.setText(countryName);

        loadFlags(view);

        return view;
    }

    void setViewState(final CountryViewState country) {
        CountryState state = country.getCountryState();
        for (View v : country.views) {
            View statusAdd = v.findViewById(R.id.selectStatusAdd);
            View statusProgress = v.findViewById(R.id.selectStatusProgress);
            View statusDel = v.findViewById(R.id.selectStatusDel);
            switch (state) {
                case ADD:
                    statusAdd.setVisibility(View.VISIBLE);
                    statusDel.setVisibility(View.GONE);
                    statusProgress.setVisibility(View.GONE);
                    break;
                case PROGRESS_BAR:
                    statusAdd.setVisibility(View.GONE);
                    statusDel.setVisibility(View.GONE);
                    statusProgress.setVisibility(View.VISIBLE);
                    break;
                case REMOVE_UPDATE:
                    statusAdd.setVisibility(View.GONE);
                    statusDel.setVisibility(View.VISIBLE);
                    statusProgress.setVisibility(View.GONE);
                    break;
            }
        }
    }

    private void loadFlags(final View country) {
        ImageView img = country.findViewById(R.id.countryFlag);
        img.setImageResource(R.drawable.flag_un);
        img.setColorFilter(Color.argb(200,200,200,200));

        Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Drawable>() {
            @Override
            protected Drawable doWork() {
                String countryIso = ((TextView)country.findViewById(R.id.itemID)).getText().toString();
                return new BitmapDrawable(parent.getResources(), getBitmapFromZip("flag_" + countryIso.toLowerCase() + ".png"));
            }

            @Override
            protected void thenDoUiRelatedWork(Drawable flag) {
                ImageView img = country.findViewById(R.id.countryFlag);
                img.setImageDrawable(flag);

                img.getLayoutParams().width = (int) Globals.sizeToDPI(parent, flag.getIntrinsicWidth());
                img.getLayoutParams().height = (int) Globals.sizeToDPI(parent, flag.getIntrinsicHeight());
                img.setColorFilter(null);
            }
        });
    }

    private Bitmap getBitmapFromZip(final String imageFileInZip){
        InputStream fis = getResources().openRawResource(R.raw.flags);
        ZipInputStream zis = new ZipInputStream(fis);
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(imageFileInZip)) {
                    return BitmapFactory.decodeStream(zis);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return BitmapFactory.decodeResource(getResources(), R.drawable.flag_un);
    }

    void countryClick (View v) {
        final View countryItem = (View) v.getParent().getParent();
        TextView textField = countryItem.findViewById(R.id.itemID);
        final String countryIso = textField.getText().toString();
        final CountryViewState country = DataFragment.countryMap.get(countryIso);
        final CountryState currentStatus = country.getCountryState();
        switch (v.getId()) {
            case R.id.countryAddButton:
                country.setCountryState(CountryState.PROGRESS_BAR);
                setViewState(country);

                Constants.WEB_EXECUTOR
                        .execute(new UiRelatedTask<CountryViewState>() {
                            @Override
                            protected CountryViewState doWork() {
                                try {
                                    fetchCountryData(country.countryInfo[CountryViewState.COUNTRY_ISO_ID],
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_NORTH_COORD]),
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_EAST_COORD]),
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_SOUTH_COORD]),
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_WEST_COORD]));
                                } catch (IOException | JSONException e) {
                                    country.setCountryState(currentStatus);
                                    DialogBuilder.toastOnMainThread(parent, parent.getResources().getString(R.string.exception_message,
                                            e.getMessage()));
                                    return country;
                                }

                                country.setCountryState(CountryState.REMOVE_UPDATE);
                                needRefresh = true;
                                return country;
                            }

                            @Override
                            protected void thenDoUiRelatedWork(CountryViewState result) {
                                setViewState(result);
                            }
                });
                break;

            case R.id.countryDeleteButton:
                country.setCountryState(CountryState.PROGRESS_BAR);
                setViewState(country);

                Constants.WEB_EXECUTOR
                        .execute(new UiRelatedTask<CountryViewState>() {
                            @Override
                            protected CountryViewState doWork() {
                                deleteCountryData(country.countryInfo[CountryViewState.COUNTRY_ISO_ID]);
                                country.setCountryState(CountryState.ADD);
                                needRefresh = true;
                                return country;
                            }

                            @Override
                            protected void thenDoUiRelatedWork(CountryViewState result) {
                                setViewState(result);
                            }
                });
                break;

            case R.id.countryRefreshButton:
                country.setCountryState(CountryState.PROGRESS_BAR);
                setViewState(country);

                Constants.WEB_EXECUTOR
                        .execute(new UiRelatedTask<CountryViewState>() {
                            @Override
                            protected CountryViewState doWork() {
                                try {
                                    fetchCountryData(country.countryInfo[CountryViewState.COUNTRY_ISO_ID],
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_NORTH_COORD]),
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_EAST_COORD]),
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_SOUTH_COORD]),
                                            Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_WEST_COORD]));
                                } catch (IOException | JSONException e) {
                                    country.setCountryState(currentStatus);
                                    DialogBuilder.toastOnMainThread(parent, parent.getResources().getString(R.string.exception_message,
                                            e.getMessage()));
                                    return country;
                                }

                                country.setCountryState(CountryState.REMOVE_UPDATE);
                                return country;
                            }

                            @Override
                            protected void thenDoUiRelatedWork(CountryViewState result) {
                                setViewState(result);
                            }
                });
                break;
        }
    }

    private void deleteCountryData(String countryIso) {
        Globals.appDB.nodeDao().deleteNodesFromCountry(countryIso.toLowerCase());
    }

    private void fetchCountryData(final String countryIso, final double north, final double east, final double south, final double west) throws IOException, JSONException {
        Map<Long, DisplayableGeoNode> nodes = new HashMap<>();
        downloadManager.downloadCountry(nodes,
                countryIso);
        downloadManager.pushToDb(nodes, true);

        Globals.showNotifications(parent);
    }

    public Resources getResources() {
        return parent.getResources();
    }

    <T extends View> T findViewById(@IdRes int id){
        return view.findViewById(id);
    }
}
