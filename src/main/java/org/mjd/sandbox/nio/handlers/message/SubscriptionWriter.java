package org.mjd.sandbox.nio.handlers.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import org.mjd.sandbox.nio.handlers.message.SubscriptionRegistrar.Subscriber;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.writers.ChannelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

public class SubscriptionWriter<MsgType> implements Subscriber {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionWriter.class);
	private final Pool<Kryo> kryos;
	private final SelectionKey key;
	private final ChannelWriter<MsgType, SelectionKey> channelWriter;
	private final Message<MsgType> message;

	public SubscriptionWriter(final Pool<Kryo> kryos, final SelectionKey key,
							 final ChannelWriter<MsgType, SelectionKey> writer, final Message<MsgType> message) {
		this.kryos = kryos;
		this.key = key;
		this.channelWriter = writer;
		this.message = message;
	}

	@Override
	public void receive(final String notification) {
		final Kryo kryo = kryos.obtain();
		try {
			final ResponseMessage<Object> responseMessage = new ResponseMessage<>(notification);
			final ByteBuffer resultByteBuffer = ByteBuffer.wrap(objectToKryoBytes(kryo, responseMessage));
			resultByteBuffer.position(resultByteBuffer.limit());
			channelWriter.writeResult(key, message, resultByteBuffer);
		}
		catch (IOException e) {
			LOG.error("Error notifying server of subscription message.", e);
		}
		finally {
			kryos.free(kryo);
		}

	}

}
