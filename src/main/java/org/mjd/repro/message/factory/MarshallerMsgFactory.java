package org.mjd.repro.message.factory;

import org.mjd.repro.message.RequestWithArgs;
import org.mjd.repro.serialisation.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarshallerMsgFactory<R extends RequestWithArgs> implements MessageFactory<R> {
	private static final Logger LOG = LoggerFactory.getLogger(MarshallerMsgFactory.class);

	private final Marshaller marshaller;

	private final Class<R> type;

	public MarshallerMsgFactory(final Marshaller marshaller, final Class<R> type) {
		this.marshaller = marshaller;
		this.type = type;
	}

	/**
	 * Expects a Kryo object with a marshalled RpcRequest
	 */
	@Override
	public R createMessage(final byte[] bytesRead) {
		return marshaller.unmarshall(bytesRead, type);
	}
}
