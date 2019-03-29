package org.mjd.repro.util.kryo;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import com.esotericsoftware.kryo.Kryo;

/**
 * A pool of {@link Kryo} that can be reused to avoid allocations. The pool is optionally thread safe as the the factory
 * methods. The {@link Kryo} instances in the pool are configured by the configurators which must satisfy the
 * {@link Function} interface, accept a {@link Kryo} and return a {@link Kryo}. You may of course use lambdas and/or
 * method references here. You can chain as many configurators as you wish, they will all be called in order with the
 * same kryo instance.
 *
 * <pre>
 * e,g.:
 *  KryoPool p = KryoPoo.newPool(5, kryo -> { ..kryo config and return });
 *  KryoPool p = KryoPoo.newPool(5, ConfiguratorA::configure);
 *  KryoPool p = KryoPoo.newPool(5, configuratorInstance::apply);
 * </pre>
 *
 */
public final class KryoPool {
	private final Queue<Kryo> pool;
	private final Function<Kryo, Kryo>[] configurators;

	/**
	 * Constructs a fully initialised {@link KryoPool}. Use the factories provided as they simplify using the parameters.
	 *
	 * @param threadSafe  whether access to this pool should be threadsafe or not
	 * @param maxCapacity the maximum size of the pool
	 * @param creators    0..n configurators
	 *
	 * @see KryoPool
	 */
	@SafeVarargs
	public KryoPool(final boolean threadSafe, final int maxCapacity, final Function<Kryo, Kryo>... creators) {
		this.configurators = creators;
		if (threadSafe) {
			pool = new LinkedBlockingQueue<>(maxCapacity);
		}
		else {
			pool = new ArrayDeque<Kryo>(maxCapacity) {
				@Override
				public boolean add(final Kryo object) {
					if (size() >= maxCapacity) {
						return false;
					}
					super.add(object);
					return true;
				}
			};
		}
	}

	/**
	 * Returns an object from this pool. The object may be new a chain of (from {@link #configurators}) or reused
	 * (previously {@link #free(Kryo) freed}).
	 *
	 * @return {@link Kryo} object from the pool.
	 */
	public Kryo obtain() {
		Kryo kryo = pool.poll();
		if (kryo == null) {
			kryo = new Kryo();
			for (final Function<Kryo, Kryo> configurator : configurators) {
				kryo = configurator.apply(kryo);
			}
		}
		return kryo;
	}

	/**
	 * Puts the specified object in the pool, making it eligible to be returned by {@link #obtain()}. If the pool already
	 * contains the maximum number of free objects, the specified object is reset but not added to the pool.
	 * <p>
	 * If using soft references and the pool contains the maximum number of free objects, the first soft reference whose
	 * object has been garbage collected is discarded to make room.
	 *
	 * @param kryo the {@link Kryo} to reset and return to the pool
	 */
	public void free(final Kryo kryo) {
		pool.add(kryo);
		kryo.reset();
	}

	/**
	 * Creates a new threadsafe kryo pool for the given configurators. The maximum size of the pool will be limited to the
	 * provided {@code maxCapacity} parameter.
	 *
	 * @param maxCapacity   the maximum size of the pool
	 * @param configurators Functionals that are free to configure the passed kryo instance
	 * @return {@link KryoPool}
	 */
	@SafeVarargs
	public static KryoPool newThreadSafePool(final int maxCapacity, final Function<Kryo, Kryo>... configurators) {
		return new KryoPool(true, maxCapacity, configurators);
	}

	/**
	 * Creates a new kryo pool for the given configurators. The maximum size of the pool will be limited to the provided
	 * {@code maxCapacity} parameter.
	 *
	 * @param maxCapacity   the maximum size of the pool
	 * @param configurators Functionals that are free to configure the passed kryo instance
	 * @return {@link KryoPool}
	 */
	@SafeVarargs
	public static KryoPool newPool(final int maxCapacity, final Function<Kryo, Kryo>... configurators) {
		return new KryoPool(false, maxCapacity, configurators);
	}

}
