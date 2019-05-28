package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObjectWithClassAndHash;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.FloatObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;

/** This node should only be used in primitive nodes as it may throw a PrimitiveFailed exception. */
public abstract class SqueakObjectChangeClassOfToNode extends AbstractNode {

    public abstract AbstractSqueakObject execute(AbstractSqueakObjectWithClassAndHash receiver, ClassObject argument);

    @Specialization(guards = "receiver.hasSameFormat(argument)")
    protected static final NativeObject doNative(final NativeObject receiver, final ClassObject argument) {
        receiver.setSqueakClass(argument);
        return receiver;
    }

    @Specialization(guards = {"!receiver.hasSameFormat(argument)", "argument.isBytes()"}, limit = "1")
    protected static final NativeObject doNativeConvertToBytes(final NativeObject receiver, final ClassObject argument,
                    @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
        receiver.setSqueakClass(argument);
        receiver.convertToBytesStorage(objectLibrary.nativeBytes(receiver));
        return receiver;
    }

    @Specialization(guards = {"!receiver.hasSameFormat(argument)", "argument.isShorts()"}, limit = "1")
    protected static final NativeObject doNativeConvertToShorts(final NativeObject receiver, final ClassObject argument,
                    @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
        receiver.setSqueakClass(argument);
        receiver.convertToBytesStorage(objectLibrary.nativeBytes(receiver));
        return receiver;
    }

    @Specialization(guards = {"!receiver.hasSameFormat(argument)", "argument.isWords()"}, limit = "1")
    protected static final NativeObject doNativeConvertToInts(final NativeObject receiver, final ClassObject argument,
                    @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
        receiver.setSqueakClass(argument);
        receiver.convertToBytesStorage(objectLibrary.nativeBytes(receiver));
        return receiver;
    }

    @Specialization(guards = {"!receiver.hasSameFormat(argument)", "argument.isLongs()"}, limit = "1")
    protected static final NativeObject doNativeConvertToLongs(final NativeObject receiver, final ClassObject argument,
                    @CachedLibrary("receiver") final SqueakObjectLibrary objectLibrary) {
        receiver.setSqueakClass(argument);
        receiver.convertToBytesStorage(objectLibrary.nativeBytes(receiver));
        return receiver;
    }

    @Specialization(guards = {"argument.isBytes()"})
    protected static final LargeIntegerObject doLargeInteger(final LargeIntegerObject receiver, final ClassObject argument) {
        receiver.setSqueakClass(argument);
        return receiver;
    }

    @Specialization(guards = {"argument.isWords()"})
    protected static final FloatObject doFloat(final FloatObject receiver, final ClassObject argument) {
        receiver.setSqueakClass(argument);
        return receiver;
    }

    @Specialization(guards = {"!isNativeObject(receiver)", "!isLargeIntegerObject(receiver)", "!isFloatObject(receiver)",
                    "receiver.getSqueakClass().getFormat() == argument.getFormat()"})
    protected static final AbstractSqueakObject doSqueakObject(final AbstractSqueakObjectWithClassAndHash receiver, final ClassObject argument) {
        receiver.setSqueakClass(argument);
        return receiver;
    }

    @SuppressWarnings("unused")
    @Fallback
    protected static final AbstractSqueakObject doFail(final AbstractSqueakObjectWithClassAndHash receiver, final ClassObject argument) {
        throw new PrimitiveFailed();
    }
}
