package org.mjd.sandbox.nio.util;

import java.util.Map;
import java.util.function.Supplier;

public abstract class Mapper<K, V> {

	public static <K, V> Mapper<K, V> findInMap(Map<K, V> map, K key) {
		V result = map.get(key);
		if (result != null) {
			return new Found<>(result);
		}
		return new NotFound<>(map, key);
	}

	public abstract V get();

	public abstract V or(Supplier<V> supplier);

	public abstract boolean found();

	public static class Found<K, V> extends Mapper<K, V> {
		V value;

		public Found(V found) {
			value = found;
		}

		@Override
		public V or(Supplier<V> supplier) {
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

	public static class NotFound<K, V> extends Mapper<K, V> {
		Map<K, V> map;
		K key;
		public NotFound(Map<K, V> map, K key) {
			this.map = map;
			this.key = key;
		}

		@Override
		public V or(Supplier<V> supplier) {
			final V supplied = supplier.get();
			map.put(key, supplied);
			return supplied;
		}

		@Override
		public V get() {
			throw new IllegalStateException("Value was not in the map and no create insttruction was provided");
		}

		@Override
		public boolean found() {
			return false;
		}
	}

}
