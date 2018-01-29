package com.ar.openClimbAR.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

import static junit.framework.Assert.assertTrue;

/**
 * Created by xyz on 1/29/18.
 */
@RunWith(MockitoJUnitRunner.class)
public class ArUtilsTest {

    @Test
    public void getXYPosition() throws Exception {
        Vector2f objSize = new Vector2f(5, 10);
        Vector2f fieldOfViewDeg = new Vector2f(60f, 60f);


        Vector2f displaySize = new Vector2f(1920f, 1920f);

        for (int i = -40; i<= 40; ++i) {
            float[] pos = ArUtils.getXYPosition(i, 0, 15, 0, objSize, fieldOfViewDeg, displaySize);
            System.out.println(Arrays.toString(pos));
        }
    }

    @Test
    public void remapScale() throws Exception {
        assertTrue("Negative origin scale Min", ArUtils.remapScale(-30f, 30f, 0f, 2000f, -30f) == 0.0f);
        assertTrue("Negative origin scale Max", ArUtils.remapScale(-30f, 30f, 0f, 2000f, 30f) == 2000f);
        assertTrue("Negative origin scale Mid", ArUtils.remapScale(-30f, 30f, 0f, 2000f, 0f) == 1000f);

        assertTrue("Positive origin scale Min", ArUtils.remapScale(0f, 30f, 0f, 2000f, 0f) == 0.0f);
        assertTrue("Positive origin scale Max", ArUtils.remapScale(0f, 30f, 0f, 2000f, 30f) == 2000f);
        assertTrue("Positive origin scale Mid", ArUtils.remapScale(0f, 30f, 0f, 2000f, 15f) == 1000f);

        assertTrue("Negative dest scale Min", ArUtils.remapScale(0f, 30f, -1000f, 1000f, 0f) == -1000f);
        assertTrue("Negative dest scale Max", ArUtils.remapScale(0f, 30f, -1000f, 1000f, 30f) == 1000f);
        assertTrue("Negative dest scale Mid", ArUtils.remapScale(0f, 30f, -1000f, 1000f, 15f) == 0f);

        assertTrue("Under scale", ArUtils.remapScale(-30f, 30f, 0f, 2000f, -40f) == -333.33334f);
        assertTrue("Over scale", ArUtils.remapScale(-30f, 30f, 0f, 2000f, 40f) == 2333.3333f);
    }
}