package org.mjd.sandbox.nio;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.RpcRequestMessage;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ServerObjectMessageIT
{
    private final ExecutorService serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
    
    private Server<RpcRequest> rpcServer;
    
    private Kryo kryo = new Kryo();
    
    public static final class FakeRpcTarget
    {
        public void callMeVoid() { }
        public String callMeString() { return "callMeString"; }
    }
    
    private final FakeRpcTarget rpcTarget = new FakeRpcTarget();
    
    @Before
    public void startServer()
    {
        kryo.register(RpcRequest.class);
        
        try
        {
            rpcServer = new Server<>(new MessageFactory<RpcRequest>()
            {
                @Override 
                public int getHeaderSize() { return Integer.BYTES; }
                
                /**
                 * Expects a Kryo object with a marshalled RpcRequest
                 */
                @Override 
                public Message<RpcRequest> create(byte[] bytesRead) throws MessageCreationException {
                    try {
                        kryo.register(RpcRequest.class);
                        Input in = new Input(bytesRead);
                        RpcRequest request = kryo.readObject(in, RpcRequest.class);
                        return new RpcRequestMessage(request);
                    }
                    catch (IOException e) {
                        throw new MessageCreationException(e);
                    }
                }
            });
            
            // Add echo handler
            rpcServer.addHandler(new RespondingHandler<RpcRequest>() {
                @Override 
                public ByteBuffer execute(Message<RpcRequest> message) {
                    byte[] msgBytes;
                    Object result;
                    RpcRequest request = message.getValue();
                    try
                    {
                        String requestedMethodCall = request.getMethod();
                        result = MethodUtils.invokeMethod(rpcTarget, requestedMethodCall);
                    }
                    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException e)
                    {
                        result = new String("Error executing method: " + request.getMethod() + " due to " + e);
                    }
                    try
                    {
                        msgBytes = objectToKryoBytes(result);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        msgBytes = new byte[] {0x00};
                    }
                    return (ByteBuffer)ByteBuffer.allocate(msgBytes.length).put(msgBytes).flip();
                }
            });

            serverService.submit(() -> {
                try
                {
                    rpcServer.start();
                }
                catch (IOException | MessageCreationException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    @After
    public void shutdownServer() throws IOException, InterruptedException
    {
        serverService.shutdownNow();
        await().atMost(Duration.TEN_SECONDS).until(() -> { return rpcServer.isShutdown();});
        await().atMost(Duration.TEN_SECONDS).until(() -> { return serverService.isTerminated();});
    }
    
    /**
     * Response is as follows:
     * <pre>
     *   ---------------------------------
     *  | header [1Byte] | body [n Bytes] |
     *  |   msgSize      |      msg       |
     *   ---------------------------------
     * </pre>
     *   
     * @param in
     * @return
     * @throws IOException
     */
    private String readResponse(DataInputStream in) throws IOException
    {
        await("Waiting for response").forever().until(() -> { return in.available() > Integer.BYTES;});
        int responseSize = in.readInt();
        byte[] bytesRead = new byte[responseSize];
        int totalRead = 0;
        int bodyRead = 0;
        while((bodyRead = in.read(bytesRead, bodyRead, responseSize - bodyRead)) > 0)
        {
            // We don't want to potentially head into a further message.
            totalRead += bodyRead;
            if(totalRead == responseSize)
            {
                break;
            }
            // else there is more to read
        }
        
        Input kin = new Input(bytesRead);
        String result = kryo.readObject(kin, String.class);
        
        return result;
    }

    @Test
    public void test() throws UnknownHostException, IOException, InterruptedException, ExecutionException
    {
        Socket testSocket = new Socket("localhost", 12509);
        DataInputStream in = new DataInputStream(testSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(testSocket.getOutputStream());
        
        // Spin up a thread to check the response in parallel. This is probably what would happen in a client.
        Future<?> rspReadJob = Executors.newSingleThreadExecutor().submit(() -> {
            try
            {
                assertThat(readResponse(in), is("callMeString"));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        });
        
        RpcRequest request = new RpcRequest(0, "callMeString");
        
        Output kryoSocketOut = new Output(out);
        writeKryoWithHeader(kryoSocketOut, request).flush();
        
        rspReadJob.get();
        
        kryoSocketOut.close();
        testSocket.close();
    }
    
    Output writeKryoWithHeader(Output kryoOut, Object request) throws IOException
    {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Output kryoByteArrayOut = new Output(bos))
        {
            kryo.writeObject(kryoByteArrayOut, request);
            kryoByteArrayOut.flush();
            
            kryoOut.writeInt(bos.size());
            kryoOut.write(bos.toByteArray());
            return kryoOut;
        }
    }
    
    byte[] objectToKryoBytes(Object obj) throws IOException
    {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Output kryoByteArrayOut = new Output(bos))
        {
            kryo.writeObject(kryoByteArrayOut, obj);
            kryoByteArrayOut.flush();
            return bos.toByteArray();
        }
    }

}

