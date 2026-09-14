package com.climbtheworld.app.walkietalkie;

import android.graphics.Color;

public interface ITransportLayer {
	void sendData(byte[] data);

	ClientType getType();

	LayerStatus getLayerStatus();

	void notifyConfigChange();

	void onDestroy();

	enum LayerStatus {
		GRAY(Color.argb(200, 255, 255, 255)),
		RED(Color.argb(200, 255, 0, 0)),
		YELLOW(Color.argb(200, 255, 255, 0)),
		GREEN(Color.argb(200, 0, 255, 0));

		public final int color;

		LayerStatus(int color) {
			this.color = color;
		}
	}
}
