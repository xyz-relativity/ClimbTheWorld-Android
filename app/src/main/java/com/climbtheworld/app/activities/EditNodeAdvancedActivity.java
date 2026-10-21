package com.climbtheworld.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.editor.ITags;
import com.climbtheworld.app.map.editor.OtherTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.EdgeToEdge;

import org.json.JSONException;

public class EditNodeAdvancedActivity extends AppCompatActivity implements View.OnClickListener {
	GeoNode poi;
	ITags tags;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_node_advanced);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

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
		int id = view.getId();
		if (id == R.id.buttonCancel) {
			setResult(RESULT_CANCELED);
			finish();
		} else if (id == R.id.buttonSave) {
			tags.saveToNode(poi);

			Intent intent = new Intent();
			intent.putExtra("nodeJson", poi.toJSONString());
			setResult(RESULT_OK, intent);
			finish();
		}
	}
}
