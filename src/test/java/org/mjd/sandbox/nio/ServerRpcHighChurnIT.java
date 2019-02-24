package org.mjd.sandbox.nio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.awaitility.Duration;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.RpcRequestMessage;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TEN_SECONDS;
import static org.hamcrest.Matchers.is;

@RunWith(OleasterRunner.class)
public class ServerRpcHighChurnIT
{
    private static final Logger LOG = LoggerFactory.getLogger(ServerRpcHighChurnIT.class);

    private ExecutorService serverService;

    private Server<RpcRequest> rpcServer;

    private final Pool<Kryo> kryos = new Pool<Kryo>(true, false, 5000) {
        @Override
        protected Kryo create () {
            Kryo kryo = new Kryo();
            kryo.register(RpcRequest.class);
            return kryo;
        }
    };

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
    private Kryo kryo;

    // TEST BLOCK
    {
        beforeEach(()->{
            // pre charge the kryo pool
            for(int i = 0; i < 500; i++)
            {
                kryos.free(kryos.obtain());
            }
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
            startServer();
            kryo = kryos.obtain();
        });

        afterEach(() -> {
            shutdownServer();
            kryos.free(kryo);
        });

        describe("When a valid kryo RPC request/reply RpcRequest is sent to the server by multple clients", () -> {
            it("should reply correctly to all of them", () -> {
                final int numClients = 50_000;
                ExecutorService executor = Executors.newFixedThreadPool(500);

                BlockingQueue<Future<?>> clientJobs = new ArrayBlockingQueue<>(numClients);
                for(int i = 0; i < numClients; i++)
                {
                    clientJobs.add(executor.submit(() -> {
                        Kryo kryo = kryos.obtain();
                        LOG.trace("Running client");
                        Socket clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress("localhost", 12509), 512000);
                        await().atMost(TEN_SECONDS.multiply(12)).until(()-> clientSocket.isConnected());
                        LOG.trace("Client isConnected!");
                        DataInputStream clientIn = new DataInputStream(clientSocket.getInputStream());
                        DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
                        RpcRequest request = new RpcRequest("0", "callMeString");
                        writeKryoWithHeader(kryo, clientOut, request).flush();
                        LOG.trace("Reading response from server...");
                        await().atMost(ONE_MINUTE).until(new ReadResponse(kryo, clientIn), is("callMeString"));
                        LOG.debug("Expectation passed");
                        kryos.free(kryo);
                        return clientSocket;
                    }));
                }

                for(Future<?> clientJob : clientJobs)
                {
                    ((Socket) clientJob.get()).close();
                }
            });
        });
    }

    private static final class RpcRequestMsgFactory implements MessageFactory<RpcRequest>
    {
        private final Kryo kryo = new Kryo();

        public RpcRequestMsgFactory()
        {
            kryo.register(RpcRequest.class);
        }

        @Override
        public int getHeaderSize() { return Integer.BYTES; }

        /**
         * Expects a Kryo object with a marshalled RpcRequest
         */
        @Override
        public Message<RpcRequest> create(byte[] bytesRead) throws MessageCreationException {
            try {
                kryo.register(RpcRequest.class);
                RpcRequest request = readBytesWithKryo(kryo, bytesRead);
                return new RpcRequestMessage(request);
            }
            catch (IOException | IndexOutOfBoundsException e) {
                System.err.println("[" + Thread.currentThread().getName() + "]" + " Kryo: " + kryo + " " + e.toString());
                e.printStackTrace();
                throw new MessageCreationException(e);
            }
        }

        private static RpcRequest readBytesWithKryo(Kryo kryo, byte[] data) throws IOException
        {
            try(ByteArrayInputStream bin = new ByteArrayInputStream(data);
                Input kryoByteArrayIn = new Input(bin))
            {
                RpcRequest req = kryo.readObject(kryoByteArrayIn, RpcRequest.class);
                return req;
            }
            catch (IOException e)
            {
                LOG.error("Error deserialising response from server", e);
                throw e;
            }
        }
    }

    private void startServer()
    {
        rpcServer = new Server<>(new RpcRequestMsgFactory());

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
                    msgBytes = objectToKryoBytes(kryo, result);
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
            catch (Exception e)
            {
                LOG.error("ERROR IN SERVER, see following stack trace:");
                LOG.error(Joiner.on(System.lineSeparator()).join(e.getStackTrace()));
                e.printStackTrace();
            }
        });
        await().until(() -> { return rpcServer.isAvailable();});
    }

    private void shutdownServer()
    {
        LOG.debug("Test is shutting down server....");
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
     * @param kryo
     *
     * @param in
     * @return
     * @throws IOException
     */
    private String readResponse(Kryo kryo, DataInputStream in) throws IOException
    {
        int responseSize;
        try
        {
            // blocks until
            responseSize = in.readInt();
        }
        catch (IOException e)
        {
            LOG.error("Error reading header client-side due to {}", e.toString());
            e.printStackTrace();
            throw e;
        }
        byte[] bytesRead = new byte[responseSize];
        int bodyRead = 0;
        LOG.trace("Reading response of size: {}",  responseSize);
        try
        {
            while((bodyRead = in.read(bytesRead, bodyRead, responseSize - bodyRead)) > 0)
            {
                // Just keep reading
            }
        }
        catch (IOException e)
        {
            LOG.error("Error reading body client-side");
            e.printStackTrace();
            throw e;
        }
        try(Input kin = new Input(bytesRead))
        {
        	String result = kryo.readObject(kin, String.class);
        	return result;
        }
    }

    private final class ReadResponse implements Callable<String>
    {
        private final Kryo kryo;
        private final DataInputStream in;
        public ReadResponse(Kryo kryo, DataInputStream in) {
            this.kryo = kryo;
            this.in = in;
        }
        @Override public String call() throws Exception { return readResponse(kryo, in); }
    }

    static DataOutputStream writeKryoWithHeader(Kryo kryo, DataOutputStream clientOut, Object request) throws IOException
    {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Output kryoByteArrayOut = new Output(bos))
        {
            kryo.writeObject(kryoByteArrayOut, request);
            kryoByteArrayOut.flush();
            bos.flush();
            clientOut.writeInt(bos.size());
            clientOut.write(bos.toByteArray());
            return clientOut;
        }
        catch (IOException e)
        {
            LOG.error("Error writing request to server", e);
            throw e;
        }
    }

    static byte[] objectToKryoBytes(Kryo kryo, Object obj) throws IOException
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

