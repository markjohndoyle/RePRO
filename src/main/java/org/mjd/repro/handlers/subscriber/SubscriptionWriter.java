package org.mjd.repro.handlers.subscriber;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;

import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.handlers.subscriber.SubscriptionRegistrar.Subscriber;
import org.mjd.repro.message.RequestWithArgs;
import org.mjd.repro.serialisation.Marshaller;
import org.mjd.repro.writers.ChannelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SubscriptionWriter} is an implementation of {@link Subscriber}. It is capable of receiving notifications and
 * forwarding them to a {@link ChannelWriter} with the correct {@link SelectionKey}. It also maintains the original
 * request message as required by the {@link ChannelWriter}
 *
 * @param <R> the type of {@link RequestWithArgs} messages that the Server processes to subscribe this
 *        {@link SubscriptionWriter}
 *
 * @NotThreadSafe unless the marshaller is
 */
public final class SubscriptionWriter<R extends RequestWithArgs> implements Subscriber {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionWriter.class);
	private final Object mutex = new Object();
	private final Marshaller marshaller;
	private final SelectionKey key;
	private final ChannelWriter<R, SelectionKey> channelWriter;
	private final R message;

	/**
	 * Constructs a fully initialised {@link SubscriptionWriter} ready to process notifications.
	 *
	 * @param marshaller kryo object used to serialise incoming notifications
	 * @param key        the {@link SelectionKey} associated with the original client subscription request. This links
	 * 					 the client {@link Channel}
	 * @param writer     A {@link ChannelWriter} to handle writing back notifications to the client
	 * @param message    The original {@link RequestWithArgs} message
	 */
	public SubscriptionWriter(final Marshaller marshaller, final SelectionKey key,
			final ChannelWriter<R, SelectionKey> writer, final R message) {
		this.marshaller = marshaller;
		this.key = key;
		this.channelWriter = writer;
		this.message = message;
	}

	@Override
	public void receive(final String notification) {
		final ResponseMessage<Object> responseMessage = new ResponseMessage<>(message.getId(), notification);
		final ByteBuffer resultByteBuffer = ByteBuffer.wrap(marshaller.marshall(responseMessage, ResponseMessage.class));
		resultByteBuffer.position(resultByteBuffer.limit());
		LOG.trace(SubscriptionWriter.class + "received notification; handing result over to channel writer");
		synchronized (mutex) {
			resultByteBuffer.flip();
			channelWriter.prepWrite(key, message, resultByteBuffer);
		}
	}

}
