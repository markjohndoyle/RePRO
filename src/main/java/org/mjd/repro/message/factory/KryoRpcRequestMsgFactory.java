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

public final class KryoRpcRequestMsgFactory implements MessageFactory<RpcRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(KryoRpcRequestMsgFactory.class);

	private final Kryo kryo = new RpcRequestKryoPool(false, false, 1).obtain();

	/**
	 * Expects a Kryo object with a marshalled RpcRequest
	 */
	@Override
	public Message<RpcRequest> createMessage(byte[] bytesRead) {
		try {
			return new RequestMessage<>(readBytesWithKryo(kryo, bytesRead));
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