package org.mjd.sandbox.nio.message.factory;

import org.mjd.sandbox.nio.message.Message;

/**
 * Creates {@link Message} instances of type T from a byte array.
 *
 * @param <T> the type of the Message.
 */
public interface MessageFactory<T>
{
    public class MessageCreationException extends RuntimeException
    {
    	private static final long serialVersionUID = 1L;
        public MessageCreationException(Throwable e)
        {
            super(e);
        }

    }

    Message<T> createMessage(byte[] bytesRead) throws MessageCreationException;

    int getHeaderSize();

}
