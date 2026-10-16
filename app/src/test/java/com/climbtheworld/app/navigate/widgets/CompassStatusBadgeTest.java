package com.climbtheworld.app.navigate.widgets;

import static org.junit.Assert.assertEquals;

import com.climbtheworld.app.R;
import com.climbtheworld.app.sensors.orientation.OrientationManager.CompassStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CompassStatusBadgeTest {
	@Test
	public void eachCompassProblemHasItsOwnBadge() {
		assertEquals(R.drawable.ic_compass_badge_calibrate,
				CompassStatusBadge.badgeIcon(CompassStatus.NEEDS_CALIBRATION));
		assertEquals(R.drawable.ic_compass_badge_disturbed,
				CompassStatusBadge.badgeIcon(CompassStatus.DISTURBED));
	}

	@Test
	public void noBadgeWhileTheCompassIsFine() {
		assertEquals(0, CompassStatusBadge.badgeIcon(CompassStatus.OK));
	}
}
