package de.hpi.swa.graal.squeak.image.writing;

import java.nio.channels.SeekableByteChannel;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.model.WeakPointersObject;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.accessing.WeakPointersObjectNodes.WeakPointersObjectWriteNode;

public abstract class WriteSqueakObjectNode extends Node {
    private final SqueakImageContext image;
    private final SeekableByteChannel stream;

    protected WriteSqueakObjectNode(final SqueakImageContext image, final SeekableByteChannel stream) {
        this.image = image;
        this.stream = stream;
    }

    public static WriteSqueakObjectNode create(final SqueakImageContext image, final SeekableByteChannel stream) {
        return WriteSqueakObjectNodeGen.create(image, stream);
    }

    public abstract void execute(Object obj);

    @Specialization
    protected static final void doBlockClosure(final BlockClosureObject obj) {

    }

    @Specialization
    protected final void doClassObj(final ClassObject obj) {
    }

    @Specialization
    protected static final void doCompiledCodeObj(final CompiledCodeObject obj) {

    }

    @SuppressWarnings("unused")
    @Specialization
    protected static final void doContext(final ContextObject obj) {
        /** {@link ContextObject}s are filled in at a later stage by a {@link FillInContextNode}. */
    }

    @Specialization(guards = {"obj.isByteType()"})
    protected static final void doNativeByte(final NativeObject obj) {
        obj.getByteStorage();
    }

    @Specialization(guards = "obj.isShortType()")
    protected static final void doNativeShort(final NativeObject obj) {
        obj.getShortStorage();
    }

    @Specialization(guards = "obj.isIntType()")
    protected static final void doNativeInt(final NativeObject obj) {
        obj.getIntStorage();
    }

    @Specialization(guards = "obj.isLongType()")
    protected static final void doNativeLong(final NativeObject obj) {
        obj.getLongStorage();
    }

    @Specialization
    protected static final void doArrays(final ArrayObject obj,
                    @Cached final ArrayObjectWriteNode writeNode) {
        // obj.setStorageAndSpecialize(chunk.getPointers(), writeNode);
    }

    @Specialization
    protected static final void doPointers(final PointersObject obj) {
        obj.getPointers();
    }

    @Specialization
    protected static final void doWeakPointers(final WeakPointersObject obj,
                    @Cached final WeakPointersObjectWriteNode writeNode) {
        obj.getPointers();
    }

    @SuppressWarnings("unused")
    @Fallback
    protected static final void doNothing(final Object obj) {
        // do nothing
    }
}
