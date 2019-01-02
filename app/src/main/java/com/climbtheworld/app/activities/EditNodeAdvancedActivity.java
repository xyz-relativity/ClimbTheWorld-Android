package com.climbtheworld.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class EditNodeAdvancedActivity extends AppCompatActivity implements View.OnClickListener {
    LinearLayout scrollViewContainer;
    GeoNode poi;

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

        scrollViewContainer = findViewById(R.id.scrollViewContainer);

        JSONObject tags = poi.getTags();
        Iterator<String> keyIt = tags.keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();

            LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View tagView = inflater.inflate(R.layout.list_item_json_entry, null);

            ((EditText)tagView.findViewById(R.id.editTag)).setText(key);
            ((EditText)tagView.findViewById(R.id.editValue)).setText(tags.optString(key));
            tagView.findViewById(R.id.buttonDeleteField).setOnClickListener(this);

            scrollViewContainer.addView(tagView, scrollViewContainer.getChildCount() - 1);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonDeleteField:
                scrollViewContainer.removeView(((ViewGroup) view.getParent()));
            break;

            case R.id.buttonAddNew:
                LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View tagView = inflater.inflate(R.layout.list_item_json_entry, null);

                ((EditText)tagView.findViewById(R.id.editTag)).setText("");
                ((EditText)tagView.findViewById(R.id.editValue)).setText("");
                tagView.findViewById(R.id.buttonDeleteField).setOnClickListener(this);

                scrollViewContainer.addView(tagView);

                scrollViewContainer.post(new Runnable() {
                    @Override
                    public void run() {
                        ((ScrollView)scrollViewContainer.getParent()).fullScroll(View.FOCUS_DOWN);
                    }
                });
            break;

            case R.id.buttonCancel:
                setResult(RESULT_CANCELED);
                finish();
                break;

            case R.id.buttonSave:
                JSONObject newTags = new JSONObject();
                for (int i = 0; i < scrollViewContainer.getChildCount(); i++) {
                    View child = scrollViewContainer.getChildAt(i);

                    if (!(child instanceof LinearLayout)) {
                        continue;
                    }

                    try {
                        String key = ((EditText)child.findViewById(R.id.editTag)).getText().toString();
                        String value = ((EditText)child.findViewById(R.id.editValue)).getText().toString();
                        if (!key.isEmpty()) {
                            newTags.put(key, value);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                poi.setTags(newTags);

                Intent intent = new Intent();
                intent.putExtra("nodeJson", poi.toJSONString());
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
    }
}
