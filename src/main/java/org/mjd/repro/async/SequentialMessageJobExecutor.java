package org.mjd.repro.async;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.mjd.repro.handlers.op.WriteOpHandler;
import org.mjd.repro.writers.ChannelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.repro.util.thread.Threads.called;

/**
 * Single threaded sequential implementation of an {@link AsyncMessageJobExecutor}.</br>
 * In this implementation one {@link AsyncMessageJob} will be processed one at a time, in the order they were added.
 *
 * @param <MsgType>
 */
public final class SequentialMessageJobExecutor<MsgType> implements AsyncMessageJobExecutor<MsgType> {
	private static final Logger LOG = LoggerFactory.getLogger(SequentialMessageJobExecutor.class);

	private final ExecutorService executor = Executors.newSingleThreadExecutor(called("AsyncMsgJobExec"));
	private final ChannelWriter<MsgType, SelectionKey> channelWriter;
	private final Selector selector;
	private final BlockingQueue<AsyncMessageJob<MsgType>> messageJobs = new LinkedBlockingQueue<>();
	private final boolean acknowledgeVoids;

	/**
	 * Constructs a fully initialised {@link SequentialMessageJobExecutor}
	 *
	 * @param selector         the selector associated with the message jobs with executor processes.
	 * @param writer           a {@link WriteOpHandler} that can write responses back to the correct clients
	 * @param acknowledgeVoids whether this executor shoudl write back void responses, i.e., acknowledgements when the
	 *                         result of the message job is empty
	 */
	public SequentialMessageJobExecutor(final Selector selector, final ChannelWriter<MsgType, SelectionKey> writer,
			final boolean acknowledgeVoids) {
		this.selector = selector;
		this.channelWriter = writer;
		this.acknowledgeVoids = acknowledgeVoids;
	}

	@Override
	public void start() {
		executor.execute(() -> startAsyncMessageJobHandler());
	}

	@Override
	public void stop() {
		executor.shutdownNow();
	}

	@Override
	public void add(final AsyncMessageJob<MsgType> job) {
		messageJobs.add(job);
	}

	/**
	 * Takes {@link AsyncMessageJob} instances from the blocking queue and waits for 500ms fo rthem to complete. If they
	 * don't complete in that time, they are put back on the end of the queue and the next job is checked.</br>
	 * The result is checked if a job is complete (or completes within the timeout). If present, that is, the
	 * {@link AsyncMessageJob} returned a result, it is sent to the {@link #channelWriter}.
	 * </p>
	 * The selector is always woken up in case the key changed within a blocking {@link Selector#select()} call.
	 */
	private void startAsyncMessageJobHandler() {
		try {
			while (!Thread.interrupted()) {
				processJobs();
			}
		}
		catch (final InterruptedException ie) {
			LOG.info("Interrupted whilst waiting for jobs; the server is likely shutting down");
			Thread.currentThread().interrupt();
		}
		catch (final ExecutionException e) {
			LOG.error("Error in message processing job", e);
		}
	}

	private AsyncMessageJob<MsgType> processJobs() throws InterruptedException, ExecutionException {
		AsyncMessageJob<MsgType> job = null;
		try {
			job = messageJobs.take();
			LOG.trace("[{}] Found a job. There are {} remaining.", job.getKey().attachment(), messageJobs.size());
			writeResponse(job, job.getMessageJob().get(500, TimeUnit.MILLISECONDS));
			selector.wakeup();
		}
		catch (final TimeoutException e) {
			try {
				LOG.debug("Waiting for job timed out, putting it back on the end of the queue");
				messageJobs.put(job);
			}
			catch (final InterruptedException ie) {
				LOG.info("Interrupted whilst returning unfinished job; the server is likely shutting down");
				Thread.currentThread().interrupt();
			}
		}
		return job;
	}

	private void writeResponse(final AsyncMessageJob<MsgType> job, final Optional<ByteBuffer> result) {
		if (result.isPresent()) {
			channelWriter.writeResult(job.getKey(), job.getMessage(), result.get());
		}
		else if (acknowledgeVoids) {
			LOG.trace("No return for call {}; Writing empty buffer back", job);
			channelWriter.writeResult(job.getKey(), job.getMessage(), ByteBuffer.allocate(0));
		}
	}
}
