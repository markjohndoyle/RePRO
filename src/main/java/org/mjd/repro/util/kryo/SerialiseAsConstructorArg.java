/*
 * PROJECTNAME: IDPF MTG
 * AUTHOR: CGI
 * COPYRIGHT: EUMETSAT 2011
 */
package org.mjd.repro.util.kryo;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Designates that a field should be serialised and then used as the constructor argument at the given index
 * when deserialising.
 * This allows class with no no-arg constructors to the serialised and deserialised. This could also be an
 * immutable class with final fields if the serialiser using the annotations uses reflection.
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface SerialiseAsConstructorArg
{
    /** the constructor argument index this field's value should be passed to */
    int index();
}
