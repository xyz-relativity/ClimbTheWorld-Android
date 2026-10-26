package com.climbtheworld.app.sensors.orientation;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.view.Surface;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;

import java.lang.ref.WeakReference;

/**
 * Which way the device faces, from its sensors, as azimuth, pitch and roll of the top of the
 * screen and of the camera, see OrientationEvent.
 * <p>
 * The orientation comes from the game rotation vector, which fuses the gyroscope and the
 * accelerometer only, so it is steady and quick but knows nothing of north. The rotation vector,
 * which adds the magnetometer, only tells where north is in that world, averaged slowly and
 * ignored while the compass is not to be trusted, see HeadingOffset: the field bending around
 * steel or rock then no longer swings the whole view with it. On a device without a game rotation
 * vector, the rotation vector gives the orientation directly, as it always did. The magnetometer
 * is read as well, for its calibration and for whether the field is disturbed, see
 * MagneticInterferenceDetector.
 */
public class OrientationManager implements SensorEventListener {
	// Over tens of kilometres, the earth's field turns by a fraction of a degree and changes its
	// strength by a fraction of a percent, so it is only recomputed once the observer moved about
	// this far.
	private static final double FIELD_UPDATE_DEGREES = 0.1;
	// The device counts as held up beyond the first pitch, and as lying flat again below the
	// second, so that a device held around the middle does not keep switching.
	private static final double HELD_UP_PITCH_DEGREES = 50;
	private static final double LYING_FLAT_PITCH_DEGREES = 40;
	// The dip of the magnetic field is worked out from a magnetometer reading and the latest
	// orientation, which can be up to a fifth of a second apart. While the device tilts faster
	// than this, the dip would look off by several degrees, and so the field disturbed.
	private static final double STILL_TILT_DEGREES_PER_SECOND = 20;

	private final WeakReference<AppCompatActivity> parent;
	private IOrientationListener orientationListener;
	private final SensorManager sensorManager;
	private final DisplayManager displayManager;
	// The rate the screen asked for, and the one the rotation vector is read at now, which is
	// faster while a map follows the compass.
	private final int slowSamplingPeriodUs;
	private int samplingPeriodUs;
	private int displayRotation = Surface.ROTATION_0;

	// Turning the screen half way around, from one landscape to the other, does not recreate the
	// activity, so the display rotation is followed for as long as the sensors are.
	private final DisplayManager.DisplayListener displayListener =
			new DisplayManager.DisplayListener() {
				@Override
				public void onDisplayAdded(int displayId) {
				}

				@Override
				public void onDisplayRemoved(int displayId) {
				}

				@Override
				public void onDisplayChanged(int displayId) {
					updateDisplayRotation();
				}
			};

	// Holds sensor data: the rotation vector's matrix, with the magnetometer, ...
	private final float[] originRotationMatrix = new float[16];
	// ... and the game rotation vector's, without, the one the orientation is read from when the
	// device has it, see HeadingOffset.
	private final float[] gameRotationMatrix = new float[16];
	// Whether gameRotationMatrix holds a reading since the updates were requested.
	private boolean gameRotationKnown;
	// Whether the device has a game rotation vector, and so the orientation is read from it.
	private boolean onGameRotation;
	private final HeadingOffset headingOffset = new HeadingOffset();
	private final float[] remappedRotationMatrix = new float[16];
	private final float[] screenRotationMatrix = new float[16];
	private final float[] orientationVector = new float[3];
	private final OrientationEvent orientation = new OrientationEvent();
	private double fieldLatitude = Double.NaN;
	private double fieldLongitude = Double.NaN;
	private GeomagneticField cachedField;
	private int lastMagnetometerAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
	private final MagneticInterferenceDetector interferenceDetector =
			new MagneticInterferenceDetector();
	// Whether originRotationMatrix holds a reading of the rotation vector since the updates were
	// requested.
	private boolean rotationKnown;
	// Up, in device coordinates, at the last reading of the rotation vector and the one before,
	// to tell how fast the device tilts. NaN until two readings came.
	private final float[] up = new float[3];
	private final float[] lastUp = new float[3];
	private long lastUpTimestampNanos;
	private double tiltDegreesPerSecond = Double.NaN;
	private final OrientationSmoother smoother = new OrientationSmoother();
	// Whether the readings turn Globals.virtualCamera, which ARCore may turn instead.
	private boolean turnsCamera = true;

