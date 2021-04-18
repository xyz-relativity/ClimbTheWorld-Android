package com.climbtheworld.app.map.editor;

import android.app.TimePickerDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.Calendar;

public class ContactTags extends Tags implements ITags {
	private final EditText editNo;
	private final EditText editWebsite;
	private final EditText editPhone;
	private final EditText editStreet;
	private final EditText editUnit;
	private final EditText editCity;
	private final EditText editProvince;
	private final EditText editPostcode;
	private final EditText editMondayStartTime;
	private final ViewGroup buttonMondayStartTime;

	public ContactTags(GeoNode editNode, final AppCompatActivity parent, ViewGroup container) {
		super(parent, container, R.layout.fragment_edit_contact);

		this.editWebsite = container.findViewById(R.id.editWebsite);
		this.editPhone = container.findViewById(R.id.editPhone);

		this.editNo = container.findViewById(R.id.editNo);
		this.editStreet = container.findViewById(R.id.editStreet);
		this.editUnit = container.findViewById(R.id.editUnit);
		this.editCity = container.findViewById(R.id.editCity);
		this.editProvince = container.findViewById(R.id.editProvince);
		this.editPostcode = container.findViewById(R.id.editPostcode);

		this.editMondayStartTime = container.findViewById(R.id.editMondayStartTime);
		this.buttonMondayStartTime = container.findViewById(R.id.buttonStartTimePick);

		buttonMondayStartTime.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Calendar mcurrentTime = Calendar.getInstance();
				int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
				int minute = mcurrentTime.get(Calendar.MINUTE);
				TimePickerDialog mTimePicker;
				mTimePicker = new TimePickerDialog(parent, new TimePickerDialog.OnTimeSetListener() {
					@Override
					public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
						editMondayStartTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute));
					}
				}, hour, minute, true);//Yes 24 hour time
				mTimePicker.setTitle("Select Time");
				mTimePicker.show();
			}
		});

		editWebsite.setText(editNode.getWebsite());
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
		editNode.setWebsite(editWebsite.getText().toString());
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
		editNode.setWebsite(null);
		editNode.setPhone(null);

		editNode.setKey(ClimbingTags.KEY_ADDR_STREETNO, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_STREET, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_UNIT, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_CITY, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_PROVINCE, null);
		editNode.setKey(ClimbingTags.KEY_ADDR_POSTCODE, null);
	}
}
