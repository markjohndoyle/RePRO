package org.mjd.repro.async;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Optional;
import java.util.concurrent.Future;

import org.mjd.repro.message.Message;

/**
 * {@link AsyncMessageJob} is a data class that holds related information about an asynchronour message handling job,
 * it's associated {@link SelectionKey} and the original {@link Message}
 *
 * @param <MsgType> the type of {@link Message} this job is for
 */
public final class AsyncMessageJob<MsgType> {
	/**
	 * {@link Future} holding the result of the message processing job.
	 */
	private final Future<Optional<ByteBuffer>> messageJob;

	/**
	 * The {@link SelectionKey} this asynchronous message processing job was started for
	 */
	private final SelectionKey key;

	/**
	 * The {@link Message} this asynchronous message processing job is processing/processed.
	 */
	private final Message<MsgType> message;

	/**
	 * Constructs a complete {@link AsyncMessageJob} for type MsgType.
	 *
	 * @param key
	 * @param message
	 * @param asyncHandle
	 */
	public AsyncMessageJob(final SelectionKey key, final Message<MsgType> message,
						   final Future<Optional<ByteBuffer>> asyncHandle) {
		this.messageJob = asyncHandle;
		this.key = key;
		this.message= message;
	}

	public Future<Optional<ByteBuffer>> getMessageJob() {
		return messageJob;
	}

	public SelectionKey getKey() {
		return key;
	}

	public Message<MsgType> getMessage() {
		return message;
	}
}