package com.climbtheworld.app.intercon.networking;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

public class DataFrame implements INetworkFrame {
    byte[] data;
    FrameType type;

    public void fromData(byte[] data, FrameType type) {
        this.data = data;
        this.type =type;
    }

    public void fromTransport(byte[] data) {
        if (data[0] == INetworkFrame.FrameType.SIGNAL.frameByte) {
            type = FrameType.SIGNAL;
        }

        if (data[0] == INetworkFrame.FrameType.DATA.frameByte) {
            type = FrameType.DATA;
        }
        this.data = Arrays.copyOfRange(data, 1, data.length);
    }

    @Override
    public FrameType getFrameType() {
        return type;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public int getLength() {
        return data.length + 1;
    }

    @Override
    public byte[] toByteArray() {
        return (ArrayUtils.addAll(new byte[]{type.frameByte}, data));
    }
}
