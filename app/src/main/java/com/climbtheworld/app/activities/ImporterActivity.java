package com.climbtheworld.app.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.widgets.MapViewWidget;
import com.climbtheworld.app.widgets.MapWidgetFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IGeoPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ImporterActivity extends AppCompatActivity {
    private MapViewWidget mapWidget;

    protected static class DownloadedData {
        public JSONObject theCrag;
        public List<JSONObject> theRoutes = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder response = new StringBuilder();
            try {
                response.append(theCrag.toString(2));

                for (JSONObject node: theRoutes) {
                    response.append(node.toString(2));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            return response.toString();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_importer);

        mapWidget = MapWidgetFactory.buildMapView(this);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonCancel:
                finish();
                break;

            case R.id.buttonImport:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Title");

                final ViewGroup group = new LinearLayout(this);
                final EditText input = new EditText(this);

                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setText("14529289");
                group.addView(input);

                builder.setView(group);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        downloadCrag(input.getText().toString());
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });

                builder.show();
                break;
        }
    }

    private void downloadCrag(final String areaID) {
        Constants.WEB_EXECUTOR.execute(new Runnable() {
            public void run() {
                try {
                    OkHttpClient httpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30,
                            TimeUnit.SECONDS).cookieJar(new CookieJar() {
                        private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

                        @Override
                        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                            cookieStore.put(url.topPrivateDomain(), cookies);
                        }

                        @Override
                        public List<Cookie> loadForRequest(HttpUrl url) {
                            List<Cookie> cookies = cookieStore.get(url.topPrivateDomain());
                            return cookies != null ? cookies : new ArrayList<Cookie>();
                        }
                    }).build();

                    //setup a web cookie
                    Request request = new Request.Builder()
                            .url("https://www.thecrag.com/")
                            .get()
                            .build();

                    Response response = httpClient.newCall(request).execute();

                    //setup a web cookie
                    request = new Request.Builder()
                            .url("https://www.thecrag.com/api/node/id/" + areaID)
                            .get()
                            .build();

                    response = httpClient.newCall(request).execute();

                    DownloadedData cragData = new DownloadedData();
                    cragData.theCrag = new JSONObject(response.body().string());

                    JSONArray routes = cragData.theCrag.getJSONObject("data").getJSONArray("childIDs");
                    for (int i=0; i < routes.length(); ++i) {
                        request = new Request.Builder()
                                .url("https://www.thecrag.com/api/node/id/" + routes.getString(i))
                                .get()
                                .build();

                        response = httpClient.newCall(request).execute();
                        cragData.theRoutes.add(new JSONObject(response.body().string()));
                    };
                    System.out.println(cragData.toString());

                    buildClimbingNodes(cragData, mapWidget.getOsmMap().getMapCenter());
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void buildClimbingNodes(ImporterActivity.DownloadedData cragData, IGeoPoint coord) throws JSONException {
        Map<Long, MapViewWidget.MapMarkerElement> result = new HashMap<>();

        JSONObject overpassJson = new JSONObject();
        overpassJson.put("elements", new JSONArray());

        for (JSONObject node: cragData.theRoutes) {
            overpassJson.getJSONArray("elements").put(convertToOverpass(node.getJSONObject("data")));
        }

        System.out.println(overpassJson.toString());
    }

    private JSONObject convertToOverpass(JSONObject node) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("type", "node");

        JSONObject tags = new JSONObject();
        result.put(GeoNode.KEY_TAGS, tags);

        tags.put(GeoNode.KEY_CLIMBING, "route_bottom");
        tags.put(GeoNode.KEY_SPORT, "climbing");
        tags.put(GeoNode.KEY_NAME, node.get("name"));
        tags.put(String.format(GeoNode.KEY_GRADE_TAG, "uiaa"), GradeSystem.uiaa.getGrade(GradeSystem.yds.indexOf(node.getJSONObject("gradeAtom").getString("grade"))));

        GeoNode.ClimbingStyle style = GeoNode.ClimbingStyle.valueOf(node.getString("style").toLowerCase());
        tags.put(GeoNode.KEY_CLIMBING + GeoNode.KEY_SEPARATOR + style.name(), "yes");

        return result;
    }
}
