package com.climbtheworld.app.storage.offline.importer;

import java.io.IOException;

/** Overpass answered with an HTTP error status. */
public class OverpassHttpException extends IOException {
	private final int statusCode;

	public OverpassHttpException(int statusCode, String statusMessage) {
		super("Overpass request failed: " + statusCode + " " + statusMessage);
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}

	/** Rate limited or overloaded; trying again later is likely to succeed. */
	public boolean isServerBusy() {
		return isServerBusy(statusCode);
	}

	static boolean isServerBusy(int statusCode) {
		return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
	}
}