	/**
	 * Whether the azimuths can be trusted, and if not, what the user can do about it.
	 */
	public enum CompassStatus {
		OK,
		// The magnetometer reports a poor calibration, which moving the device in a figure 8
		// improves.
		NEEDS_CALIBRATION,
		// Something nearby bends the magnetic field, see MagneticInterferenceDetector.
		DISTURBED
	}

	public static class OrientationEvent {
		public Vector4d screen = new Vector4d();
		public Vector4d camera = new Vector4d();
		// How far off the azimuths may be, in degrees, NaN when the device does not tell.
		public double headingAccuracyDegrees = Double.NaN;
		public CompassStatus compassStatus = CompassStatus.OK;
		private boolean followsCamera;

		/**
		 * The way the user faces: the top of the screen while the device lies about flat, and the
		 * camera once it is held up, when the top of the screen points at the sky and its azimuth
		 * no longer means much.
		 */
		public Vector4d getAdjusted() {
			return followsCamera ? camera : screen;
		}
	}

	public OrientationManager(AppCompatActivity parent, int samplingPeriodUs) {
		this.parent = new WeakReference<>(parent);
		this.slowSamplingPeriodUs = samplingPeriodUs;
		this.samplingPeriodUs = samplingPeriodUs;

		sensorManager = (SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);
		displayManager = (DisplayManager) parent.getSystemService(Context.DISPLAY_SERVICE);
	}

	/**
	 * Whether the device can tell which way it is facing. Without a compass no orientation is
	 * ever reported.
	 */
	public static boolean hasCompass(Context context) {
		return getRotationSensor(
				(SensorManager) context.getSystemService(Context.SENSOR_SERVICE)) != null;
	}

	/**
	 * The rotation vector, which also uses the gyroscope to react faster and steadier, or on
	 * devices without one, the geomagnetic rotation vector, which only needs the accelerometer
	 * and the magnetometer. Null when there is no magnetometer, since then neither exists.
	 */
	private static Sensor getRotationSensor(SensorManager sensorManager) {
		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		if (sensor == null) {
			sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
		}
		return sensor;
	}

	/**
	 * The sensor the orientation is read from, at the rate asked for: the game rotation vector,
	 * or the rotation vector on a device without one.
	 */
	private Sensor getOrientationSensor() {
		Sensor sensor = getGameRotationSensor();
		return sensor != null ? sensor : getRotationSensor(sensorManager);
	}

	private Sensor getGameRotationSensor() {
		return sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
	}

