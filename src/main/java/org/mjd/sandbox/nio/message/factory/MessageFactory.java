package org.mjd.sandbox.nio.message.factory;

import java.nio.ByteBuffer;

import org.mjd.sandbox.nio.message.Message;

/**
 * Creates {@link Message} instances of type T from a byte array.
 *
 * @param <T>
 */
public interface MessageFactory<T>
{
    public class MessageCreationException extends RuntimeException
    {
        public MessageCreationException(Throwable e)
        {
            super(e);
        }

        private static final long serialVersionUID = 1L;
    }

    Message<T> create(byte[] bytesRead) throws MessageCreationException;

    int getHeaderSize();

	Message<T> create(ByteBuffer wrap);
}
