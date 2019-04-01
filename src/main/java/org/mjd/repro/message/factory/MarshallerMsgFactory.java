package org.mjd.repro.message.factory;

import org.mjd.repro.serialisation.Marshaller;

public final class MarshallerMsgFactory<MsgType> implements MessageFactory<MsgType> {
	private final Marshaller marshaller;
	private final Class<MsgType> type;

	public MarshallerMsgFactory(final Marshaller marshaller, final Class<MsgType> type) {
		this.marshaller = marshaller;
		this.type = type;
	}

	@Override
	public MsgType createMessage(final byte[] bytesRead) {
		return marshaller.unmarshall(bytesRead, type);
	}
}
