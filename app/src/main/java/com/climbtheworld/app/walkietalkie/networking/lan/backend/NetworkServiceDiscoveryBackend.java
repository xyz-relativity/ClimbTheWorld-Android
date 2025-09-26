package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.networking.DataFrame;

import java.net.InetAddress;

public class NetworkServiceDiscoveryBackend implements IDataLayerBackend {
	private static final String SERVICE_TYPE = "_ctwwalkie._udp";

	private final Context context;
	private final int port;
	private NsdManager.RegistrationListener registrationListener;
	private String serviceName = "ClimbTheWall_Walkie";
	private NsdManager nsdManager;
	private NsdManager.DiscoveryListener discoveryListener;
	private NsdManager.ResolveListener resolveListener;

	public NetworkServiceDiscoveryBackend(Context parent, int port) {
		this.context = parent;
		this.port = port;
	}

	@Override
	public void startServer() {
		initializeRegistrationListener();
		registerService();
	}

	@Override
	public void stopServer() {
		tearDown();
	}

	@Override
	public void sendData(DataFrame sendData, String destination) {

	}

	@Override
	public void broadcastData(DataFrame sendData) {

	}

	private void registerService() {
		// Create the NsdServiceInfo object, and populate it.
		NsdServiceInfo serviceInfo = new NsdServiceInfo();

		// The name is subject to change based on conflicts
		// with other services advertised on the same network.
		serviceInfo.setServiceName(serviceName);
		serviceInfo.setServiceType(SERVICE_TYPE);
		serviceInfo.setPort(port);

		nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);

		nsdManager.registerService(
				serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
	}

	private void initializeResolveListener() {
		resolveListener = new NsdManager.ResolveListener() {

			@Override
			public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
				// Called when the resolve fails. Use the error code to debug.
				Log.e("NSDBackend", "Resolve failed: " + errorCode);
			}

			@Override
			public void onServiceResolved(NsdServiceInfo serviceInfo) {
				Log.e("NSDBackend", "Resolve Succeeded. " + serviceInfo);

				if (serviceInfo.getServiceName().equals(serviceName)) {
					Log.d("NSDBackend", "Same IP.");
					return;
				}
				int port = serviceInfo.getPort();
				InetAddress host = serviceInfo.getHost();
			}
		};
	}

	private void initializeRegistrationListener() {
		registrationListener = new NsdManager.RegistrationListener() {

			@Override
			public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
				// Save the service name. Android may have changed it in order to
				// resolve a conflict, so update the name you initially requested
				// with the name Android actually used.
				serviceName = NsdServiceInfo.getServiceName();
				initializeResolveListener();
				initializeDiscoveryListener();
			}

			@Override
			public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				// Registration failed! Put debugging code here to determine why.
			}

			@Override
			public void onServiceUnregistered(NsdServiceInfo arg0) {
				// Service has been unregistered. This only happens when you call
				// NsdManager.unregisterService() and pass in this listener.
			}

			@Override
			public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				// Unregistration failed. Put debugging code here to determine why.
			}
		};
	}

	public void initializeDiscoveryListener() {

		// Instantiate a new DiscoveryListener
		discoveryListener = new NsdManager.DiscoveryListener() {

			// Called as soon as service discovery begins.
			@Override
			public void onDiscoveryStarted(String regType) {
				Log.d("NSDBackend", "Service discovery started");
			}

			@Override
			public void onServiceFound(NsdServiceInfo service) {
				// A service was found! Do something with it.
				Log.d("NSDBackend", "Service discovery success" + service);
				if (!service.getServiceType().equals(SERVICE_TYPE)) {
					// Service type is the string containing the protocol and
					// transport layer for this service.
					Log.d("NSDBackend", "Unknown Service Type: " + service.getServiceType());
				} else if (service.getServiceName().equals(serviceName)) {
					// The name of the service tells the user what they'd be
					// connecting to. It could be "Bob's Chat App".
					Log.d("NSDBackend", "Same machine: " + serviceName);
				} else if (service.getServiceName().contains("ClimbTheWall")){
					nsdManager.resolveService(service, resolveListener);
				}
			}

			@Override
			public void onServiceLost(NsdServiceInfo service) {
				// When the network service is no longer available.
				// Internal bookkeeping code goes here.
				Log.e("NSDBackend", "service lost: " + service);
			}

			@Override
			public void onDiscoveryStopped(String serviceType) {
				Log.i("NSDBackend", "Discovery stopped: " + serviceType);
			}

			@Override
			public void onStartDiscoveryFailed(String serviceType, int errorCode) {
				Log.e("NSDBackend", "Discovery failed: Error code:" + errorCode);
				nsdManager.stopServiceDiscovery(this);
			}

			@Override
			public void onStopDiscoveryFailed(String serviceType, int errorCode) {
				Log.e("NSDBackend", "Discovery failed: Error code:" + errorCode);
				nsdManager.stopServiceDiscovery(this);
			}
		};
	}

	private void tearDown() {
		nsdManager.unregisterService(registrationListener);
		nsdManager.stopServiceDiscovery(discoveryListener);
	}
}
