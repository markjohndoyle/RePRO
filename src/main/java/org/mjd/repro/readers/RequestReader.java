package org.mjd.repro.readers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Optional;

import org.mjd.repro.message.factory.MessageFactory;
import org.mjd.repro.message.factory.MessageFactory.MessageCreationException;
import org.mjd.repro.readers.body.BodyReader;
import org.mjd.repro.readers.body.SingleMessageBodyReader;
import org.mjd.repro.readers.header.HeaderReader;
import org.mjd.repro.readers.header.IntHeaderReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestReader<T> implements MessageReader<T> {
	private static final Logger LOG = LoggerFactory.getLogger(RequestReader.class);
	private final ScatteringByteChannel channel;
	private final String id;
	private final HeaderReader<Integer> headerReader;
	private final BodyReader<T> bodyReader;
	private final int headerSize;
	private boolean vectoredIO;
	private T message;
	private boolean endOfStream;

	/**
	 * @param requestID
	 * @param channel
	 * @param factory
	 */
	public RequestReader(final String requestID, final ScatteringByteChannel channel, final MessageFactory<T> factory) {
		this.id = requestID;
		this.channel = channel;
		headerReader = new IntHeaderReader(id);
		headerSize = headerReader.getSize();
		bodyReader = new SingleMessageBodyReader<>(id, factory);
	}

	@Override
	public ByteBuffer[] read(final ByteBuffer headerBuffer, final ByteBuffer bodyBuffer)
			throws MessageCreationException, IOException {
		LOG.trace("[{}] ------------NEW READ------------------", id);
		prereadCheck();

		final ByteBuffer[] vectoredBuffers = setupVectoredBuffers(headerBuffer, bodyBuffer);
		LOG.trace("[{}] headerbuffer state before send to read: {}  {}", id, headerBuffer, Arrays.toString(headerBuffer.array()));
		LOG.trace("[{}] bodybuffer state before send to read: {} ", id, bodyBuffer);// , Arrays.toString(bodyBuffer.array()));
		final long totalBytesReadThisCall = readFromChannel(vectoredBuffers);

		if (totalBytesReadThisCall == -1) {
			LOG.debug("[{}] ENDOFSTREAM for '{}'. Message complete: {}", id, id, messageComplete());
			endOfStream = true;
			return new ByteBuffer[] { ByteBuffer.allocate(0), ByteBuffer.allocate(0) };
		}

		final ByteBuffer[] remaining = decodeData(headerBuffer, bodyBuffer, totalBytesReadThisCall);
		LOG.trace("[{}] ------------END READ------------------", id);
		return remaining;
	}

	@Override
	public ByteBuffer[] readPreloaded(final ByteBuffer headerBuffer, final ByteBuffer bodyBuffer) throws IOException {
		LOG.trace("[{}] ------------NEW PRELOADED READ------------------", id);
		prereadCheck();

		LOG.trace("[{}] headerbuffer state before send to read: {}  {}", id, headerBuffer, Arrays.toString(headerBuffer.array()));
		LOG.trace("[{}] bodybuffer state before send to read: {} ", id, bodyBuffer);// , Arrays.toString(bodyBuffer.array()));
		vectoredIO = true;
		final int totalBytesReadThisCall = headerBuffer.position() + bodyBuffer.position();
		final ByteBuffer[] remaining = decodeData(headerBuffer, bodyBuffer, totalBytesReadThisCall);
		LOG.trace("[{}] ------------END PRELOADED READ------------------", id);
		return remaining;
	}

	private void prereadCheck() throws IOException {
		if (endOfStream) {
			throw new IOException(
					"Cannot read because this reader encloses a channel that as previously sent " + "end of stream");
		}
	}

	/**
	 * Sets up the red buffer array. This may be vectored, that is, the header and body buffer included in that order, or
	 * just the body buffer if the header has already been competely decoded.
	 *
	 * If the header is not yet completely decoded we set the buffer to the position to where it previously read up to.
	 *
	 *
	 * Here is a typicaly header body layout.
	 *
	 * <pre>
	 *   _____________   ________
	 *  | h1 h2 h3 h4 | | b b b b ...
	 *   -------------   --------
	 * </pre>
	 *
	 * If we previosly read two bytes (h1 and h2) then we must move the header buffer position to h3 otherwise the next
	 * vectored read with put h3 at position 0 and any further data directly after that. This means the body message will
	 * pollute the header.
	 *
	 * We mark the position because we will need to read it later. Simply flipping the buffer will take us back to position
	 * 0 which will contain undefeined data.
	 *
	 * @param headerBuffer
	 * @param bodyBuffer
	 * @return
	 */
	private ByteBuffer[] setupVectoredBuffers(final ByteBuffer headerBuffer, final ByteBuffer bodyBuffer) {
		ByteBuffer[] vectoredBuffers;
		if (!headerReader.isComplete()) {
			// we don't have the header yet but we MAY have part of it
			// Set the headerBuffer position to the position it would be at and mark it for later
			// If we don't do this, the vectored "scattering" read on the channel will write the beginning of the
			// body message into the end of the header buffer, thus polluting it. We need the mark so we can
			// position the buffer for reading later, flipping it will take us back to the beginning which will
			// include irrelevant data.
			// If we don't have anything header dat yet this will effectivly flip the buffer
			headerBuffer.position(headerSize - headerReader.remaining()).mark(); // Explain this part - header split over multiple
																				 // reads - aligning buffer to simulate it
																				 // arriving in one go
			LOG.trace("[{}] partial header in vectored read - remaining bytes is {} of {}; setting position and "
					+ "marking to {}", id, headerReader.remaining(), headerSize, headerSize - headerReader.remaining());
			vectoredBuffers = new ByteBuffer[] { headerBuffer, bodyBuffer };
			vectoredIO = true;
		}
		else {
			LOG.trace("[{}] Header already complete on a previous read, using body buffer alone for further reads", id);
			vectoredBuffers = new ByteBuffer[] { bodyBuffer };
			vectoredIO = false;
		}
		return vectoredBuffers;
	}

	/**
	 * Reads from the {@link #channel} into the buffer array.
	 *
	 * @param vectoredBuffers
	 * @return
	 */
	private long readFromChannel(final ByteBuffer[] vectoredBuffers) {
		long totalBytesReadThisCall = 0;
		try {
			LOG.trace("[{}] Reading from channel <--[=====--]. Vectoring is {}, Using {} buffer(s)", id, vectoredIO,
					vectoredBuffers.length);
			long bytesRead;
			while ((bytesRead = channel.read(vectoredBuffers)) > 0) {
				LOG.trace("[{}] Read {} bytes from channel", id, bytesRead);
				totalBytesReadThisCall += bytesRead;
			}
			if (bytesRead == -1) {
				return -1;
			}
			LOG.trace("[{}] Read {} bytes from channel until no more data, ie. last bytes read was {}", id,
					totalBytesReadThisCall, bytesRead);
		}
		catch (final IOException e) {
			LOG.trace("[{}] Client channel disconnected in read. Ending stream.", id);
			endOfStream = true;
			return -1L;
		}
		return totalBytesReadThisCall;
	}

	private ByteBuffer[] decodeData(final ByteBuffer headerBuffer, final ByteBuffer bodyBuffer,
			final long totalBytesReadThisCall) {
		ByteBuffer remaining = ByteBuffer.allocate(0);
		if (totalBytesReadThisCall > 0) {
			if (!headerReader.isComplete()) {
				readHeader(headerBuffer, totalBytesReadThisCall);
			}

			// Maybe it's complete now? check again
			if (headerReader.isComplete()) {
				bodyReader.setBodySize(headerReader.getValue());
				remaining = readBody(bodyBuffer);
			}
		}

		if (remaining.hasRemaining()) {
			final int lim = remaining.limit();
			remaining.position(0).limit(Math.min(headerSize, remaining.limit()));
			final ByteBuffer remainingHeader = ByteBuffer.allocate(headerSize).put(remaining);
			remaining.limit(lim);
			remaining.compact().position(remaining.limit());
			remaining.limit(remaining.limit() - Math.min(headerSize, remaining.limit()));
			return new ByteBuffer[] { remainingHeader, remaining };
		}
		return new ByteBuffer[] { remaining, remaining };
	}

	private ByteBuffer readBody(final ByteBuffer bodyBuffer) {
		final ByteBuffer remainingData = bodyReader.read((ByteBuffer) bodyBuffer.flip());
		if (bodyReader.isComplete()) {
			message = bodyReader.getMessage();
		}
		return remainingData;
	}

	private void readHeader(final ByteBuffer headerBuffer, final long totalBytesReadThisCall) {
		LOG.trace("[{}] headerbuff state before send to Header reader: {} Contents: {}", id, headerBuffer,
				Arrays.toString(headerBuffer.array()));
		// Header is at 0 - 3, that is, we have started a partial read. We need to reposition the buffer
		// for reading so the header reader gets the correct dats
		// for partial reads we read into the buffer at the expected offset. We had to do this so the
		// message body didn't leak into the end of the header buffer in a vectored read.
		if (headerReader.remaining() < headerSize) {
			final int inThisBuffer = Math.min(headerReader.remaining(), Math.toIntExact(totalBytesReadThisCall));
			LOG.trace("{} header remaining is not equal to {} header size. "
					  + "Reseting to mark and moving limit back to {} before passing to header reader."
					  + "There are {} bytes of header in this buffer",
					  headerReader.remaining(), headerSize, headerBuffer.position() + inThisBuffer, inThisBuffer);
			headerReader.readHeader((ByteBuffer) headerBuffer.reset().limit(headerBuffer.position() + inThisBuffer));
		}
		else {
			// We've not read any header data previously therefore we should read the header buffer from
			// position 0
			LOG.trace("[{}] Not read any header before this read cycle", id);
			headerReader.readHeader((ByteBuffer) headerBuffer.flip());
		}
	}

	@Override
	public Optional<T> getMessage() {
		return Optional.ofNullable(message);
	}

	@Override
	public boolean messageComplete() {
		return message != null;
	}

	@Override
	public boolean isEndOfStream() {
		return endOfStream;
	}

	public static <MsgType> RequestReader<MsgType>
	from(final SelectionKey key, final MessageFactory<MsgType> messageFactory) {
		return new RequestReader<>((String) key.attachment(), (SocketChannel) key.channel(), messageFactory);
	}

	@Override
	public int getHeaderSize() {
		return headerSize;
	}
}
