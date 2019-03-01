package org.mjd.sandbox.nio.writers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Writes a ByteBuffer to a SocketChannel with a prepended {@link Integer} header whose value
 * describes the length of the bytebuffer written.
 * </p>
 * Given a 21 byte buffer to write to the channel, this {@link Writer} will add a 4 byte header with the
 * value 21 in. </br>
 * Visually:
 * <pre>
 * +---------+ +--------------------------------+
 * |         | |                                |
 * |   21    | |  21 byte buffer to write       |
 * |         | |                                |
 * +---------+ +--------------------------------+
 * </pre>
 *
 * The write is capable of writing over multple calls if required.
 */
public final class SizeHeaderWriter implements Writer
{
    private static final Logger LOG = LoggerFactory.getLogger(SizeHeaderWriter.class);

    private static final int HEADER_LENGTH = Integer.BYTES;
    private ByteBuffer buffer;
    private final WritableByteChannel channel;
    private int bytesWritten;
    private int bodySize;
    private int expectedWrite = HEADER_LENGTH;
    private Object id;
    private ByteBuffer headerBuffer = ByteBuffer.allocate(Integer.BYTES);

    public SizeHeaderWriter(Object id, WritableByteChannel channel, ByteBuffer bufferToWrite)
    {
        this.id = id;
        this.channel = channel;
        this.buffer = bufferToWrite;
        bodySize = buffer.limit();
        LOG.trace("[{}] Body is {}", id, bodySize);
        expectedWrite = bodySize + HEADER_LENGTH;
        headerBuffer.putInt(bodySize).flip();
        LOG.trace("[{}] Writer created for response; expected write is '{}' bytes", id, expectedWrite);
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

    // This can block the server if it cannot write.
    @Override
    public void writeCompleteBuffer() {
    	LOG.trace("[{}] Writing...", id);
    	try {
    		while(headerBuffer.hasRemaining()) {
    			bytesWritten += channel.write(headerBuffer);
    			LOG.trace("Writing header {}, total written {}", headerBuffer, bytesWritten);
    		}
    		while(buffer.hasRemaining()) {
    			bytesWritten += channel.write(buffer);
    			LOG.trace("Writing body {}, total written (inc header) {}", buffer, bytesWritten);
    		}
    	}
    	catch(IOException e) {
    		LOG.error("[{}] Error writing {}", id, buffer, e);
    	}
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

    public static SizeHeaderWriter from(SelectionKey key, ByteBuffer bufferToWrite)
    {
        return new SizeHeaderWriter(key.attachment(), (WritableByteChannel) key.channel(), bufferToWrite);
    }
}
