package org.mjd.repro.message;

public interface Message<T>
{
    T getValue();

    int size();

//    byte[] asByteArray();
}