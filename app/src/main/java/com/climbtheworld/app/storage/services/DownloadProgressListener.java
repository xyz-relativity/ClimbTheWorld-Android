package com.climbtheworld.app.storage.services;

public interface DownloadProgressListener {
	int STATUS_WAITING = -1;
	int STATUS_DONE = -2;
	int STATUS_ERROR = -3;

	void onProgressChanged(String eventOwner, int progressEvent);
}
