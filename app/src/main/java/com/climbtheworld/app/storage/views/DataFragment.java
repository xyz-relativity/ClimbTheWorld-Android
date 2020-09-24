package com.climbtheworld.app.storage.views;

import android.content.Intent;
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
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.services.DownloadProgressListener;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.IPagerViewFragment;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import needle.UiRelatedTask;

public abstract class DataFragment implements DownloadProgressListener, IPagerViewFragment {
    protected final Configs configs;
    final AppCompatActivity parent;
    Map<String, CountryViewState> countryMap; //ConcurrentSkipListMap<>();
    Map<String, CountryState> displayCountryMap = new HashMap<>();
    @LayoutRes
    int viewID;
    ViewGroup view;
    LayoutInflater inflater;
    DataManager downloadManager;
    ListView tab;

    DataFragment(AppCompatActivity parent, @LayoutRes int viewID) {
        this.parent = parent;
        this.viewID = viewID;

        inflater = parent.getLayoutInflater();
        configs = Configs.instance(parent);

        initCountryMap();
    }

    protected void initCountryMap() {
        countryMap = new LinkedHashMap<>();
        Set<String> sortedCountryList = Globals.loadCountryList();
        for (String country : sortedCountryList) {
            CountryViewState countryView = new DataFragment.CountryViewState(DataFragment.CountryState.ADD, country);
            countryMap.put(countryView.countryISO, countryView);
        }
    }

    @Override
    public void onProgress(String eventOwner, int progressEvent) {
        ProgressBar statusProgress;
        ViewSwitcher statusContainer;
        CountryViewState country = countryMap.get(eventOwner);
        if (country == null) { //this country is not visible in this observer
            return;
        }

        View countryView = tab.getChildAt(country.listViewOrder);
        if (countryView == null) {
            return;
        }

        switch (progressEvent) {
            case DownloadProgressListener.PROGRESS_WAITING:
                country.setCountryState(CountryState.PROGRESS_BAR);
                setViewState(country.getCountryState(), countryView);

                statusProgress = countryView.findViewById(R.id.statusProgressBar);
                statusProgress.setIndeterminate(true);
                break;

            case DownloadProgressListener.PROGRESS_START:
                statusContainer = countryView.findViewById(R.id.selectStatusProgress);
                statusContainer.showNext();

                statusProgress = countryView.findViewById(R.id.statusProgressBar);
                statusProgress.setIndeterminate(false);
                break;

            case DownloadProgressListener.PROGRESS_DONE:
                country.setCountryState(CountryState.REMOVE_UPDATE);
                setViewState(country.getCountryState(), countryView);

                statusContainer = countryView.findViewById(R.id.selectStatusProgress);
                statusContainer.showPrevious();
                break;

            case DownloadProgressListener.PROGRESS_ERROR:
                country.setCountryState(CountryState.ADD);
                setViewState(country.getCountryState(), countryView);
                statusContainer = countryView.findViewById(R.id.selectStatusProgress);
                statusContainer.showPrevious();
                break;

            default:
                statusProgress = countryView.findViewById(R.id.statusProgressBar);
                statusProgress.setIndeterminate(false);
                statusProgress.setProgress(progressEvent);
        }
    }

    View buildCountriesView(View view, final ViewGroup tab, CountryViewState country, View.OnClickListener onClick) {
        if (view == null) {
            view = inflater.inflate(R.layout.list_item_country, tab, false);
        }

        TextView textField = view.findViewById(R.id.itemID);
        textField.setText(country.countryISO);

        view.findViewById(R.id.countryAddButton).setOnClickListener(onClick);
        view.findViewById(R.id.countryDeleteButton).setOnClickListener(onClick);
        view.findViewById(R.id.countryRefreshButton).setOnClickListener(onClick);

        textField = view.findViewById(R.id.selectText);
        textField.setText(country.countryName);

//        loadFlags(view);

        System.out.println("^^^^^^^^^^^^^^ Build View: "
                + " | Country ISO: " + country.countryISO
                + " | Country Name: " + country.countryName
                + " | view ID: " + ((TextView) view.findViewById(R.id.itemID)).getText()
                + " | view Name: " + ((TextView) view.findViewById(R.id.selectText)).getText()
        );

        return view;
    }

