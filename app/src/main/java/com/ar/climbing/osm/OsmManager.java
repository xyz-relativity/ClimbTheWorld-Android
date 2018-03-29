package com.ar.climbing.osm;

import android.widget.TextView;

import com.ar.climbing.storage.DataManager;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;

import static com.google.android.gms.internal.zzahn.runOnUiThread;

public class OsmManager {
    public void pushData(final List<Long> toChange, final TextView status) {
        (new Thread() {
            public void run() {
                DataManager dataMgr = new DataManager();
                Map<Long, GeoNode> poiMap = new HashMap<>();
                dataMgr.downloadIDs(toChange, poiMap);
                runOnUiThread(new Thread() {
                    public void run() {
                        status.setText("Creating new change set.");
                    }
                });

                Request request = new Request.Builder()
                        .url(Constants.DEFAULT_API + "permissions")
                        .get()
                        .build();
                try (Response response = Globals.httpClient.newCall(request).execute()) {
                    System.out.println(response.body().string());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
