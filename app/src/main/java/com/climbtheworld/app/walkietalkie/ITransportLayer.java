package com.climbtheworld.app.walkietalkie;

import android.graphics.Color;

public interface ITransportLayer {
	/** Sends the data to every peer connected through this transport. */
	void sendData(byte[] data);

	ClientType getType();

	LayerStatus getLayerStatus();

	void notifyConfigChange();

	/**
	 * Says whether the distance to the peers is being shown. Measuring it costs battery, so a
	 * transport only does it while it is shown, and forgets what it measured once it is not: a
	 * distance from before is not the one to show when the screen comes back.
	 */
	void setDistanceShown(boolean shown);

	void onDestroy();

	enum LayerStatus {
		GRAY(Color.argb(255, 255, 255, 255)),
		RED(Color.argb(255, 255, 0, 0)),
		YELLOW(Color.argb(255, 255, 255, 0)),
		GREEN(Color.argb(255, 0, 255, 0));

		public final int color;

		LayerStatus(int color) {
			this.color = color;
		}
	}
}
