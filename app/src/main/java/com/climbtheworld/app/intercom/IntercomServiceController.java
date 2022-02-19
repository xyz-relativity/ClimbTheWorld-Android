package com.climbtheworld.app.intercom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.states.InterconState;

public class IntercomServiceController implements IClientEventListener {
	private final Context parent;
	private final Configs configs;
	private final IClientEventListener eventReceiver;
	private ServiceConnection intercomServiceConnection;
	private IntercomBackgroundService backgroundService = null;

	public IntercomServiceController (Context parent, Configs configs, IClientEventListener eventReceiver) {
		this.parent = parent;
		this.configs = configs;
		this.eventReceiver = eventReceiver;
	}

	public void initIntercom() {
		Intent intercomServiceIntent = new Intent(parent, IntercomBackgroundService.class);
		intercomServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
				backgroundService = ((IntercomBackgroundService.LocalBinder) iBinder).getService();
				backgroundService.startIntercom(IntercomServiceController.this, configs);
			}

			@Override
			public void onServiceDisconnected(ComponentName componentName) {
				backgroundService = null;
			}
		};
		parent.bindService(intercomServiceIntent, intercomServiceConnection, Context.BIND_AUTO_CREATE);
	}

	public void updateConfigs() {
		if (backgroundService != null) {
			backgroundService.updateConfigs();
		}
	}

	// IClientEventListener
	@Override
	public void onData(DataFrame data, String address) {
		eventReceiver.onData(data, address);
	}

	@Override
	public void onClientConnected(ClientType type, String address) {
		eventReceiver.onClientConnected(type, address);
	}

	@Override
	public void onClientDisconnected(ClientType type, String address) {
		eventReceiver.onClientDisconnected(type, address);
	} //END IClientEventListener

	public void onDestroy() {
		if (intercomServiceConnection != null) {
			parent.unbindService(intercomServiceConnection);
		}
	}

	public void setRecordingState(InterconState activeState) {
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
