package com.climbtheworld.app.walkietalkie;

import com.climbtheworld.app.R;

public enum ClientType {
	WIFI_AWARE(R.drawable.ic_wifi_direct),
	WIFI_DIRECT(R.drawable.ic_wifi_direct),
	WIFI(R.drawable.ic_wifi),
	BLUETOOTH(R.drawable.ic_bluetooth),
	GENERIC(R.drawable.ic_person);

	public final int icoRes;

	ClientType(int icoRes) {
		this.icoRes = icoRes;
	}
}
