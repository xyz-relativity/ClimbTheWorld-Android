package com.climbtheworld.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class EditNodeAdvancedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_node_advanced);

        Intent intent = getIntent();
        String nodeJson = intent.getStringExtra("nodeJson");
        GeoNode poi = null;
        try {
            poi = new GeoNode(nodeJson);
        } catch (JSONException e) {
            finish();
            return;
        }

        LinearLayout scrollViewContainer = findViewById(R.id.scrollViewContainer);

        JSONObject tags = poi.getTags();
        Iterator<String> keyIt = tags.keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();

            LinearLayout tagView = new LinearLayout(this);
            tagView.setOrientation(LinearLayout.HORIZONTAL);
            tagView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            EditText tagName = new EditText(this);
            tagName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            EditText tagValue = new EditText(this);
            tagValue.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            int padding = (int)Globals.sizeToDPI(this, 5);
            tagName.setPaddingRelative(padding, padding, padding, padding);
            tagValue.setPaddingRelative(padding, padding, padding, padding);

            tagName.setText(key);
            tagValue.setText(tags.optString(key));

            tagView.addView(tagName);
            tagView.addView(tagValue);

            scrollViewContainer.addView(tagView);
        }
    }
}
