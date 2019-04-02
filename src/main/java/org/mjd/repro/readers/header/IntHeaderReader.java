package org.mjd.repro.readers.header;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IntHeaderReader} is an implementaton of {@link HeaderReader} for a simple single integer value header.
 *
 * Therefore the size of the header is exepcted to be {@link Integer#BYTES} bytes. The value can of course represent
 * whatever your protocol demands. A typical use case would be the size of the following message body.</p>
 *
 * As mandated by {@link HeaderReader} this implementation will work across multiple calls to
 * {@link #readHeader(ByteBuffer)} and/or {@link #readHeader(ByteBuffer, int)}.
 */
public final class IntHeaderReader implements HeaderReader<Integer> {
	private static final Logger LOG = LoggerFactory.getLogger(IntHeaderReader.class);
	private static final int headerSize = Integer.BYTES;

	private final String id;
	private ByteBuffer headerBuffer;

	// Header value defaults to -1 which means unknown. We use this rather than optional for performance
	// This is ok since it's completely an implementation detail of this class.
	private int headerValue = -1;

	/**
	 * Constructs a fully initialised {@link IntHeaderReader} with an identfier used in logging. This is useful to correlate
	 * calls across both threads or non-blocking reads.
	 * </p>
	 * The identifier can be anything you require.
	 *
	 * @param id identifer for this {@link HeaderReader}.
	 */
	public IntHeaderReader(final String id) {
		this.id = id;
	}

	/**
	 * Constructs a fully initialised {@link IntHeaderReader} using this class name as the identfier.
	 *
	 * @see IntHeaderReader#IntHeaderReader(String)
	 */
	public IntHeaderReader() {
		this(IntHeaderReader.class.getName());
	}

	@Override
	public void readHeader(final ByteBuffer buffer) {
		LOG.trace("[{}] Reading header from {} with {} remaining ", id, buffer, buffer.remaining());
		if (thereIsDataInTheBuffer(buffer)) {
			if (thereIsOnlyPartOfTheDataInThe(buffer)) {
				setupLocalBufferIfNecessary();
				headerBuffer.put(buffer);
				if (!headerBuffer.hasRemaining()) {
					// We completed this time..
					LOG.trace("[{}] Header completed over multiple reads.", id);
					headerValue = ((ByteBuffer) headerBuffer.flip()).getInt();
				}
			}
			else {
				// it's all here first time, we can simply read it. HeaderReaders cannot be passed a buffer with
				// extra data.
				LOG.trace("[{}] Header arrived in once single read.", id);
				headerValue = buffer.getInt();
				headerBuffer = null;
			}
		}
	}

	@Override
	public void readHeader(final ByteBuffer headerBuffer, final int offset) {
		LOG.trace("[{}] Reading header from {} at offset {}", id, headerBuffer, offset);
		readHeader((ByteBuffer) headerBuffer.position(offset));
	}

	@Override
	public boolean isComplete() {
		return headerValue != -1;
	}

	@Override
	public Integer getValue() {
		if (headerValue == -1) {
			throw new IllegalStateException("IntHeaderReader.get() cannot be called before it is complete.");
		}
		return headerValue;
	}

	@Override
	public int remaining() {
		// We haven't read any partial header data
		if (headerBuffer == null) {
			// and the header was never read in one single read
			if (headerValue == -1) {
				return headerSize;
			}
			// we havent read part of the header and the value is complete so we must have 0 remaining
			// The data arrived in one read.
			return 0;
		}
		LOG.trace("[{}] Partial header buffer in play, state is {}", id, headerBuffer);
		return headerBuffer.remaining();
	}

	@Override
	public int getSize() {
		return headerSize;
	}

	private static boolean thereIsOnlyPartOfTheDataInThe(final ByteBuffer buffer) {
		return buffer.remaining() < headerSize;
	}

	private static boolean thereIsDataInTheBuffer(final ByteBuffer buffer) {
		return buffer.remaining() > 0;
	}

	private void setupLocalBufferIfNecessary() {
		if (!localBufferSetup()) {
			LOG.trace("[{}] Partial header received, creating partial header buffer cache", id);
			headerBuffer = ByteBuffer.allocate(headerSize);
		}
	}

	private boolean localBufferSetup() {
		return headerBuffer != null;
	}
}
