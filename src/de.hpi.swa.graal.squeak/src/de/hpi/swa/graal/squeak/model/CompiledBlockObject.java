package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.nodes.SqueakGuards;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectLibrary;

@ExportLibrary(SqueakObjectLibrary.class)
public final class CompiledBlockObject extends CompiledCodeObject {
    private final CompiledMethodObject outerMethod;
    private final int offset;

    private CompiledBlockObject(final CompiledCodeObject code, final CompiledMethodObject outerMethod, final int numArgs, final int numCopied, final int bytecodeOffset, final int blockSize) {
        super(code.image, 0, numCopied);
        this.outerMethod = outerMethod;
        final int additionalOffset = code instanceof CompiledBlockObject ? ((CompiledBlockObject) code).getOffset() : 0;
        offset = additionalOffset + bytecodeOffset;
        final Object[] outerLiterals = outerMethod.getLiterals();
        final int outerLiteralsLength = outerLiterals.length;
        literals = new Object[outerLiteralsLength + 1];
        literals[0] = makeHeader(numArgs, numCopied, code.numLiterals, false, outerMethod.needsLargeFrame);
        System.arraycopy(outerLiterals, 1, literals, 1, outerLiteralsLength - 1);
        literals[outerLiteralsLength] = outerMethod; // Last literal is back pointer to method.
        bytes = Arrays.copyOfRange(code.getBytes(), bytecodeOffset, bytecodeOffset + blockSize);
        decodeHeader();
    }

    private CompiledBlockObject(final CompiledBlockObject original) {
        super(original);
        outerMethod = original.outerMethod;
        offset = original.offset;
    }

    public static CompiledBlockObject create(final CompiledCodeObject code, final CompiledMethodObject outerMethod, final int numArgs, final int numCopied, final int bytecodeOffset,
                    final int blockSize) {
        return new CompiledBlockObject(code, outerMethod, numArgs, numCopied, bytecodeOffset, blockSize);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        String className = "UnknownClass";
        String selector = "unknownSelector";
        final ClassObject methodClass = outerMethod.getMethodClass();
        if (methodClass != null) {
            className = methodClass.getClassName();
        }
        final NativeObject selectorObj = outerMethod.getCompiledInSelector();
        if (selectorObj != null) {
            selector = selectorObj.asStringUnsafe();
        }
        return className + ">>" + selector;
    }

    public CompiledMethodObject getMethod() {
        return outerMethod;
    }

    public int getInitialPC() {
        return outerMethod.getInitialPC() + getOffset();
    }

    public int getOffset() {
        return offset;
    }

    @ExportMessage
    public Object at0(final int index) {
        if (index < getBytecodeOffset() - getOffset()) {
            assert index % image.flags.wordSize() == 0;
            return literals[index / image.flags.wordSize()];
        } else {
            return getMethod().at0(index);
        }
    }

    @ExportMessage
    public void atput0(final int index, final Object obj) {
        atput0Shared(index, obj);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public int instsize() {
        return 0;
    }

    @ImportStatic(SqueakGuards.class)
    @ExportMessage
    public static class ReplaceFromToWithStartingAt {
        @Specialization(guards = "inBounds(rcvr.instsize(), rcvr.size(), start, stop, repl.instsize(), repl.size(), replStart)")
        protected static final boolean doBlock(final CompiledBlockObject rcvr, final int start, final int stop, final CompiledBlockObject repl, final int replStart) {
            final int repOff = replStart - start;
            for (int i = start - 1; i < stop; i++) {
                rcvr.atput0(i, repl.at0(repOff + i));
            }
            return true;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected static final boolean doFail(final CompiledBlockObject rcvr, final int start, final int stop, final Object repl, final int replStart) {
            return false;
        }
    }

    @ExportMessage
    public int size() {
        return outerMethod.size();
    }

    @ExportMessage
    public CompiledBlockObject shallowCopy() {
        return new CompiledBlockObject(this);
    }
}
