package org.mjd.repro.support;

import java.util.function.Function;

import com.esotericsoftware.kryo.Kryo;
import org.mjd.repro.serialisation.Marshaller;

public final class KryoMarshaller implements Marshaller {

	private final KryoPool kryos;

	@SafeVarargs
	public KryoMarshaller(final int poolSize, final Function<Kryo, Kryo>... configurators) {
		kryos = KryoPool.newThreadSafePool(poolSize, configurators);
	}

	@Override
	public <T> byte[] marshall(final T object, final Class<T> type) {
		final Kryo kryo = kryos.obtain();
		try {
			return KryoRpcUtils.objectToKryoBytes(kryo, object);
		}
		finally {
			kryos.free(kryo);
		}
	}

	@Override
	public <T> T unmarshall(final byte[] bytesRead, final Class<T> type) {
		final Kryo kryo = kryos.obtain();
		try {
			return KryoRpcUtils.readBytesWithKryo(kryo, bytesRead, type);
		}
		finally {
			kryos.free(kryo);
		}
	}

}
