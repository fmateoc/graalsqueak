package de.hpi.swa.trufflesqueak.model;

import de.hpi.swa.trufflesqueak.SqueakImageContext;

public class ImmediateCharacter extends ImmutableObject {
    private final int value;

    public ImmediateCharacter(SqueakImageContext img, int i) {
        super(img);
        value = i;
    }

    @Override
    public String toString() {
        return new String(new byte[]{(byte) getValue()});
    }

    @Override
    public BaseSqueakObject getSqClass() {
        return getImage().characterClass;
    }

    @Override
    public BaseSqueakObject at0(int idx) {
        if (idx == 0) {
            return getImage().wrapInt(getValue());
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public int unwrapInt() {
        return getValue();
    }

    public int getValue() {
        return value;
    }
}