	public void requestUpdates(IOrientationListener orientationListener) {
		this.orientationListener = orientationListener;
		Sensor rotationSensor = getRotationSensor(sensorManager);
		if (rotationSensor == null) {
			return;
		}

		updateDisplayRotation();
		displayManager.registerDisplayListener(displayListener, null);
		// The device may have turned any way while the updates were stopped, and the game
		// rotation vector's world drifted.
		smoother.reset();
		headingOffset.reset();
		gameRotationKnown = false;
		Sensor gameRotationSensor = getGameRotationSensor();
		onGameRotation = gameRotationSensor != null;
		if (onGameRotation) {
			sensorManager.registerListener(this, gameRotationSensor, samplingPeriodUs);
			// The compass only sets where north is, slowly: the rate the screen asked for is
			// enough for it, whatever the orientation is read at.
			sensorManager.registerListener(this, rotationSensor, slowSamplingPeriodUs);
		} else {
			sensorManager.registerListener(this, rotationSensor, samplingPeriodUs);
		}

		// The rotation vector's own accuracy does not reliably reflect the magnetometer
		// calibration, and says nothing of a disturbed field, so the magnetometer is listened to
		// as well, for its accuracy and the strength of the field it measures.
		Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		if (magnetometer != null) {
			// The accuracy is kept from before the updates were stopped until the magnetometer
			// tells it again: assuming it calibrated meanwhile would close the calibration dialog,
			// see CompassStatusBadge, each time its screen comes back.
			interferenceDetector.reset();
			rotationKnown = false;
			sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	/**
	 * A map following the compass turns once per reading, so it turns in visible steps unless the
	 * rotation vector is read at the game rate, as in the AR view. The rest of the time the rate
	 * the screen asked for is enough.
	 *
	 * @param fast whether to read at the game rate rather than the rate given to the constructor
	 */
	public void setFastUpdates(boolean fast) {
		int period = fast ? SensorManager.SENSOR_DELAY_GAME : slowSamplingPeriodUs;
		if (period == samplingPeriodUs) {
			return;
		}
		samplingPeriodUs = period;

		Sensor orientationSensor = getOrientationSensor();
		if (orientationListener != null && orientationSensor != null) {
			sensorManager.unregisterListener(this, orientationSensor);
			sensorManager.registerListener(this, orientationSensor, samplingPeriodUs);
		}
	}

	/**
	 * Lets something else turn Globals.virtualCamera, such as ARCore in the A.R. view, while the
	 * listener still gets the readings, as for the compass.
	 *
	 * @param turns whether the readings turn the camera, as they do at first
	 */
	public void setTurnsCamera(boolean turns) {
		turnsCamera = turns;
	}

	public void stopUpdates() {
		sensorManager.unregisterListener(this);
		displayManager.unregisterDisplayListener(displayListener);
		this.orientationListener = null;
	}

	private void updateDisplayRotation() {
		AppCompatActivity activity = parent.get();
		if (activity != null) {
			displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			// Read from the events rather than onAccuracyChanged: that one only reports changes,
			// starting from unreliable, so a magnetometer unreliable from the start is never
			// reported.
			if (event.accuracy >= SensorManager.SENSOR_STATUS_UNRELIABLE) {
				lastMagnetometerAccuracy = event.accuracy;
			}
			updateInterferenceDetector(event);
		} else if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
			SensorManager.getRotationMatrixFromVector(gameRotationMatrix,
					smoother.update(event.values, event.timestamp));
			gameRotationKnown = true;
			// Nothing is known of north until the compass has been read against this world.
			if (headingOffset.isKnown()) {
				report(gameRotationMatrix, headingOffset.getDegrees() + getDeclination());
			}
		} else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR
				|| event.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
			SensorManager.getRotationMatrixFromVector(originRotationMatrix, onGameRotation
					? event.values : smoother.update(event.values, event.timestamp));
			// Up, in device coordinates, is the third row of the rotation matrix. Turning around
			// it leaves it as it is, so only tilting counts.
			System.arraycopy(originRotationMatrix, 8, up, 0, up.length);
			tiltDegreesPerSecond = rotationKnown ? tiltDegreesPerSecond(lastUp,
					lastUpTimestampNanos, up, event.timestamp) : Double.NaN;
			System.arraycopy(up, 0, lastUp, 0, up.length);
			lastUpTimestampNanos = event.timestamp;
			rotationKnown = true;
			orientation.headingAccuracyDegrees = headingAccuracyDegrees(event.values);
			orientation.compassStatus =
					compassStatus(lastMagnetometerAccuracy, interferenceDetector.isDisturbed());

			if (!onGameRotation) {
				report(originRotationMatrix, getDeclination());
			} else if (gameRotationKnown && (!headingOffset.isKnown() || HeadingOffset.canTake(
					orientation.compassStatus, smoother.turnRateDegreesPerSecond()))) {
				headingOffset.add(HeadingOffset.yawDegrees(originRotationMatrix, gameRotationMatrix),
						orientation.headingAccuracyDegrees, event.timestamp);
			}
		}
	}

