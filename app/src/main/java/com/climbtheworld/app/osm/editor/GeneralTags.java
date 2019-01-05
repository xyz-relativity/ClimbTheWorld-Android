package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.Locale;

public class GeneralTags extends Tags implements ITags {
    private EditText editTopoName;
    private EditText editElevation;
    private EditText editDescription;
    private EditText editLatitude;
    private EditText editLongitude;
    private GeoNode editPoi;

    public GeneralTags (GeoNode poi, final Activity parent, ViewGroup container, final EditNodeActivity mapListener) {
        super(parent, container, R.layout.fragment_edit_general);

        this.editPoi = poi;

        editTopoName = parent.findViewById(R.id.editTopoName);
        editElevation = parent.findViewById(R.id.editElevation);
        editDescription = parent.findViewById(R.id.editDescription);
        editLatitude = parent.findViewById(R.id.editLatitude);
        editLongitude = parent.findViewById(R.id.editLongitude);

        editLatitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLongitude));

        editLatitude.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    editPoi.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                            Double.parseDouble(editLongitude.getText().toString()),
                            Double.parseDouble(editElevation.getText().toString()));
                    mapListener.updateMapMarker();
                }
            }
        });
        editLongitude.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    editPoi.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                            Double.parseDouble(editLongitude.getText().toString()),
                            Double.parseDouble(editElevation.getText().toString()));
                    mapListener.updateMapMarker();
                }
            }
        });

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
        editDescription.setText(poi.getKey(GeoNode.KEY_DESCRIPTION));
    }

    @Override
    public void SaveToNode(GeoNode editNode) {
        if (isVisible()) {
            editNode.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                Double.parseDouble(editLongitude.getText().toString()),
                Double.parseDouble(editElevation.getText().toString()));

            editNode.setName(editTopoName.getText().toString());
            editNode.setKey(GeoNode.KEY_DESCRIPTION, editDescription.getText().toString());
        }
    }

    public void updateLocation() {
        editLatitude.setText(String.format(Locale.getDefault(), "%f", editPoi.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), "%f", editPoi.decimalLongitude));
    }
}
