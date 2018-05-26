package com.climbtheworld.app.storage;

import java.util.Map;

/**
 * Created by xyz on 2/9/18.
 */

public interface IDataManagerEventListener {
    void onProgress(int progress, boolean hasChanges, Map<String, Object> results);
}
