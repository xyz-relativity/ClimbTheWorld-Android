package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.TextWatchers;

/**
 * The editors' name section, right after the type: the name of the edited element, which its pin
 * shows on the editor's map as it is typed.
 */
public class NameTags extends Tags implements ITags {
	private final EditText editTopoName;

	public NameTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_name);

		editTopoName = parent.findViewById(R.id.editTopoName);
		editTopoName.setText(editNode.getName());
		editTopoName.addTextChangedListener(TextWatchers.afterTextChanged(editable -> {
			saveToNode(editNode);
			refreshEditor();
		}));
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		if (isVisible()) {
			editNode.setName(editTopoName.getText().toString());
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
}
