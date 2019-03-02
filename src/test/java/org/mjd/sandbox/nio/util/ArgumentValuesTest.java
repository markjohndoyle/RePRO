package org.mjd.sandbox.nio.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mjd.sandbox.nio.util.ArgumentValues.newArgumentValues;

public final class ArgumentValuesTest
{
    private ArgumentValues argVals;


    @Before
    public void setup()
    {
        argVals = new ArgumentValues();
    }

    @Test
    public void testStringPutAndGet()
    {
        argVals.put(String.class, 0, "StringArg", "Prefer Heterogeneous Containers");
        String value = argVals.get(String.class, "StringArg").get();
        assertThat(value, is("Prefer Heterogeneous Containers"));
    }

    @Test
    public void testStringPutAndGetFromFactory()
    {
        argVals = newArgumentValues(Pair.<String, Object>of("StringArg", "Prefer Heterogeneous Containers"),
                                    Pair.<String, Object>of("IntArg", 5));
        String strValue = argVals.get(String.class, "StringArg").get();
        Integer intValue = argVals.get(Integer.class, "IntArg").get();
        assertThat(strValue, is("Prefer Heterogeneous Containers"));
        assertThat(intValue, is(5));
    }

    @Test
    public void testIntegerPutAndGet()
    {
        argVals.put(Integer.class, 0, "IntArg", 5);
        Integer value = argVals.get(Integer.class, "IntArg").get();
        assertThat(value, is(5));
    }

    @Test
    public void testEmptyListPutAndGet()
    {
        argVals.put(List.class, 0, "emptyListArg", new ArrayList<>());
        List<?> emptyValue = argVals.get(List.class, "emptyListArg").get();
        assertThat(emptyValue, empty());
    }

    @Test
    public void testListPutAndGet()
    {
        argVals.put(List.class, 0, "populatedListArg", ImmutableList.of(1, 2, 3));
        List<?> populatedValue = argVals.get(List.class, "populatedListArg").get();
        assertThat(populatedValue, is(not(empty())));
    }

    @Test
    public void testSubclassPutAndGet()
    {
        List<Integer> testList = new ArrayList<>();
        testList.add(1);
        testList.add(2);
        testList.add(3);
        argVals.put(testList.getClass(), "populatedListArg", testList);
        List<?> populatedValue = argVals.get(List.class, "populatedListArg").get();
        assertThat(populatedValue, is(not(empty())));
    }

    @Test
    public void testListPutAndGetAppend()
    {
        argVals.put(List.class, "populatedListArg", ImmutableList.of(1, 2, 3));
        List<?> populatedValue = argVals.get(List.class, "populatedListArg").get();
        assertThat(populatedValue, is(not(empty())));
    }

    @Test
    public void testMultipleSameTypePutAndGet()
    {
        argVals.put(Boolean.class, 0, "x", true);
        argVals.put(Boolean.class, 1, "y", true);
        argVals.put(Boolean.class, 2, "z", false);

        Boolean xVal = argVals.get(Boolean.class, "x").get();
        Boolean yVal = argVals.get(Boolean.class, "y").get();
        Boolean zVal = argVals.get(Boolean.class, "z").get();

        assertThat(xVal, is(true));
        assertThat(yVal, is(true));
        assertThat(zVal, is(false));
    }

    @Test
    public void testMultipleSameTypePutAndGetAppend()
    {
        argVals.put(Boolean.class, "x", true);
        argVals.put(Boolean.class, "y", true);
        argVals.put(Boolean.class, "z", false);

        Boolean xVal = argVals.get(Boolean.class, "x").get();
        Boolean yVal = argVals.get(Boolean.class, "y").get();
        Boolean zVal = argVals.get(Boolean.class, "z").get();

        assertThat(xVal, is(true));
        assertThat(yVal, is(true));
        assertThat(zVal, is(false));
    }

