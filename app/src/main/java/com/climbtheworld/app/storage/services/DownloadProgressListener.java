package com.climbtheworld.app.storage.services;

public interface DownloadProgressListener {
	int STATUS_WAITING = -1;
	int STATUS_DONE = -2;
	int STATUS_ERROR = -3;
	/** Cancelled by the user. */
	int STATUS_CANCELLED = -4;

	void onProgressChanged(String eventOwner, int progressEvent);

	/**
	 * Receives the last state of each queued or running download once, when the listener is
	 * added. Finished downloads are not replayed: the database says which countries are installed.
	 */
	void onProgressRestored(String eventOwner, int progressEvent);
}
