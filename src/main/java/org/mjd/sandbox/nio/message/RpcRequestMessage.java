package org.mjd.sandbox.nio.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public class RpcRequestMessage implements Message<RpcRequest>
{
    private RpcRequest request;
    private ByteBuffer byteBuffer;

    public RpcRequestMessage(RpcRequest request) throws IOException
    {
        this.request = request;
        convertToByteStream(request);
    }   
    
    private static void convertToByteStream(RpcRequest request) throws IOException
    {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream())
        {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(request);
            ByteBuffer.wrap(bos.toByteArray());
        }
    }
    
    @Override
    public RpcRequest getValue()
    {
        return request;
    }

    @Override
    public int size()
    {
        return byteBuffer.capacity();
    }

    @Override
    public byte[] asByteArray()
    {
        return byteBuffer.array();
    }

}
