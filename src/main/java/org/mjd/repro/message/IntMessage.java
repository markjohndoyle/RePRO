package org.mjd.repro.message;

import java.nio.ByteBuffer;

import com.google.common.primitives.Ints;

public final class IntMessage implements Message<Integer>
{
    private final int value;
    private final byte[] bytes;

    public IntMessage(int value)
    {
        this.value = value;
        bytes = ByteBuffer.allocate(size()).putInt(value).array();
    }

    /* (non-Javadoc)
     * @see org.mjd.repro.Message#getValue()
     */
    @Override
    public Integer getValue()
    {
        return value;
    }

    /* (non-Javadoc)
     * @see org.mjd.repro.Message#size()
     */
    @Override
    public int size()
    {
        return Integer.BYTES;
    }

    /* (non-Javadoc)
     * @see org.mjd.repro.Message#asByteArray()
     */
//    @Override
//    public byte[] asByteArray()
//    {
//        return bytes;
//    }

    public static Message<Integer> from(byte[] message)
    {
        return new IntMessage(Ints.fromByteArray(message));
    }

}
