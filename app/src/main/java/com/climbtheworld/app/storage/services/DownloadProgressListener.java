package com.climbtheworld.app.storage.services;

public interface DownloadProgressListener {
    int PROGRESS_WAITING = -1;
    int PROGRESS_START = 0;
    int PROGRESS_DONE = 100;
    int PROGRESS_ERROR = -2;

    void onProgressChanged(String eventOwner, int progressEvent);
}