	/**
	 * Tells the listener, and the camera, which way the device faces.
	 *
	 * @param rotationMatrix the device's orientation, see
	 *                       SensorManager.getRotationMatrixFromVector()
	 * @param northDegrees   what to add to the matrix's azimuths to count them from true north
	 */
	private void report(float[] rotationMatrix, double northDegrees) {
		// The screen azimuth follows the top of the screen, which is another side of the device
		// once the screen is turned.
		remapToDisplay(rotationMatrix, displayRotation, screenRotationMatrix);
		SensorManager.getOrientation(screenRotationMatrix, orientationVector);
		orientation.screen.x = toTrueAzimuth(Math.toDegrees(orientationVector[0]), northDegrees);
		orientation.screen.y = Math.toDegrees(orientationVector[1]);// pitch
		orientation.screen.z = Math.toDegrees(orientationVector[2]);// roll

		//align coordinates with the camera (for AR)
		SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X,
				SensorManager.AXIS_Z, remappedRotationMatrix);
		SensorManager.getOrientation(remappedRotationMatrix, orientationVector);
		orientation.camera.x = toTrueAzimuth(Math.toDegrees(orientationVector[0]), northDegrees);
		orientation.camera.y = Math.toDegrees(orientationVector[1]);
		orientation.camera.z = Math.toDegrees(orientationVector[2]);
		orientation.followsCamera = followsCamera(orientation.screen.y, orientation.followsCamera);

