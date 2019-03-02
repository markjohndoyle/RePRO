package org.mjd.sandbox.nio;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map.Entry;
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
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Duration;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.handlers.message.RpcRequestInvoker;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.RpcRequestMessage;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.support.FakeRpcTarget;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;
import org.mjd.sandbox.nio.util.kryo.KryoRpcUtils;
import org.mjd.sandbox.nio.util.kryo.RpcRequestKryoPool;
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
import static org.mjd.sandbox.nio.handlers.response.provided.RpcRequestRefiners.prepend;

@RunWith(OleasterRunner.class)
public class ServerRpcHighClientChurnIT {

    private static final Logger LOG = LoggerFactory.getLogger(ServerRpcHighClientChurnIT.class);
    private ExecutorService serverService;
    private Server<RpcRequest> rpcServer;
    private static AtomicLong reqId = new AtomicLong();
    private Pool<Kryo> kryos = new RpcRequestKryoPool(true, false, 5000);
    private FakeRpcTarget rpcTarget;
    private RpcRequestInvoker rpcInvoker;


    // TEST BLOCK
    {
        beforeEach(()->{
        	rpcTarget = new FakeRpcTarget();
        	rpcInvoker = new RpcRequestInvoker(kryos.obtain(), rpcTarget);
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
                final int numClients = 10_000;
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
        rpcServer = new Server<>(new RpcRequestMsgFactory()).addHandler(rpcInvoker::handle)
        		 											.addHandler(prepend::requestId);

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

	private static final class RpcRequestMsgFactory implements MessageFactory<RpcRequest>
	{
		private static final Logger REQLOG = LoggerFactory.getLogger(RpcRequestMsgFactory.class);
	    private final Kryo kryo = new Kryo();

	    public RpcRequestMsgFactory()
	    {
	        kryo.register(RpcRequest.class);
	        kryo.register(ArgumentValues.class);
            kryo.register(ArgumentValuePair.class);
	    }

	    @Override
	    public int getHeaderSize() { return Integer.BYTES; }

	    /**
	     * Expects a Kryo object with a marshalled RpcRequest
	     */
	    @Override
	    public Message<RpcRequest> createMessage(byte[] bytesRead) throws MessageCreationException {
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
	            REQLOG.error("Error deserialising response from server", e);
	            throw e;
	        }
	    }
	}

	private static final class RpcClientRequestJob implements Callable<Socket>
	{
		private static final Logger LOG = LoggerFactory.getLogger(RpcClientRequestJob.class);
		private final Pool<Kryo> kryos;

		private AtomicLong requestId;

		public RpcClientRequestJob(Pool<Kryo> kryos, AtomicLong reqId) throws Exception {
			this.kryos = kryos;
			this.requestId = reqId;
		}

		@Override
		public Socket call() throws Exception {
			Kryo kryo = kryos.obtain();
			Socket clientSocket = connectToServer();
			Pair<Long, Object> request = makeRpcCall(kryo, clientSocket);

			LOG.trace("Reading response from server...");
			DataInputStream clientIn = new DataInputStream(clientSocket.getInputStream());
			await().atMost(ONE_MINUTE).until(new ReadResponse(kryo, clientIn), is(request));
			LOG.debug("Expectation passed");
			kryos.free(kryo);
			return clientSocket;
		}

		private static Socket connectToServer() throws IOException {
			LOG.trace("Running client");
			Socket clientSocket = new Socket();
			clientSocket.connect(new InetSocketAddress("localhost", 12509), 512000);
			await().atMost(TEN_SECONDS.multiply(12)).until(() -> clientSocket.isConnected());
			LOG.trace("Client isConnected!");
			return clientSocket;
		}

		private Pair<Long, Object> makeRpcCall(Kryo kryo, Socket clientSocket) throws IOException {
			DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
			// RpcRequest request = new RpcRequest(Integer.toString(id), "callMeString");
			final int methodIndex = new Random().nextInt(FakeRpcTarget.methodNamesAndReturnValues.size());
			Entry<String, Object> call = FakeRpcTarget.methodNamesAndReturnValues.entrySet().stream().skip(methodIndex).findFirst().get();
			final long id = requestId.getAndIncrement();
			Pair<Long, Object> request = Pair.of(id, call.getValue());
			LOG.trace("Making request to {}", call.getKey()	);
			RpcRequest rpcRequest = new RpcRequest(request.getLeft(), call.getKey());
			KryoRpcUtils.writeKryoWithHeader(kryo, clientOut, rpcRequest).flush();
			return request;
		}

		private final class ReadResponse implements Callable<Pair<Long, String>> {
			private final Kryo readRespKryo;
			private final DataInputStream in;

			public ReadResponse(Kryo kryo, DataInputStream in) {
				this.readRespKryo = kryo;
				this.in = in;
			}

			@Override
			public Pair<Long, String> call() throws Exception {
				return readResponse(readRespKryo, in);
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
		private static Pair<Long, String> readResponse(Kryo kryo, DataInputStream in) throws IOException {
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

