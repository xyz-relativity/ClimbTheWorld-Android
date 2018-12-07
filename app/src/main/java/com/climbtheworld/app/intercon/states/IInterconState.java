package com.climbtheworld.app.intercon.states;

import android.graphics.Color;

public interface IInterconState {
    int DISABLED_MIC_COLOR = Color.argb(200, 255, 255, 255);
    int BROADCASTING_MIC_COLOR = Color.argb(200, 0, 255, 0);
    int HANDSFREE_MIC_COLOR = Color.argb(200, 255, 255, 0);
    void finish();
}
