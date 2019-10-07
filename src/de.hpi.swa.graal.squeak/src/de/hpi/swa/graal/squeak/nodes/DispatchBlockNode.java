/*
 * Copyright (c) 2017-2019 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@NodeInfo(cost = NodeCost.NONE)
public abstract class DispatchBlockNode extends AbstractNode {

    public final Object executeClosure0(final BlockClosureObject closure, final CompiledBlockObject block, final Object contextOrMarker) {
        return executeClosure(closure, FrameAccess.newClosureArgumentsTemplate(closure, block, contextOrMarker, 0));
    }

    public final Object executeClosure1(final BlockClosureObject closure, final CompiledBlockObject block, final Object contextOrMarker, final Object arg) {
        final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, block, contextOrMarker, 1);
        frameArguments[FrameAccess.getArgumentStartIndex()] = arg;
        return executeClosure(closure, frameArguments);
    }

    public final Object executeClosure2(final BlockClosureObject closure, final CompiledBlockObject block, final Object contextOrMarker, final Object arg1, final Object arg2) {
        final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, block, contextOrMarker, 2);
        frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
        frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
        return executeClosure(closure, frameArguments);
    }

    public final Object executeClosure3(final BlockClosureObject closure, final CompiledBlockObject block, final Object contextOrMarker, final Object arg1, final Object arg2, final Object arg3) {
        final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, block, contextOrMarker, 3);
        frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
        frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
        frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
        return executeClosure(closure, frameArguments);
    }

    public final Object executeClosure4(final BlockClosureObject closure, final CompiledBlockObject block, final Object contextOrMarker, final Object arg1, final Object arg2, final Object arg3,
                    final Object arg4) {
        final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, block, contextOrMarker, 4);
        frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
        frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
        frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
        frameArguments[FrameAccess.getArgumentStartIndex() + 3] = arg4;
        return executeClosure(closure, frameArguments);
    }

    public final Object executeClosure5(final BlockClosureObject closure, final CompiledBlockObject block, final Object contextOrMarker, final Object arg1, final Object arg2, final Object arg3,
                    final Object arg4, final Object arg5) {
        final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, block, contextOrMarker, 5);
        frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
        frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
        frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
        frameArguments[FrameAccess.getArgumentStartIndex() + 3] = arg4;
        frameArguments[FrameAccess.getArgumentStartIndex() + 4] = arg5;
        return executeClosure(closure, frameArguments);
    }

    public final Object executeClosureN(final BlockClosureObject closure, final CompiledBlockObject block, final Object contextOrMarker, final Object[] arguments) {
        return executeClosure(closure, FrameAccess.newClosureArguments(closure, block, contextOrMarker, arguments));
    }

    protected abstract Object executeClosure(BlockClosureObject closure, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(guards = {"closure.getCompiledBlock() == cachedClosure"}, assumptions = {"cachedClosure.getCallTargetStable()"})
    protected static final Object doDirect(final BlockClosureObject closure, final Object[] arguments,
                    @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedClosure,
                    @Cached("create(cachedClosure.getCallTarget())") final DirectCallNode directCallNode) {
        return directCallNode.call(arguments);
    }

    @Specialization(replaces = "doDirect")
    protected static final Object doIndirect(final BlockClosureObject closure, final Object[] arguments,
                    @Cached final IndirectCallNode indirectCallNode) {
        return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), arguments);
    }
}
