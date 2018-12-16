package com.climbtheworld.app.intercon.states;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.audiotools.IRecordingListener;
import com.climbtheworld.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

import needle.Needle;

public class InterconState {
    List<IRecordingListener> listeners = new ArrayList<>();
    public static class FeedBackDisplay {
        public ProgressBar energyDisplay;
        public ImageView mic;
    }

    public Activity parent;

    FeedBackDisplay feedbackView = new FeedBackDisplay();
    double lastPeak = 0f;

    public void addListener(IRecordingListener listener) {
        listeners.add(listener);
    }

    public void removeListener(IRecordingListener listener) {
        listeners.remove(listener);
    }

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

    void sendData(byte[] frame, final int numberOfReadBytes) {
        if (numberOfReadBytes > 0) {
            final byte[] result = new byte[numberOfReadBytes];
            System.arraycopy(frame, 0, result, 0, numberOfReadBytes);

            for (final IRecordingListener listener : listeners) {
                Constants.AUDIO_TASK_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onAudio(result, numberOfReadBytes, 0, 0);
                    }
                });
            }
        }
    }
}
