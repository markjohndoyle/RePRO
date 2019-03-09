package org.mjd.sandbox.nio.message.factory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.util.Pool;
import org.mjd.sandbox.nio.message.IdentifiableRequest;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.RequestMessage;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;
import org.mjd.sandbox.nio.util.kryo.RpcRequestKryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KryoBasedRequestMsgFactory implements MessageFactory<IdentifiableRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(KryoBasedRequestMsgFactory.class);

	private final Kryo kryo = new RpcRequestKryoPool(false, false, 1).obtain();

	/**
	 * Expects a Kryo object with a marshalled RpcRequest
	 */
	@Override
	public Message<IdentifiableRequest> createMessage(byte[] bytesRead) {
		try {
			return new RequestMessage<>(readBytesWithKryo(kryo, bytesRead));
		}
		catch (IOException e) {
			throw new MessageCreationException(e);
		}
	}

	private static IdentifiableRequest readBytesWithKryo(Kryo kryo, byte[] data) {
		try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
			 Input kryoByteArrayIn = new Input(bin)) {
			return kryo.readObject(kryoByteArrayIn, IdentifiableRequest.class);
		}
		catch (IOException e) {
			LOG.error("Error deserialising response from server", e);
			throw new MessageCreationException(e);
		}
	}
}