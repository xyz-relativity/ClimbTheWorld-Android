package com.climbtheworld.app.storage.views;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.AsyncDataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONException;
import org.osmdroid.util.BoundingBox;

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

public class DataFragment {
    public enum CountryState {
        ADD,
        PROGRESS_BAR,
        REMOVE_UPDATE;
    }

    public static class CountryViewState {
        public static final int COUNTRY_ISO_ID = 0;
        public static final int COUNTRY_NORTH_COORD = 5;
        public static final int COUNTRY_EAST_COORD = 4;
        public static final int COUNTRY_SOUTH_COORD = 3;
        public static final int COUNTRY_WEST_COORD = 2;

        public CountryState state;
        public String[] countryInfo;
        public List<View> views = new ArrayList<>();
        public CountryViewState(CountryState state, String[] countryInfo) {
            this.state = state;
            this.countryInfo = countryInfo;
        }
    }

    Activity parent;
    @LayoutRes
    int viewID;
    ViewGroup view;
    LayoutInflater inflater;
    AsyncDataManager downloadManager;
    Map<String, View> displayCountryMap = new HashMap<>();

    public static Set<String> sortedCountryList = new LinkedHashSet<>();
    public static Map<String, CountryViewState> countryMap = new ConcurrentHashMap<>(); //ConcurrentSkipListMap<>();
    static boolean needRefresh = false;

    DataFragment (Activity parent, @LayoutRes int viewID) {
        this.parent = parent;
        this.viewID = viewID;

        inflater = parent.getLayoutInflater();
    }

    void showLoadingProgress(final @IdRes int id, final boolean show) {
        parent.runOnUiThread(new Thread() {
            public void run() {
                if (show) {
                    findViewById(id).setVisibility(View.VISIBLE);
                } else {
                    findViewById(id).setVisibility(View.GONE);
                }
            }
        });

    }

    View buildCountriesView(final ViewGroup tab, String[] country, final int visibility, View.OnClickListener onClick) {
        final String countryIso = country[0];
        String countryName = country[1];

        final View newViewElement = inflater.inflate(R.layout.list_element_country, tab, false);

        TextView textField = newViewElement.findViewById(R.id.itemID);
        textField.setText(countryIso);

        newViewElement.findViewById(R.id.countryAddButton).setOnClickListener(onClick);
        newViewElement.findViewById(R.id.countryDeleteButton).setOnClickListener(onClick);
        newViewElement.findViewById(R.id.countryRefreshButton).setOnClickListener(onClick);

        textField = newViewElement.findViewById(R.id.selectText);
        textField.setText(countryName);

        loadFlags(newViewElement);

        parent.runOnUiThread(new Thread() {
            public void run() {
                tab.addView(newViewElement);
                newViewElement.setVisibility(visibility);
            }
        });

        return newViewElement;
    }

    void setViewState(final CountryViewState country) {
        parent.runOnUiThread(new Thread() {
            public void run() {
                CountryState state = country.state;
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
        });
    }

    private void loadFlags(final View country) {
        TextView textField = country.findViewById(R.id.itemID);
        String countryIso = textField.getText().toString();

        ImageView img = country.findViewById(R.id.countryFlag);
        Bitmap flag = getBitmapFromZip("flag_" + countryIso.toLowerCase() + ".png");
        img.setImageBitmap(flag);

        img.getLayoutParams().width = (int) Globals.sizeToDPI(parent, flag.getWidth());
        img.getLayoutParams().height = (int) Globals.sizeToDPI(parent, flag.getHeight());
    }

    private Bitmap getBitmapFromZip(final String imageFileInZip){
        InputStream fis = getResources().openRawResource(R.raw.flags);
        ZipInputStream zis = new ZipInputStream(fis);
        try {
            ZipEntry ze = null;
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
        switch (v.getId()) {
            case R.id.countryAddButton:
                (new Thread() {
                    public void run() {
                        final CountryState currentStatus = country.state;

                        country.state = CountryState.PROGRESS_BAR;
                        setViewState(country);

                        try {
                            fetchCountryData(country.countryInfo[CountryViewState.COUNTRY_ISO_ID],
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_NORTH_COORD]),
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_EAST_COORD]),
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_SOUTH_COORD]),
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_WEST_COORD]));
                        } catch (IOException | JSONException e) {
                            country.state = currentStatus;
                            setViewState(country);
                            parent.runOnUiThread(new Thread() {
                                public void run() {
                                    Toast.makeText(parent, parent.getResources().getString(R.string.exception_message,
                                            e.getMessage()), Toast.LENGTH_LONG).show();
                                }
                            });
                            return;
                        }

                        country.state = CountryState.REMOVE_UPDATE;
                        setViewState(country);
                        needRefresh = true;
                    }
                }).start();
                break;

            case R.id.countryDeleteButton:
                (new Thread() {
                    public void run() {
                        country.state = CountryState.PROGRESS_BAR;
                        setViewState(country);

                        deleteCountryData(country.countryInfo[CountryViewState.COUNTRY_ISO_ID]);
                        country.state = CountryState.ADD;
                        setViewState(country);
                        needRefresh = true;
                    }
                }).start();
                break;

            case R.id.countryRefreshButton:
                (new Thread() {
                    public void run() {
                        final CountryState currentStatus = country.state;
                        country.state = CountryState.PROGRESS_BAR;
                        setViewState(country);

                        try {
                            fetchCountryData(country.countryInfo[CountryViewState.COUNTRY_ISO_ID],
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_NORTH_COORD]),
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_EAST_COORD]),
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_SOUTH_COORD]),
                                    Double.parseDouble(country.countryInfo[CountryViewState.COUNTRY_WEST_COORD]));
                        } catch (IOException | JSONException e) {
                            country.state = currentStatus;
                            setViewState(country);
                            parent.runOnUiThread(new Thread() {
                                public void run() {
                                    Toast.makeText(parent, parent.getResources().getString(R.string.exception_message,
                                            e.getMessage()), Toast.LENGTH_LONG).show();
                                }
                            });
                            return;
                        }

                        country.state = CountryState.REMOVE_UPDATE;
                        setViewState(country);
                    }
                }).start();
                break;
        }
    }

    private void deleteCountryData(String countryIso) {
        List<GeoNode> countryNodes = Globals.appDB.nodeDao().loadNodesFromCountry(countryIso.toLowerCase());
        Globals.appDB.nodeDao().deleteNodes(countryNodes.toArray(new GeoNode[countryNodes.size()]));
    }

    private void fetchCountryData(String countryIso, double north, double east, double south, double west) throws IOException, JSONException {
        Map<Long, GeoNode> nodes = new HashMap<>();
        downloadManager.getDataManager().downloadCountry(new BoundingBox(north, east, south, west),
                nodes,
                countryIso);
        downloadManager.getDataManager().pushToDb(nodes, true);
        Globals.showNotifications(parent);
    }

    public Resources getResources() {
        return parent.getResources();
    }

    <T extends View> T findViewById(@IdRes int id){
        return view.findViewById(id);
    }
}
