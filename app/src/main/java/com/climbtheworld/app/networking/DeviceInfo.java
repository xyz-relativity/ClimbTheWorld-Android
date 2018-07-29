package com.climbtheworld.app.networking;

/**
 * https://code.tutsplus.com/tutorials/create-a-bluetooth-scanner-with-androids-bluetooth-api--cms-24084
 */
public class DeviceInfo
{
    private String name;
    private String address;
    INetworkClient client;

    public DeviceInfo(String name, String address, INetworkClient client)
    {
        this.name = name;
        this.address = address;
        this.client = client;
    }
    // Return Device name
    public String getName()
    {
        return name;
    }
    // Return Device address
    public String getAddress()
    {
        return address;
    }

    public INetworkClient getClient() {
        return client;
    }

    @Override
    public String toString() {
        return getName();
    }
}
