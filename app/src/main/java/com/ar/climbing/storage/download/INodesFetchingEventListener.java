package com.ar.climbing.storage.download;

import java.util.Map;

/**
 * Created by xyz on 2/9/18.
 */

public interface INodesFetchingEventListener {
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results);
}
