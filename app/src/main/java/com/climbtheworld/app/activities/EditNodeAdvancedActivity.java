package com.climbtheworld.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import com.climbtheworld.app.R;
import com.climbtheworld.app.osm.editor.ITags;
import com.climbtheworld.app.osm.editor.OtherTags;
import com.climbtheworld.app.storage.database.GeoNode;

import org.json.JSONException;

public class EditNodeAdvancedActivity extends AppCompatActivity implements View.OnClickListener {
    GeoNode poi;
    ITags tags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_node_advanced);

        Intent intent = getIntent();
        String nodeJson = intent.getStringExtra("nodeJson");
        poi = null;
        try {
            poi = new GeoNode(nodeJson);
        } catch (JSONException e) {
            finish();
            return;
        }

        ViewGroup tagsView = findViewById(R.id.tagsView);

        tags = new OtherTags(poi, this, tagsView);
        tags.showTags();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonCancel:
                setResult(RESULT_CANCELED);
                finish();
                break;

            case R.id.buttonSave:
                tags.saveToNode(poi);

                Intent intent = new Intent();
                intent.putExtra("nodeJson", poi.toJSONString());
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
    }
}
