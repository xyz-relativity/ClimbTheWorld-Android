package com.ar.openClimbAR.tools;

import android.content.Context;

import com.ar.openClimbAR.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xyz on 12/23/17.
 */

public class GradeConverter {
    private static GradeConverter converter = null;
    private Map<String, ArrayList<String>> dataMap = new HashMap();
    public String[] systems;
    public int maxGrades;

    private GradeConverter(Context context) {
        InputStream is = context.getResources().openRawResource(R.raw.grades_conversion);

        BufferedReader reader = null;
        reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        String line = "";
        try {
            List<String[]> csv = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                csv.add(line.split(","));
            }

            buildMap(csv);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void buildMap(List<String[]> data) {
        systems = data.get(0);
        maxGrades = data.size() - 1;
        for (int i = 0; i< systems.length; ++i) {
            ArrayList<String> elements = new ArrayList<>();
            for (int j = 1; j <= maxGrades; ++j) {
                elements.add(data.get(j)[i]);
            }
            for (String key: systems[i].split("\\|")) {
                dataMap.put(key.toUpperCase(), elements);
            }
        }
    }

    public String convert(String fromSystem, String toSystem, String value) {
        int order = getGradeOrder(fromSystem, value);
        if (order >= 0) {
            if (dataMap.containsKey(toSystem)) {
                return dataMap.get(toSystem).get(order);
            } else {
                return "Invalid to system: " + toSystem;
            }
        } else {
            return "Invalid from system: " + fromSystem;
        }
    }

    public int getGradeOrder(String format, String value) {
        if (dataMap.containsKey(format)) {
            return dataMap.get(format).indexOf(value);
        } else {
            return -1;
        }
    }

    public String fromGradeFromOrder(String toSystem, int value) {
        if (dataMap.containsKey(toSystem)) {
            return dataMap.get(toSystem).get(value);
        } else {
            return "Invalid system: " + toSystem;
        }
    }

    public static GradeConverter getConverter() {
        return converter;
    }

    public static GradeConverter getConverter(Context context) {
        if (converter == null) {
            synchronized (GradeConverter.class) {
                if (converter == null) {
                    converter = new GradeConverter(context);
                }
            }
        }

        return converter;
    }
}
