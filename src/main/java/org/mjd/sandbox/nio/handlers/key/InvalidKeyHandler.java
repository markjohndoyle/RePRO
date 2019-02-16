package org.mjd.sandbox.nio.handlers.key;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface InvalidKeyHandler
{
    public static final class InvalidKeyHandlerException extends Exception
    {
        public InvalidKeyHandlerException(String msg, IOException e)
        {
            super(msg, e);
        }

        private static final long serialVersionUID = 1L;
        
    }
    
    void handle(SelectionKey key) throws InvalidKeyHandlerException;
}
