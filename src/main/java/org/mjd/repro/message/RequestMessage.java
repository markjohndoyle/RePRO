package org.mjd.repro.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public final class RequestMessage<R extends RequestWithArgs> implements Message<R>
{
    private final R request;
    private ByteBuffer byteBuffer;

    public RequestMessage(final R request) throws IOException
    {
        this.request = request;
        //convertToByteStream(request);
    }

    private void convertToByteStream(final R request) throws IOException
    {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream())
        {
            final ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(request);
            ByteBuffer.wrap(bos.toByteArray());
        }
    }

    @Override
    public R getValue()
    {
        return request;
    }

    @Override
    public int size()
    {
        return byteBuffer.capacity();
    }

//    @Override
//    public byte[] asByteArray()
//    {
//        return byteBuffer.array();
//    }

	@Override
	public String toString() {
		return "RpcRequestMessage [request=" + request + ", byteBuffer=" + byteBuffer + "]";
	}


}
