package org.mjd.repro.util;

import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * @param <K>
 * @param <V>
 */
public abstract class Mapper<K, V> {

	public static <K, V> Mapper<K, V> findInMap(final Map<K, V> map, final K key) {
		final V result = map.get(key);
		if (result != null) {
			return new Found<>(result);
		}
		return new NotFound<>(map, key);
	}

	public abstract V get();

	public abstract V or(Supplier<V> supplier);

	public abstract boolean found();

	public static final class Found<K, V> extends Mapper<K, V> {
		private final V value;

		public Found(final V found) {
			value = found;
		}

		@Override
		public V or(final Supplier<V> supplier) {
			return value;
		}

		@Override
		public V get() {
			return value;
		}

		@Override
		public boolean found() {
			return true;
		}
	}

	public static final class NotFound<K, V> extends Mapper<K, V> {
		private final Map<K, V> map;
		private final K key;
		public NotFound(final Map<K, V> map, final K key) {
			this.map = map;
			this.key = key;
		}

		@Override
		public V or(final Supplier<V> supplier) {
			final V supplied = supplier.get();
			map.put(key, supplied);
			return supplied;
		}

		@Override
		public V get() {
			throw new IllegalStateException("Value was not in the map and no Supplier was provided");
		}

		@Override
		public boolean found() {
			return false;
		}
	}

}
