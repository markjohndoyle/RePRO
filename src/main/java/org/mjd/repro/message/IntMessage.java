package org.mjd.repro.message;

import com.google.common.primitives.Ints;

public final class IntMessage implements Message<Integer>
{
    private final int value;

    public IntMessage(final int value)
    {
        this.value = value;
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

    public static Message<Integer> from(final byte[] message)
    {
        return new IntMessage(Ints.fromByteArray(message));
    }

}
