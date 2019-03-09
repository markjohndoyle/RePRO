package org.mjd.sandbox.nio.handlers.op;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.mjd.sandbox.nio.handlers.response.ResponseRefiner;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.writers.SizeHeaderWriter;
import org.mjd.sandbox.nio.writers.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

public final class WriteOpHandler<MsgType> {
	private static final Logger LOG = LoggerFactory.getLogger(WriteOpHandler.class);

	private final ReentrantReadWriteLock responseWritersLock = new ReentrantReadWriteLock();
	private final ListMultimap<Channel, Writer> responseWriters = ArrayListMultimap.create();
	private List<ResponseRefiner<MsgType>> responseRefiners = new ArrayList<>();
	private final Selector selector;

	public WriteOpHandler(Selector selector) {
		this.selector = selector;
	}

	public WriteOpHandler(Selector selector, List<ResponseRefiner<MsgType>> refiners) {
		this.selector = selector;
		responseRefiners = refiners;
	}

	public void handle(SelectionKey key) throws IOException {
		try {
			responseWritersLock.writeLock().lock();
			List<Writer> rspWriters = responseWriters.get(key.channel());
			LOG.trace("There are {} write jobs for the {} key/channel", rspWriters.size(), key.attachment());
			Iterator<Writer> it = rspWriters.iterator();
			while(it.hasNext()) {
				it.next().writeCompleteBuffer();
				it.remove();
			}
			LOG.trace("Response writers for {} are complete, resetting to read ops only", key.attachment());
			key.interestOps(OP_READ);
		}
		finally {
			responseWritersLock.writeLock().unlock();
		}
	}

	public void add(SelectionKey key, Writer writer) {
		responseWritersLock.writeLock().lock();
		try {
			responseWriters.put(key.channel(), writer);
			LOG.trace("[{}] There are now {} response writers", key.attachment(), responseWriters.get(key.channel()).size(), key);
		}
		finally {
			responseWritersLock.writeLock().unlock();
		}
	}

	public void writeResult(SelectionKey key, Message<MsgType> message, ByteBuffer resultToWrite) {
		ByteBuffer bufferToWriteBack = refineResponse(message.getValue(), resultToWrite);
		LOG.trace("Buffer post refinement, pre write {}", bufferToWriteBack);
		add(key, SizeHeaderWriter.from(key, bufferToWriteBack));
		key.interestOps(key.interestOps() | OP_WRITE);
	}

	private ByteBuffer refineResponse(MsgType message, ByteBuffer resultToWrite) {
		ByteBuffer refinedBuffer = (ByteBuffer) resultToWrite.flip();
		for(ResponseRefiner<MsgType> responseHandler : responseRefiners) {
			LOG.trace("Buffer post message handler pre response refininer {}", refinedBuffer);
			LOG.debug("Passing message value '{}' to response refiner", message);
			refinedBuffer = responseHandler.execute(message, refinedBuffer);
			refinedBuffer.flip();
		}
		return refinedBuffer;
	}
}
