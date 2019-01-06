package com.climbtheworld.app.osm.editor;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;

public class ContactTags extends Tags implements ITags {
    private final EditText editNo;
    private final EditText editWebsite;
    private final EditText editPhone;
    private final EditText editStreet;
    private final EditText editUnit;
    private final EditText editCity;
    private final EditText editProvince;
    private final EditText editPostcode;

    public ContactTags(GeoNode editNode, final Activity parent, ViewGroup container) {
        super(parent, container, R.layout.fragment_edit_contact);

        this.editWebsite = container.findViewById(R.id.editWebsite);
        this.editPhone = container.findViewById(R.id.editPhone);

        this.editNo = container.findViewById(R.id.editNo);
        this.editStreet = container.findViewById(R.id.editStreet);
        this.editUnit = container.findViewById(R.id.editUnit);
        this.editCity = container.findViewById(R.id.editCity);
        this.editProvince = container.findViewById(R.id.editProvince);
        this.editPostcode = container.findViewById(R.id.editPostcode);

        editWebsite.setText(editNode.getWebsite());
        editPhone.setText(editNode.getPhone());

        editNo.setText(editNode.getKey(GeoNode.KEY_ADDR_STREETNO));
        editStreet.setText(editNode.getKey(GeoNode.KEY_ADDR_STREET));
        editUnit.setText(editNode.getKey(GeoNode.KEY_ADDR_UNIT));
        editCity.setText(editNode.getKey(GeoNode.KEY_ADDR_CITY));
        editProvince.setText(editNode.getKey(GeoNode.KEY_ADDR_PROVINCE));
        editPostcode.setText(editNode.getKey(GeoNode.KEY_ADDR_POSTCODE));
    }

    @Override
    public void SaveToNode(GeoNode editNode) {
        if (isVisible()) {
            editNode.setWebsite(editWebsite.getText().toString());
            editNode.setPhone(editPhone.getText().toString());

            editNode.setKey(GeoNode.KEY_ADDR_STREETNO, editNo.getText().toString());
            editNode.setKey(GeoNode.KEY_ADDR_STREET, editStreet.getText().toString());
            editNode.setKey(GeoNode.KEY_ADDR_UNIT, editUnit.getText().toString());
            editNode.setKey(GeoNode.KEY_ADDR_CITY, editCity.getText().toString());
            editNode.setKey(GeoNode.KEY_ADDR_PROVINCE, editProvince.getText().toString());
            editNode.setKey(GeoNode.KEY_ADDR_POSTCODE, editPostcode.getText().toString());
        }
    }
}
