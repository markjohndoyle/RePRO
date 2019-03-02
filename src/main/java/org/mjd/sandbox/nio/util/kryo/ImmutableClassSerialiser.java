/*
 * PROJECTNAME: IDPF MTG
 * AUTHOR: CGI
 * COPYRIGHT: EUMETSAT 2011
 */
package org.mjd.sandbox.nio.util.kryo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.reflect.FieldUtils;

/**
 * Kryo {@link Serializer} for Immutable types annotated with {@link SerialiseAsConstructorArg}
 *
 * This serialiser uses field annotations in the target class to determine which values should
 * be passed to the correct constructor in order to recreate (deserialise phase) the class
 * as it was serialised. This is required, of course, because immutable classes must be
 * constructed fully initialised and must have no way of altering their state at runtime.
 *
 * Reflection is used to gather values from the immutable class when serialisation is triggered through
 * the read {@link #write(Kryo, Output, Object)} method.
 *
 * @param <T>
 *            the type this {@link Serializer} is serialising
 */
//Shortening the class name will sacrifice clarity, that is, obfuscate the code.
@SuppressWarnings("squid:S00101")
public final class ImmutableClassSerialiser<T> extends Serializer<T>
{
    /** The type this {@link Serializer} is serialising */
    private final Class<T> type;
    /** The fields required for the constructor in the correct order; taken from annotations in the type class */
    private final List<Field> fieldsToSerialise;
    /** The constructor required to instantiate the type class with the given fields */
    private final Constructor<T> targetConstructor;

    /**
     * Constructs the serialiser for the given type. We need the class type due to type erasure of generics.
     *
     * This {@link Serializer} is itself is immutable, everything is initialised at construction and no
     * public methods change the internal state.
     *
     * The serialiser is defined as immutable with regards to {@link Serializer#isImmutable()}
     *
     * @param targetType
     *            the type the serialisers is serialising, it is of type T
     */
    public ImmutableClassSerialiser(Class<T> targetType)
    {
        super(false, true);
        this.type = targetType;
        fieldsToSerialise = getSortedConstructorFields();
        try
        {
            targetConstructor = findConstructor();
        }
        catch (NoSuchMethodException e)
        {
            throw new KryoException(e);
        }
    }

    @Override
    public void write(Kryo kryo, Output output, T object)
    {
        try
        {
            for (Field field : fieldsToSerialise)
            {
                Object value = FieldUtils.readField(field, object, true);
                kryo.writeClassAndObject(output, value);
            }
        }
        catch (IllegalAccessException e)
        {
            throw new KryoException(e);
        }
    }

    @Override
	public T read(Kryo kryo, Input input, Class<? extends T> type) {
        try
        {
            return createInstance(input, kryo);
        }
        catch (IllegalAccessException | InvocationTargetException | InstantiationException e)
        {
            throw new KryoException(e);
        }
    }

    /**
     * Creates an instance of T (the type we are serialising/deserialising) using the necessary fields and
     * the corresponding values retrieved from the kryo instance.
     *
     * @param input
     *            the kryo input to read required serialised data from
     * @param kryo
     *            the kryo instance to read required serialised data from
     * @return an new instance of type T
     * @throws IllegalAccessException
     *             if the new instance cannot be created or the attempt failed.
     * @throws InvocationTargetException
     *             if the new instance cannot be created or the attempt failed.
     * @throws InstantiationException
     *             if the new instance cannot be created or the attempt failed.
     */
    private T createInstance(Input input, Kryo kryo) throws IllegalAccessException, InvocationTargetException,
                                                     InstantiationException
    {
        int numArgs = fieldsToSerialise.size();
        Object[] argValues = new Object[numArgs];
        for (int i = 0; i < numArgs; i++)
        {
            argValues[i] = kryo.readClassAndObject(input);
        }
        return targetConstructor.newInstance(argValues);
    }

    /**
     * Gathers all the fields that have been annotated with {@link SerialiseAsConstructorArg}. These will be
     * sorted with respect to the index specified in the {@link SerialiseAsConstructorArg} annotation.
     *
     * @return a List of {@link Field} instances for every annotated field of the type we are serialising.
     */
    // Shortening the method name will sacrifice clarity, that is, obfuscate the code.
    @SuppressWarnings("squid:S00100")
    private List<Field> getSortedConstructorFields()
    {
        List<Field> serialiseFields = FieldUtils.getFieldsListWithAnnotation(type, SerialiseAsConstructorArg.class);
        Collections.sort(serialiseFields, (lhs, rhs) -> {
		    int leftIndex = lhs.getAnnotation(SerialiseAsConstructorArg.class).index();
		    int rightIndex = rhs.getAnnotation(SerialiseAsConstructorArg.class).index();
		    return Integer.compare(leftIndex, rightIndex);
		});
        return serialiseFields;
    }

    /**
     * Finds the constructor required for deserialisation based upon the annotated fields in the target class T.
     *
     * The target {@link Constructor} is determined based upon the types and order of the the fields annotated
     * in the type T, that is, the class this {@link Serializer} is serialising.
     *
     * If the annotations do not match a Constructor on type T a {@link NoSuchMethodException} will be thrown
     * and the {@link Serializer} will fail to construct at runtime before any serialisation operations are
     * attempted.
     *
     * @return The {@link Constructor} for type T that matches the fields annotated in class T.
     * @throws NoSuchMethodException
     *             if the expected {@link Constructor} does not exist on the type T
     */
    private Constructor<T> findConstructor() throws NoSuchMethodException
    {
        List<Class<?>> argTypes = Lists.transform(fieldsToSerialise, input -> input.getType());
        Class<?>[] argsArray = Iterables.toArray(argTypes, Class.class);
        return type.getConstructor(argsArray);
    }
}