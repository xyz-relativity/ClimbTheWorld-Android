package com.climbtheworld.app.intercon.states;

import android.app.Activity;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.climbtheworld.app.R;

import needle.Needle;

public class InterconState {
    public static class FeedBackDisplay {
        public ProgressBar energyDisplay;
        public ImageView mic;
    }

    public Activity parent;

    FeedBackDisplay feedbackView = new FeedBackDisplay();
    double lastPeak = 0f;

    InterconState(Activity parent) {
        this.parent = parent;
        feedbackView.energyDisplay = parent.findViewById(R.id.progressBar);
        feedbackView.mic = parent.findViewById(R.id.microphoneIcon);
    }

    void updateEnergy(double energy) {
        double peak = energy;
        if(lastPeak > peak) {
            peak = lastPeak * 0.575f;
        }

        lastPeak = peak;

        final double displayPeak = peak;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                feedbackView.energyDisplay.setProgress((int) (displayPeak * 100));
            }
        });
    }

    void runOnUiThread(Runnable r) {
        Needle.onMainThread().execute(r);
    }
}
