package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.interop.InteropArray;
import de.hpi.swa.graal.squeak.interop.LookupMethodByStringNode;
import de.hpi.swa.graal.squeak.interop.WrapToSqueakNode;
import de.hpi.swa.graal.squeak.nodes.DispatchSendNode;
import de.hpi.swa.graal.squeak.nodes.DispatchUneagerlyNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectAt0Node;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectSizeNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

@ExportLibrary(InteropLibrary.class)
public abstract class AbstractImmutableSqueakObjectWithClassAndHash extends AbstractSqueakObject {
    public static final int IDENTITY_HASH_MASK = 0x400000 - 1;
    private static final int PINNED_BIT_SHIFT = 30;

    public final SqueakImageContext image;
    private final long squeakHash;
    private final ClassObject squeakClass;

    protected AbstractImmutableSqueakObjectWithClassAndHash(final SqueakImageContext image, final ClassObject klass) {
        this.image = image;
        squeakHash = calculateHash();
        squeakClass = klass;
    }

    protected AbstractImmutableSqueakObjectWithClassAndHash(final SqueakImageContext image, final long hash, final ClassObject klass) {
        this.image = image;
        squeakHash = hash;
        squeakClass = klass;
    }

    public final ClassObject getSqueakClass() {
        return squeakClass;
    }

    public final String getSqueakClassName() {
        if (isClass()) {
            return nameAsClass() + " class";
        } else {
            return getSqueakClass().nameAsClass();
        }
    }

    private long calculateHash() {
        return hashCode() & IDENTITY_HASH_MASK;
    }

    public final long getSqueakHash() {
        return squeakHash;
    }

    public final boolean isClass() {
        assert getSqueakClass().isMetaClass() || getSqueakClass().getSqueakClass().isMetaClass();
        CompilerAsserts.neverPartOfCompilation();
        return false;
    }

    public final boolean isPinned() {
        return (squeakHash >> PINNED_BIT_SHIFT & 1) == 1;
    }

    public String nameAsClass() {
        return "???NotAClass";
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "a " + getSqueakClassName();
    }

    public final Object send(final String selector, final Object... arguments) {
        CompilerAsserts.neverPartOfCompilation("For testing or instrumentation only.");
        final Object methodObject = LookupMethodByStringNode.getUncached().executeLookup(getSqueakClass(), selector);
        if (methodObject instanceof CompiledMethodObject) {
            final CompiledMethodObject method = (CompiledMethodObject) methodObject;
            final MaterializedFrame frame = Truffle.getRuntime().createMaterializedFrame(ArrayUtils.EMPTY_ARRAY, method.getFrameDescriptor());
            return DispatchSendNode.create(image).executeSend(frame, method.getCompiledInSelector(), method, getSqueakClass(), ArrayUtils.copyWithFirst(arguments, this), NilObject.SINGLETON);
        } else {
            throw SqueakExceptions.SqueakException.create("CompiledMethodObject expected, got: " + methodObject);
        }
    }

    /*
     * INTEROPERABILITY
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    protected final boolean hasArrayElements() {
        return squeakClass.isVariable();
    }

    @ExportMessage
    protected final long getArraySize(@Shared("sizeNode") @Cached final SqueakObjectSizeNode sizeNode) {
        return sizeNode.execute(this);
    }

    @SuppressWarnings("static-method")
    @ExportMessage(name = "isArrayElementReadable")
    protected final boolean isArrayElementReadable(final long index, @Shared("sizeNode") @Cached final SqueakObjectSizeNode sizeNode) {
        return 0 <= index && index < sizeNode.execute(this);
    }

    @SuppressWarnings("static-method")
    @ExportMessage(name = "isArrayElementInsertable")
    @ExportMessage(name = "isArrayElementModifiable")
    protected final boolean isArrayElementModifiable(@SuppressWarnings("unused") final long index) {
        return false;
    }

    @ExportMessage
    protected final Object readArrayElement(final long index, @Cached final SqueakObjectAt0Node at0Node) {
        return at0Node.execute(this, index);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected final void writeArrayElement(@SuppressWarnings("unused") final long index,
                                           @SuppressWarnings("unused") final Object value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected final boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected final Object getMembers(@SuppressWarnings("unused") final boolean includeInternal) {
        return new InteropArray(getSqueakClass().listMethods());
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberInvocable")
    public boolean isMemberReadable(final String key,
                    @Shared("lookupNode") @Cached final LookupMethodByStringNode lookupNode) {
        return lookupNode.executeLookup(getSqueakClass(), toSelector(key)) != null;
    }

    @ExportMessage(name = "isMemberInsertable")
    @ExportMessage(name = "isMemberModifiable")
    public boolean isMemberModifiable(@SuppressWarnings("unused") final String key) {
        return false;
    }

    @ExportMessage
    public Object readMember(final String key,
                    @Shared("lookupNode") @Cached final LookupMethodByStringNode lookupNode) {
        return lookupNode.executeLookup(getSqueakClass(), toSelector(key));
    }

    @ExportMessage
    public void writeMember(@SuppressWarnings("unused") final String member, @SuppressWarnings("unused") final Object value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public Object invokeMember(final String member, final Object[] arguments,
                    @Shared("lookupNode") @Cached final LookupMethodByStringNode lookupNode,
                    @Cached final WrapToSqueakNode wrapNode,
                    @Cached final DispatchUneagerlyNode dispatchNode) throws UnsupportedMessageException, ArityException {
        final Object methodObject = lookupNode.executeLookup(getSqueakClass(), toSelector(member));
        if (methodObject instanceof CompiledMethodObject) {
            final CompiledMethodObject method = (CompiledMethodObject) methodObject;
            final int actualArity = arguments.length;
            final int expectedArity = method.getNumArgs();
            if (actualArity == expectedArity) {
                return dispatchNode.executeDispatch(method, ArrayUtils.copyWithFirst(wrapNode.executeObjects(arguments), this), NilObject.SINGLETON);
            } else {
                throw ArityException.create(1 + expectedArity, 1 + actualArity);  // +1 for receiver
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    /**
     * Converts an interop identifier to a Smalltalk selector. Most languages do not allow colons in
     * identifiers, so treat underscores as colons as well.
     *
     * @param identifier for interop
     * @return Smalltalk selector
     */
    private static String toSelector(final String identifier) {
        return identifier.replace('_', ':');
    }
}
