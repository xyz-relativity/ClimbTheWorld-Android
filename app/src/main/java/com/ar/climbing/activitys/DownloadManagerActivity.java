package com.ar.climbing.activitys;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.storage.download.INodesFetchingEventListener;
import com.ar.climbing.storage.download.NodesFetchingManager;
import com.ar.climbing.utils.Globals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadManagerActivity extends AppCompatActivity implements INodesFetchingEventListener {

    private static final int TAB_COUNT = 3;

    private static final int DOWNLOADS_TAB = 0;
    private static final int UPDATES_TAB = 1;
    private static final int TO_UPLOAD_TAB = 2;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private static List<String> countryList = Collections.synchronizedList(new ArrayList<String>());
    private static List<String> installedCountries = Collections.synchronizedList(new ArrayList<String>());
    private static NodesFetchingManager downloadManager;
    private static Map<Long, GeoNode> allPOIs = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_manager);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        downloadManager = new NodesFetchingManager(this);
        downloadManager.addObserver(this);

        loadCountries();
    }

    private void loadCountries() {
        (new Thread() {
            public void run() {
                installedCountries = Globals.appDB.nodeDao().loadCountries();
            }
        }).start();

        String line = "";
        InputStream is = getResources().openRawResource(R.raw.country_bbox);

        BufferedReader reader = null;
        reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        try {
            reader.readLine(); //ignore headers
            while ((line = reader.readLine()) != null) {
                countryList.add(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> parameters) {
        if (progress == 100 && parameters.containsKey("operation") && parameters.get("operation").equals(NodesFetchingManager.DownloadOperation.BBOX_DOWNLOAD.name())) {
//            downloadManager.pushToDb(allPOIs.values(), );
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber, Map<Long, GeoNode> allPOIs) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            int viewId = getArguments().getInt(ARG_SECTION_NUMBER) - 1;

            View rootView = inflater.inflate(R.layout.fragment_download_manager, container, false);
            TextView textView = rootView.findViewById(R.id.section_label);
            textView.setText(getResources().getStringArray(R.array.download_manager_section)[viewId]);

            SeekBar seekBar = rootView.findViewById(R.id.downloadViewLocation);
            seekBar.setProgress(viewId);
            seekBar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    return true;
                }
            });
            seekBar.setMax(TAB_COUNT - 1);

            switch (viewId) {
                case DOWNLOADS_TAB:
                    ViewGroup countryOwner = rootView.findViewById(R.id.countryContainer);
                    countryOwner.removeAllViews();
                    View newViewElement;

                    int id = 0;
                    for (String country: countryList) {
                        String[] elements = country.split(",");
                        String countryIso = elements[0].toLowerCase();
                        String flagIc = "flag_" + countryIso;
                        String countryName = elements[1];

                        newViewElement = inflater.inflate(R.layout.country_select_button, countryOwner, false);
                        Switch sw = newViewElement.findViewById(R.id.countrySwitch);
                        sw.setText(countryName);
                        sw.setId(id);
                        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                final String[] country = countryList.get(buttonView.getId()).split(",");
                                if (isChecked) {
                                    downloadManager.downloadBBox(Double.parseDouble(country[3]),
                                            Double.parseDouble(country[2]),
                                            Double.parseDouble(country[5]),
                                            Double.parseDouble(country[4]), allPOIs);
                                } else {
                                    (new Thread() {
                                        public void run() {
                                            List<GeoNode> countryNodes = Globals.appDB.nodeDao().loadNodesFromCountry(country[0].toLowerCase());
                                            Globals.appDB.nodeDao().deleteNodes(countryNodes.toArray(new GeoNode[countryNodes.size()]));
                                        }
                                    }).start();
                                }
                            }
                        });

                        ImageView img = newViewElement.findViewById(R.id.countryFlag);

                        Resources resources = getResources();
                        final int resourceId = resources.getIdentifier(flagIc, "drawable",
                                container.getContext().getPackageName());

                        img.setImageResource(resourceId);

                        if (installedCountries.contains(countryIso)) {
                            sw.setChecked(true);
                        }
                        countryOwner.addView(newViewElement);
                        id++;
                    }
                    break;

                case UPDATES_TAB:
                    break;

                case TO_UPLOAD_TAB:
                    break;
            }

            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1, allPOIs);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return TAB_COUNT;
        }
    }
}
