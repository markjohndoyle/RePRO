package org.mjd.sandbox.nio.handlers.message;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;

import com.esotericsoftware.kryo.Kryo;
import org.mjd.sandbox.nio.handlers.message.SubscriptionRegistrar.Subscriber;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.writers.ChannelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

/**
 * {@link SubscriptionWriter} is an implementation of {@link Subscriber}. It is capable of receiving notifications and
 * forwarding them to a {@link ChannelWriter} with the correct {@link SelectionKey}. It also maintains the original
 * request message as required by the {@link ChannelWriter}
 *
 * @param <MsgType> the type of messages that the Server processes to subscribe this {@link SubscriptionWriter}
 *
 * @ThreadSafe
 */
public final class SubscriptionWriter<MsgType> implements Subscriber {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionWriter.class);
	private final Object mutex = new Object();
	private final Kryo kryo;
	private final SelectionKey key;
	private final ChannelWriter<MsgType, SelectionKey> channelWriter;
	private final Message<MsgType> message;

	/**
	 * Constructs a fully initialised {@link SubscriptionWriter} ready to process notifications.
	 *
	 * @param kryo    kryo object used to serialise incoming notifications
	 * @param key     the {@link SelectionKey} associated with the original client subscription request. This links the
	 *                client {@link Channel}
	 * @param writer  A {@link ChannelWriter} to handle writing back notifications to the client
	 * @param message The original request {@link Message}
	 */
	public SubscriptionWriter(final Kryo kryo, final SelectionKey key, final ChannelWriter<MsgType, SelectionKey> writer,
			final Message<MsgType> message) {
		this.kryo = kryo;
		this.key = key;
		this.channelWriter = writer;
		this.message = message;
	}

	@Override
	public void receive(final String notification) {
		final ResponseMessage<Object> responseMessage = new ResponseMessage<>(notification);
		final ByteBuffer resultByteBuffer = ByteBuffer.wrap(objectToKryoBytes(kryo, responseMessage));
		resultByteBuffer.position(resultByteBuffer.limit());
		LOG.trace(SubscriptionWriter.class + "recieved notification; handing result over to channel writer");
		synchronized (mutex) {
			channelWriter.writeResult(key, message, resultByteBuffer);
		}
	}

}
