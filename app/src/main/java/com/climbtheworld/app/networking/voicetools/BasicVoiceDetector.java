package com.climbtheworld.app.networking.voicetools;

public class BasicVoiceDetector implements IVoiceDetector {
    private int frameHistory = 3;
    private int tempIndex = 0;
    private float tempFloatBuffer[] = new float[3];
    private boolean recording = false;

    public boolean onAudio(byte[] frame, int numberOfReadBytes, double energy)
    {
        float totalAbsValue = 0.0f;
        short sample = 0;
        // Analyze Sound.
        for( int i=0; i<frame.length; i+=2 )
        {
            sample = (short)( (frame[i]) | frame[i + 1] << 8 );
            totalAbsValue += Math.abs( sample ) / (numberOfReadBytes/2);
        }

        // Analyze temp buffer.
        tempFloatBuffer[tempIndex%frameHistory] = totalAbsValue;
        float temp                   = 0.0f;
        for( int i=0; i<frameHistory; ++i )
            temp += tempFloatBuffer[i];

        System.out.println(temp);

        if( (temp >=0 && temp <= 350) && recording == false )
        {
            tempIndex++;
            return recording;
        }

        if( temp > 350 && recording == false )
        {
            recording = true;
        }

        if( (temp >= 0 && temp <= 350) && recording == true )
        {
            tempIndex++;
            recording = false;
        }

        tempIndex++;
        return recording;
    }
}
