package com.ubudu.iot.sample.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by mgasztold on 23/11/2017.
 */

public class ByteUtil {

    public static byte[] downSample(final byte[] data) {
        final byte[] output = new byte[data.length / 2];
        int length = data.length;
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < length; i += 2) {
            output[i / 2] = (byte) (((bb.getShort() * 128.0) / 32768.0) + 128.0);
        }
        return output;
    }

}
