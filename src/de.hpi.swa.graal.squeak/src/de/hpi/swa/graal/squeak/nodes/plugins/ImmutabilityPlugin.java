package de.hpi.swa.graal.squeak.nodes.plugins;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import de.hpi.swa.graal.squeak.model.AbstractImmutableSqueakObjectWithClassAndHash;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ImmutableConsCharObject;
import de.hpi.swa.graal.squeak.model.ImmutablePointersObject;
import de.hpi.swa.graal.squeak.model.NativeImmutableBytesObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NotProvided;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.OctonaryPrimitive;
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

        protected final boolean isConsCharObject(final PointersObject object) {
            return object.getSqueakClassName().equals("Cons") && (object.getPointers()[0] instanceof Character);
        }

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

        @Specialization(guards = "isConsCharObject(receiver)")
        protected Object doConsCharObject(final PointersObject receiver) {
            return new ImmutableConsCharObject(receiver);
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

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveImmutableFromArgs")
    public abstract static class PrimImmutableFromArgs extends AbstractPrimitiveNode implements OctonaryPrimitive {

        @SuppressWarnings("unused")
        protected final boolean isConsClassObject(final ClassObject classObject) {
            return classObject.getClassName().equals("Cons");
        }

        @SuppressWarnings("unused")
        protected final boolean isCharacterObject(final Object object) {
            return (object instanceof Character);
        }

        protected PrimImmutableFromArgs(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0"})
        protected final Object doArg0(final ClassObject classObject, final NotProvided n1, final NotProvided n2, final NotProvided n3, final NotProvided n4, final NotProvided n5,
                                      final NotProvided n6, final NotProvided n7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{});
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)"})
        protected final Object doArg1(final ClassObject classObject, final Object arg1, final NotProvided n2, final NotProvided n3, final NotProvided n4, final NotProvided n5,
                                      final NotProvided n6, final NotProvided n7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{arg1});
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)", "!isNotProvided(arg2)", "isConsClassObject(classObject)", "isCharacterObject(arg1)"})
        protected final Object doArgConsChar(final ClassObject classObject, final Object arg1, final Object arg2, final NotProvided n3, final NotProvided n4, final NotProvided n5, final NotProvided n6,
                                             final NotProvided n7) {
            return new ImmutableConsCharObject(method.image,classObject, (Character)arg1, arg2);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)", "!isNotProvided(arg2)"})
        protected final Object doArg2(final ClassObject classObject, final Object arg1, final Object arg2, final NotProvided n3, final NotProvided n4, final NotProvided n5, final NotProvided n6,
                                      final NotProvided n7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{arg1,arg2});
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)", "!isNotProvided(arg2)", "!isNotProvided(arg3)"})
        protected final Object doArg3(final ClassObject classObject, final Object arg1, final Object arg2, final Object arg3, final NotProvided n4, final NotProvided n5, final NotProvided n6,
                                      final NotProvided n7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{arg1,arg2,arg3});
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)", "!isNotProvided(arg2)", "!isNotProvided(arg3)", "!isNotProvided(arg4)"})
        protected final Object doArg3(final ClassObject classObject, final Object arg1, final Object arg2, final Object arg3, final Object arg4, final NotProvided n5, final NotProvided n6,
                                      final NotProvided n7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{arg1,arg2,arg3,arg4});
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)", "!isNotProvided(arg2)", "!isNotProvided(arg3)", "!isNotProvided(arg4)", "!isNotProvided(arg5)"})
        protected final Object doArg5(final ClassObject classObject, final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5, final NotProvided n6,
                                      final NotProvided n7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{arg1,arg2,arg3,arg4,arg5});
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)", "!isNotProvided(arg2)", "!isNotProvided(arg3)", "!isNotProvided(arg4)", "!isNotProvided(arg5)",
                "!isNotProvided(arg6)"})
        protected final Object doArg6(final ClassObject classObject, final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5, final Object arg6,
                                      final NotProvided n7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{arg1,arg2,arg3,arg4,arg5,arg6});
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"classObject.isNonIndexableWithInstVars()", "method.getNumLiterals() > 0", "!isNotProvided(arg1)", "!isNotProvided(arg2)", "!isNotProvided(arg3)", "!isNotProvided(arg4)", "!isNotProvided(arg5)",
                "!isNotProvided(arg6)", "!isNotProvided(arg7)"})
        protected final Object doArg7(final ClassObject classObject, final Object arg1, final Object arg2, final Object arg3, final Object arg4, final Object arg5, final Object arg6,
                                      final Object arg7) {
            return new ImmutablePointersObject(method.image,classObject, new Object[]{arg1,arg2,arg3,arg4,arg5,arg6,arg7});
        }
    }

}
