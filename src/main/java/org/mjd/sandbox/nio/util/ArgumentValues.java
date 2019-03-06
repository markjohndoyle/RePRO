package org.mjd.sandbox.nio.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ordering;
import com.google.common.collect.UnmodifiableIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;
import org.mjd.sandbox.nio.util.kryo.ArgumentValueSerialiser;
import org.mjd.sandbox.nio.util.kryo.ImmutableClassSerialiser;
import org.mjd.sandbox.nio.util.kryo.SerialiseAsConstructorArg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DefaultSerializer(ArgumentValueSerialiser.class)
public final class ArgumentValues implements Iterable<ArgumentValuePair>, Serializable {
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(ArgumentValues.class);

	private final Map<Class<?>, Set<ArgumentValuePair>> internalStore = new ConcurrentHashMap<>();
	private int highestIndex = -1;

	@DefaultSerializer(ImmutableClassSerialiser.class)
	public static final class ArgumentValuePair implements Comparable<ArgumentValuePair>, Serializable {
		private static final long serialVersionUID = 1L;

		@SerialiseAsConstructorArg(index = 0)
		private final int index;
		@SerialiseAsConstructorArg(index = 1)
		private final String name;
		@SerialiseAsConstructorArg(index = 2)
		private final Object value;

		public ArgumentValuePair(int argumentIndex, String argumentName, Object argumentValue) {
			this.index = argumentIndex;
			this.name = argumentName;
			this.value = argumentValue;
		}

		public String getName() {
			return name;
		}

		/**
		 * If you change the {@link Object} then this class is no longer immutable.
		 *
		 * @return value of the argument.
		 */
		public Object getValue() {
			return value;
		}

		@Override
		public boolean equals(Object other) {
			if (other == this) {
				return true;
			}
			if (!(other instanceof ArgumentValuePair)) {
				return false;
			}
			ArgumentValuePair otherArgValPair = ArgumentValuePair.class.cast(other);
			return Objects.equals(otherArgValPair.name, name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name);
		}

		@Override
		public int compareTo(ArgumentValuePair o) {
			return Integer.compare(index, o.index);
		}

		@Override
		public String toString() {
			return "[" + index + ":" + name + "=" + value + "]";
		}

	}

	public <T> Optional<T> get(Class<T> type, String argumentName) {
		Set<ArgumentValuePair> argValues = findValidInternalStore(type);
		if (argValues != null) {
			Iterator<ArgumentValuePair> it = Iterables.unmodifiableIterable(argValues).iterator();
			while (it.hasNext()) {
				ArgumentValuePair pair = it.next();
				if (pair.getName().equals(argumentName)) {
					return Optional.fromNullable(type.cast(pair.getValue()));
				}
			}
		}
		return Optional.absent();
	}

	private <T> Set<ArgumentValuePair> findValidInternalStore(Class<T> type) {
		Set<ArgumentValuePair> argValues = internalStore.get(type);
		if (argValues == null) {
			if (type.isInterface()) {
				for (Class<?> key : internalStore.keySet()) {
					if (type.isAssignableFrom(key)) {
						argValues = internalStore.get(key);
					}
				}
			}
			else {
				return null;
			}
		}
		return argValues;
	}

	public <T> T put(Class<T> type, int index, String argumentName, Object argumentValue) {
		if (type == null) {
			throw new IllegalArgumentException("Type key cannot be null in " + getClass().getName() + ". Cannot put.");
		}
		Set<ArgumentValuePair> argPairs = internalStore.get(type);
		if (argPairs == null) {
			argPairs = new LinkedHashSet<>();
			internalStore.put(type, argPairs);
		}
		ArgumentValuePair newArgPair = new ArgumentValuePair(index, argumentName, argumentValue);
		if (argPairs.add(newArgPair)) {
			updateIndex(index);
			try {
				return type.cast(newArgPair.getValue());
			}
			catch (ClassCastException e) {
				LOG.error("Class cast for arg '{}' to value '{}'. Expected type '{}'", argumentName, argumentValue, type);
				throw e;
			}
		}
		argPairs.remove(newArgPair);
		argPairs.add(newArgPair);
		return type.cast(newArgPair.getValue());
	}