    void setViewState(final CountryState state, View countryView) {
        View statusAdd = countryView.findViewById(R.id.selectStatusAdd);
        View statusProgress = countryView.findViewById(R.id.selectStatusProgress);
        View statusDel = countryView.findViewById(R.id.selectStatusDel);
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
        countryView.invalidate();
    }

    private void loadFlags(final View country) {
        ImageView img = country.findViewById(R.id.countryFlag);
        img.setImageResource(R.drawable.flag_un);
        img.setColorFilter(Color.argb(200, 200, 200, 200));

        Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Drawable>() {
            @Override
            protected Drawable doWork() {
                String countryIso = ((TextView) country.findViewById(R.id.itemID)).getText().toString();
                return new BitmapDrawable(parent.getResources(), getBitmapFromZip("flag_" + countryIso.toLowerCase() + ".png"));
            }

            @Override
            protected void thenDoUiRelatedWork(Drawable flag) {
                ImageView img = country.findViewById(R.id.countryFlag);
                img.setImageDrawable(flag);

                img.getLayoutParams().width = (int) Globals.convertDpToPixel(flag.getIntrinsicWidth());
                img.getLayoutParams().height = (int) Globals.convertDpToPixel(flag.getIntrinsicHeight());
                img.setColorFilter(null);
            }
        });
    }

    private Bitmap getBitmapFromZip(final String imageFileInZip) {
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

    void countryClick(View v) {
        final View countryItem = (View) v.getParent().getParent();
        TextView textField = countryItem.findViewById(R.id.itemID);
        final String countryKey = textField.getText().toString();
        final CountryViewState country = countryMap.get(countryKey);
        switch (v.getId()) {
            case R.id.countryAddButton:
            case R.id.countryRefreshButton:
                Intent intent = new Intent(parent, DownloadService.class);
                intent.putExtra("countryISO", country.countryISO);
                parent.startService(intent);
                break;

            case R.id.countryDeleteButton:
                country.setCountryState(CountryState.PROGRESS_BAR);
                setViewState(country.getCountryState(), tab.getChildAt(country.listViewOrder));

                Constants.WEB_EXECUTOR
                        .execute(new UiRelatedTask<CountryViewState>() {
                            @Override
                            protected CountryViewState doWork() {
                                deleteCountryData(country.countryISO);
                                country.setCountryState(CountryState.ADD);
                                return country;
                            }

                            @Override
                            protected void thenDoUiRelatedWork(CountryViewState result) {
                                setViewState(result.getCountryState(), tab.getChildAt(result.listViewOrder));
                            }
                        });
                break;
        }
    }

    private void deleteCountryData(String countryIso) {
        Globals.appDB.nodeDao().deleteNodesFromCountry(countryIso.toLowerCase());
    }

    public Resources getResources() {
        return parent.getResources();
    }

    <T extends View> T findViewById(@IdRes int id) {
        return view.findViewById(id);
    }

    public abstract void onActivityResult(int requestCode, int resultCode, Intent data);

    public enum CountryState {
        ADD,
        PROGRESS_BAR,
        REMOVE_UPDATE
    }

    //column location in the CSV file.
    public static class CountryViewState {
        private static final int COUNTRY_ISO_ID = 0;
        private static final int COUNTRY_NAME_ID = 1;

        public String countryISO;
        public String countryName;
        public int listViewOrder = -1;
        CountryState countryState;

        public CountryViewState(CountryState state, String countryInfo) {
            String[] countryInfoSplit = countryInfo.split(",");
            this.countryISO = countryInfoSplit[COUNTRY_ISO_ID];
            this.countryName = countryInfoSplit[COUNTRY_NAME_ID];
            setCountryState(state);
        }

        public CountryState getCountryState() {
            return countryState;
        }

        public void setCountryState(CountryState state) {
            countryState = state;
        }
    }
}
