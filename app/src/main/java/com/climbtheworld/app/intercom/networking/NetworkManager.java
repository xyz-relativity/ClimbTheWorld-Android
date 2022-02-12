package com.climbtheworld.app.intercom.networking;

import android.content.Context;

import com.climbtheworld.app.intercom.IClientEventListener;

abstract public class NetworkManager implements INetworkBackend{
	protected IClientEventListener uiHandler;
	protected Context parent;

	protected DataFrame inDataFrame = new DataFrame();
	protected DataFrame outDataFrame = new DataFrame();

	public NetworkManager (Context parent, IClientEventListener uiHandler) {
		this.uiHandler = uiHandler;
		this.parent = parent;
	}
}
