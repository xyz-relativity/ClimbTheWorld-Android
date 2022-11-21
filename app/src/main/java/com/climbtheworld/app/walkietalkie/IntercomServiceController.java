package com.climbtheworld.app.walkietalkie;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.states.InterconState;

import java.lang.ref.WeakReference;

public class IntercomServiceController {
	private final WeakReference<Context> parent;
	private final Configs configs;
	private IClientEventListener eventReceiver;
	private ServiceConnection intercomServiceConnection;
	private IntercomBackgroundService backgroundService = null;
	private InterconState activeState;

	public IntercomServiceController (Context parent, Configs configs) {
		this.parent = new WeakReference<>(parent);
		this.configs = configs;
	}

	public void initIntercom(IClientEventListener eventReceiver) {
		this.eventReceiver = eventReceiver;
		Intent intercomServiceIntent = new Intent(parent.get(), IntercomBackgroundService.class);
		intercomServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
				backgroundService = ((IntercomBackgroundService.LocalBinder) iBinder).getService();
				backgroundService.startIntercom(eventReceiver, configs);
				backgroundService.setRecordingState(activeState);
			}

			@Override
			public void onServiceDisconnected(ComponentName componentName) {
				backgroundService = null;
			}
		};
		parent.get().getApplicationContext().bindService(intercomServiceIntent, intercomServiceConnection, Context.BIND_AUTO_CREATE);
	}

	public void updateConfigs() {
		if (backgroundService != null) {
			backgroundService.updateConfigs();
		}
	}

	public void onDestroy() {
		if (intercomServiceConnection != null) {
			parent.get().getApplicationContext().unbindService(intercomServiceConnection);
		}

		eventReceiver = null;
	}

	public void setRecordingState(InterconState activeState) {
		this.activeState = activeState;
		if (backgroundService != null) {
			backgroundService.setRecordingState(activeState);
		}
	}

	public void sendData(DataFrame frame) {
		if (backgroundService != null) {
			backgroundService.sendData(frame);
		}
	}

	public void onStart() {

	}
}
