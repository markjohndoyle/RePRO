package org.mjd.sandbox.nio;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.awaitility.Duration;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.RpcRequestMessage;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;

@RunWith(OleasterRunner.class)
public class ServerRpcIT
{
    private ExecutorService serverService;
    
    private Server<RpcRequest> rpcServer;
    
    private Kryo kryo = new Kryo();
    
    /**
     * Fake target for RPC calls. A server will have direct access to something like this
     * when it's setup. 
     */
    public static final class FakeRpcTarget
    {
        public void callMeVoid() { }
        public String callMeString() { return "callMeString"; }
    }
    
    private final FakeRpcTarget rpcTarget = new FakeRpcTarget();
    private Socket testSocket;
    private DataInputStream in;
    private DataOutputStream out;
    
    // TEST BLOCK
    {
        beforeEach(()->{
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
            startServer();  
            testSocket = new Socket("localhost", 12509);
            in = new DataInputStream(testSocket.getInputStream());
            out = new DataOutputStream(testSocket.getOutputStream());
        });
        
        afterEach(() -> {
            in.close();
            out.close();
            testSocket.close();
            shutdownServer();
        });
        
        describe("When a valid kryo RPC request/reply RpcRequest is sent to the server", () -> {
           it("should reply with the expected object", () -> {
               // Spin up a thread to check the response in parallel. This is probably what would happen in a client.
               Future<?> rspReadJob = Executors.newSingleThreadExecutor().submit(() -> {
                   expect(readResponse(in)).toEqual("callMeString");
               });
               
               RpcRequest request = new RpcRequest(0, "callMeString");
               
               Output kryoSocketOut = new Output(out);
               writeKryoWithHeader(kryoSocketOut, request).flush();
               
               rspReadJob.get();
               
               kryoSocketOut.close();
               testSocket.close();
           });
        });
    }
    
    public void startServer()
    {
        kryo.register(RpcRequest.class);
        
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
            public Optional<ByteBuffer> execute(Message<RpcRequest> message) {
                byte[] msgBytes;
                Object result;
                RpcRequest request = message.getValue();
                try
                {
                    String requestedMethodCall = request.getMethod();
                    result = MethodUtils.invokeMethod(rpcTarget, requestedMethodCall);
                    if(result == null)
                    {
                        return Optional.empty();
                    }
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
                return Optional.of((ByteBuffer)ByteBuffer.allocate(msgBytes.length).put(msgBytes).flip());
            }

            @Override
            public int execute(Message<RpcRequest> message, ByteBuffer writeBuffer)
            {
                // TODO Auto-generated method stub
                return 0;
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
        await().until(() -> { return rpcServer.isAvailable();});
    }
    
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
    private String readResponse(DataInputStream in)
    {
        await("Waiting for response").forever().until(() -> { return in.available() > Integer.BYTES;});
        int responseSize;
        try
        {
            responseSize = in.readInt();
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
        catch (IOException e)
        {
            fail(e.toString());
            return null;
        }
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

