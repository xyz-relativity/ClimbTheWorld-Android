package com.climbtheworld.app.map.editor;

import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;

/**
 * The phone and address of artificial climbing. The OSM wiki's Climbing page gives routes, crags
 * and areas neither; their website is in GeneralTags.
 */
public class ContactTags extends Tags implements ITags {
	private final EditText editNo;
	private final EditText editPhone;
	private final EditText editStreet;
	private final EditText editUnit;
	private final EditText editCity;
	private final EditText editProvince;
	private final EditText editPostcode;

	public ContactTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_contact);

		this.editPhone = container.findViewById(R.id.editPhone);

		this.editNo = container.findViewById(R.id.editNo);
		this.editStreet = container.findViewById(R.id.editStreet);
		this.editUnit = container.findViewById(R.id.editUnit);
		this.editCity = container.findViewById(R.id.editCity);
		this.editProvince = container.findViewById(R.id.editProvince);
		this.editPostcode = container.findViewById(R.id.editPostcode);

		editPhone.setText(editNode.getPhone());

		editNo.setText(editNode.getKey(ClimbingTags.KEY_ADDR_STREETNO));
		editStreet.setText(editNode.getKey(ClimbingTags.KEY_ADDR_STREET));
		editUnit.setText(editNode.getKey(ClimbingTags.KEY_ADDR_UNIT));
		editCity.setText(editNode.getKey(ClimbingTags.KEY_ADDR_CITY));
		editProvince.setText(editNode.getKey(ClimbingTags.KEY_ADDR_PROVINCE));
		editPostcode.setText(editNode.getKey(ClimbingTags.KEY_ADDR_POSTCODE));
	}

	@Override
	public boolean saveToNode(GeoNode editNode) {
		editNode.setPhone(editPhone.getText().toString());

		editNode.setKey(ClimbingTags.KEY_ADDR_STREETNO, editNo.getText().toString());
		editNode.setKey(ClimbingTags.KEY_ADDR_STREET, editStreet.getText().toString());
		editNode.setKey(ClimbingTags.KEY_ADDR_UNIT, editUnit.getText().toString());
		editNode.setKey(ClimbingTags.KEY_ADDR_CITY, editCity.getText().toString());
		editNode.setKey(ClimbingTags.KEY_ADDR_PROVINCE, editProvince.getText().toString());
		editNode.setKey(ClimbingTags.KEY_ADDR_POSTCODE, editPostcode.getText().toString());
		return true;
	}

	@Override
	public void cancelNode(GeoNode editNode) {
		editNode.setPhone(null);

		editNode.setKey(ClimbingTags.KEY_ADDR_STREETNO, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_STREET, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_UNIT, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_CITY, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_PROVINCE, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_POSTCODE, null);
	}
}
