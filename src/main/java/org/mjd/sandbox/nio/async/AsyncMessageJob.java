package org.mjd.sandbox.nio.async;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Optional;
import java.util.concurrent.Future;

import org.mjd.sandbox.nio.message.Message;

public final class AsyncMessageJob<MsgType> {
	public final Future<Optional<ByteBuffer>> messageJob;
	public final SelectionKey key;
	public final Message<MsgType> message;

	public AsyncMessageJob(SelectionKey key, Message<MsgType> message, Future<Optional<ByteBuffer>> asyncHandle) {
		this.messageJob = asyncHandle;
		this.key = key;
		this.message= message;
	}
}