package com.climbtheworld.app.intercom.states;

import android.graphics.Color;

public interface IInterconState {
    int MIC_DISABLED_COLOR = Color.argb(200, 255, 255, 255);
    int MIC_BROADCASTING_COLOR = Color.argb(200, 0, 255, 0);
    int MIC_HANDSFREE_COLOR = Color.argb(200, 255, 255, 0);
    void finish();
}
