package com.climbtheworld.app.map.editor;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The node editor's position section: where the edited node is, its latitude and longitude, which
 * moving it on the map or in A.R. fills in, see updateLocation, and its elevation. Not for
 * relations, whose place is that of their members.
 */
public class LocationTags extends Tags implements ITags {
	private final EditText editElevation;
	private final EditText editLatitude;
	private final EditText editLongitude;
	private final GeoNode editPoi;

	public LocationTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_location);

		this.editPoi = editNode;

		editElevation = parent.findViewById(R.id.editElevation);
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

		// Empty for an element without an ele tag, so that 0 can mean sea level.
		editElevation.setText(editNode.getTags().has(ClimbingTags.KEY_ELEVATION)
				? BigDecimal.valueOf(editNode.elevationMeters).stripTrailingZeros().toPlainString() : "");
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		if (isVisible()) {
			try {
				writeLocation(editNode);
			} catch (NumberFormatException e) {
				Toast.makeText(parent, R.string.coordinates_parse_failed, Toast.LENGTH_LONG).show();
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
				node.elevationMeters);
		String elevation = editElevation.getText().toString().trim();
		node.setElevation(elevation.isEmpty() ? null : parseDecimal(editElevation));
	}

	/** Takes a decimal comma too, which keyboards in some languages type. */
	private static double parseDecimal(EditText field) {
		return Double.parseDouble(field.getText().toString().trim().replace(',', '.'));
	}
}
