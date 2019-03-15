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
	 * @return
	 * @throws IOException
	 */
	public static Pair<Long, Object> readResponse(final Kryo kryo, final DataInputStream in) throws IOException {
		int responseSize;
		long requestId;
		try {
			responseSize = in.readInt();
			requestId = in.readLong();
		}
		catch (final IOException e) {
			LOG.error("Error reading header client-side due to {}", e.toString());
			e.printStackTrace();
			throw e;
		}

		if(responseSize == Long.BYTES) {
			LOG.trace("Returning void message, only have ID");
			return Pair.of(requestId, null);
		}

		final byte[] bytesRead = new byte[responseSize];
		int bodyRead = 0;
		LOG.trace("Reading response of size: {}", responseSize);
		try {
			while ((bodyRead = in.read(bytesRead, bodyRead, responseSize - Long.BYTES - bodyRead)) > 0) {
				// Just keep reading
			}
		}
		catch (final IOException e) {
			LOG.error("Error reading body client-side");
			e.printStackTrace();
			throw e;
		}
		try (Input kin = new Input(bytesRead)) {
//			String result = kryo.readObject(kin, String.class);
			final ResponseMessage<String> responseMessage = kryo.readObject(kin, ResponseMessage.class);
	        if(responseMessage.isError()) {
	        	return Pair.of(requestId, responseMessage.getError().get().toString());
	        }
			return Pair.of(requestId, responseMessage.getValue().get());
		}
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
