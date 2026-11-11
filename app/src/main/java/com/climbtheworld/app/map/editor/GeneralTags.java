package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.TextWatchers;

/**
 * The editors' general tags, the description and the website, after the crags or areas the
 * element is in. The name has its own section right after the type, see NameTags, and a node's
 * position its own too, see LocationTags.
 */
public class GeneralTags extends Tags implements ITags {
	private final EditText editDescription;
	private final EditText editWebsite;

	public GeneralTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_general);

		editDescription = parent.findViewById(R.id.editDescription);
		editWebsite = parent.findViewById(R.id.editWebsite);

		TextView description = parent.findViewById(R.id.description);
		editDescription.addTextChangedListener(TextWatchers.afterTextChanged(s -> {
			description.setText(parent.getString(R.string.description_num_characters, editDescription.getText().length()));
			editDescription.setHint(parent.getString(R.string.description_num_characters, editDescription.getText().length()));
		}));
		editDescription.setText(editNode.getKey(ClimbingTags.KEY_DESCRIPTION));
		editWebsite.setText(editNode.getWebsite());
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		if (isVisible()) {
			editNode.setKey(ClimbingTags.KEY_DESCRIPTION, editDescription.getText().toString());
			editNode.setWebsite(editWebsite.getText().toString());
		}

		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {

	}
}
