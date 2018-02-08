package com.ar.climbing.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static junit.framework.Assert.assertTrue;

/**
 * Created by xyz on 1/29/18.
 */
@RunWith(MockitoJUnitRunner.class)
public class AugmentedRealityUtilsTest {

    @Test
    public void getXYPosition() throws Exception {
        Vector2d objSize = new Vector2d(1, 1);
        Vector2d fieldOfViewDeg = new Vector2d(60, 60);

        Vector2d displaySize = new Vector2d(2000, 2000);

        for (int i = 0; i<= 360; ++i) {
            Quaternion pos = AugmentedRealityUtils.getXYPosition(-10, 0, i, 0, objSize, fieldOfViewDeg, displaySize);
            System.out.println(pos.x + "," + pos.y + "," + pos.w);
        }
    }

    @Test
    public void remapScale() throws Exception {
        assertTrue("Negative origin scale Min", AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, -30f) == 0.0f);
        assertTrue("Negative origin scale Max", AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, 30f) == 2000f);
        assertTrue("Negative origin scale Mid", AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, 0f) == 1000f);

        assertTrue("Positive origin scale Min", AugmentedRealityUtils.remapScale(0f, 30f, 0f, 2000f, 0f) == 0.0f);
        assertTrue("Positive origin scale Max", AugmentedRealityUtils.remapScale(0f, 30f, 0f, 2000f, 30f) == 2000f);
        assertTrue("Positive origin scale Mid", AugmentedRealityUtils.remapScale(0f, 30f, 0f, 2000f, 15f) == 1000f);

        assertTrue("Negative dest scale Min", AugmentedRealityUtils.remapScale(0f, 30f, -1000f, 1000f, 0f) == -1000f);
        assertTrue("Negative dest scale Max", AugmentedRealityUtils.remapScale(0f, 30f, -1000f, 1000f, 30f) == 1000f);
        assertTrue("Negative dest scale Mid", AugmentedRealityUtils.remapScale(0f, 30f, -1000f, 1000f, 15f) == 0f);

        assertTrue("Under scale", AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, -40f) == -333.33334f);
        assertTrue("Over scale", AugmentedRealityUtils.remapScale(-30f, 30f, 0f, 2000f, 40f) == 2333.3333f);
    }
}