    @Test
    public void testMultipleSameTypePutAndInvalidGet()
    {
        argVals.put(Boolean.class, 0, "x", true);
        argVals.put(Boolean.class, 1, "y", true);
        argVals.put(Boolean.class, 2, "z", false);

        assertThat(argVals.get(Boolean.class, "trees are not bools").isPresent(), is(false));
    }

    @Test
    public void testPutSameArgValueReplacesOldValue()
    {
        argVals.put(String.class, 0, "x", "firstAdd");
        String resultOfAdd = argVals.put(String.class, 1, "x", "secondAdd");

        String xVal = argVals.get(String.class, "x").get();

        assertThat(resultOfAdd, is(not(nullValue())));
        assertThat(xVal, is("secondAdd"));
    }

    @Test
    public void testPutAppendSameArgValueReplacesOldValue()
    {
        argVals.put(String.class, "x", "firstAdd");
        String resultOfAdd = argVals.put(String.class, "x", "secondAdd");

        String xVal = argVals.get(String.class, "x").get();

        assertThat(resultOfAdd, is(not(nullValue())));
        assertThat(xVal, is("secondAdd"));
    }

    @Test
    public void testIteratorOrderWithHeterogeneousTypesPutInAlternateOrder()
    {
        argVals.put(Integer.class, 0, "first", 1);
        argVals.put(Boolean.class, 1, "second", true);
        argVals.put(Integer.class, 2, "third", 1);

        List<String> expectedOrderOfArgs = Lists.newArrayList("first", "second", "third");

        UnmodifiableIterator<ArgumentValuePair> it = argVals.iterator();
        Iterator<String> expectedIt = expectedOrderOfArgs.iterator();
        while(it.hasNext())
        {
            ArgumentValuePair value = it.next();
            assertThat(value.getName(), is(expectedIt.next()));
        }
    }

    @Test
    public void testIteratorOrderWithHeterogeneousTypesAppendedInAlternateOrder()
    {
        argVals.put(Integer.class, "first", 1);
        argVals.put(Boolean.class, "second", true);
        argVals.put(Integer.class, "third", 1);

        List<String> expectedOrderOfArgs = Lists.newArrayList("first", "second", "third");

        UnmodifiableIterator<ArgumentValuePair> it = argVals.iterator();
        Iterator<String> expectedIt = expectedOrderOfArgs.iterator();
        while(it.hasNext())
        {
            ArgumentValuePair value = it.next();
            assertThat(value.getName(), is(expectedIt.next()));
        }
    }

    @Test
    public void testIteratorOrderWithHeterogeneousTypesPutInAlternateOrderFromFactory()
    {
        argVals = newArgumentValues(Pair.<String, Object>of("first", 1),
                                    Pair.<String, Object>of("second", true),
                                    Pair.<String, Object>of("third", 1));

        List<String> expectedOrderOfArgs = Lists.newArrayList("first", "second", "third");

        UnmodifiableIterator<ArgumentValuePair> it = argVals.iterator();
        Iterator<String> expectedIt = expectedOrderOfArgs.iterator();
        while(it.hasNext())
        {
            ArgumentValuePair value = it.next();
            assertThat(value.getName(), is(expectedIt.next()));
        }
    }

    @Test
    public void testAppendUpdateExisting()
    {
        argVals.put(Integer.class, "first", 1);
        argVals.put(Boolean.class, "second", true);
        argVals.put(Integer.class, "third", 1);

        argVals.put(Integer.class, "first", 50);
        argVals.put(Boolean.class, "second", false);
        argVals.put(Integer.class, "third", 100);

        Iterator<Object> expectedValuesInOrder = Lists.<Object>newArrayList(50, false, 100).iterator();

        for(ArgumentValuePair argVal : argVals)
        {
            assertThat(argVal.getValue(), is(expectedValuesInOrder.next()));
        }
    }

    @Test
    public void testEmptyARgsReturnsEmptyArgsArray() {
    	Object[] array = argVals.asObjArray();
    	assertThat(array.length, is(0));
    }

}
