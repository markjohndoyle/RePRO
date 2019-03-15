package org.mjd.repro.message.factory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.mjd.repro.message.Message;
import org.mjd.repro.message.RequestMessage;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.util.kryo.RpcRequestKryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KryoRpcRequestMsgFactory<R extends RpcRequest> implements MessageFactory<R> {
	private static final Logger LOG = LoggerFactory.getLogger(KryoRpcRequestMsgFactory.class);

	private final Kryo kryo;

	private final Class<R> type;

	public KryoRpcRequestMsgFactory(final Kryo kryo, final Class<R> type) {
		this.kryo = kryo;
		this.type = type;
	}

	/**
	 * Expects a Kryo object with a marshalled RpcRequest
	 */
	@Override
	public Message<R> createMessage(final byte[] bytesRead) {
		try {
			return new RequestMessage<>(readBytesWithKryo(kryo, bytesRead));
		}
		catch (final IOException e) {
			throw new MessageCreationException(e);
		}
	}

	private R readBytesWithKryo(final Kryo kryo, final byte[] data) {
		try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
			 Input kryoByteArrayIn = new Input(bin)) {
			return kryo.readObject(kryoByteArrayIn, type);
		}
		catch (final IOException e) {
			LOG.error("Error deserialising response from server", e);
			throw new MessageCreationException(e);
		}
	}
}