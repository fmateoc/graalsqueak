package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary
public abstract class SqueakObjectLibrary extends Library {
    private static final LibraryFactory<SqueakObjectLibrary> FACTORY = LibraryFactory.resolve(SqueakObjectLibrary.class);

    @Abstract(ifExported = "atput0")
    public abstract Object at0(Object receiver, int index);

    @Abstract
    public abstract void atput0(Object receiver, int index, Object value);

// public abstract boolean become(Object left, Object right);
//
    @Abstract
    public abstract int instsize(Object receiver);

    @Abstract
    public abstract int size(Object receiver);

    public abstract Object shallowCopy(Object receiver);

// public abstract void pointersBecomeOneWay(Object obj, Object[] from, Object[] to, boolean
// copyHash);

    public static LibraryFactory<SqueakObjectLibrary> getFactory() {
        return FACTORY;
    }
}
