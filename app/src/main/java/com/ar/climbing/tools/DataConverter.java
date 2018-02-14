package com.ar.climbing.tools;

import android.arch.persistence.room.TypeConverter;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by xyz on 2/9/18.
 */

public class DataConverter {

    @TypeConverter
    public JSONObject storedStringToJSONObject(String value) {
        try {
            return new JSONObject(value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }

    @TypeConverter
    public String JSONObjectToStoredString(JSONObject cl) {
        return cl.toString();
    }
}
