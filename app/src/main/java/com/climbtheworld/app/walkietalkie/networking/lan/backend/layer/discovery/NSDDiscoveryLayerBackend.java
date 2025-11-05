package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

public class NSDDiscoveryLayerBackend extends Thread {
	private static final String TAG = NSDDiscoveryLayerBackend.class.getSimpleName();
	// Service Type must be in the format "_<protocol>._<transportlayer>"
	private static final String SERVICE_TYPE = "_walkie._udp.";
	private final INDSEventListener clientEventListener;
	private final NsdManager nsdManager;
	private final AtomicBoolean isDiscoveryActive = new AtomicBoolean(false);
	private final String serviceName;
	private NsdManager.RegistrationListener registrationListener;
	private NsdManager.DiscoveryListener discoveryListener;

	public NSDDiscoveryLayerBackend(Context parent, String uuid,
	                                INDSEventListener clientEventListener) {
		this.clientEventListener = clientEventListener;
		nsdManager = (NsdManager) parent.getSystemService(Context.NSD_SERVICE);
		serviceName = uuid;
	}

	@Override
	public void run() {
		Log.d(TAG, "Starting NDS discovery");
		int port = 0;
		try {
			ServerSocket serverSocket = new ServerSocket(0);
			port = serverSocket.getLocalPort();
			// We don't need the socket itself, just the port, so close it.
			serverSocket.close();
		} catch (IOException e) {
			Log.d(TAG, "NDS discovery failed to get a free port.", e);
		}


		registerService(port);
		initializeDiscoveryListener();
		nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
		isDiscoveryActive.set(true);
	}

	public void stopServer() {
		Log.d(TAG, "Stopping discovery");
		if (registrationListener != null) {
			nsdManager.unregisterService(registrationListener);
			registrationListener = null;
		}
		if (discoveryListener != null) {
			nsdManager.stopServiceDiscovery(discoveryListener);
			discoveryListener = null;
		}
		isDiscoveryActive.set(false);

		interrupt();
	}

	public void registerService(int port) {
		NsdServiceInfo serviceInfo = new NsdServiceInfo();
		serviceInfo.setServiceName(serviceName);
		serviceInfo.setServiceType(SERVICE_TYPE);
		serviceInfo.setPort(port);

		serviceInfo.setAttribute("id", serviceName);
		serviceInfo.setAttribute("name", "CTW_Walkietalkie");
		serviceInfo.setAttribute("type", SERVICE_TYPE);
		serviceInfo.setAttribute("protocol", "TCP/UDP");

		initializeRegistrationListener();

		Log.d(TAG, "Registering service: " + serviceInfo);
		nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
	}

	private void initializeRegistrationListener() {
		registrationListener = new NsdManager.RegistrationListener() {
			@Override
			public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
				// Save the service name.
				String registeredServiceName = NsdServiceInfo.getServiceName();
				Log.d(TAG, "Service registered: " + registeredServiceName);
			}

			@Override
			public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				Log.e(TAG, "Service registration failed: Error code: " + errorCode);
			}

			@Override
			public void onServiceUnregistered(NsdServiceInfo arg0) {
				Log.d(TAG, "Service unregistered: " + arg0.getServiceName());
			}

			@Override
			public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				Log.e(TAG, "Service unregistration failed: Error code: " + errorCode);
			}
		};
	}

	private void initializeDiscoveryListener() {
		discoveryListener = new NsdManager.DiscoveryListener() {
			@Override
			public void onDiscoveryStarted(String regType) {
				Log.d(TAG, "Service discovery started");
			}

			@Override
			public void onServiceFound(NsdServiceInfo service) {
				Log.d(TAG, "Service discovery success: " + service);
				// A service was found! Do not resolve here.
				// The NSD service will resolve the service for you which may take a second.
				if (!service.getServiceType().equals(SERVICE_TYPE)) {
					Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
				} else if (service.getServiceName().equals(serviceName)) {
					Log.d(TAG, "Same machine: " + serviceName);
				} else {
					// Resolve the service to get its IP address and port.
					nsdManager.resolveService(service,
							createResolveListener(service.getServiceName()));
				}
			}

			@Override
			public void onServiceLost(NsdServiceInfo service) {
				Log.i(TAG, "service lost: " + service);
				clientEventListener.onNSDNodeLost(service.getServiceName());
			}

			@Override
			public void onDiscoveryStopped(String serviceType) {
				Log.i(TAG, "Discovery stopped: " + serviceType);
			}

			@Override
			public void onStartDiscoveryFailed(String serviceType, int errorCode) {
				Log.e(TAG, "Discovery failed: Error code:" + errorCode);
				nsdManager.stopServiceDiscovery(this);
			}

			@Override
			public void onStopDiscoveryFailed(String serviceType, int errorCode) {
				Log.e(TAG, "Stop Discovery failed: Error code:" + errorCode);
				nsdManager.stopServiceDiscovery(this);
			}
		};
	}

	// We create a new ResolveListener for each service we find.
	private NsdManager.ResolveListener createResolveListener(final String serviceNameKey) {
		return new NsdManager.ResolveListener() {
			@Override
			public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
				Log.e(TAG, "Resolve failed: " + errorCode);
			}

			@Override
			public void onServiceResolved(NsdServiceInfo serviceInfo) {
				Log.d(TAG, "Resolve Succeeded. " + serviceInfo);
				clientEventListener.onNSDNodeDiscovered(serviceInfo.getHost());
			}
		};
	}

	public interface INDSEventListener {
		void onNSDNodeDiscovered(InetAddress host);

		void onNSDNodeLost(String serviceName);
	}
}
