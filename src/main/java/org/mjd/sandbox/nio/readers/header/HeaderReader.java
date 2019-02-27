package org.mjd.sandbox.nio.readers.header;

import java.nio.ByteBuffer;

public interface HeaderReader<T>
{

	/**
	 * Buffer must be ready for reading, that is, flipped to read mode
	 * @param headerBuffer
	 */
	void readHeader(String id, ByteBuffer headerBuffer);


	void readHeader(String id, ByteBuffer headerBuffer, int offset);

	/**
	 * if complete this implies {@link #remaining()} is 0. Implementations are required to enforce this.
	 * @return
	 */
    boolean isComplete();


    int getValue();

    /**
     * If remaining is < the header size this implies tha header reader is not yet complete
     * @return how many bytes of the header remain to be read.
     */
    int remaining();

}
