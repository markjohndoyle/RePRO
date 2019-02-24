package org.mjd.sandbox.nio.readers.header;

import java.nio.ByteBuffer;

public interface HeaderReader<T>
{

    boolean isComplete();

    /**
     * Buffer must be ready for reading, that is, flipped to read mode
     * @param headerBuffer
     */
    void readHeader(String id, ByteBuffer headerBuffer);

    int getValue();

    int remaining();

    void readHeader(String id, ByteBuffer headerBuffer, int offset);

}
