package com.climbtheworld.app.map.editor;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;

import java.math.BigDecimal;
import java.util.Locale;

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

		updateLocation();

		editLatitude.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View view, boolean b) {
				if (!b) {
					try {
						writeLocation(editPoi);
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
						writeLocation(editPoi);
					} catch (NumberFormatException e) {
						Toast.makeText(parent, "Failed to parse coordinates.", Toast.LENGTH_LONG).show();
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
				((EditNodeActivity) parent).updateMapMarker();
			}

			@Override
			public void afterTextChanged(Editable editable) {

			}
		});

		editElevation.setText(BigDecimal.valueOf(editNode.elevationMeters).stripTrailingZeros().toPlainString());
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
		editDescription.setText(editNode.getKey(ClimbingTags.KEY_DESCRIPTION));
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		if (isVisible()) {
			try {
				writeLocation(editNode);

				editNode.setName(editTopoName.getText().toString());
				editNode.setKey(ClimbingTags.KEY_DESCRIPTION, editDescription.getText().toString());
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

	/** Shows the edited node's position, with the 7 decimals OSM keeps. */
	public void updateLocation() {
		editLatitude.setText(String.format(Locale.ROOT, "%.7f", editPoi.decimalLatitude));
		editLongitude.setText(String.format(Locale.ROOT, "%.7f", editPoi.decimalLongitude));
	}

	private void writeLocation(GeoNode node) {
		node.updatePOILocation(parseDecimal(editLatitude), parseDecimal(editLongitude),
				parseDecimal(editElevation));
	}

	/** Takes a decimal comma too, which keyboards in some languages type. */
	private static double parseDecimal(EditText field) {
		return Double.parseDouble(field.getText().toString().trim().replace(',', '.'));
	}
}
