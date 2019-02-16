package org.mjd.sandbox.nio.writers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

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
    private ByteBuffer buffer;
    private final WritableByteChannel channel;
    private int bytesWritten;
    private int bodySize;
    private int expectedWrite = HEADER_LENGTH;


    public ByteBufferWriter(WritableByteChannel channel, ByteBuffer bufferToWrite)
    {
        this.channel = channel;
        this.buffer = bufferToWrite;
        bodySize = buffer.limit();
        expectedWrite = bodySize + HEADER_LENGTH;
        LOG.trace("Total expected write is '{}' bytes", expectedWrite);
    }

    public ByteBufferWriter(WritableByteChannel channel, int size)
    {
        this.channel = channel;
        bodySize = size;
        expectedWrite = bodySize + HEADER_LENGTH;
        LOG.trace("Total expected write is '{}' bytes", expectedWrite);
    }
    
    /**
     * Creates a ByteByfferWrited
     * @param key
     * @param bufferToWrite
     * @return
     */
    public static ByteBufferWriter from(SelectionKey key, ByteBuffer bufferToWrite)
    {
        return new ByteBufferWriter((WritableByteChannel) key.channel(), bufferToWrite);
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
    public void write(ByteBuffer writeBuffer)
    {
//        if (bytesWritten < HEADER_LENGTH)
//        {
//            bytesWritten += channel.write((ByteBuffer) ByteBuffer.allocate(Integer.BYTES).putInt(bodySize).flip());
//        }
//        else
//        {
//            bytesWritten += channel.write(writeBuffer);
//        }
    }

    @Override
    public boolean complete()
    {
        return bytesWritten == expectedWrite;
    }


}
