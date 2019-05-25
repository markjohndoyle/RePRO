package org.mjd.repro.writers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.mjd.repro.handlers.response.ResponseRefiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * The {@link RefiningChannelWriter} is an implementation of a {@link ChannelWriter}.
 *
 * It is able to maintain a collection of {@link Writer} instances associated with a {@link SelectionKey} and
 * trigger each of them to write when requested.</br>
 * As part of writing, this class can refined the response data before sending it to the Writer. This is configured
 * by passing a list of {@link ResponseRefiner} instances at construction time.
 *
 *
 * @param <MsgType>
 * @param <K>
 */
public final class RefiningChannelWriter<MsgType, K extends SelectionKey> implements ChannelWriter<MsgType, K> {
	private static final Logger LOG = LoggerFactory.getLogger(RefiningChannelWriter.class);

	private final ReentrantReadWriteLock responseWritersLock = new ReentrantReadWriteLock();
	private final ListMultimap<Channel, Writer> responseWriters = ArrayListMultimap.create();
	private final Selector selector;
	private final List<ResponseRefiner<MsgType>> responseRefiners;
	private final BiFunction<SelectionKey, ByteBuffer, Writer> writerSupplier;

	/**
	 * @param selector
	 * @param refiners
	 * @param writerSupplier
	 */
	public RefiningChannelWriter(final Selector selector, final List<ResponseRefiner<MsgType>> refiners,
								 final BiFunction<SelectionKey, ByteBuffer, Writer> writerSupplier) {
		this.selector = selector;
		this.responseRefiners = Collections.unmodifiableList(refiners);
		this.writerSupplier = writerSupplier;
	}

	@Override
	public void write(final K key) {
		try {
			responseWritersLock.writeLock().lock();
			final List<Writer> rspWriters = responseWriters.get(key.channel());
			LOG.trace("There are {} write jobs for the {} key/channel", rspWriters.size(), key.attachment());
			final Iterator<Writer> it = rspWriters.iterator();
			while (it.hasNext()) {
				it.next().write();
				it.remove();
			}
			LOG.trace("Response writers for {} are complete, resetting to read ops only", key.attachment());
			key.interestOps(OP_READ);
		}
		catch (final CancelledKeyException e) {
			LOG.warn("Key {} cancelled during write. Channel probably closed or server is shutting down", key.attachment());
		}
		catch (final IOException e) {
			e.printStackTrace();
			return;
		}
		finally {
			responseWritersLock.writeLock().unlock();
		}
	}

	@Override
	public void prepWrite(final SelectionKey key, final MsgType message, final ByteBuffer resultToWrite) {
		final ByteBuffer bufferToWriteBack = refineResponse(message, resultToWrite);
		LOG.debug("Preparing writer for msg ID {}. Bufffer state post refinement, pre write {}", message, bufferToWriteBack);
		add(key, writerSupplier.apply(key, bufferToWriteBack));
		try {
			key.interestOps(key.interestOps() | OP_WRITE);
		}
		catch (final CancelledKeyException | ClosedSelectorException ex) {
			LOG.warn("Server was about to write response to client {} but it's key was cancelled. Removing all "
					+ "writers for this key", key.attachment());
			responseWritersLock.writeLock().lock();
			try {
				responseWriters.removeAll(key.channel());
			}
			finally {
				responseWritersLock.writeLock().unlock();
			}
			return;
		}
		selector.wakeup();
	}

	private void add(final SelectionKey key, final Writer writer) {
		responseWritersLock.writeLock().lock();
		try {
			responseWriters.put(key.channel(), writer);
			LOG.trace("[{}] There are now {} response writers", key.attachment(), responseWriters.get(key.channel()).size(), key);
		}
		finally {
			responseWritersLock.writeLock().unlock();
		}
	}

	private ByteBuffer refineResponse(final MsgType message, final ByteBuffer resultToWrite) {
		ByteBuffer refinedBuffer = resultToWrite;
		for (final ResponseRefiner<MsgType> responseHandler : responseRefiners) {
			LOG.trace("Buffer post message handler pre response refininer {}", refinedBuffer);
			refinedBuffer = responseHandler.execute(message, refinedBuffer);
			refinedBuffer.flip();
		}
		return refinedBuffer;
	}
}
