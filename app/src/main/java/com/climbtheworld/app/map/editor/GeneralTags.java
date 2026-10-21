package com.climbtheworld.app.map.editor;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.TextWatchers;

import java.math.BigDecimal;
import java.util.Locale;

public class GeneralTags extends Tags implements ITags {
	private EditText editTopoName;
	private EditText editElevation;
	private EditText editDescription;
	private EditText editWebsite;
	private EditText editLatitude;
	private EditText editLongitude;
	private GeoNode editPoi;
	private boolean showsLocation = true;

	public GeneralTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_general);

		this.editPoi = editNode;

		editTopoName = parent.findViewById(R.id.editTopoName);
		editElevation = parent.findViewById(R.id.editElevation);
		editDescription = parent.findViewById(R.id.editDescription);
		editWebsite = parent.findViewById(R.id.editWebsite);
		editLatitude = parent.findViewById(R.id.editLatitude);
		editLongitude = parent.findViewById(R.id.editLongitude);

		updateLocation();

		View.OnFocusChangeListener writeLocationOnLeave = (view, hasFocus) -> {
			if (!hasFocus) {
				try {
					writeLocation(editPoi);
				} catch (NumberFormatException e) {
					Toast.makeText(parent, R.string.coordinates_parse_failed, Toast.LENGTH_LONG).show();
				}
			}
		};
		editLatitude.setOnFocusChangeListener(writeLocationOnLeave);
		editLongitude.setOnFocusChangeListener(writeLocationOnLeave);

		editTopoName.setText(editNode.getName());
		editTopoName.addTextChangedListener(TextWatchers.afterTextChanged(editable -> {
			saveToNode(editNode);
			refreshEditor();
		}));

		// Empty for an element without an ele tag, so that 0 can mean sea level.
		editElevation.setText(editNode.getTags().has(ClimbingTags.KEY_ELEVATION)
				? BigDecimal.valueOf(editNode.elevationMeters).stripTrailingZeros().toPlainString() : "");
		TextView description = parent.findViewById(R.id.description);
		editDescription.addTextChangedListener(TextWatchers.afterTextChanged(s -> {
			description.setText(parent.getString(R.string.description_num_characters, editDescription.getText().length()));
			editDescription.setHint(parent.getString(R.string.description_num_characters, editDescription.getText().length()));
		}));
		editDescription.setText(editNode.getKey(ClimbingTags.KEY_DESCRIPTION));
		editWebsite.setText(editNode.getWebsite());
	}

	/** For relations, whose place is that of their members. */
	public void hideLocation() {
		showsLocation = false;
		parent.findViewById(R.id.layoutCoordinates).setVisibility(View.GONE);
		parent.findViewById(R.id.layoutElevation).setVisibility(View.GONE);
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		if (isVisible()) {
			try {
				if (showsLocation) {
					writeLocation(editNode);
				}

				editNode.setName(editTopoName.getText().toString());
				editNode.setKey(ClimbingTags.KEY_DESCRIPTION, editDescription.getText().toString());
				editNode.setWebsite(editWebsite.getText().toString());
			} catch (NumberFormatException e) {
				Toast.makeText(parent, R.string.coordinates_parse_failed, Toast.LENGTH_LONG).show();
				return false;
			}
		}

		return true;
	}

	/** Whether the name field has a name; when it doesn't, the field shows an error and takes focus. */
	public boolean checkNameGiven() {
		if (!editTopoName.getText().toString().trim().isEmpty()) {
			return true;
		}
		editTopoName.setError(parent.getString(R.string.name_required));
		editTopoName.requestFocus();
		return false;
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
				node.elevationMeters);
		String elevation = editElevation.getText().toString().trim();
		node.setElevation(elevation.isEmpty() ? null : parseDecimal(editElevation));
	}

	/** Takes a decimal comma too, which keyboards in some languages type. */
	private static double parseDecimal(EditText field) {
		return Double.parseDouble(field.getText().toString().trim().replace(',', '.'));
	}
}
