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
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.widgets.MapViewWidget;
import com.climbtheworld.app.widgets.MapWidgetFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    public static final int IMPORT_COUNTER = 0;
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
                    if (IMPORT_COUNTER == 0) {
                        buildClimbingNodes(testData);
                        return;
                    }
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

                    buildClimbingNodes(cragData);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void buildClimbingNodes(ImporterActivity.DownloadedData cragData) throws JSONException {
        Map<Long, MapViewWidget.MapMarkerElement> nodesMap = new HashMap<>();

        Long nodeID = Globals.getNewNodeID();

        JSONObject overpassJson = new JSONObject();
        overpassJson.put("elements", new JSONArray());

        JSONObject theCrag = overpassCrag(cragData.theCrag, nodeID);
        nodeID --;

        for (JSONObject node: cragData.theRoutes) {
            overpassJson.getJSONArray("elements").put(convertToOverpass(node.getJSONObject("data"), theCrag, nodeID));
            nodeID --;
        }
        overpassJson.getJSONArray("elements").put(theCrag);

        DataManager.buildPOIsMapFromJsonString(overpassJson.toString(), nodesMap, "");

        System.out.println(overpassJson.toString());
        System.out.println(theCrag.toString());
    }

    private JSONObject overpassCrag(JSONObject node, long nodeID) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("type", "node");
        result.put("id", nodeID);
        JSONObject tags = new JSONObject();
        result.put(GeoNode.KEY_TAGS, tags);

        tags.put(GeoNode.KEY_CLIMBING, "crag");
        tags.put(GeoNode.KEY_SPORT, "climbing");
        tags.put(GeoNode.KEY_NAME, node.opt("name"));
        tags.put(GeoNode.KEY_ROUTES, "0");

        return result;
    }

    private JSONObject convertToOverpass(JSONObject node, JSONObject crag, long nodeID) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("type", "node");
        result.put("id", nodeID);

        JSONObject tags = new JSONObject();
        result.put(GeoNode.KEY_TAGS, tags);

        tags.put(GeoNode.KEY_CLIMBING, "route_bottom");
        tags.put(GeoNode.KEY_SPORT, "climbing");
        tags.put(GeoNode.KEY_NAME, node.opt("name"));

        String length = null;
        if (node.has("height")) {
            length = (String) node.getJSONArray("height").get(0);
            tags.put(GeoNode.KEY_LENGTH, length);
        }

        tags.put(GeoNode.KEY_BOLTS, node.opt("bolts"));

        if (node.has("pitches") && Integer.parseInt((String) node.get("pitches")) != 1) {
            tags.put(GeoNode.KEY_BOLTS, node.opt("pitches"));
        }

        Integer gradeIndex = null;
        if (node.getJSONObject("gradeAtom").has("grade")) {
            gradeIndex = GradeSystem.yds.indexOf(node.getJSONObject("gradeAtom").getString("grade"));
            tags.put(String.format(GeoNode.KEY_GRADE_TAG, "uiaa"), GradeSystem.uiaa.getGrade(gradeIndex));
        }

        GeoNode.ClimbingStyle style = null;
        if (node.has("style")) {
            style = GeoNode.ClimbingStyle.valueOf(node.getString("style").toLowerCase());
            tags.put(GeoNode.KEY_CLIMBING + GeoNode.KEY_SEPARATOR + style.name(), "yes");
        }

        upgradeCrag(gradeIndex, length, style, crag);

        return result;
    }

    private void upgradeCrag(Integer gradeIndex, String length, GeoNode.ClimbingStyle style, JSONObject crag) throws JSONException {
        JSONObject tags = crag.getJSONObject(GeoNode.KEY_TAGS);
        tags.put(GeoNode.KEY_ROUTES, Integer.parseInt(tags.getString(GeoNode.KEY_ROUTES)) + 1);

        if (style != null) {
            tags.put(GeoNode.KEY_CLIMBING + GeoNode.KEY_SEPARATOR + style.name(), "yes");
        }

        if (length != null) {
            if (!tags.has(GeoNode.KEY_MAX_LENGTH)) {
                tags.put(GeoNode.KEY_MAX_LENGTH, length);
            }

            if (!tags.has(GeoNode.KEY_MIN_LENGTH)) {
                tags.put(GeoNode.KEY_MIN_LENGTH, length);
            }

            Double tmpLength = tags.getDouble(GeoNode.KEY_MAX_LENGTH);
            if (Double.parseDouble(length) > tmpLength) {
                tags.put(GeoNode.KEY_MAX_LENGTH, length);
            }

            tmpLength = tags.getDouble(GeoNode.KEY_MIN_LENGTH);
            if (Double.parseDouble(length) < tmpLength) {
                tags.put(GeoNode.KEY_MIN_LENGTH, length);
            }
        }

        if (gradeIndex != null && gradeIndex != -1) {
            String maxGradeKey = String.format(GeoNode.KEY_GRADE_TAG_MAX, "uiaa");
            String minGradeKey = String.format(GeoNode.KEY_GRADE_TAG_MIN, "uiaa");
            if (!tags.has(maxGradeKey)) {
                tags.put(maxGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
            }

            if (!tags.has(minGradeKey)) {
                tags.put(minGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
            }

            Integer tmpIndex = GradeSystem.uiaa.indexOf(tags.getString(maxGradeKey));
            if (gradeIndex > tmpIndex) {
                tags.put(maxGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
            }

            tmpIndex = GradeSystem.uiaa.indexOf(tags.getString(minGradeKey));
            if (gradeIndex < tmpIndex) {
                tags.put(minGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
            }
        }
    }














    ///testdata
    private static DownloadedData testData;

    static {
        try {
            testData = new DownloadedData();
            testData.theCrag = new JSONObject("{\n" +
                    "      \"data\": {\n" +
                    "        \"ascentCount\": \"43\",\n" +
                    "        \"asciiName\": \"The Dark Side\",\n" +
                    "        \"averageHeight\": [\n" +
                    "          21,\n" +
                    "          \"m\"\n" +
                    "        ],\n" +
                    "        \"childIDs\": [\n" +
                    "          \"2310354318\",\n" +
                    "          \"2310354417\",\n" +
                    "          \"2310354519\",\n" +
                    "          \"2310354633\",\n" +
                    "          \"2310354768\",\n" +
                    "          \"2310354900\",\n" +
                    "          \"2310354999\",\n" +
                    "          \"2310355098\",\n" +
                    "          \"17098981\",\n" +
                    "          \"1475763669\",\n" +
                    "          \"2310361644\",\n" +
                    "          \"17098927\",\n" +
                    "          \"2310362544\",\n" +
                    "          \"2310362643\",\n" +
                    "          \"1475763774\",\n" +
                    "          \"2310366030\",\n" +
                    "          \"2310366174\",\n" +
                    "          \"2310366273\",\n" +
                    "          \"2310366372\",\n" +
                    "          \"2310366471\",\n" +
                    "          \"2310366570\",\n" +
                    "          \"2310366669\",\n" +
                    "          \"1957620387\",\n" +
                    "          \"2310370254\",\n" +
                    "          \"1495441935\",\n" +
                    "          \"2310371646\",\n" +
                    "          \"2724390285\",\n" +
                    "          \"2724390537\",\n" +
                    "          \"2724391023\",\n" +
                    "          \"2724392262\",\n" +
                    "          \"2724393753\",\n" +
                    "          \"2724394488\",\n" +
                    "          \"2724395004\",\n" +
                    "          \"2724395238\",\n" +
                    "          \"2724395517\",\n" +
                    "          \"2724396516\",\n" +
                    "          \"2724397752\"\n" +
                    "        ],\n" +
                    "        \"depth\": \"6\",\n" +
                    "        \"displayAverageHeight\": [\n" +
                    "          21,\n" +
                    "          \"m\"\n" +
                    "        ],\n" +
                    "        \"hasUnarchivedChildren\": 1,\n" +
                    "        \"hide\": {},\n" +
                    "        \"id\": \"14529313\",\n" +
                    "        \"kudos\": \"82\",\n" +
                    "        \"lastPDFStaticDate\": \"2020-02-21T21:17:07Z\",\n" +
                    "        \"lastPDFStaticSize\": \"524375\",\n" +
                    "        \"locatedness\": 240,\n" +
                    "        \"maxPop\": 30,\n" +
                    "        \"name\": \"The Dark Side\",\n" +
                    "        \"numberPhotos\": 1,\n" +
                    "        \"numberRoutes\": \"37\",\n" +
                    "        \"parentID\": \"195029640\",\n" +
                    "        \"permitNode\": {\n" +
                    "          \"node\": {\n" +
                    "            \"id\": \"11900101\"\n" +
                    "          }\n" +
                    "        },\n" +
                    "        \"priceCategory\": \"Medium\",\n" +
                    "        \"redirectStubs\": [],\n" +
                    "        \"seasonality\": [\n" +
                    "          1,\n" +
                    "          0,\n" +
                    "          3,\n" +
                    "          2,\n" +
                    "          3,\n" +
                    "          1,\n" +
                    "          4,\n" +
                    "          2,\n" +
                    "          1,\n" +
                    "          18,\n" +
                    "          1,\n" +
                    "          6\n" +
                    "        ],\n" +
                    "        \"subType\": \"Cliff\",\n" +
                    "        \"tlc\": {\n" +
                    "          \"node\": {\n" +
                    "            \"id\": \"11900101\"\n" +
                    "          }\n" +
                    "        },\n" +
                    "        \"type\": \"area\",\n" +
                    "        \"urlAncestorStub\": \"united-states\\/red-river-gorge\"\n" +
                    "      },\n" +
                    "      \"query\": {\n" +
                    "        \"entity\": \"node\",\n" +
                    "        \"id\": \"14529313\",\n" +
                    "        \"request\": \"id\"\n" +
                    "      }\n" +
                    "    }");

            testData.theRoutes.add(new JSONObject("{\n" +
                    "      \"data\": {\n" +
                    "        \"asciiName\": \"Dagobah\",\n" +
                    "        \"context\": \"US\",\n" +
                    "        \"cragScore\": 1.99,\n" +
                    "        \"depth\": \"7\",\n" +
                    "        \"displayHeight\": [\n" +
                    "          24,\n" +
                    "          \"m\"\n" +
                    "        ],\n" +
                    "        \"flags\": {\n" +
                    "          \"IsFree\": 1,\n" +
                    "          \"IsSport\": 1\n" +
                    "        },\n" +
                    "        \"grade\": \"5.12a\",\n" +
                    "        \"gradeAtom\": {\n" +
                    "          \"context\": \"US\",\n" +
                    "          \"grade\": \"5.12a\",\n" +
                    "          \"gradeBand\": 3,\n" +
                    "          \"gradeInContext\": \"5.12a\",\n" +
                    "          \"gradeStyle\": \"Free\",\n" +
                    "          \"internal\": [\n" +
                    "            289,\n" +
                    "            300\n" +
                    "          ],\n" +
                    "          \"type\": \"grade\"\n" +
                    "        },\n" +
                    "        \"gradeBand\": 3,\n" +
                    "        \"gradeInContext\": \"5.12a\",\n" +
                    "        \"gradeStyle\": \"Free\",\n" +
                    "        \"height\": [\n" +
                    "          \"24.38\",\n" +
                    "          \"m\"\n" +
                    "        ],\n" +
                    "        \"hide\": {},\n" +
                    "        \"id\": \"2310354318\",\n" +
                    "        \"name\": \"Dagobah\",\n" +
                    "        \"parentID\": \"14529313\",\n" +
                    "        \"pitches\": \"1\",\n" +
                    "        \"priceCategory\": \"Medium\",\n" +
                    "        \"rawGrade\": [\n" +
                    "          289,\n" +
                    "          300\n" +
                    "        ],\n" +
                    "        \"stars\": \"0\",\n" +
                    "        \"style\": \"Sport\",\n" +
                    "        \"tlc\": {\n" +
                    "          \"node\": {\n" +
                    "            \"id\": \"11900101\"\n" +
                    "          }\n" +
                    "        },\n" +
                    "        \"type\": \"route\",\n" +
                    "        \"urlAncestorStub\": \"united-states\\/red-river-gorge\"\n" +
                    "      },\n" +
                    "      \"query\": {\n" +
                    "        \"entity\": \"node\",\n" +
                    "        \"id\": \"2310354318\",\n" +
                    "        \"request\": \"id\"\n" +
                    "      }\n" +
                    "    }"));

            testData.theRoutes.add(new JSONObject("{\n" +
                    "      \"data\": {\n" +
                    "        \"asciiName\": \"Overdrive\",\n" +
                    "        \"canDelete\": 1,\n" +
                    "        \"context\": \"US\",\n" +
                    "        \"cragScore\": 1,\n" +
                    "        \"depth\": \"7\",\n" +
                    "        \"displayHeight\": [\n" +
                    "          21,\n" +
                    "          \"m\"\n" +
                    "        ],\n" +
                    "        \"flags\": {\n" +
                    "          \"IsFree\": 1,\n" +
                    "          \"IsTrad\": 1\n" +
                    "        },\n" +
                    "        \"grade\": \"5.9\",\n" +
                    "        \"gradeAtom\": {\n" +
                    "          \"context\": \"US\",\n" +
                    "          \"grade\": \"5.9\",\n" +
                    "          \"gradeBand\": 2,\n" +
                    "          \"gradeInContext\": \"5.9\",\n" +
                    "          \"gradeStyle\": \"Free\",\n" +
                    "          \"internal\": [\n" +
                    "            161,\n" +
                    "            180\n" +
                    "          ],\n" +
                    "          \"type\": \"grade\"\n" +
                    "        },\n" +
                    "        \"gradeBand\": 2,\n" +
                    "        \"gradeInContext\": \"5.9\",\n" +
                    "        \"gradeStyle\": \"Free\",\n" +
                    "        \"height\": [\n" +
                    "          \"21.34\",\n" +
                    "          \"m\"\n" +
                    "        ],\n" +
                    "        \"hide\": {},\n" +
                    "        \"id\": \"220518252\",\n" +
                    "        \"name\": \"Overdrive\",\n" +
                    "        \"parentID\": \"14529289\",\n" +
                    "        \"pitches\": \"1\",\n" +
                    "        \"priceCategory\": \"Medium\",\n" +
                    "        \"rawGrade\": [\n" +
                    "          161,\n" +
                    "          180\n" +
                    "        ],\n" +
                    "        \"stars\": \"0\",\n" +
                    "        \"style\": \"Trad\",\n" +
                    "        \"tlc\": {\n" +
                    "          \"node\": {\n" +
                    "            \"id\": \"11900101\"\n" +
                    "          }\n" +
                    "        },\n" +
                    "        \"type\": \"route\",\n" +
                    "        \"urlAncestorStub\": \"united-states\\/red-river-gorge\"\n" +
                    "      },\n" +
                    "      \"query\": {\n" +
                    "        \"entity\": \"node\",\n" +
                    "        \"id\": \"220518252\",\n" +
                    "        \"request\": \"id\"\n" +
                    "      }\n" +
                    "    }"));

            testData.theRoutes.add(new JSONObject("\n" +
                    "\n" +
                    "{\n" +
                    "    \"data\": {\n" +
                    "        \"asciiName\": \"LOMM\",\n" +
                    "        \"bolts\": \"8\",\n" +
                    "        \"canDelete\": 1,\n" +
                    "        \"depth\": \"7\",\n" +
                    "        \"displayHeight\": [\n" +
                    "            24,\n" +
                    "            \"m\"\n" +
                    "        ],\n" +
                    "        \"flags\": {\n" +
                    "            \"IsProject\": 1,\n" +
                    "            \"IsSport\": 1\n" +
                    "        },\n" +
                    "        \"gradeAtom\": {\n" +
                    "            \"gradeBand\": 0,\n" +
                    "            \"gradeStyle\": \"Unknown\",\n" +
                    "            \"type\": \"grade\"\n" +
                    "        },\n" +
                    "        \"gradeBand\": 0,\n" +
                    "        \"gradeStyle\": \"Unknown\",\n" +
                    "        \"height\": [\n" +
                    "            \"24.38\",\n" +
                    "            \"m\"\n" +
                    "        ],\n" +
                    "        \"hide\": [],\n" +
                    "        \"id\": \"747679047\",\n" +
                    "        \"name\": \"LOMM\",\n" +
                    "        \"parentID\": \"501158076\",\n" +
                    "        \"pitches\": \"1\",\n" +
                    "        \"priceCategory\": \"Medium\",\n" +
                    "        \"stars\": \"0\",\n" +
                    "        \"style\": \"Sport\",\n" +
                    "        \"tlc\": {\n" +
                    "            \"node\": {\n" +
                    "                \"id\": \"11900101\"\n" +
                    "            }\n" +
                    "        },\n" +
                    "        \"type\": \"route\",\n" +
                    "        \"urlAncestorStub\": \"united-states\\/red-river-gorge\"\n" +
                    "    },\n" +
                    "    \"query\": {\n" +
                    "        \"entity\": \"node\",\n" +
                    "        \"id\": \"747679047\",\n" +
                    "        \"request\": \"id\"\n" +
                    "    }\n" +
                    "}\n" +
                    "\n"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
