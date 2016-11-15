package org.broadinstitute.hellbender.utils.collections;

import java.util.ArrayList;
import java.util.Collection;

/**
 * ArrayList class that automatically expands whenever {@link #expandingGet(int, Object)} or {@link #set(int, Object)}
 * are called and the requested index exceeds the current ArrayList size.
 * @param <E>
 */
public class ExpandingArrayList<E> extends ArrayList<E> {
    private static final long serialVersionUID = 1L;

    public ExpandingArrayList() { super(); }
    public ExpandingArrayList(final Collection<? extends E> c) { super(c); }
    public ExpandingArrayList(final int initialCapacity) { super(initialCapacity); }

    /**
     * Returns the element at the specified position in this list.  If index > size,
     * returns null.  Otherwise tries to access the array
     * @param index
     * @return
     * @throws IndexOutOfBoundsException in index < 0
     */
    @Override
    public E get(final int index) throws IndexOutOfBoundsException {
        if ( index < size() )
            return super.get(index);
        else
            return null;
    }

    public E expandingGet(final int index, final E default_value) throws IndexOutOfBoundsException {
        maybeExpand(index, default_value);
        return super.get(index);
    }

    @Override
    public E set(final int index, final E element) {
        maybeExpand(index, null);
        return super.set(index, element);
    }
    private void maybeExpand(final int index, final E value) {
        if ( index >= size() ) {
            ensureCapacity(index+1); // make sure we have space to hold at least index + 1 elements
            // We need to add null items until we can safely set index to element
            for ( int i = size(); i <= index; i++ )
                add(value);
        }
    }

}
