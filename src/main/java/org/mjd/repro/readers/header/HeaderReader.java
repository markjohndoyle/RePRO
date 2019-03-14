package org.mjd.repro.readers.header;

import java.nio.ByteBuffer;

/**
 * A {@link HeaderReader} is a type capable of extracting and decoding a header from  a ByteBuffer.
 * Once complete, the {@link HeaderReader} will make the decoded head value (currently fixed as an int)
 * available through the getter {@link #getValue()}.
 *
 * The {@link HeaderReader} will know when it is complete (it is free to take multiple reads before
 * completion, for example, in a non-blocking scenario). It will know how many bytes are remaining before
 * it can complete and it will know how many bytes the header is expected to be.
 *
 * @param <T> the type that represents the decoded header value
 */
public interface HeaderReader<T>
{

	/**
	 * Read header bytes from the given {@link ByteBuffer} {@code headerBuffer}. The {@link ByteBuffer} may
	 * not necessarily contain all the data needed for this {@link HeaderReader} to complete.
	 * Implementations must take care to satisfy this for non-blocking scenarios. The buffer may <b>NOT</b>
	 * contain extra data, it's limit must be the within or the end of the header data.
	 * </p>
	 * The {@code headerBuffer} must be ready for reading, that is, flipped to read mode.
	 * @param id
	 * @param headerBuffer
	 */
	void readHeader(ByteBuffer headerBuffer);

	/**
	 * Read header bytes from the given {@link ByteBuffer} {@code headerBuffer} startign at {@code offsett}. The
	 * {@link ByteBuffer} may not necessarily contain all the data needed for this {@link HeaderReader} to complete.
	 * Implementations must take care to satisfy this for non-blocking scenarios.
	 * The buffer may <b>NOT</b> contain extra data, it's limit must be the within or the end of the header data.
	 * </p>
	 * The {@code headerBuffer} must be ready for reading, that is, flipped to read mode.
	 *
	 * @param headerBuffer the {@link ByteBuffer} containing header data. The buffer will only contain header bytes in
	 * 					   the correct order. The {@link HeaderReader} does not need to manipulate the buffer in any
	 * 					   way other than reading.
	 * @param offset       the position to read the buffer from. Reading data before this offset is considered
	 * 					   undefined behaviour. {@link HeaderReader} implementations should ignore it.
	 */
	void readHeader(ByteBuffer headerBuffer, int offset);

	/**
	 * if complete this implies {@link #remaining()} is 0. Implementations are required to enforce this.
	 *
	 * @return true if this {@link HeaderReader} has read the complete header
	 */
    boolean isComplete();


	/**
	 * @return the decoded header value.
	 */
    T getValue();

	/**
	 * If remaining is < the header size this implies tha header reader is not yet complete.
	 *
	 * @return how many bytes of the header remain to be read.
	 */
    int remaining();


	/**
	 * @return the size in bytes of the header. This is the total number of bytes the {@link HeaderReader} needs to decode
	 *         the header value.
	 */
	int getSize();

}
