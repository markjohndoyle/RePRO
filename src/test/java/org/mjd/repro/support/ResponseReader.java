package org.mjd.repro.support;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.Callable;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.apache.commons.lang3.tuple.Pair;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResponseReader {
	private static final Logger LOG = LoggerFactory.getLogger(ResponseReader.class);

	private ResponseReader() {
		// Utility class
	}

	/**
	 * Response is as follows:
	 *
	 * <pre>
	 *   ------------------------------------------------
	 *  | header [4 bytes] |        body [n Bytes]       |
	 *  |                  |-----------------------------|
	 *  |     msgSize      | ID [8 bytes] |    msg       |
	 *   ------------------------------------------------
	 * </pre>
	 *
	 * @param kryo
	 *
	 * @param in
	 * @return {@link Pair} containing the request ID (Key/Left) and the response value {@link Object} (Value/Right)
	 * @throws IOException
	 */
	public static Pair<Long, Object> readResponse(final Kryo kryo, final DataInputStream in) throws IOException {
		final int responseSize = in.readInt() - Long.BYTES;
		final long requestId = in.readLong();

		LOG.trace("Reading response of size: {}", responseSize);
		final byte[] bytesRead = readBytes(in, responseSize);

		if (bytesRead != null)
        {
			try (Input kin = new Input(bytesRead)) {
				final ResponseMessage<String> responseMessage = kryo.readObject(kin, ResponseMessage.class);
				if (responseMessage.isError()) {
					return Pair.of(requestId, responseMessage.getError().get().toString());
				}
				if (responseMessage.getValue().isPresent()) {
					return Pair.of(requestId, responseMessage.getValue().get());
				}
				return Pair.of(requestId, null);
			}
        }
		return null;
	}

	private static byte[] readBytes(final DataInputStream in, final int payloadSize) throws IOException
    {
        final byte[] payloadStore = new byte[payloadSize];
        int totalBytesRead = 0;
        int payloadBytesRead = 0;
        LOG.debug("Reading response of size: {}", payloadSize);
        while (totalBytesRead < payloadSize)
        {
            payloadBytesRead = in.read(payloadStore, payloadBytesRead, payloadSize - payloadBytesRead);
            if (payloadBytesRead == -1)
            {
                LOG.warn("End of stream reached");
                return null;
            }
			totalBytesRead += payloadBytesRead;
        }
        return payloadStore;
    }

	public static final class BlockingResponseReader implements Callable<Pair<Long, Object>> {
		private final Kryo readRespKryo;
		private final DataInputStream in;

		public BlockingResponseReader(final Kryo kryo, final DataInputStream in) {
			this.readRespKryo = kryo;
			this.in = in;
		}

		@Override
		public Pair<Long, Object> call() throws Exception {
			return readResponse(readRespKryo, in);
		}
	}
}
