package org.mjd.repro.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.primitives.Ints;

public final class KryoRpcUtils {
	private KryoRpcUtils() {
	}

	public synchronized static DataOutputStream writeKryoWithHeader(final Kryo kryo, final DataOutputStream clientOut,
			final Object request) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); Output kryoByteArrayOut = new Output(bos)) {
			kryo.writeObject(kryoByteArrayOut, request);
			kryoByteArrayOut.flush();
			bos.flush();
			final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			outputStream.write(Ints.toByteArray(bos.size()));
			outputStream.write(bos.toByteArray());
			clientOut.write(outputStream.toByteArray());

			return clientOut;
		}
	}

	public static byte[] objectToKryoBytes(final Kryo kryo, final Object obj) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); Output kryoByteArrayOut = new Output(bos)) {
			kryo.writeObject(kryoByteArrayOut, obj);
			kryoByteArrayOut.flush();
			return bos.toByteArray();
		}
		catch (final IOException e) {
			throw new IllegalStateException("Error serialising " + obj + " to bytes with kryo", e);
		}
	}

	public static <T> T readBytesWithKryo(final Kryo kryo, final byte[] data, final Class<T> type) {
		try (ByteArrayInputStream bin = new ByteArrayInputStream(data); Input kryoByteArrayIn = new Input(bin)) {
			return kryo.readObject(kryoByteArrayIn, type);
		}
		catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
