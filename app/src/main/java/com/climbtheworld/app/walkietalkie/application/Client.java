package com.climbtheworld.app.walkietalkie.application;

import com.climbtheworld.app.walkietalkie.ITransportLayer;

import java.util.Comparator;
import java.util.TreeSet;

public class Client {
	public final String clientUUID;
	public String callSign = "";
	public TreeSet<ITransportLayer> transportClientSet =
			new TreeSet<>(Comparator.comparing(ITransportLayer::getType));
	public double distanceMeters = -1;

	public Client(String clientUUID, String callSign, ITransportLayer client) {
		this.clientUUID = clientUUID;
		this.callSign = callSign;
		this.transportClientSet.add(client);
	}

	public Client withDistance(double distanceMeters) {
		this.distanceMeters = distanceMeters;
		return this;
	}

	public void sendData(byte[] data) {
		transportClientSet.first().sendData(data);
	}

	public void onDestroy() {
	}
}
