package org.mjd.sandbox.nio.handlers.op;

import java.nio.channels.SelectionKey;

import org.mjd.sandbox.nio.util.chain.AbstractHandler;
import org.mjd.sandbox.nio.writers.ChannelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WriteOpHandler<MsgType, K extends SelectionKey> extends AbstractHandler<K> {
	private static final Logger LOG = LoggerFactory.getLogger(WriteOpHandler.class);

	private final ChannelWriter<MsgType, K> channelWriter;

//	private final ReentrantReadWriteLock responseWritersLock = new ReentrantReadWriteLock();
//	private final ListMultimap<Channel, Writer> responseWriters = ArrayListMultimap.create();
//	private List<ResponseRefiner<MsgType>> responseRefiners = new ArrayList<>();
//	private final Selector selector;


	public WriteOpHandler(final ChannelWriter<MsgType, K> channelWriter) {
		this.channelWriter = channelWriter;
	}


	@Override
	public void handle(final K key) {
		if(key.isValid() && key.isWritable()) {
			channelWriter.write(key);
//			try {
//				responseWritersLock.writeLock().lock();
//				final List<Writer> rspWriters = responseWriters.get(key.channel());
//				LOG.trace("There are {} write jobs for the {} key/channel", rspWriters.size(), key.attachment());
//				final Iterator<Writer> it = rspWriters.iterator();
//				while(it.hasNext()) {
//					it.next().writeCompleteBuffer();
//					it.remove();
//				}
//				LOG.trace("Response writers for {} are complete, resetting to read ops only", key.attachment());
//				key.interestOps(OP_READ);
//			}
//			catch (IOException e) {
//				e.printStackTrace();
//				return;
//			}
//			finally {
//				responseWritersLock.writeLock().unlock();
//			}
		}
		passOnToNextHandler(key);
	}

//	public void writeResult(final SelectionKey key, final Message<MsgType> message, final ByteBuffer resultToWrite) {
//		final ByteBuffer bufferToWriteBack = refineResponse(message.getValue(), resultToWrite);
//		LOG.trace("Buffer post refinement, pre write {}", bufferToWriteBack);
//		add(key, SizeHeaderWriter.from(key, bufferToWriteBack));
//		key.interestOps(key.interestOps() | OP_WRITE);
//		selector.wakeup();
//	}
//
//	private void add(final SelectionKey key, final Writer writer) {
//		responseWritersLock.writeLock().lock();
//		try {
//			responseWriters.put(key.channel(), writer);
//			LOG.trace("[{}] There are now {} response writers", key.attachment(), responseWriters.get(key.channel()).size(), key);
//		}
//		finally {
//			responseWritersLock.writeLock().unlock();
//		}
//	}
//
//	private ByteBuffer refineResponse(final MsgType message, final ByteBuffer resultToWrite) {
//		ByteBuffer refinedBuffer = (ByteBuffer) resultToWrite.flip();
//		for(final ResponseRefiner<MsgType> responseHandler : responseRefiners) {
//			LOG.trace("Buffer post message handler pre response refininer {}", refinedBuffer);
//			LOG.debug("Passing message value '{}' to response refiner", message);
//			refinedBuffer = responseHandler.execute(message, refinedBuffer);
//			refinedBuffer.flip();
//		}
//		return refinedBuffer;
//	}
}