		if (turnsCamera) {
			Globals.virtualCamera.updateOrientation(orientation);
		}
		if (orientationListener != null) {
			orientationListener.updateOrientation(orientation);
		}
	}

	/**
	 * The rotation vector points to magnetic north, while maps and bearings use true north. The
	 * declination is the angle between them, positive when magnetic north is east of true north.
	 */
	private double getDeclination() {
		return getGeomagneticField().getDeclination();
	}

	/**
	 * Whether the heading follows the camera rather than the top of the screen, see
	 * OrientationEvent.getAdjusted().
	 *
	 * @param screenPitchDegrees how far the top of the screen points up or down
	 * @param followedCamera     whether it followed the camera so far
	 */
	static boolean followsCamera(double screenPitchDegrees, boolean followedCamera) {
		double pitch = Math.abs(screenPitchDegrees);
		if (pitch > HELD_UP_PITCH_DEGREES) {
			return true;
		}
		if (pitch < LYING_FLAT_PITCH_DEGREES) {
			return false;
		}
		return followedCamera;
	}

	/**
	 * How far off the heading may be, as the rotation vector estimates it, in degrees, or NaN when
	 * the device does not tell.
	 */
	static double headingAccuracyDegrees(float[] rotationVector) {
		// The fifth value is the estimate, in radians, and -1 when the device has none. Some
		// devices leave it at 0 instead, which would be a perfect heading, so that is no estimate
		// either.
		if (rotationVector.length < 5 || rotationVector[4] <= 0) {
			return Double.NaN;
		}
		return Math.toDegrees(rotationVector[4]);
	}

	/**
	 * Turns a rotation matrix of the device into one of the screen as it is displayed: its X axis
	 * points to the right of the screen and its Y axis to the top, whichever way the screen is
	 * turned.
	 *
	 * @param displayRotation one of the Surface.ROTATION_ values, see Display.getRotation()
	 */
	static void remapToDisplay(float[] deviceRotationMatrix, int displayRotation,
	                           float[] screenRotationMatrix) {
		int axisX;
		int axisY;
		switch (displayRotation) {
			case Surface.ROTATION_90:
				axisX = SensorManager.AXIS_Y;
				axisY = SensorManager.AXIS_MINUS_X;
				break;
			case Surface.ROTATION_180:
				axisX = SensorManager.AXIS_MINUS_X;
				axisY = SensorManager.AXIS_MINUS_Y;
				break;
			case Surface.ROTATION_270:
				axisX = SensorManager.AXIS_MINUS_Y;
				axisY = SensorManager.AXIS_X;
				break;
			default:
				axisX = SensorManager.AXIS_X;
				axisY = SensorManager.AXIS_Y;
				break;
		}
		SensorManager.remapCoordinateSystem(deviceRotationMatrix, axisX, axisY,
				screenRotationMatrix);
	}

	/**
	 * The earth's magnetic field at the observer, as a model predicts it, without whatever
	 * disturbs it locally.
	 */
	private GeomagneticField getGeomagneticField() {
		double latitude = Globals.virtualCamera.decimalLatitude;
		double longitude = Globals.virtualCamera.decimalLongitude;
		if (Double.isNaN(fieldLatitude)
				|| Math.abs(latitude - fieldLatitude) > FIELD_UPDATE_DEGREES
				|| Math.abs(longitude - fieldLongitude) > FIELD_UPDATE_DEGREES) {
			cachedField = new GeomagneticField((float) latitude, (float) longitude,
					(float) Globals.virtualCamera.elevationMeters, System.currentTimeMillis());
			fieldLatitude = latitude;
			fieldLongitude = longitude;
		}
		return cachedField;
	}

	/**
	 * Turns an azimuth measured from magnetic north into one measured from true north, between 0
	 * and 360 degrees.
	 *
	 * @param declination what to add: the declination, and, for an azimuth of the game rotation
	 *                    vector's world, where magnetic north is in it, see HeadingOffset
	 */
	static double toTrueAzimuth(double magneticAzimuth, double declination) {
		return (((magneticAzimuth + declination) % 360) + 360) % 360;
	}

	/**
	 * Calibration comes first, since until the magnetometer is calibrated, its own offsets can
	 * look like a disturbed field. A medium accuracy is good enough not to bother the user.
	 */
	static CompassStatus compassStatus(int magnetometerAccuracy, boolean disturbed) {
		if (magnetometerAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
			return CompassStatus.NEEDS_CALIBRATION;
		}
		return disturbed ? CompassStatus.DISTURBED : CompassStatus.OK;
	}

	/**
	 * Whether a magnetometer reading can tell if something disturbs the field. Not while the
	 * magnetometer needs calibrating, as its own offsets would look like a disturbance (the
	 * calibration status covers that, see compassStatus()), nor while the device tilts quickly,
	 * see STILL_TILT_DEGREES_PER_SECOND.
	 *
	 * @param tiltDegreesPerSecond how fast the device tilts, NaN when not known yet
	 */
	static boolean canJudgeField(int magnetometerAccuracy, double tiltDegreesPerSecond) {
		return magnetometerAccuracy > SensorManager.SENSOR_STATUS_ACCURACY_LOW
				&& !(tiltDegreesPerSecond > STILL_TILT_DEGREES_PER_SECOND);
	}

	/**
	 * How fast the device tilts, from which way up is in device coordinates at two times.
	 *
	 * @return in degrees per second, NaN when the second time is not later
	 */
	static double tiltDegreesPerSecond(float[] previousUp, long previousTimestampNanos,
	                                   float[] up, long timestampNanos) {
		if (timestampNanos <= previousTimestampNanos) {
			return Double.NaN;
		}
		double dot = previousUp[0] * up[0] + previousUp[1] * up[1] + previousUp[2] * up[2];
		double degrees = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
		return degrees / ((timestampNanos - previousTimestampNanos) / 1e9);
	}

	private void updateInterferenceDetector(SensorEvent event) {
		// The dip needs the tilt of the device, which comes from the rotation vector.
		if (!canJudgeField(event.accuracy, tiltDegreesPerSecond) || !rotationKnown) {
			return;
		}
		GeomagneticField field = getGeomagneticField();
		interferenceDetector.update(
				MagneticInterferenceDetector.strength(event.values),
				// The magnetometer measures in microtesla, the model in nanotesla.
				field.getFieldStrength() / 1000.0,
				MagneticInterferenceDetector.dipDegrees(event.values, originRotationMatrix),
				field.getInclination(), event.timestamp);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// The accuracy is read from the sensor events, see onSensorChanged.
	}

}
