package org.mjd.sandbox.nio.readers.header;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class IntHeaderReader implements HeaderReader<Integer>
{
    private static final Logger LOG = LoggerFactory.getLogger(IntHeaderReader.class);

    private final String id;
    private final int headerSize;
    private int headerValue = -1;
    private ByteBuffer headerBuffer = null;

    public IntHeaderReader(int size)
    {
    	this("Unknown client", size);
    }
    public IntHeaderReader(String id, int size)
    {
    	this.id = id;
        if(size != Integer.BYTES)
        {
            throw new IllegalArgumentException("Header size must be " + Integer.BYTES + ". It is " + size);
        }
        headerSize = size;
    }

    @Override
    public void readHeader(String id, ByteBuffer buffer)
    {
        LOG.trace("[{}] Reading header from {} with {} remaining ", id, buffer, buffer.remaining());
        // if we didn't read enough
        if(buffer.remaining() < headerSize)
        {
            if(headerBuffer == null)
            {
            	LOG.trace("[{}] Partial header received, creating partial header buffer cache", id);
                headerBuffer = ByteBuffer.allocate(headerSize);
            }
            // The buffer contains part of the header, it could be any part
            LOG.trace("[{}] HeaderBuffer for partials state {} pre copy", id, headerBuffer);
        	headerBuffer.put(buffer);
            LOG.trace("[{}] HeaderBuffer for partials state {} post copy", id, headerBuffer);

            // we didn't read enough for a complete header, but it was the final piece, the header is now complete
            if(!headerBuffer.hasRemaining())
            {
                // We completed this time
                LOG.trace("[{}] Header completed over multiple reads.", id);
                headerValue = ((ByteBuffer)headerBuffer.flip()).getInt();
            }
        }
        // we read the entire header
        else
        {
            // it's all here first time, we can simply read it
            LOG.trace("[{}] Header arrived in once single read.", id);
            headerValue = buffer.getInt();
            headerBuffer = null;
        }
    }

    private int calcHowMuchToRead(ByteBuffer buffer) {
    	if(headerBuffer != null)
    	{
    		// We have a partial read in progress
    		return Math.min(buffer.remaining(), remaining());
    	}
    	return Math.min(buffer.remaining(), headerSize);
	}

	@Override
    public void readHeader(String id, ByteBuffer headerBuffer, int offset)
    {
        LOG.trace("[{}] Reading header from {} at offset {}", id, headerBuffer, offset);
        readHeader(id, (ByteBuffer) headerBuffer.position(offset));
    }

    @Override
    public boolean isComplete()
    {
        return headerValue != -1;
    }

    @Override
    public int getValue()
    {
        return headerValue;
    }

    @Override
    public int remaining()
    {
    	LOG.trace("[{}] HASH: {}", id, hashCode());
        if(headerBuffer == null)
        {
        	LOG.trace("[{}] Header buffer is null, we must have all {} bytes of the message remaining", id, headerSize);
            return headerSize;
        }
        LOG.trace("[{}] Partial header buffer in play, state is {}", id, headerBuffer);
        return headerBuffer.remaining();
    }

}
