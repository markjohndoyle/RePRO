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
    private Object id;

    public ByteBufferWriter(Object id, WritableByteChannel channel, ByteBuffer bufferToWrite)
    {
        this.id = id;
        this.channel = channel;
        this.buffer = bufferToWrite;
        bodySize = buffer.limit();
        expectedWrite = bodySize + HEADER_LENGTH;
        LOG.trace("[{}] Writer created for response; expected write is '{}' bytes", id, expectedWrite);
    }

    public ByteBufferWriter(Object id, WritableByteChannel channel, int size)
    {
        this.id = id;
        this.channel = channel;
        bodySize = size;
        expectedWrite = bodySize + HEADER_LENGTH;
        LOG.trace("[{}] Writer created for response; expected write is '{}' bytes", id, expectedWrite);
    }

    /**
     * Creates a ByteByfferWrited
     * @param key
     * @param bufferToWrite
     * @return
     */
    public static ByteBufferWriter from(Object id, SelectionKey key, ByteBuffer bufferToWrite)
    {
        return new ByteBufferWriter(id, (WritableByteChannel) key.channel(), bufferToWrite);
    }

    @Override
    public void write()
    {
        LOG.trace("[{}] Writing...", id);
        try
        {
            if (bytesWritten == 0)
            {
                bytesWritten += channel.write((ByteBuffer) ByteBuffer.allocate(Integer.BYTES).putInt(bodySize).flip());
                LOG.trace("[{}] {}/{} header bytes written", id, bytesWritten, Integer.BYTES);
            }
            if(bytesWritten >= HEADER_LENGTH)
            {
                bytesWritten += channel.write(buffer);
                LOG.trace("[{}] {}/{} bytes written", id, bytesWritten - Integer.BYTES, bodySize);
            }
        }
        catch(IOException e)
        {
            LOG.error("[{}] Error writing {}", id, buffer, e);
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
    public boolean isComplete()
    {
        if(bytesWritten > expectedWrite)
        {
            LOG.error("[{}] We wrote too much, somehow. This is wrong! {}/{}", id, bytesWritten, expectedWrite);
        }
        return bytesWritten == expectedWrite;
    }

    public static ByteBufferWriter from(SelectionKey key, ByteBuffer bufferToWrite)
    {
        return new ByteBufferWriter(key.attachment(), (WritableByteChannel) key.channel(), bufferToWrite);
    }
}
