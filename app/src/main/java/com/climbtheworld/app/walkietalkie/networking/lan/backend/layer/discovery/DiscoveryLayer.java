package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.discovery;

public class DiscoveryLayer {

//		this.dataLayerBackend = new UDPMulticastBackend(parent, port, new INetworkEventListener() {
//			@Override
//			public void onDataReceived(String sourceAddress, byte[] data) {
//				DataFrame inDataFrame = DataFrame.parseData(data);
//
//				if (inDataFrame.getFrameType() != DataFrame.FrameType.NETWORK) {
//					if (connectedClients.containsKey(sourceAddress)) {
//						clientHandler.onData(inDataFrame, sourceAddress);
//					}
//					return;
//				}
//
//				updateClients(sourceAddress, new String(inDataFrame.getData()));
//			}
//		}, clientType);
}
