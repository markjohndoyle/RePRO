package org.mjd.sandbox.nio.message;

public interface Message<T>
{
    T getValue();

    int size();
    
    byte[] asByteArray();
}