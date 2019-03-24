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
	 * @param key         the {@link SelectionKey} this asynchronous message processing job was started for
	 * @param message     the {@link Message} this asynchronous message processing job is processing/processed.
	 * @param asyncHandle {@link Future} holding the result of the message processing job.
	 */
	public AsyncMessageJob(final SelectionKey key, final Message<MsgType> message,
						   final Future<Optional<ByteBuffer>> asyncHandle) {
		this.messageJob = asyncHandle;
		this.key = key;
		this.message= message;
	}

	/** @return {@link #messageJob} */
	public Future<Optional<ByteBuffer>> getMessageJob() {
		return messageJob;
	}

	/** @return {@link #key} */
	public SelectionKey getKey() {
		return key;
	}

	/** @return {@link #message} */
	public Message<MsgType> getMessage() {
		return message;
	}

	/**
	 * Simple factory for {@link AsyncMessageJob} for when a declarative style is more readable.
	 *
	 * @param key         the {@link SelectionKey} this asynchronous message processing job was started for
	 * @param message     the {@link Message} this asynchronous message processing job is processing/processed.
	 * @param handlingJob {@link Future} holding the result of the message processing job.
	 * @return {@link AsyncMessageJob}
	 */
	public static <MsgType> AsyncMessageJob<MsgType>
	from(final SelectionKey key, final Message<MsgType> message, final Future<Optional<ByteBuffer>> handlingJob) {
		return new AsyncMessageJob<>(key, message, handlingJob);
	}
}