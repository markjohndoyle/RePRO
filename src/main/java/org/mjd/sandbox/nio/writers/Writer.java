package org.mjd.sandbox.nio.writers;

import java.io.IOException;

/**
 * Writes stuff and tracks whether it has finished.
 * 
 * this{@link #write()} must not block. 
 */
public interface Writer
{
    void write() throws IOException;

    boolean complete();
}