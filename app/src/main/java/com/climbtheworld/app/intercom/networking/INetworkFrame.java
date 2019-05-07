package com.climbtheworld.app.intercom.networking;

public interface INetworkFrame {
    enum FrameType {
        SIGNAL((byte) 1),
        DATA((byte) 2);

        public final byte frameByte;
        FrameType(byte frameByte) {
            this.frameByte = frameByte;
        }
    }

    FrameType getFrameType();
    byte[] getData();
    int getLength();
    byte[] toByteArray();
}
