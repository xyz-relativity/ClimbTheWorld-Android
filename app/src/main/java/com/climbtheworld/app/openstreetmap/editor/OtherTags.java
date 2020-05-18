package com.climbtheworld.app.openstreetmap.editor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class OtherTags extends Tags implements ITags, View.OnClickListener {

    private GeoNode editPoi;
    private LinearLayout scrollViewContainer;
    private AppCompatActivity parent;

    public OtherTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_other_tags);

        this.editPoi = editNode;
        this.parent = parent;

        scrollViewContainer = parent.findViewById(R.id.scrollViewContainer);

        parent.findViewById(R.id.buttonAddNew).setOnClickListener(this);

        JSONObject tags = editPoi.getTags();
        Iterator<String> keyIt = tags.keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();

            LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
                LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
        }
    }

    @Override
    public boolean saveToNode(GeoNode editNode) {
        if (isVisible()) {
            JSONObject newTags = editNode.getTags();
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
            editNode.setTags(newTags);
        }

        return true;
    }

    @Override
    public void cancelNode(GeoNode editNode) {

    }
}
