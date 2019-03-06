package org.mjd.sandbox.nio.readers.header;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class IntHeaderReader implements HeaderReader<Integer>
{
    private static final Logger LOG = LoggerFactory.getLogger(IntHeaderReader.class);

    private final String id;
    private final int headerSize = Integer.BYTES;
    private int headerValue = -1;
    private ByteBuffer headerBuffer = null;

    public IntHeaderReader(String id)
    {
    	this.id = id;
    }

    @Override
    public void readHeader(ByteBuffer buffer)
    {
        LOG.trace("[{}] Reading header from {} with {} remaining ", id, buffer, buffer.remaining());
        // if we didn't read enough
        if(buffer.remaining() < headerSize)
        {
        	if(buffer.remaining() > 0)
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

	@Override
    public void readHeader(ByteBuffer headerBuffer, int offset)
    {
        LOG.trace("[{}] Reading header from {} at offset {}", id, headerBuffer, offset);
        readHeader((ByteBuffer) headerBuffer.position(offset));
    }

    @Override
    public boolean isComplete()
    {
        return headerValue != -1;
    }

    @Override
    public Integer getValue()
    {
    	if(headerValue == -1)
    	{
    		throw new IllegalStateException("IntHeaderReader.get() cannot be called before it is complete.");
    	}
        return headerValue;
    }

    @Override
    public int remaining()
    {
    	// We haven't read any partial header data
        if(headerBuffer == null)
        {
        	// and the header was never read in one single read
        	if(headerValue == -1)
        	{
        		return headerSize;
        	}
        	// we havent read part of the header and the value is complete so we must have 0 remaining
        	// The data arrived in one read.
			return 0;
        }
        LOG.trace("[{}] Partial header buffer in play, state is {}", id, headerBuffer);
        return headerBuffer.remaining();
    }

	@Override
	public int getSize() {
		return headerSize;
	}

}
