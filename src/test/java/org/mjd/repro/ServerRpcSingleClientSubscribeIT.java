package org.mjd.repro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.message.SubscriptionInvoker;
import org.mjd.repro.message.RequestWithArgs;
import org.mjd.repro.message.factory.KryoRpcRequestMsgFactory;
import org.mjd.repro.support.FakeRpcTarget;
import org.mjd.repro.util.kryo.KryoRpcUtils;
import org.mjd.repro.util.kryo.RpcRequestKryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.TEN_SECONDS;
import static org.mjd.repro.handlers.response.provided.RpcRequestRefiners.prepend;
import static org.mjd.repro.support.ResponseReader.readResponse;

/**
 * RPC integration test using a single client that makes multiple asynchronous calls.
 */
@RunWith(OleasterRunner.class)
public class ServerRpcSingleClientSubscribeIT
{
    private static final Logger LOG = LoggerFactory.getLogger(ServerRpcSingleClientSubscribeIT.class);
    private final Pool<Kryo> kryos = new RpcRequestKryoPool(true, false, 1000);
    private static final AtomicLong reqId = new AtomicLong();
    private ExecutorService serverService;
    private Server<RequestWithArgs> rpcServer;
    private FakeRpcTarget rpcTarget;
    private Socket clientSocket;
    private MessageHandler<RequestWithArgs> rpcInvoker;
    private Kryo kryo;

	// TEST BLOCK
    {
        beforeEach(()-> {
        	kryo = kryos.obtain();
        	rpcTarget = new FakeRpcTarget();
        	rpcInvoker = new SubscriptionInvoker(kryo, rpcTarget);
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
            startServer();
        });

        afterEach(() -> {
        	kryos.free(kryo);
        	rpcTarget.close();
            shutdownServer();
        });

        describe("When a single client", () -> {
        	beforeEach(() -> {
        		clientSocket = new Socket("localhost", 12509);
        		await().atMost(TEN_SECONDS.multiply(12)).until(() -> clientSocket.isConnected());
        		LOG.debug("Client isConnected!");
        	});
        	afterEach(() -> {
        		clientSocket.close();
        	});
        	describe("requests a subscription", () -> {
        		describe("and waits for at least 5 notifications", () -> {
		        	it("it should recieve all of the notifications", () -> {
		        		final int numNotifications = 5;
		        		final DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());

		        		final long subscriptionId = reqId.getAndIncrement();
	        			subscribeOverRpc(clientOut, subscriptionId);

	        			final Kryo responseReaderKryo = kryos.obtain();

		        		final DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());
		        		final ExecutorService receiverService = Executors.newSingleThreadExecutor();
		        		final Future<?> receiverJob = receiverService.submit(() -> {
		        			int notifications = 0;
		        			while(notifications <  numNotifications) {
			        			try {
			        				final Pair<Long, Object> readResponse = readResponse(responseReaderKryo, dataIn);
			        				if(readResponse.getLeft() == subscriptionId) {
			        		    		expect(readResponse.getLeft()).toEqual(subscriptionId);
			        		    		if(readResponse.getRight() != null)
			        		    		{
			        		    			expect(readResponse.getRight().toString()).toStartWith("Things just seem so much better in theory than in practice");
			        		    			notifications++;
			        		    		}
			        		    	}
								}
								catch (final IOException e) {
									e.printStackTrace();
								}
		        			}
		        		});
		        		receiverJob.get();
		        		dataIn.close();
		        		clientOut.close();
		        		kryos.free(responseReaderKryo);
		        	});
	        	});
        	});
        });
    }

	private RequestWithArgs subscribeOverRpc(final DataOutputStream clientOut, final long id)
			throws IOException {
		final Kryo kryo = kryos.obtain();
		try {
			final RequestWithArgs identifiableSubRequest = new RequestWithArgs(id);
			LOG.debug("Preparing to call request {}", id);
			KryoRpcUtils.writeKryoWithHeader(kryo, clientOut, identifiableSubRequest).flush();
			LOG.trace("Request {} written to server from client", identifiableSubRequest);
			return identifiableSubRequest;
		}
		finally {
			kryos.free(kryo);
		}
	}

	private void startServer()
    {
	        rpcServer = new Server<>(new InetSocketAddress(12509),
        						 new KryoRpcRequestMsgFactory<>(kryos.obtain(), RequestWithArgs.class));
        rpcServer.addHandler(rpcInvoker::handle)
        		 .addHandler(prepend::requestId);

        serverService.submit(() -> rpcServer.start());
        await().until(() -> rpcServer.isAvailable());
    }

	private void shutdownServer()
    {
        LOG.info("Test is shutting down server....");
        serverService.shutdownNow();
        await().atMost(TEN_SECONDS).until(() -> { return rpcServer.isShutdown();});
        await().atMost(TEN_SECONDS).until(() -> { return serverService.isTerminated();});
    }

}

