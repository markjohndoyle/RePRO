package org.mjd.repro.message.factory;

import org.mjd.repro.message.Message;

/**
 * Creates {@link Message} instances of type T from a byte array.
 *
 * @param <T> the type of the Message.
 */
public interface MessageFactory<T>
{
    class MessageCreationException extends RuntimeException
    {
    	private static final long serialVersionUID = 1L;
        public MessageCreationException(final Throwable e)
        {
            super(e);
        }

    }

    Message<T> createMessage(byte[] bytesRead) throws MessageCreationException;
}
