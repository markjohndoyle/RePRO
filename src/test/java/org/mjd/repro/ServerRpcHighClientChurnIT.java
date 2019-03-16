package org.mjd.repro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Iterator;
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
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.factories.RpcHandlers;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.message.factory.KryoRpcRequestMsgFactory;
import org.mjd.repro.support.FakeRpcTarget;
import org.mjd.repro.util.kryo.KryoRpcUtils;
import org.mjd.repro.util.kryo.RpcRequestKryoPool;
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
import static org.mjd.repro.handlers.response.provided.RpcRequestRefiners.prepend;
import static org.mjd.repro.support.ResponseReader.readResponse;

@RunWith(OleasterRunner.class)
public class ServerRpcHighClientChurnIT {

    private static final Logger LOG = LoggerFactory.getLogger(ServerRpcHighClientChurnIT.class);
    private static final AtomicLong reqId = new AtomicLong();
    private final Pool<Kryo> kryos = new RpcRequestKryoPool(true, false, 5000);
    private ExecutorService serverService;
    private Server<RpcRequest> rpcServer;
    private FakeRpcTarget rpcTarget;
    private MessageHandler<RpcRequest> rpcInvoker;
	private int port;


    // TEST BLOCK
    {
        beforeEach(() -> {
        	rpcTarget = new FakeRpcTarget();
        	rpcInvoker = RpcHandlers.singleThreadRpcInvoker(kryos.obtain(), rpcTarget);
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
                final int numClients = 1200;
                final ExecutorService executor = Executors.newFixedThreadPool(600);

                final BlockingQueue<Future<?>> clientJobs = new ArrayBlockingQueue<>(numClients);
                for(int i = 0; i < numClients; i++)
                {
                    clientJobs.add(executor.submit(new RpcClientRequestJob(kryos, reqId, port)));
                }

                final Iterator<Future<?>> it = clientJobs.iterator();
                while(it.hasNext()) {
                	final Future<?> clientJob = it.next();
                	((Socket) clientJob.get()).close();
                	it.remove();
                }
            });
        });
    }

    private void startServer()
    {
        rpcServer = new Server<>(new KryoRpcRequestMsgFactory<>(kryos.obtain(), RpcRequest.class))
        						.addHandler(rpcInvoker)
        						.addHandler(prepend::requestId);

        serverService.submit(() ->  {
            try
            {
                rpcServer.start();
            }
            catch (final Exception e)
            {
                LOG.error("ERROR IN SERVER, see following stack trace:");
                LOG.error(Joiner.on(System.lineSeparator()).join(e.getStackTrace()));
                e.printStackTrace();
            }
        });
        await().until(() -> { return rpcServer.isAvailable();});
        port = rpcServer.getPort();
    }

	private void shutdownServer()
    {
        LOG.debug("Test is shutting down server....");
        serverService.shutdownNow();
        await().atMost(TEN_SECONDS).until(() -> { return rpcServer.isShutdown();});
        await().atMost(TEN_SECONDS).until(() -> { return serverService.isTerminated();});
    }


	private static final class RpcClientRequestJob implements Callable<Socket>
	{
		private static final Logger LOG = LoggerFactory.getLogger(RpcClientRequestJob.class);
		private final Pool<Kryo> kryos;
		private final AtomicLong requestId;
		private final int port;

		RpcClientRequestJob(final Pool<Kryo> kryos, final AtomicLong reqId, final int port) {
			this.kryos = kryos;
			this.requestId = reqId;
			this.port = port;
		}

		@Override
		public Socket call() throws Exception {
			final Kryo kryo = kryos.obtain();
			final Socket clientSocket = connectToServer(port);
			final Pair<Long, Object> request = makeRpcCall(kryo, clientSocket);

			LOG.trace("Reading response from server...");
			final DataInputStream clientIn = new DataInputStream(clientSocket.getInputStream());
			await().atMost(ONE_MINUTE).until(new ReadResponse(kryo, clientIn), is(request));
			LOG.debug("Expectation passed");
			kryos.free(kryo);
			return clientSocket;
		}

		private static Socket connectToServer(final int port) throws IOException {
			LOG.trace("Running client");
			final Socket clientSocket = new Socket();
			clientSocket.connect(new InetSocketAddress("localhost", port), 512000);
			await().atMost(TEN_SECONDS.multiply(12)).until(() -> clientSocket.isConnected());
			LOG.trace("Client isConnected!");
			return clientSocket;
		}

		private Pair<Long, Object> makeRpcCall(final Kryo kryo, final Socket clientSocket) throws IOException {
			final DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
			final int methodIndex = new Random().nextInt(FakeRpcTarget.methodNamesAndReturnValues.size());
			final Entry<String, Object> call = FakeRpcTarget.methodNamesAndReturnValues.entrySet().stream().skip(methodIndex).findFirst().get();
			final long id = requestId.getAndIncrement();
			final Pair<Long, Object> requestIdAndExpectedReturn = Pair.of(id, call.getValue());
			LOG.trace("Making request to {}", call.getKey()	);
			final RpcRequest rpcRequest = new RpcRequest(id, call.getKey());
			KryoRpcUtils.writeKryoWithHeader(kryo, clientOut, rpcRequest).flush();
			return requestIdAndExpectedReturn;
		}

		private final class ReadResponse implements Callable<Pair<Long, Object>> {
			private final Kryo readRespKryo;
			private final DataInputStream in;

			ReadResponse(final Kryo kryo, final DataInputStream in) {
				this.readRespKryo = kryo;
				this.in = in;
			}

			@Override
			public Pair<Long, Object> call() throws Exception {
				return readResponse(readRespKryo, in);
			}
		}

	}
}

