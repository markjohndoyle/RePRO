package org.mjd.sandbox.nio.async;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.mjd.sandbox.nio.handlers.op.WriteOpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SequentialMessageJobExecutor<MsgType> implements AsyncMessageJobExecutor<MsgType> {
	private static final Logger LOG = LoggerFactory.getLogger(SequentialMessageJobExecutor.class);

	private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
																			.setNameFormat("AsyncMsgJobExec").build());

	private BlockingQueue<AsyncMessageJob<MsgType>> messageJobs = new LinkedBlockingQueue<>();

	private final WriteOpHandler<MsgType> writerHandler;

	private Selector selector;

	public SequentialMessageJobExecutor(WriteOpHandler<MsgType> server) {
		this.writerHandler = server;
	}

	@Override
	public void start(final Selector selector) {
		this.selector = selector;
		executor.execute(() -> startAsyncMessageJobHandler());
	}

	@Override
	public void add(final AsyncMessageJob<MsgType> job) {
		messageJobs.add(job);
	}

	private void startAsyncMessageJobHandler() {
		AsyncMessageJob<MsgType> job = null;
		try {
			while(!Thread.interrupted()) {
				LOG.trace("Blocking on message job queue....");
				job = messageJobs.take();
				LOG.trace("[{}] Found a job. There are {} remaining.", job.key.attachment(), messageJobs.size());
				final Optional<ByteBuffer> result = job.messageJob.get(500, TimeUnit.MILLISECONDS);
				LOG.trace("[{}] The job has finished.", job.key.attachment());
				if(result.isPresent()) {
					writerHandler.writeResult(job.key, job.message, result.get());
				}
				selector.wakeup();
			}
		}
		catch (TimeoutException e) {
			try {
				LOG.debug("Waiting for job '{}' timed out, putting it back on the end of the queue", job.message.getValue());
				messageJobs.put(job);
			}
			catch (InterruptedException e1) {
				Thread.currentThread().interrupt();
			}
		}
		catch (InterruptedException | ExecutionException | CancellationException e) {
			System.err.println(e.toString());
			e.printStackTrace();
		}
	}
}
