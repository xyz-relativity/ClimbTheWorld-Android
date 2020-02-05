package com.climbtheworld.app.intercom.states;

import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

import needle.Needle;

public class InterconState {
    private List<IRecordingListener> listeners = new ArrayList<>();
    public static class FeedBackDisplay {
        ProgressBar energyDisplay;
        ImageView mic;
    }

    public AppCompatActivity parent;

    FeedBackDisplay feedbackView = new FeedBackDisplay();
    private double lastPeak = 0f;

    public void addListener(IRecordingListener listener) {
        listeners.add(listener);
    }

    public void removeListener(IRecordingListener listener) {
        listeners.remove(listener);
    }

    InterconState(AppCompatActivity parent) {
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

    void sendData(final byte[] frame, final int numberOfReadBytes) {
        if (numberOfReadBytes > 0) {

            for (final IRecordingListener listener : listeners) {
                Constants.AUDIO_TASK_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onRawAudio(frame, numberOfReadBytes);
                    }
                });
            }
        }
    }
}
