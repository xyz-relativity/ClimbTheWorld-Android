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

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.storage.AsyncDataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

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
    enum countryState {
        ADD,
        PROGRESS_BAR,
        REMOVE_UPDATE;
    }

    Activity parent;
    @LayoutRes
    int viewID;
    @IdRes
    int menuItemID;
    ViewGroup view;
    private LayoutInflater inflater;
    public AsyncDataManager downloadManager;

    static Map<String, countryState> countryStatusMap = new ConcurrentHashMap<>();
    public static Set<String> sortedCountryList = new LinkedHashSet<>();
    public static Map<String, String[]> countryMap = new ConcurrentHashMap<>(); //ConcurrentSkipListMap<>();


    DataFragment (Activity parent) {
        this.parent = parent;
        inflater = parent.getLayoutInflater();
    }

    protected void showLoadingProgress(final @IdRes int id, final boolean show) {
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

    protected View buildCountriesView(final ViewGroup tab, String[] country, final int visibility, List<String> installedCountries) {
        final String countryIso = country[0];
        String countryName = country[1];

        final View newViewElement = inflater.inflate(R.layout.country_list_element, tab, false);

        TextView textField = newViewElement.findViewById(R.id.itemID);
        textField.setText(countryIso);

        textField = newViewElement.findViewById(R.id.selectText);
        textField.setText(countryName);

        if (countryStatusMap.containsKey(countryIso)) {
            setViewState(countryStatusMap.get(countryIso), newViewElement);
        } else if (installedCountries.contains(countryIso)) {
            setViewState(countryState.REMOVE_UPDATE, newViewElement);
        }

        loadFlags(newViewElement);

        parent.runOnUiThread(new Thread() {
            public void run() {
                tab.addView(newViewElement);
                newViewElement.setVisibility(visibility);
            }
        });

        return newViewElement;
    }

    private static void setViewState(countryState state, View v) {
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

//    private void countryClick (View v) {
//        final View countryItem = (View) v.getParent().getParent();
//        TextView textField = countryItem.findViewById(R.id.itemID);
//        final String countryIso = textField.getText().toString();
//        final String[] country = DataFragment.countryMap.get(countryIso);
//        switch (v.getId()) {
//            case R.id.countryAddButton:
//                (new Thread() {
//                    public void run() {
//                        countryStatusMap.put(countryIso, countryState.PROGRESS_BAR);
//                        parent.runOnUiThread(new Thread() {
//                            public void run() {
//                                setViewState(countryState.PROGRESS_BAR, countryItem);
//                            }
//                        });
//
//                        fetchCountryData(country[0],
//                                Double.parseDouble(country[5]),
//                                Double.parseDouble(country[4]),
//                                Double.parseDouble(country[3]),
//                                Double.parseDouble(country[2]));
//
//                        parent.runOnUiThread(new Thread() {
//                            public void run() {
//                                setViewState(countryState.REMOVE_UPDATE, countryItem);
//                                if (displayCountryMap.containsKey(country[0])) {
//                                    setViewState(countryState.REMOVE_UPDATE, displayCountryMap.get(country[0]));
//                                }
//                            }
//                        });
//                        countryStatusMap.remove(countryIso);
//                    }
//                }).start();
//                break;
//
//            case R.id.countryDeleteButton:
//                (new Thread() {
//                    public void run() {
//                        parent.runOnUiThread(new Thread() {
//                            public void run() {
//                                setViewState(countryState.PROGRESS_BAR, countryItem);
//                            }
//                        });
//
//                        deleteCountryData(country[0]);
//
//                        parent.runOnUiThread(new Thread() {
//                            public void run() {
//                                setViewState(countryState.ADD, countryItem);
//                                if (displayCountryMap.containsKey(country[0])) {
//                                    setViewState(countryState.ADD, displayCountryMap.get(country[0]));
//                                }
//                            }
//                        });
//                    }
//                }).start();
//                break;
//
//            case R.id.countryRefreshButton:
//                (new Thread() {
//                    public void run() {
//                        countryStatusMap.put(countryIso, countryState.PROGRESS_BAR);
//                        parent.runOnUiThread(new Thread() {
//                            public void run() {
//                                setViewState(countryState.PROGRESS_BAR, countryItem);
//                            }
//                        });
//
//                        deleteCountryData(country[0]);
//                        fetchCountryData(country[0],
//                                Double.parseDouble(country[5]),
//                                Double.parseDouble(country[4]),
//                                Double.parseDouble(country[3]),
//                                Double.parseDouble(country[2]));
//
//                        parent.runOnUiThread(new Thread() {
//                            public void run() {
//                                setViewState(countryState.REMOVE_UPDATE, countryItem);
//                                if (displayCountryMap.containsKey(country[0])) {
//                                    setViewState(countryState.REMOVE_UPDATE, displayCountryMap.get(country[0]));
//                                }
//                            }
//                        });
//                        countryStatusMap.remove(countryIso);
//                    }
//                }).start();
//                break;
//        }
//    }

    private void loadFlags(final View country) {
        TextView textField = country.findViewById(R.id.itemID);
        String countryIso = textField.getText().toString();

        ImageView img = country.findViewById(R.id.countryFlag);
        Bitmap flag = getBitmapFromZip("flag_" + countryIso.toLowerCase() + ".png");
        img.setImageBitmap(flag);

        img.getLayoutParams().width = (int) AugmentedRealityUtils.sizeToDPI(parent, flag.getWidth());
        img.getLayoutParams().height = (int) AugmentedRealityUtils.sizeToDPI(parent, flag.getHeight());
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

    private void deleteCountryData (String countryIso) {
        List<GeoNode> countryNodes = Globals.appDB.nodeDao().loadNodesFromCountry(countryIso.toLowerCase());
        Globals.appDB.nodeDao().deleteNodes(countryNodes.toArray(new GeoNode[countryNodes.size()]));
    }

    private void fetchCountryData (String countryIso, double north, double east, double south, double west) {
        Map<Long, GeoNode> nodes = new HashMap<>();
        downloadManager.getDataManager().downloadCountry(new BoundingBox(north, east, south, west),
                nodes,
                countryIso);
        downloadManager.getDataManager().pushToDb(nodes, true);
    }

    public Resources getResources() {
        return parent.getResources();
    }

    <T extends View> T findViewById(@IdRes int id){
        return view.findViewById(id);
    }
}
