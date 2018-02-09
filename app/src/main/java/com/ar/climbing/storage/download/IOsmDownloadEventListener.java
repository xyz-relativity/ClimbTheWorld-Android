package com.ar.climbing.storage.download;

/**
 * Created by xyz on 2/9/18.
 */

public interface IOsmDownloadEventListener {
    public void onProgress(int progress, boolean done, boolean hasChanges);
}