	public <T> T put(Class<T> type, String argumentName, Object argumentValue) {
		if (type == null) {
			throw new IllegalArgumentException("Type key cannot be null in " + getClass().getName() + ". Cannot put.");
		}
		Set<ArgumentValuePair> argPairs = internalStore.get(type);
		if (argPairs == null) {
			argPairs = new LinkedHashSet<>();
			internalStore.put(type, argPairs);
		}

		ArgumentValuePair newArgPair = new ArgumentValuePair(++highestIndex, argumentName, argumentValue);
		if (argPairs.add(newArgPair)) {
			try {
				return type.cast(newArgPair.getValue());
			}
			catch (ClassCastException e) {
				LOG.error("Class cast for arg {} to value {}. Expected type {}", argumentName, argumentValue, type);
				throw e;
			}
		}
		argPairs.remove(newArgPair);
		argPairs.add(newArgPair);
		return type.cast(newArgPair.getValue());
	}

	@Override
	public UnmodifiableIterator<ArgumentValuePair> iterator() {
		Collection<Set<ArgumentValuePair>> values = internalStore.values();
		Iterator<ArgumentValuePair> sorted = Iterables.mergeSorted(values, Ordering.natural()).iterator();
		return Iterators.unmodifiableIterator(sorted);
	}

	public boolean containsValueForArg(String name) {
		Iterable<ArgumentValuePair> argPairs = Iterables.concat(internalStore.values());
		for (ArgumentValuePair pair : argPairs) {
			if (StringUtils.equals(name, pair.getName())) {
				return true;
			}
		}
		return false;
	}

	public Object[] asObjArray() {
		List<Object> vals = new ArrayList<>();
		iterator().forEachRemaining(pair -> vals.add(pair.getValue()));
		return vals.toArray();
	}

	public int size() {
		return Iterables.size(Iterables.concat(internalStore.values()));
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof ArgumentValues)) {
			return false;
		}
		ArgumentValues argVals = (ArgumentValues) o;
		return Objects.equals(argVals.internalStore, internalStore);
	}

	@Override
	public int hashCode() {
		return Objects.hash(internalStore);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ArgumentValues [");
		if (internalStore != null) {
			builder.append("internalStore=").append(Joiner.on(" ").withKeyValueSeparator(":").join(internalStore)).append(", ");
		}
		builder.append("highestIndex=").append(highestIndex).append("]");
		return builder.toString();
	}

	private void updateIndex(int index) {
		highestIndex = Math.max(highestIndex, index);
	}

	public static ArgumentValues singleArg(String name, Object value) {
		ArgumentValues argVals = new ArgumentValues();
		argVals.put(value.getClass(), name, value);
		return argVals;
	}

	public static ArgumentValues of(String name, Object value) {
		return newArgumentValues(pair(name, value));
	}

	public static ArgumentValues of(String name, Object value, String name2, Object value2) {
		return newArgumentValues(pair(name, value), pair(name2, value2));
	}

	public static ArgumentValues of(String name, Object value, String name2, Object value2, String name3, Object value3) {
		return newArgumentValues(pair(name, value), pair(name2, value2), pair(name3, value3));
	}

	public static ArgumentValues of(String name, Object value, String name2, Object value2, String name3, Object value3,
			String name4, Object value4) {
		return newArgumentValues(pair(name, value), pair(name2, value2), pair(name3, value3), pair(name4, value4));
	}

	public static ArgumentValues of(String name, Object value, String name2, Object value2, String name3, Object value3,
			String name4, Object value4, String name5, Object value5) {
		return newArgumentValues(pair(name, value), pair(name2, value2), pair(name3, value3), pair(name4, value4),
				pair(name5, value5));
	}

	public static ArgumentValues of(String name, Object value, String name2, Object value2, String name3, Object value3,
			String name4, Object value4, String name5, Object value5, String name6, Object value6) {
		return newArgumentValues(pair(name, value), pair(name2, value2), pair(name3, value3), pair(name4, value4),
				pair(name5, value5), pair(name6, value6));
	}

	@SafeVarargs
	public static ArgumentValues newArgumentValues(Pair<String, Object>... args) {
		ArgumentValues argVals = new ArgumentValues();
		int index = 0;
		for (Pair<String, Object> arg : args) {
			argVals.put(arg.getRight().getClass(), index++, arg.getLeft(), arg.getRight());
		}
		return argVals;
	}

	public static ArgumentValues newArgumentValues(ArgumentValues provided) {
		ArgumentValues argVals = new ArgumentValues();
		for (ArgumentValuePair arg : provided) {
			int index = 0;
			argVals.put(arg.value.getClass(), index++, arg.name, arg.value);
		}
		return argVals;
	}

	public static ArgumentValues none() {
		return new ArgumentValues();
	}

	private static <V> Pair<String, V> pair(String name, V value) {
		return Pair.of(name, value);
	}
}

