package com.climbtheworld.app.intercom.audiotools;

public class BasicVoiceDetector implements IVoiceDetector {
    private final int FRAME_HISTORY = 5;
    private double minEnergy = 0.05;
    private int tempIndex = 0;
    private double tempFloatBuffer[] = new double[FRAME_HISTORY];
    private boolean recording = false;

    public boolean onAudio(byte[] frame, int numberOfReadBytes, double energy)
    {
        // Analyze temp buffer.
        tempFloatBuffer[tempIndex % FRAME_HISTORY] = energy;
        float temp = 0.0f;
        for(int i = 0; i < FRAME_HISTORY; ++i )
            temp += tempFloatBuffer[i];

        if((temp >= 0 && temp <= minEnergy) && !recording)
        {
            tempIndex++;
            return recording;
        }

        if(temp > minEnergy && !recording)
        {
            recording = true;
        }

        if((temp >= 0 && temp <= minEnergy) && recording)
        {
            tempIndex++;
            recording = false;
        }

        tempIndex++;
        return recording;
    }
}
