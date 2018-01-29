package com.ar.openClimbAR.utils;

import android.util.Size;
import android.util.SizeF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

import static org.mockito.Mockito.when;

/**
 * Created by xyz on 1/29/18.
 */
@RunWith(MockitoJUnitRunner.class)
public class ArUtilsTest {
    @Mock
    Size objSize;
    @Mock
    SizeF fieldOfViewDeg;
    @Mock
    SizeF displaySize;

    @Test
    public void getXYPosition() throws Exception {
        when(objSize.getWidth()).thenReturn(10);
        when(objSize.getHeight()).thenReturn(10);

        when(fieldOfViewDeg.getWidth()).thenReturn(60f);
        when(fieldOfViewDeg.getHeight()).thenReturn(40f);

        when(displaySize.getWidth()).thenReturn(1920f);
        when(displaySize.getHeight()).thenReturn(1080f);
        float[] result = {955.0f, 535.0f, 0f};

        for (int i = -30; i<= 30; ++i) {
            float[] pos = ArUtils.getXYPosition(i, 10, 25, 0, objSize, fieldOfViewDeg, displaySize);
            System.out.println(Arrays.toString(pos));
        }
    }
}