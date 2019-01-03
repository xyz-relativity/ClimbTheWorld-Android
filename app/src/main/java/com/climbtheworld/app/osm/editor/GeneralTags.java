package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.Locale;

public class GeneralTags extends Tags implements ITags {

    public GeneralTags (GeoNode poi, final Activity parent, ViewGroup container) {
        this.container = container;

        container.addView(parent.getLayoutInflater().inflate(R.layout.fragment_edit_general, container, false));
        hideTags();

        EditText editTopoName = parent.findViewById(R.id.editTopoName);
        EditText editElevation = parent.findViewById(R.id.editElevation);
        final EditText editDescription = parent.findViewById(R.id.editDescription);
        EditText editLatitude = parent.findViewById(R.id.editLatitude);
        EditText editLongitude = parent.findViewById(R.id.editLongitude);

        editLatitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLongitude));

        editTopoName.setText(poi.getName());
        editElevation.setText(String.format(Locale.getDefault(), "%.2f", poi.elevationMeters));
        editDescription.addTextChangedListener(new TextWatcher() {
            TextView description = parent.findViewById(R.id.description);
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                description.setText(parent.getString(R.string.description_num_characters, editDescription.getText().length()));
                editDescription.setHint(parent.getString(R.string.description_num_characters, editDescription.getText().length()));
            }
        });
        editDescription.setText(poi.getDescription());
    }

    @Override
    public void SaveToNode(GeoNode poi) {

    }
}
