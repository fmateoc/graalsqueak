package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.*;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeImmutableObjectNodes.NativeImmutableObjectSizeNode;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodes.NativeObjectSizeNode;

@GenerateUncached
public abstract class SqueakObjectSizeNode extends AbstractNode {

    public static SqueakObjectSizeNode create() {
        return SqueakObjectSizeNodeGen.create();
    }

    public abstract int execute(Object obj);

    @Specialization
    protected static final int doArray(final ArrayObject obj, @Cached final ArrayObjectSizeNode sizeNode) {
        return sizeNode.execute(obj);
    }

    @Specialization
    protected static final int doClass(final ClassObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doContext(final ContextObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doAbstractPointers(final AbstractPointersObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doClosure(final BlockClosureObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doMethod(final CompiledMethodObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doBlock(final CompiledBlockObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doEmpty(final EmptyObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doNative(final NativeObject obj, @Cached final NativeObjectSizeNode sizeNode) {
        return sizeNode.execute(obj);
    }

    @Specialization
    protected  static final int doNativeImmutableBytes(final NativeImmutableBytesObject obj) {
        return obj.getByteLength();
    }

    @Specialization
    protected static final int doFloat(final FloatObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doLargeInteger(final LargeIntegerObject obj) {
        return obj.size();
    }

    @Specialization
    protected static final int doNil(final NilObject obj) {
        return obj.size();
    }

    @Fallback
    protected static final int doFallback(final Object obj) {
        throw SqueakException.create("Object does not support size:", obj);
    }
}
