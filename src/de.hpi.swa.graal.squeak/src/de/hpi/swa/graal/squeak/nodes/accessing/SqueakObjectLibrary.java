package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

@GenerateLibrary
public abstract class SqueakObjectLibrary extends Library {

    public boolean isArray(final Object receiver) {
        return false;
    }

    public abstract Object at0(Object receiver, int index);

    public abstract void atput0(Object receiver, int index, Object value);

// public abstract boolean become(Object left, Object right);
//
// public abstract int instsize(Object receiver);
//
// public abstract int size(Object receiver);
//
// public abstract Object shallowCopy(Object receiver);
//
// public abstract void pointersBecomeOneWay(Object obj, Object[] from, Object[] to, boolean
// copyHash);
}
