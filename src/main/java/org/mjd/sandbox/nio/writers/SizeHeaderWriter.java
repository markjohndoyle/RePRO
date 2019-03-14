package org.mjd.sandbox.nio.writers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a {@link Writer} that that wites a ByteBuffer to a SocketChannel with a prepended {@link Integer}
 * header whose value describes the length of the bytebuffer written.
 * </p>
 * E.g. Given a 21 byte buffer to write to the channel, this {@link Writer} will add a 4 byte header with the value 21
 * in. </br>
 * Visually:
 *
 * <pre>
 * +---------+ +--------------------------------+
 * | 4 bytes | |           21 bytes             |
 * |   21    | |         message data           |
 * +---------+ +--------------------------------+
 * </pre>
 *
 * The writer is capable of writing over multple calls if required.
 *
 * @NotThreadSafe
 */
public final class SizeHeaderWriter implements Writer {
	private static final Logger LOG = LoggerFactory.getLogger(SizeHeaderWriter.class);

	private static final int HEADER_LENGTH = Integer.BYTES;
	private final ByteBuffer buffer;
	private final WritableByteChannel channel;
	private final int bodySize;
	private final Object id;
	private final ByteBuffer headerBuffer = ByteBuffer.allocate(Integer.BYTES);
	private final int expectedWrite;
	private int bytesWritten;

	/**
	 * Constructs a fully initialised {@link SizeHeaderWriter} that can write the given {@link ByteBuffer} to the given
	 * {@link Channel}.
	 *
	 * @param id            identifier for this {@link Writer}. Used only for logging.
	 * @param channel       the {@link Channel} to write the data to
	 * @param bufferToWrite the {@link ByteBuffer} to write to the {@link Channel}
	 */
	public SizeHeaderWriter(final Object id, final WritableByteChannel channel, final ByteBuffer bufferToWrite) {
		this.id = id;
		this.channel = channel;
		this.buffer = bufferToWrite;
		headerBuffer.mark();
		buffer.mark();
		bodySize = buffer.limit();
		expectedWrite = bodySize + HEADER_LENGTH;
		headerBuffer.putInt(bodySize).flip();
		LOG.trace("[{}] Writer created for response; expected write is '{}' bytes of which {} is the body",
				  id, expectedWrite, bodySize);
	}

	@Override
	public void write() {
		try {
			writeHeader();
			writeBody();
		}
		catch (final IOException e) {
			headerBuffer.reset();
			buffer.reset();
			LOG.error("[{}] Error writing {}; buffers have been reset for any reattempts", id, buffer, e);
		}
	}

	/**
	 * Writes the complete {@link #headerBuffer}
	 *
	 * @throws IOException
	 */
	private void writeHeader() throws IOException {
		while (headerBuffer.hasRemaining()) {
			bytesWritten += channel.write(headerBuffer);
			LOG.trace("Writing header {}, total written {}", headerBuffer, bytesWritten);
		}
	}

	/**
	 * writes the complete {@link #buffer}
	 *
	 * @throws IOException
	 */
	private void writeBody() throws IOException {
		while (buffer.hasRemaining()) {
			bytesWritten += channel.write(buffer);
			LOG.trace("Writing body {}, total written (inc header) {}", buffer, bytesWritten);
		}
	}

	@Override
	public boolean isComplete() {
		if (bytesWritten > expectedWrite) {
			LOG.error("[{}] We wrote too much, somehow. This is wrong! {}/{}", id, bytesWritten, expectedWrite);
		}
		return bytesWritten == expectedWrite;
	}

	/**
	 * Static factory method for creating {@link SizeHeaderWriter}. Offers a more declarative syntactic sugar for clients
	 * that may have a Selection key rather than a {@link Channel}
	 *
	 * @param key           the {@link SelectionKey} that associated with the {@link Channel} the writer should use
	 * @param bufferToWrite the {@link ByteBuffer} to write to the {@link Channel}
	 * @return a new {@link SizeHeaderWriter}
	 *
	 * @see SizeHeaderWriter#SizeHeaderWriter(Object, WritableByteChannel, ByteBuffer)
	 */
	public static SizeHeaderWriter from(final SelectionKey key, final ByteBuffer bufferToWrite) {
		return new SizeHeaderWriter(key.attachment(), (WritableByteChannel) key.channel(), bufferToWrite);
	}
}
