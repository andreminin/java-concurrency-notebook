package org.lucentrix.demo.sandbox;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class MultiIterator<T> implements Iterator<T> {
    private final Iterator<T>[] iterators;
    private int offset;
    private Iterator<T> currentIterator;
    private boolean nextCalled;


    public MultiIterator(Iterator<T>... iterators) {
        if (iterators == null) {
            throw new IllegalArgumentException("Iterators array cannot be null");
        }
        this.iterators = iterators;
        this.offset = 0;
        this.currentIterator = null;
        this.nextCalled = false;
    }

    @Override
    public boolean hasNext() {
        while (offset < iterators.length) {
            if (currentIterator == null) {
                currentIterator = iterators[offset];
            }

            if (currentIterator.hasNext()) {
                return true;
            } else {
                currentIterator = null;
                offset++;
            }
        }

        return false;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        nextCalled = true;

        return currentIterator.next();
    }

    @Override
    public void remove() {
        if (!nextCalled) {
            throw new IllegalStateException("next() must be called before remove()");
        }

        if (currentIterator == null) {
            throw new IllegalStateException("No iterator to remove from");
        }

        currentIterator.remove();

        nextCalled = false;
    }
}