package com.climbtheworld.app.sensors.environment;

import com.climbtheworld.app.utils.Quaternion;

public interface IEnvironmentListener {
		void updateSensors(Quaternion sensors);
}
