package org.mjd.repro.message.factory;

import org.mjd.repro.serialisation.Marshaller;

/**
 * A {@link MessageFactory} that uses a {@link Marshaller} to create messages of type MsgType from an array of bytes.
 *
 * @param <MsgType> the type of messages this {@link MessageFactory} creates.
 */
public final class MarshallerMsgFactory<MsgType> implements MessageFactory<MsgType> {
	private final Marshaller marshaller;
	private final Class<MsgType> type;

	/**
	 * Constructs a fully initialised {@link MarshallerMsgFactory} that uses a {@link Marshaller} to create messages of the
	 * given {@code type}
	 *
	 * @param marshaller the {@link Marshaller} that decodes bytes into messages of type MsgType
	 * @param type       the type of message this factory creates
	 */
	public MarshallerMsgFactory(final Marshaller marshaller, final Class<MsgType> type) {
		this.marshaller = marshaller;
		this.type = type;
	}

	@Override
	public MsgType createMessage(final byte[] bytesRead) {
		return marshaller.unmarshall(bytesRead, type);
	}
}
