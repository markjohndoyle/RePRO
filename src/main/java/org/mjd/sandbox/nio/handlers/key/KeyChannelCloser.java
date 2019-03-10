package org.mjd.sandbox.nio.handlers.key;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeyChannelCloser implements KeyHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(KeyChannelCloser.class);

    @Override
    public void handle(final SelectionKey key) throws KeyHandlerException
    {
        LOG.trace("Invalid key {} closing channel", key.attachment());
        try
        {
            key.channel().close();
        }
        catch (IOException e)
        {
            throw new KeyHandlerException("Exception closing key channel ", e);
        }
    }

}
