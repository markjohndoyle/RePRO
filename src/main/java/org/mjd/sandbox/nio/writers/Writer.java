package org.mjd.sandbox.nio.writers;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Writes stuff and tracks whether it has finished.
 * 
 * this{@link #write()} must not block. 
 */
public interface Writer
{
    void write() throws IOException;

    boolean isComplete();

    void write(ByteBuffer writeBuffer);
}