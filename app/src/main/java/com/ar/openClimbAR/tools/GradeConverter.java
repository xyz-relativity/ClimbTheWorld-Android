package com.ar.openClimbAR.tools;

import android.content.Context;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.utils.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xyz on 12/23/17.
 */

public class GradeConverter {
    private static GradeConverter converter = null;
    private Map<String, ArrayList<String>> dataMap = new HashMap();
    public ArrayList<String> systems;
    public ArrayList<String> cleanSystems = new ArrayList<>();
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
        systems = new ArrayList<>(Arrays.asList(data.get(0)));
        maxGrades = data.size();
        for (int i = 0; i< systems.size(); ++i) {
            ArrayList<String> elements = new ArrayList<>();
            elements.add(Constants.UNKNOWN_GRADE_STRING);
            for (int j = 1; j < maxGrades; ++j) {
                elements.add(data.get(j)[i].toLowerCase());
            }
            for (String key: systems.get(i).split("\\|")) {
                dataMap.put(key.toLowerCase(), elements);
            }
            cleanSystems.add(systems.get(i).split("\\|")[0]);
        }
    }

    public String convert(String fromSystem, String toSystem, String value) {
        String fromSystemLowerCase = fromSystem.toLowerCase();
        String toSystemLowerCase = toSystem.toLowerCase();
        String valueLowerCase = value.toLowerCase();

        int order = getGradeOrder(fromSystemLowerCase, valueLowerCase);
        if (order > 0) {
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
        int result = 0;

        if (dataMap.containsKey(fromSystemLowerCase)) {
            result = dataMap.get(fromSystemLowerCase).indexOf(valueLowerCase);
        }

        return (result==-1 ? 0 : result);
    }

    public String getGradeFromOrder(String toSystem, int value) {
        String toSystemLowerCase = toSystem.toLowerCase();

        if (dataMap.containsKey(toSystemLowerCase.toLowerCase())) {
            return dataMap.get(toSystemLowerCase.toLowerCase()).get(value);
        } else {
            return "Invalid system: " + toSystemLowerCase;
        }
    }

    public boolean isValidSystem(String system) {
        return dataMap.keySet().contains(system.toLowerCase());
    }

    public ArrayList<String> getAllGrades(String system) {
        return dataMap.get(system.toLowerCase());
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
