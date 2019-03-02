package org.mjd.sandbox.nio.support;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.Callable;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseReader {
	private static final Logger LOG = LoggerFactory.getLogger(ResponseReader.class);

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
	public static Pair<Long, String> readResponse(Kryo kryo, DataInputStream in) throws IOException {
		int responseSize;
		long requestId;
		try {
			responseSize = in.readInt();
			requestId = in.readLong();
		}
		catch (IOException e) {
			LOG.error("Error reading header client-side due to {}", e.toString());
			e.printStackTrace();
			throw e;
		}
		byte[] bytesRead = new byte[responseSize];
		int bodyRead = 0;
		LOG.trace("Reading response of size: {}", responseSize);
		try {
			while ((bodyRead = in.read(bytesRead, bodyRead, responseSize - Long.BYTES - bodyRead)) > 0) {
				// Just keep reading
			}
		}
		catch (IOException e) {
			LOG.error("Error reading body client-side");
			e.printStackTrace();
			throw e;
		}
		try (Input kin = new Input(bytesRead)) {
			String result = kryo.readObject(kin, String.class);
			return Pair.of(requestId, result);
		}
	}

	public static final class BlockingResponseReader implements Callable<Pair<Long, String>> {
		private final Kryo readRespKryo;
		private final DataInputStream in;

		public BlockingResponseReader(Kryo kryo, DataInputStream in) {
			this.readRespKryo = kryo;
			this.in = in;
		}

		@Override
		public Pair<Long, String> call() throws Exception {
			return readResponse(readRespKryo, in);
		}
	}
}
