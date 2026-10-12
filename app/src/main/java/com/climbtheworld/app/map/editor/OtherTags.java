package com.climbtheworld.app.map.editor;

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class OtherTags extends Tags implements ITags, View.OnClickListener {

	private final GeoNode editPoi;
	private final LinearLayout scrollViewContainer;
	private final AppCompatActivity parent;
	/** The tags as listed when the rows were built, to tell which rows the user changed. */
	private final Map<String, String> listedTags = new HashMap<>();

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

			LayoutInflater inflater =
					(LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View tagView = inflater.inflate(R.layout.list_item_json_entry, null);

			((EditText) tagView.findViewById(R.id.editTag)).setText(key);
			((EditText) tagView.findViewById(R.id.editValue)).setText(tags.optString(key));
			tagView.findViewById(R.id.buttonDeleteField).setOnClickListener(this);

			scrollViewContainer.addView(tagView, scrollViewContainer.getChildCount() - 1);
			listedTags.put(key, tags.optString(key));
		}

	}

	@Override
	public void onClick(View view) {
		int id = view.getId();
		if (id == R.id.buttonDeleteField) {
			scrollViewContainer.removeView(((ViewGroup) view.getParent()));
		} else if (id == R.id.buttonAddNew) {
			LayoutInflater inflater =
					(LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View tagView = inflater.inflate(R.layout.list_item_json_entry, null);

			((EditText) tagView.findViewById(R.id.editTag)).setText("");
			((EditText) tagView.findViewById(R.id.editValue)).setText("");
			tagView.findViewById(R.id.buttonDeleteField).setOnClickListener(this);

			scrollViewContainer.addView(tagView);

			scrollViewContainer.post(new Runnable() {
				@Override
				public void run() {
					((ScrollView) scrollViewContainer.getParent()).fullScroll(View.FOCUS_DOWN);
				}
			});
		}
	}

	/**
	 * Writes only the rows the user changed, added or removed, so a tag another form section also
	 * shows, like the name, keeps that section's value unless it was changed here.
	 */
	@Override
	public boolean saveToNode(GeoNode editNode) {
		if (isVisible()) {
			Map<String, String> rows = new HashMap<>();
			for (int i = 0; i < scrollViewContainer.getChildCount(); i++) {
				View child = scrollViewContainer.getChildAt(i);

				if (!(child instanceof LinearLayout)) {
					continue;
				}

				String key = ((EditText) child.findViewById(R.id.editTag)).getText().toString().trim();
				String value = ((EditText) child.findViewById(R.id.editValue)).getText().toString();
				if (!key.isEmpty()) {
					rows.put(key, value);
				}
			}

			JSONObject tags = editNode.getTags();
			for (String key : listedTags.keySet()) {
				if (!rows.containsKey(key)) {
					tags.remove(key);
				}
			}
			for (Map.Entry<String, String> row : rows.entrySet()) {
				if (Objects.equals(listedTags.get(row.getKey()), row.getValue())) {
					continue;
				}
				try {
					if (row.getValue().isEmpty()) {
						tags.remove(row.getKey());
					} else {
						tags.put(row.getKey(), row.getValue());
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}

		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {

	}
}
