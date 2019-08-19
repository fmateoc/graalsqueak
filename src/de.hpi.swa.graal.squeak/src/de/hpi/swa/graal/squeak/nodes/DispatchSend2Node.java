package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakError;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakSyntaxError;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectClassNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackPopNNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackPopNode;
import de.hpi.swa.graal.squeak.util.MiscUtils;

@NodeInfo(cost = NodeCost.NONE)
public abstract class DispatchSend2Node extends AbstractNodeWithCode {
    protected final int argumentCount;

    protected DispatchSend2Node(final CompiledCodeObject code, final int argumentCount) {
        super(code);
        this.argumentCount = argumentCount;
    }

    public static DispatchSend2Node create(final CompiledCodeObject code, final int argumentCount) {
        return DispatchSend2NodeGen.create(code, argumentCount);
    }

    public abstract Object executeSend(VirtualFrame frame, NativeObject selector, Object lookupResult, ClassObject rcvrClass);

    @Specialization(guards = {"!code.image.isHeadless() || selector.isAllowedInHeadlessMode()"})
    protected static final Object doDispatch(final VirtualFrame frame, @SuppressWarnings("unused") final NativeObject selector, @SuppressWarnings("unused") final CompiledMethodObject lookupResult,
                    @SuppressWarnings("unused") final ClassObject rcvrClass,
                    @Cached("create(code, argumentCount)") final DispatchEagerly2Node dispatchNode) {
        return dispatchNode.executeDispatch(frame, lookupResult);
    }

    @Specialization(guards = {"!code.image.isHeadless() || selector.isAllowedInHeadlessMode()"})
    protected static final Object doDispatchNeedsSender(final VirtualFrame frame, @SuppressWarnings("unused") final NativeObject selector, final CompiledMethodObject lookupResult,
                    @SuppressWarnings("unused") final ClassObject rcvrClass,
                    @Cached("create(code, argumentCount)") final DispatchEagerly2Node dispatchNode) {
        return dispatchNode.executeDispatch(frame, lookupResult);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"code.image.isHeadless()", "selector.isDebugErrorSelector()"})
    protected final Object doDispatchHeadlessError(final VirtualFrame frame, final NativeObject selector, final CompiledMethodObject lookupResult, final ClassObject rcvrClass) {
        throw new SqueakError(this, MiscUtils.format("%s>>#%s detected in headless mode. Aborting...", rcvrClass.getSqueakClassName(), selector.asStringUnsafe()));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"code.image.isHeadless()", "selector.isDebugSyntaxErrorSelector()"})
    protected static final Object doDispatchHeadlessSyntaxError(final VirtualFrame frame, final NativeObject selector, final CompiledMethodObject lookupResult, final ClassObject rcvrClass,
                    @Cached("create(code, argumentCount)") final FrameStackPopNNode popNNode) {
        throw new SqueakSyntaxError((PointersObject) popNNode.execute(frame)[0]);
    }

    @Specialization(guards = {"lookupResult == null"})
    protected final Object doDoesNotUnderstand(final VirtualFrame frame, final NativeObject selector, @SuppressWarnings("unused") final Object lookupResult,
                    final ClassObject rcvrClass,
                    @Cached final LookupMethodNode lookupNode,
                    @Cached("create(code, argumentCount)") final FrameStackPopNNode popNNode,
                    @Cached("create(code)") final FrameStackPopNode popReceiverNode,
                    @Cached("create(code)") final DispatchEagerlyNode dispatchNode) {
        final Object[] arguments = popNNode.execute(frame);
        final Object receiver = popReceiverNode.execute(frame);
        final CompiledMethodObject doesNotUnderstandMethod = (CompiledMethodObject) lookupNode.executeLookup(rcvrClass, code.image.doesNotUnderstand);
        final PointersObject message = code.image.newMessage(selector, rcvrClass, arguments);
        return dispatchNode.executeDispatch(frame, doesNotUnderstandMethod, new Object[]{receiver, message});
    }

    @Specialization(guards = {"!isCompiledMethodObject(targetObject)"})
    protected final Object doObjectAsMethod(final VirtualFrame frame, final NativeObject selector, final Object targetObject,
                    @SuppressWarnings("unused") final ClassObject rcvrClass,
                    @Cached("create(code, argumentCount)") final FrameStackPopNNode popNNode,
                    @Cached("create(code)") final FrameStackPopNode popReceiverNode,
                    @Cached final SqueakObjectClassNode classNode,
                    @Cached final LookupMethodNode lookupNode,
                    @Cached("createBinaryProfile()") final ConditionProfile isDoesNotUnderstandProfile,
                    @Cached("create(code)") final DispatchEagerlyNode dispatchNode) {
        final Object[] arguments = popNNode.execute(frame);
        final Object receiver = popReceiverNode.execute(frame);
        final ClassObject targetClass = classNode.executeLookup(targetObject);
        final Object newLookupResult = lookupNode.executeLookup(targetClass, code.image.runWithInSelector);
        if (isDoesNotUnderstandProfile.profile(newLookupResult == null)) {
            final Object doesNotUnderstandMethod = lookupNode.executeLookup(targetClass, code.image.doesNotUnderstand);
            return dispatchNode.executeDispatch(frame, (CompiledMethodObject) doesNotUnderstandMethod, new Object[]{targetObject, code.image.newMessage(selector, targetClass, arguments)});
        } else {
            return dispatchNode.executeDispatch(frame, (CompiledMethodObject) newLookupResult, new Object[]{targetObject, selector, code.image.asArrayOfObjects(arguments), receiver});
        }
    }
}
