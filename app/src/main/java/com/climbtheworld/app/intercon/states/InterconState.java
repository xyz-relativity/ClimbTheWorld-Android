package com.climbtheworld.app.intercon.states;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.climbtheworld.app.R;

import java.io.IOException;

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

    void sendData(byte[] frame, int numberOfReadBytes) {
//        for (BluetoothSocket socket: activeOutSockets) {
//            if (socket.isConnected()) {
//                try {
//                    socket.getOutputStream().write(frame);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
    }
}
