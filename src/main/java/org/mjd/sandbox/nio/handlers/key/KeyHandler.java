package org.mjd.sandbox.nio.handlers.key;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface KeyHandler
{
    public static final class KeyHandlerException extends RuntimeException
    {
    	private static final long serialVersionUID = 1L;
        public KeyHandlerException(String msg, IOException e)
        {
            super(msg, e);
        }
    }

    void handle(SelectionKey key) throws KeyHandlerException;
}
