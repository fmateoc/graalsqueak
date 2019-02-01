package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;

public final class CompiledBlockObject extends CompiledCodeObject {
    private final CompiledMethodObject outerMethod;

    private CompiledBlockObject(final CompiledCodeObject code, final CompiledMethodObject outerMethod, final int numArgs, final int numCopied, final int bytecodeOffset, final int blockSize) {
        super(code.image, 0, numCopied);
        this.outerMethod = outerMethod;
        final Object[] outerLiterals = outerMethod.getLiterals();
        final int outerLiteralsLength = outerLiterals.length;
        this.literals = new Object[outerLiteralsLength + 1];
        this.literals[0] = makeHeader(numArgs, numCopied, code.numLiterals, false, outerMethod.needsLargeFrame);
        System.arraycopy(outerLiterals, 1, this.literals, 1, outerLiteralsLength - 1);
        this.literals[outerLiteralsLength] = outerMethod; // Last literal is back pointer to method.
        this.bytes = Arrays.copyOfRange(code.getBytes(), bytecodeOffset, (bytecodeOffset + blockSize));
        decodeHeader();
        initialPC = code.getInitialPC() + bytecodeOffset;
    }

    private CompiledBlockObject(final CompiledBlockObject original) {
        super(original);
        outerMethod = original.outerMethod;
    }

    public static CompiledBlockObject create(final CompiledCodeObject code, final CompiledMethodObject outerMethod, final int numArgs, final int numCopied, final int bytecodeOffset,
                    final int blockSize) {
        return new CompiledBlockObject(code, outerMethod, numArgs, numCopied, bytecodeOffset, blockSize);
    }

    public Object at0(final long longIndex) {
        final int index = (int) longIndex;
        if (index < getInitialPC()) {
            assert index % image.flags.wordSize() == 0;
            return literals[index / image.flags.wordSize()];
        } else {
            return getMethod().at0(longIndex);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        String className = "UnknownClass";
        String selector = "unknownSelector";
        final ClassObject classObject = getCompiledInClass();
        if (classObject != null) {
            className = classObject.nameAsClass();
        }
        final NativeObject selectorObj = getCompiledInSelector();
        if (selectorObj != null) {
            selector = selectorObj.asString();
        }
        return className + ">>" + selector;
    }

    public NativeObject getCompiledInSelector() {
        return outerMethod.getCompiledInSelector();
    }

    public ClassObject getCompiledInClass() {
        return outerMethod.getCompiledInClass();
    }

    public CompiledMethodObject getMethod() {
        return outerMethod;
    }

    public AbstractSqueakObject shallowCopy() {
        return new CompiledBlockObject(this);
    }

    public int size() {
        return outerMethod.size();
    }
}
