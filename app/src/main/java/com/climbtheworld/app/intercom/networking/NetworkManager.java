package com.climbtheworld.app.intercom.networking;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.intercom.IClientEventListener;

abstract public class NetworkManager implements INetworkBackend{
	protected IClientEventListener uiHandler;
	protected AppCompatActivity parent;

	protected DataFrame inDataFrame = new DataFrame();
	protected DataFrame outDataFrame = new DataFrame();

	public NetworkManager (AppCompatActivity parent, IClientEventListener uiHandler) {
		this.uiHandler = uiHandler;
		this.parent = parent;
	}
}
