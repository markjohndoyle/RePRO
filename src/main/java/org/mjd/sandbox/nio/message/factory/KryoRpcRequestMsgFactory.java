package org.mjd.sandbox.nio.message.factory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.RpcRequestMessage;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KryoRpcRequestMsgFactory implements MessageFactory<RpcRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(KryoRpcRequestMsgFactory.class);

	private final Kryo kryo = new Kryo();

	public KryoRpcRequestMsgFactory() {
		kryo.register(RpcRequest.class);
		kryo.register(ArgumentValues.class);
		kryo.register(ArgumentValuePair.class);
	}

	@Override
	public int getHeaderSize() {
		return Integer.BYTES;
	}

	/**
	 * Expects a Kryo object with a marshalled RpcRequest
	 */
	@Override
	public Message<RpcRequest> create(byte[] bytesRead) {
		try {
			return new RpcRequestMessage(readBytesWithKryo(kryo, bytesRead));
		}
		catch (IOException e) {
			throw new MessageCreationException(e);
		}
	}

	private static RpcRequest readBytesWithKryo(Kryo kryo, byte[] data) {
		try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
			 Input kryoByteArrayIn = new Input(bin)) {
			return kryo.readObject(kryoByteArrayIn, RpcRequest.class);
		}
		catch (IOException e) {
			LOG.error("Error deserialising response from server", e);
			throw new MessageCreationException(e);
		}
	}
}