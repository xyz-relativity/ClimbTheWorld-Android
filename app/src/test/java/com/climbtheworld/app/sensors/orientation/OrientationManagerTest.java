package com.climbtheworld.app.sensors.orientation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.view.Surface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowSensor;

@RunWith(RobolectricTestRunner.class)
public class OrientationManagerTest {
	// Rotation matrices map device axes to world axes (east, north, up); each column is a device
	// axis.

	// Lying flat, screen up, with the top of the device to the east, so its right side faces
	// south.
	private static final float[] FLAT_TOP_EAST = {
			0, 1, 0, 0,
			-1, 0, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1};

	// Upright with the screen facing south, at someone looking north, turned on its side so its
	// right side points up.
	private static final float[] UPRIGHT_ON_ITS_SIDE = {
			0, -1, 0, 0,
			0, 0, -1, 0,
			1, 0, 0, 0,
			0, 0, 0, 1};

	@Test
	public void toTrueAzimuthAddsDeclination() {
		assertEquals(110, OrientationManager.toTrueAzimuth(100, 10), 1e-9);
		assertEquals(86, OrientationManager.toTrueAzimuth(100, -14), 1e-9);
	}

	@Test
	public void toTrueAzimuthWrapsAroundNorth() {
		assertEquals(351, OrientationManager.toTrueAzimuth(5, -14), 1e-9);
		assertEquals(4, OrientationManager.toTrueAzimuth(354, 10), 1e-9);
		// getOrientation reports azimuths between -180 and 180.
		assertEquals(336, OrientationManager.toTrueAzimuth(-10, -14), 1e-9);
		assertEquals(190, OrientationManager.toTrueAzimuth(-175, 5), 1e-9);
	}

	@Test
	public void screenAzimuthFollowsTheTopOfTheScreen() {
		assertEquals(90, screenAzimuth(FLAT_TOP_EAST, Surface.ROTATION_0), 1e-4);
		// Turned 90 degrees counter-clockwise, the top of the screen is the right of the device.
		assertEquals(180, screenAzimuth(FLAT_TOP_EAST, Surface.ROTATION_90), 1e-4);
		assertEquals(270, screenAzimuth(FLAT_TOP_EAST, Surface.ROTATION_180), 1e-4);
		assertEquals(0, screenAzimuth(FLAT_TOP_EAST, Surface.ROTATION_270), 1e-4);
	}

	@Test
	public void screenPitchFollowsTheTopOfTheScreen() {
		// Held upright in landscape, the screen points at the sky rather than lying level, so
		// the camera direction takes over from the screen, see OrientationEvent.getAdjusted.
		assertEquals(-90, screenOrientationDegrees(UPRIGHT_ON_ITS_SIDE, Surface.ROTATION_90)[1],
				1e-4);
		assertEquals(0, screenOrientationDegrees(UPRIGHT_ON_ITS_SIDE, Surface.ROTATION_0)[1],
				1e-4);
	}

	@Test
	public void noCompassWithoutARotationVector() {
		Context context = RuntimeEnvironment.getApplication();
		addSensor(context, Sensor.TYPE_ACCELEROMETER);
		addSensor(context, Sensor.TYPE_GYROSCOPE);

		assertFalse(OrientationManager.hasCompass(context));
	}

	@Test
	public void geomagneticRotationVectorStandsInWithoutGyroscope() {
		Context context = RuntimeEnvironment.getApplication();
		addSensor(context, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);

		assertTrue(OrientationManager.hasCompass(context));
	}

	@Test
	public void rotationVectorIsACompass() {
		Context context = RuntimeEnvironment.getApplication();
		addSensor(context, Sensor.TYPE_ROTATION_VECTOR);

		assertTrue(OrientationManager.hasCompass(context));
	}

	private static void addSensor(Context context, int type) {
		shadowOf((SensorManager) context.getSystemService(Context.SENSOR_SERVICE))
				.addSensor(ShadowSensor.newInstance(type));
	}

	private static double screenAzimuth(float[] deviceRotationMatrix, int displayRotation) {
		return OrientationManager.toTrueAzimuth(
				screenOrientationDegrees(deviceRotationMatrix, displayRotation)[0], 0);
	}

	private static double[] screenOrientationDegrees(float[] deviceRotationMatrix,
	                                                 int displayRotation) {
		float[] screenRotationMatrix = new float[16];
		OrientationManager.remapToDisplay(deviceRotationMatrix, displayRotation,
				screenRotationMatrix);
		float[] radians = SensorManager.getOrientation(screenRotationMatrix, new float[3]);
		return new double[]{Math.toDegrees(radians[0]), Math.toDegrees(radians[1]),
				Math.toDegrees(radians[2])};
	}
}
