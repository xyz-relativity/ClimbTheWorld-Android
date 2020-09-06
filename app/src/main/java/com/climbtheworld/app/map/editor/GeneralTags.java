package com.climbtheworld.app.map.editor;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

public class GeneralTags extends Tags implements ITags {
    private EditText editTopoName;
    private EditText editElevation;
    private EditText editDescription;
    private EditText editLatitude;
    private EditText editLongitude;
    private GeoNode editPoi;

    public GeneralTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_general);

        this.editPoi = editNode;

        editTopoName = parent.findViewById(R.id.editTopoName);
        editElevation = parent.findViewById(R.id.editElevation);
        editDescription = parent.findViewById(R.id.editDescription);
        editLatitude = parent.findViewById(R.id.editLatitude);
        editLongitude = parent.findViewById(R.id.editLongitude);

        editLatitude.setText(String.format(Locale.getDefault(), "%f", editNode.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), "%f", editNode.decimalLongitude));

        editLatitude.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    try {
                        editPoi.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                                Double.parseDouble(editLongitude.getText().toString()),
                                Double.parseDouble(editElevation.getText().toString()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(parent, "Failed to parse coordinates.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }
        });
        editLongitude.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    try {
                        editPoi.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                                Double.parseDouble(editLongitude.getText().toString()),
                                Double.parseDouble(editElevation.getText().toString()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(parent, "Failed to parse coordinates.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }
        });

        editTopoName.setText(editNode.getName());
        editTopoName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                saveToNode(editNode);
                ((EditNodeActivity)parent).updateMapMarker();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        editElevation.setText(String.format(Locale.getDefault(), "%.2f", editNode.elevationMeters));
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
        editDescription.setText(editNode.getKey(GeoNode.KEY_DESCRIPTION));
    }

    @Override
    public boolean saveToNode(GeoNode editNode) {
        if (isVisible()) {
            try {
                editNode.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                        Double.parseDouble(editLongitude.getText().toString()),
                        Double.parseDouble(editElevation.getText().toString()));

                editNode.setName(editTopoName.getText().toString());
                editNode.setKey(GeoNode.KEY_DESCRIPTION, editDescription.getText().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(parent, "Failed to parse coordinates.", Toast.LENGTH_LONG).show();
                return false;
            }
        }

        return true;
    }

    @Override
    public void cancelNode(GeoNode editNode) {

    }

    public void updateLocation() {
        editLatitude.setText(String.format(Locale.getDefault(), "%f", editPoi.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), "%f", editPoi.decimalLongitude));
    }
}
