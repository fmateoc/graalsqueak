package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.ClassObject;

@DefaultExport(DefaultBooleanExports.class)
@DefaultExport(DefaultCharaterExports.class)
@DefaultExport(DefaultDoubleExports.class)
@DefaultExport(DefaultLongExports.class)
@DefaultExport(DefaultTruffleObjectExports.class)
@GenerateLibrary
public abstract class SqueakObjectLibrary extends Library {
    private static final LibraryFactory<SqueakObjectLibrary> FACTORY = LibraryFactory.resolve(SqueakObjectLibrary.class);

    public boolean acceptsValue(final Object receiver, final Object value) {
        throw SqueakException.create("acceptsValue not supported by", receiver, "with", value);
    }

    @Abstract(ifExported = "atput0")
    public Object at0(final Object receiver, final int index) {
        throw SqueakException.create("at0 not supported by", receiver, "at", index);
    }

    @Abstract(ifExported = "at0")
    public void atput0(final Object receiver, final int index, final Object value) {
        throw SqueakException.create("atput0 not supported by", receiver, "at", index, "with", value);
    }

    // public abstract boolean become(Object left, Object right);

    public byte[] nativeBytes(final Object receiver) {
        throw SqueakException.create("nativeBytes not supported by", receiver);
    }

    public int instsize(@SuppressWarnings("unused") final Object receiver) {
        return 0;
    }

    public boolean replaceFromToWithStartingAt(final Object receiver, final int start, final int stop, final Object repl, final int replStart) {
        throw SqueakException.create("replaceFromToWithStartingAt not supported by", receiver, "with", start, stop, repl, replStart);
    }

    public int size(@SuppressWarnings("unused") final Object receiver) {
        return 0;
    }

    public Object shallowCopy(final Object receiver) {
        throw SqueakException.create("shallowCopy not supported by", receiver);
    }

    public abstract ClassObject squeakClass(Object receiver);

    public abstract long squeakHash(Object receiver);

    // public abstract void pointersBecomeOneWay(Object obj, Object[] from, Object[] to, boolean
    // copyHash);

    public static SqueakObjectLibrary getUncached() {
        return FACTORY.getUncached();
    }

    public static LibraryFactory<SqueakObjectLibrary> getFactory() {
        return FACTORY;
    }
}
