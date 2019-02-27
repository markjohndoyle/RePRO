package org.mjd.sandbox.nio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Joiner;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.commons.lang3.tuple.Pair;
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

    private static AtomicLong reqId = new AtomicLong();

    private final Pool<Kryo> kryos = new Pool<Kryo>(true, false, 2000) {
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
        private static final Map<String, Object> requestResponses = new HashMap<>();

		public void callMeVoid() { }
        public String callMeString() { return "callMeString"; }
        public String genString() { return "generated string"; }
        public String whatIsTheString() { return "it's this string..."; }

        public FakeRpcTarget() throws Exception {
			requestResponses.put("callMeString", "callMeString");
			requestResponses.put("genString", "generated string");
			requestResponses.put("whatIsTheString", "it's this string...");
		}
    }

    private FakeRpcTarget rpcTarget;

    // TEST BLOCK
    {
        beforeEach(()->{
        	rpcTarget = new FakeRpcTarget();
            // pre charge the kryo pool
            for(int i = 0; i < 500; i++)
            {
                kryos.free(kryos.obtain());
            }
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
            startServer();
        });

        afterEach(() -> {
            shutdownServer();
        });

        describe("When a valid kryo RPC request/reply RpcRequest is sent to the server by multple clients", () -> {
            it("should reply correctly to all of them", () -> {
                final int numClients = 50_000;
                ExecutorService executor = Executors.newFixedThreadPool(600);

                BlockingQueue<Future<?>> clientJobs = new ArrayBlockingQueue<>(numClients);
                for(int i = 0; i < numClients; i++)
                {
                    clientJobs.add(executor.submit(new RpcClientRequestJob(kryos, reqId)));
                }

                for(Future<?> clientJob : clientJobs)
                {
                    ((Socket) clientJob.get()).close();
                }
            });
        });
    }

    private void startServer()
    {
        rpcServer = new Server<>(new RpcRequestMsgFactory());

        // Add RPC to rpcTarget handler
        // The handler is what the server will do with your messages.
        rpcServer.addHandler((RespondingMessageHandler<RpcRequest>) message -> {
		    byte[] msgBytes;
		    Object result;
		    RpcRequest request = message.getValue();
		    Kryo kryo = kryos.obtain();
		    try
		    {
		        String requestedMethodCall = request.getMethod();
		        result = MethodUtils.invokeMethod(rpcTarget, requestedMethodCall);
		        if(result == null)
		        {
		            return Optional.empty();
		        }
		    }
		    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException e1)
		    {
		        result = new String("Error executing method: " + request.getMethod() + " due to " + e1);
		    }
		    try
		    {
		        msgBytes = objectToKryoBytes(kryo, result);
		    }
		    catch (IOException e2)
		    {
		        e2.printStackTrace();
		        msgBytes = new byte[] {0x00};
		    }
		    finally
		    {
		    	kryos.free(kryo);
		    }
		    return Optional.of(ByteBuffer.allocate(msgBytes.length).put(msgBytes));
		}).addHandler((rpcRequest, writeBuffer) ->
						ByteBuffer.allocate(Long.BYTES + writeBuffer.capacity())
				  										.putLong(rpcRequest.getId())
				  										.put(writeBuffer));

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

    private static byte[] objectToKryoBytes(Kryo kryo, Object obj) throws IOException
	{
	    try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        Output kryoByteArrayOut = new Output(bos))
	    {
	        kryo.writeObject(kryoByteArrayOut, obj);
	        kryoByteArrayOut.flush();
	        return bos.toByteArray();
	    }
	}

	private void shutdownServer()
    {
        LOG.debug("Test is shutting down server....");
        serverService.shutdownNow();
        await().atMost(Duration.TEN_SECONDS).until(() -> { return rpcServer.isShutdown();});
        await().atMost(Duration.TEN_SECONDS).until(() -> { return serverService.isTerminated();});
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

	    @Override
		public Message<RpcRequest> create(ByteBuffer bytesRead) throws MessageCreationException {
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

	    private static RpcRequest readBytesWithKryo(Kryo kryo, ByteBuffer data)
	    {
	    	try(Input kryoByteArrayIn = new Input(data.array()))
	    	{
	    		RpcRequest req = kryo.readObject(kryoByteArrayIn, RpcRequest.class);
	    		return req;
	    	}
	    }
	}

	private static final class RpcClientRequestJob implements Callable<Socket>
	{
		private static final Logger LOG = LoggerFactory.getLogger(RpcClientRequestJob.class);
		private final Pool<Kryo> kryos;

		private final Map<Method, Object> requestResponses = new HashMap<>();
		private AtomicLong reqId;

		public RpcClientRequestJob(Pool<Kryo> kryos, AtomicLong reqId) throws Exception {
			this.kryos = kryos;
			this.reqId = reqId;
		}

		@Override
		public Socket call() throws Exception {
			Kryo kryo = kryos.obtain();
			LOG.trace("Running client");
			Socket clientSocket = new Socket();
			clientSocket.connect(new InetSocketAddress("localhost", 12509), 512000);
			await().atMost(TEN_SECONDS.multiply(12)).until(() -> clientSocket.isConnected());
			LOG.trace("Client isConnected!");

			DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
			// RpcRequest request = new RpcRequest(Integer.toString(id), "callMeString");
			final int methodIndex = new Random().nextInt(FakeRpcTarget.requestResponses.size());
			Entry<String, Object> call = FakeRpcTarget.requestResponses.entrySet().stream().skip(methodIndex).findFirst().get();
			LOG.trace("Making request to {}", call.getKey()	);
			final long id = reqId.getAndIncrement();
			RpcRequest request = new RpcRequest(id, call.getKey());
			writeKryoWithHeader(kryo, clientOut, request).flush();

			LOG.trace("Reading response from server...");
			DataInputStream clientIn = new DataInputStream(clientSocket.getInputStream());
			Pair<Long, Object> expected = Pair.of(id, call.getValue());
			await().atMost(ONE_MINUTE).until(new ReadResponse(kryo, clientIn), is(expected));
			LOG.debug("Expectation passed");
			kryos.free(kryo);
			return clientSocket;
		}

		private static DataOutputStream writeKryoWithHeader(Kryo kryo, DataOutputStream clientOut, Object request)
				throws IOException {
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); Output kryoByteArrayOut = new Output(bos)) {
				kryo.writeObject(kryoByteArrayOut, request);
				kryoByteArrayOut.flush();
				bos.flush();
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				outputStream.write(Ints.toByteArray(bos.size()));
				outputStream.write(bos.toByteArray());
				clientOut.write(outputStream.toByteArray());
				return clientOut;
			}
			catch (IOException e) {
				LOG.error("Error writing request to server", e);
				throw e;
			}
		}

		private final class ReadResponse implements Callable<Pair<Long, String>> {
			private final Kryo rrKryo;
			private final DataInputStream in;

			public ReadResponse(Kryo kryo, DataInputStream in) {
				this.rrKryo = kryo;
				this.in = in;
			}

			@Override
			public Pair<Long, String> call() throws Exception {
				return readResponse(rrKryo, in);
			}
		}

		/**
		 * Response is as follows:
		 *
		 * <pre>
		 *   ---------------------------------
		 *  | header [1Byte] | body [n Bytes] |
		 *  |   msgSize      |      msg       |
		 *   ---------------------------------
		 * </pre>
		 *
		 * @param kryo
		 *
		 * @param in
		 * @return
		 * @throws IOException
		 */
		private Pair<Long, String> readResponse(Kryo kryo, DataInputStream in) throws IOException {
			int responseSize;
			long requestId;
			try {
				responseSize = in.readInt();
				requestId = in.readLong();
			}
			catch (IOException e) {
				LOG.error("Error reading header client-side due to {}", e.toString());
				e.printStackTrace();
				throw e;
			}
			byte[] bytesRead = new byte[responseSize];
			int bodyRead = 0;
			LOG.trace("Reading response of size: {}", responseSize);
			try {
				while ((bodyRead = in.read(bytesRead, bodyRead, responseSize - Long.BYTES - bodyRead)) > 0) {
					// Just keep reading
				}
			}
			catch (IOException e) {
				LOG.error("Error reading body client-side");
				e.printStackTrace();
				throw e;
			}
			try (Input kin = new Input(bytesRead)) {
				String result = kryo.readObject(kin, String.class);
				return Pair.of(requestId, result);
			}
		}

	}
}

