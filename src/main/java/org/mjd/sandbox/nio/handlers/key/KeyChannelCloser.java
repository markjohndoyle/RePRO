package org.mjd.sandbox.nio.handlers.key;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeyChannelCloser implements InvalidKeyHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(KeyChannelCloser.class);

    @Override
    public void handle(SelectionKey key) throws InvalidKeyHandlerException
    {
        LOG.trace("Invalid key {} closing channel", key.attachment());
        try
        {
            key.channel().close();
        }
        catch (IOException e)
        {
            throw new InvalidKeyHandlerException("Exception closing key channel ", e);
        }
    }

}
