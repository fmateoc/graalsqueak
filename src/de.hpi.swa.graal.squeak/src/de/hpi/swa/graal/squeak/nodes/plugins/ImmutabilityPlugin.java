package de.hpi.swa.graal.squeak.nodes.plugins;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import de.hpi.swa.graal.squeak.model.AbstractImmutableSqueakObjectWithClassAndHash;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ImmutablePointersObject;
import de.hpi.swa.graal.squeak.model.NativeImmutableBytesObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitiveWithoutFallback;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;

import java.util.List;

public final class ImmutabilityPlugin extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ImmutabilityPluginFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveImmutableCopy")
    protected abstract static class PrimImmutableCopyNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        protected PrimImmutableCopyNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "receiver.isByteType()")
        protected Object doNativeBytes(final NativeObject receiver) {
            return new NativeImmutableBytesObject(receiver);
        }

        @Specialization
        protected Object doNativeImmutableBytes(final NativeImmutableBytesObject receiver) {
            return new NativeImmutableBytesObject(receiver);
        }

        @Specialization
        protected Object doPointersObject(final PointersObject receiver) {
            return new ImmutablePointersObject(receiver);
        }

        @Specialization
        protected Object doImmutablePointersObject(final ImmutablePointersObject receiver) {
            return new ImmutablePointersObject(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveIsImmutable")
    protected abstract static class PrimIsImmutableNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        protected PrimIsImmutableNode (final CompiledMethodObject method){
            super(method);
        }

        @Specialization
        protected boolean doAbstractImmutableSqueakObjectWithClassAndHash(@SuppressWarnings("unused") final AbstractImmutableSqueakObjectWithClassAndHash receiver){
            return true;
        }

        @Fallback
        protected boolean doNotImmutable(@SuppressWarnings("unused") final Object receiver){
            return false;
        }
    }
}
