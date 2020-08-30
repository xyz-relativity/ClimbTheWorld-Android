package com.climbtheworld.app.tools;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.ListViewItemBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;

public enum GradeSystem {
    uiaa("UIAA|Germany", R.string.grade_system_uiaa, R.string.grade_system_uiaa_short, R.string.grade_system_uiaa_description,
            new String[] {"1-","1","1+","2-","2","2+","3-","3","3+","4-","4","4+","5-","5","5+","6-","6","6+","7-","7","7+","8-","8","8+","9-","9","9+","10-","10","10+","11-","11","11+","12+","12","12+","13-","13","13+","14-"}),
    ukTech("UK Tech", R.string.grade_system_uk_tech, R.string.grade_system_uk_tech_short, R.string.grade_system_uk_tech_description,
            new String[] {"1","1","1","2","2","2","3","3","3","4a","4a","4a","4a/4b","4b","4c","4c/5a","5a","5a/5b","5b","5b/5c","5c","5c/6a","6a","6a","6b","6b/6c","6c","6c","6c/7a","7a","7a","7a/7b","7b","7b","7b",">7b",">7b",">7b",">7b",">7b"}),
    ukAdj("UK ADJ", R.string.grade_system_uk_adj, R.string.grade_system_uk_adj_short, R.string.grade_system_uk_adj_description,
            new String[] {"M","M","M","M/D","M/D","M/D","D","D","D","D/VD","D/VD","VD","S","HS","HS/VS","VS","HVS","E1","E1/E2","E2","E2/E3","E3","E4","E4/E5","E5","E6","E6/E7","E7","E7/E8","E8","E9","E9/E10","E10","E11","E11",">E11",">E11",">E11",">E11",">E11"}),
    fb("FB|French British", R.string.grade_system_fb, R.string.grade_system_fb_short, R.string.grade_system_fb_description,
            new String[] {"1","1","1","1","1","1","1/2","1/2","1/2","2","2","2","2/3","3","4a","4a/4b","4b","4c","5a","5b","5c","6a","6b","6b+","6c","6c+","7a","7a+","7a+/7b","7b","7b+","7c","7c+","7c+/8a","8a","8a+/8b","8b","8b+","8c","8c+"}),
    french("French", R.string.grade_system_french, R.string.grade_system_french_short, R.string.grade_system_french_description,
            new String[] {"1","1","1","2","2","2","3","3","3","4","4","4+","5a","5a/5b","5b","5b/5c","5c","6a","6a+","6b","6b+","6c","7a","7a+","7b+","7c","7c+","8a","8a/8a+","8a+","8b","8b+","8c","8c+","9a","9a+","9a+/9b","9b","9b+","9c"}),
    saxon("Saxon|Swiss", R.string.grade_system_saxon, R.string.grade_system_saxon_short, R.string.grade_system_saxon_description,
            new String[] {"I","I","I","II","II","II","III","III","III","IV","IV","IV/V","V","VI","VI/VIIa","VIIa","VIIb","VIIc","VIIIa","VIIIb","VIIIc","IXa","IXb","IXc","Xa","Xb","Xc","Xc","Xc/XIa","XIa","XIb","XIc","XIc/XIIa","XIIa","XIIb","XIIb/XIIc","XIIc","XIIc",">XIIc",">XIIc"}),
    nordic("Nordic|Scandinavian", R.string.grade_system_nordic, R.string.grade_system_nordic_short, R.string.grade_system_nordic_description,
            new String[] {"1","1","1","1","1","1","1/2","1/2","1/2","2","2","2","2/3","3","4a","4a/4b","4b","4c","5a","5b","5c","6a","6b","6b+","6c","6c+","7a","7a+","7a+/7b","7b","7b+","7c","7c+","7c+/8a","8a","8a+/8b","8b","8b+","8c","8c+"}),
    yds("YDS|YDS_class", R.string.grade_system_yds, R.string.grade_system_yds_short, R.string.grade_system_yds_description,
            new String[] {"5","5","5","5.1","5.1","5.2","5.2","5.3","5.3","5.4","5.5","5.6","5.7","5.8","5.9","5.10a","5.10b","5.10c","5.10d","5.11a","5.11b","5.11c","5.11d","5.12a","5.12b","5.12c","5.12d","5.13a","5.13b","5.13c","5.13d","5.14a","5.14b","5.14c","5.14d","5.15a","5.15a","5.15b","5.15c","5.15d"}),
    vGrade("V Grade", R.string.grade_system_v_grade, R.string.grade_system_v_grade_short, R.string.grade_system_v_grade_description,
            new String[] {"VB-","VB-","VB-","VB-","VB-","VB-","VB-","VB-","VB-","VB-","VB-","VB-","VB-/VB","VB","VB/V0-","V0-","V0-/V0","V0","V0+","V1","V1/V2","V2","V3","V3/V4","V4","V4/V5","V5","V6","V6/V7","V7","V8","V9","V10","V10/V11","V11","V12","V13","V14","V15","V15"}),
    undef("undefined", R.string.grade_system_undefined, R.string.grade_system_undefined_short, R.string.grade_system_undefined_description, new String[]{});

    public static int maxGrades = uiaa.data.length;

    public String key;
    public int localeName;
    public int shortName;
    public int description;
    private String[] data;

    GradeSystem(String key, int localeName, int shortName, int description, String[] data) {
        this.key = key;
        this.localeName = localeName;
        this.shortName = shortName;
        this.description = description;
        this.data = data;
    }

    public String getGrade(int index) {
        if (index >=0 && index < data.length) {
            return data[index];
        } else {
            return GeoNode.UNKNOWN_GRADE_STRING;
        }
    }

    public static GradeSystem[] printableValues() {
        List<GradeSystem> result = new ArrayList<>();
        for (GradeSystem checkSystem: GradeSystem.values()) {
            if (checkSystem != undef) {
                result.add(checkSystem);
            }
        }
        return result.toArray(new GradeSystem[0]);
    }

    public String buildExample() {
        return data[16] + ", " + data[17] + ", " + data[18] + "...";
    }

    public static GradeSystem fromString(String systemString) {
        for (GradeSystem checkSystem: GradeSystem.values()) {
            String[] keys = checkSystem.key.split("\\|");
            for (String key: keys) {
                if (key.equalsIgnoreCase(systemString)) {
                    return checkSystem;
                }
            }
        }
        return undef;
    }

    public String getMainKey () {
        return key.split("\\|")[0];
    }

    public int indexOf(String grade) {
        for (int i = 0; i < data.length; ++i) {
            if (data[i].equalsIgnoreCase(grade)) {
                return i;
            }
        }

        return -1;
    }

    public List<String> getAllGrades() {
        return Arrays.asList(data);
    }

    public static class GradeSystemArrayAdapter extends ArrayAdapter<GradeSystem> {

        private LayoutInflater inflater;
        Context context;

        public GradeSystemArrayAdapter(Context context, int resource, GradeSystem[] objects) {
            super(context, resource, objects);
            this.context = context;
            this.inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            return getCustomView(position, convertView, parent, true);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            return getCustomView(position, convertView, parent, false);
        }

        private View getCustomView(int position, View convertView, ViewGroup parent, boolean selected) {
            return ListViewItemBuilder.getBuilder(context)
                    .setTitle(context.getString(Objects.requireNonNull(getItem(position)).localeName))
                    .setDescription(context.getString(getItem(position).shortName) + ": " +getItem(position).buildExample())
                    .build();
        }
    }
}
