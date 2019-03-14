package org.mjd.repro.message.factory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.mjd.repro.message.IdentifiableRequest;
import org.mjd.repro.message.Message;
import org.mjd.repro.message.RequestMessage;
import org.mjd.repro.util.kryo.RpcRequestKryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MessageFactory} for {@link IdentifiableRequest} types.
 *
 * This factory uses {@link Kryo} to create {@link Message} instances from byte arrays.
 */
public final class KryoBasedRequestMsgFactory implements MessageFactory<IdentifiableRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(KryoBasedRequestMsgFactory.class);

	// Move this to constructor
	private final Kryo kryo = new RpcRequestKryoPool(false, false, 1).obtain();

	/**
	 * Expects a Kryo object with a marshalled RpcRequest
	 */
	@Override
	public Message<IdentifiableRequest> createMessage(final byte[] bytesRead) {
		try {
			return new RequestMessage<>(readBytesWithKryo(kryo, bytesRead));
		}
		catch (final IOException e) {
			throw new MessageCreationException(e);
		}
	}

	private static IdentifiableRequest readBytesWithKryo(final Kryo kryo, final byte[] data) {
		try (ByteArrayInputStream bin = new ByteArrayInputStream(data); Input kryoByteArrayIn = new Input(bin)) {
			return kryo.readObject(kryoByteArrayIn, IdentifiableRequest.class);
		}
		catch (final IOException e) {
			LOG.error("Error deserialising response from server", e);
			throw new MessageCreationException(e);
		}
	}
}