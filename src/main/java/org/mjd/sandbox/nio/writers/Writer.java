package org.mjd.sandbox.nio.writers;

import java.io.IOException;

/**
 * A {@link Writer} is an incredibly simple type that can write something entirely implementation specific.
 * The only other requirement of a {@link Writer} is that is can indicated when it has completed the write.
 *
 */
public interface Writer
{
    /**
     * Carry out the write. The details of this are implementation specific.
     *
     * @throws IOException thrown is the write has an error.
     */
    void write() throws IOException;

    /**
     * @return true if the {@link Writer} is complete. This implies futher calls to write will not do anything.
     */
    boolean isComplete();
}