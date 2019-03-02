package org.mjd.sandbox.nio.readers.body;

import java.nio.ByteBuffer;

import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts a single message over 1..n reads of ByteBuffers holding message data, that is, bytes.
 *
 * The {@link SingleMessageBodyReader} can complete in 1..n reads depending upon how much of the message
 * data is provided to the {@link #read(ByteBuffer)} call. All buffers must of course must be pass in
 * sequential order, the {@link BodyReader} cannot detect and resequence bytes.
 *
 * @param <T> The type of message this reader extracts.
 */
public final class SingleMessageBodyReader<T> implements BodyReader<T> {
	private static final Logger LOG = LoggerFactory.getLogger(SingleMessageBodyReader.class);
	private final String id;
	private int remainingBody;
    private int bodySize;
    private int totalBodyRead;
    private byte[] bytesReadFromChannel;
    private Message<T> message = null;
	private final MessageFactory<T> msgFactory;

	public SingleMessageBodyReader(String id, MessageFactory<T> messageFactory) {
		this.id = id;
		this.msgFactory = messageFactory;
	}

	@Override
	public ByteBuffer read(ByteBuffer bodyBuffer) {
		final int bytesInBuffer = bodyBuffer.remaining();
		int bytesBelongingToThis = getNumRelevantBytes(bytesInBuffer);
		ByteBuffer followingData = copyFollowingData(bodyBuffer, bytesInBuffer, bytesBelongingToThis);
		handleRelevantBytes(bodyBuffer, bytesBelongingToThis);
		return followingData;
	}

	private void handleRelevantBytes(ByteBuffer bodyBuffer, int bytesBelongingToThis) {
		if(bytesBelongingToThis > 0) {
		    bodyBuffer.get(bytesReadFromChannel, totalBodyRead, bytesBelongingToThis);
		    totalBodyRead += bytesBelongingToThis;

		    if(totalBodyRead == bodySize)
		    {
		        LOG.trace("[{}] Total read {}/{} of message size. Creating message.", id, totalBodyRead, bodySize);
		        message = msgFactory.createMessage(bytesReadFromChannel);
		        return;
		    }
	        LOG.trace("[{}] Not read the entire body yet. Read {} of {}", id, totalBodyRead, bodySize);
	        remainingBody = bodySize - totalBodyRead;
		}
	}

	private ByteBuffer copyFollowingData(ByteBuffer bodyBuffer, final int bytesInBuffer, int bytesBelongingToThis) {
		if(bytesInBuffer > bytesBelongingToThis)
		{
			LOG.trace("[{}] We have part of the next message, {} bytes belong to this body of {} remaining in the body buffer",
					id, remainingBody, bytesInBuffer);
			byte[] remainder = new byte[bytesInBuffer - bytesBelongingToThis];
			bodyBuffer.mark();
			bodyBuffer.position(bytesInBuffer - remainder.length);
			bodyBuffer.get(remainder, 0, remainder.length);
			bodyBuffer.reset();
			return ByteBuffer.wrap(remainder);
		}
		return ByteBuffer.allocate(0);
	}

	/**
	 * Calculates how many bytes in the body buffer are relevent for this message. This deal with two cases:
	 *
	 * A. If the buffer has too much data the relevent bytes are the size of the body message minus any
	 * bytes that have already been saved by previous read attempts.
	 * B. Otherwise, the relevant bytes is whatever is in the buffer or the remaming body to read for this message,
	 * whichever is the least.
	 *
	 * For example, the two diagrams demonstrate the cases:
	 * <pre>
	 *  Case A:
	 *  +-+-+-+-+-+-+-+-+
	 *  |x|x|x|x|h|h|h| | We are only interested in the 4 x bytes, not the following h bytes
	 *  +-+-+-+-+-+-+-+-+
	 *
	 *
	 *  Case B:
	 *  +-+-+-+-+-+-+-+-+
	 *  |a|a|b|b| | | | | a are message bytes we've previously read, in this case we only care about bytes b
	 *  +-+-+-+-+-+-+-+-+
	 * </pre>
	 *
	 * @param bytesInBuffer
	 * @return
	 */
	private int getNumRelevantBytes(final int bytesInBuffer) {
		int bytesBelongingToThis;
		if(bytesInBuffer > bodySize) {
			bytesBelongingToThis = bodySize - totalBodyRead;
		}
		else {
			bytesBelongingToThis = Math.min(bytesInBuffer, remainingBody);
		}
		return bytesBelongingToThis;
	}

	@Override
	public boolean isComplete() {
		return message != null;
	}

	@Override
	public Message<T> getMessage() {
		return message;
	}

	@Override
	public void setBodySize(int size) {
		// TODO whole "setter" thing is garbage, it will be removed.
		if(bytesReadFromChannel == null)
		{
			bodySize = size;
			bytesReadFromChannel = new byte[bodySize];
			remainingBody = bodySize;
		}
	}

}
