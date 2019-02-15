package org.mjd.sandbox.nio.writers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Writes a ByteBuffer to a SocketChannel.
 *
 */
public final class ByteBufferWriter implements Writer
{
    private static final Logger LOG = LoggerFactory.getLogger(ByteBufferWriter.class);
    
    private static final int HEADER_LENGTH = Integer.BYTES;
    private final ByteBuffer buffer;
    private final SocketChannel channel;
    private int bytesWritten;
    private final int bodySize;
    private int expectedWrite = HEADER_LENGTH;


    public ByteBufferWriter(SocketChannel channel, ByteBuffer bufferToWrite)
    {
        this.channel = channel;
        this.buffer = bufferToWrite;
        bodySize = buffer.limit();
        expectedWrite = bodySize + HEADER_LENGTH;
        LOG.trace("Total expected write is '{}' bytes", expectedWrite);
    }

    @Override
    public void write() throws IOException
    {
        if (bytesWritten < HEADER_LENGTH)
        {
            bytesWritten += channel.write((ByteBuffer) ByteBuffer.allocate(Integer.BYTES).putInt(bodySize).flip());
        }
        else
        {
            bytesWritten += channel.write(buffer);
        }
    }

    @Override
    public boolean complete()
    {
        return bytesWritten == expectedWrite;
    }

}
