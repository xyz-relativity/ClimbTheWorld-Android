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
                elements.add(data.get(j)[i].toLowerCase());
            }
            for (String key: systems[i].split("\\|")) {
                dataMap.put(key.toLowerCase(), elements);
            }
        }
    }

    public String convert(String fromSystem, String toSystem, String value) {
        String fromSystemLowerCase = fromSystem.toLowerCase();
        String toSystemLowerCase = toSystem.toLowerCase();
        String valueLowerCase = value.toLowerCase();

        int order = getGradeOrder(fromSystemLowerCase, valueLowerCase);
        if (order >= 0) {
            if (dataMap.containsKey(toSystemLowerCase)) {
                return dataMap.get(toSystemLowerCase).get(order);
            } else {
                return "Invalid to system: " + toSystemLowerCase;
            }
        } else {
            return "Invalid from system: " + fromSystemLowerCase;
        }
    }

    public int getGradeOrder(String fromSystem, String value) {
        String fromSystemLowerCase = fromSystem.toLowerCase();
        String valueLowerCase = value.toLowerCase();

        if (dataMap.containsKey(fromSystemLowerCase)) {
            return dataMap.get(fromSystemLowerCase).indexOf(valueLowerCase);
        } else {
            return -1;
        }
    }

    public String getGradeFromOrder(String toSystem, int value) {
        String toSystemLowerCase = toSystem.toLowerCase();

        if (dataMap.containsKey(toSystemLowerCase.toLowerCase())) {
            return dataMap.get(toSystemLowerCase.toLowerCase()).get(value);
        } else {
            return "Invalid system: " + toSystemLowerCase;
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
