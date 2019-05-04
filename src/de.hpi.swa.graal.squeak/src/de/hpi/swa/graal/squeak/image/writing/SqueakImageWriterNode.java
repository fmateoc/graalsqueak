package de.hpi.swa.graal.squeak.image.writing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.AbstractCollection;
import java.util.EnumSet;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakAbortException;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageReaderNode;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithImage;
import de.hpi.swa.graal.squeak.nodes.context.ObjectGraphNode;
import de.hpi.swa.graal.squeak.util.MiscUtils;

public final class SqueakImageWriterNode extends AbstractNodeWithImage {
    @CompilationFinal(dimensions = 1) private static final int[] CHUNK_HEADER_BIT_PATTERN = new int[]{22, 2, 5, 3, 22, 2, 8};
    public static final Object NIL_OBJECT_PLACEHOLDER = new Object();
    public static final long IMAGE_32BIT_VERSION = 6521;
    public static final long IMAGE_64BIT_VERSION = 68021;
    private static final int FREE_OBJECT_CLASS_INDEX_PUN = 0;
    private static final long SLOTS_MASK = 0xFF << 56;
    private static final long OVERFLOW_SLOTS = 255;
    private static final int HIDDEN_ROOTS_CHUNK_INDEX = 4;

    private static final int WORD_SIZE = 8;
    private static final int IMAGE_HEADER_SIZE = WORD_SIZE * 16;
    @CompilationFinal protected SqueakImageChunk hiddenRootsChunk;

    @Child private ObjectGraphNode graphNode;
    @Child private WriteSqueakObjectNode writeObjectNode;

    private final SeekableByteChannel stream;
    private final HashMap<Long, SqueakImageChunk> chunktable = new HashMap<>(750000);

    private SqueakImageWriterNode(final SqueakImageContext image) {
        super(image);
        final TruffleFile truffleFile = image.env.getTruffleFile("/Users/fniephaus/dev/graal/graalsqueak/images/snapshot.image");
        try {
            final EnumSet<StandardOpenOption> options = EnumSet.<StandardOpenOption> of(StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.CREATE);
            stream = truffleFile.newByteChannel(options);
        } catch (final IOException e) {
            e.printStackTrace();
            throw SqueakException.illegalState(e);
        }
        graphNode = ObjectGraphNode.create(image);
        writeObjectNode = WriteSqueakObjectNode.create(image, stream);
    }

    public static SqueakImageWriterNode create(final SqueakImageContext image) {
        // TODO Auto-generated method stub
        return new SqueakImageWriterNode(image);
    }

    public void execute(final VirtualFrame frame) {
        final long start = MiscUtils.currentTimeMillis();
        writeBody(frame);
        writeFileHeader();
        close();
        clearChunktable();
        image.printToStdOut("Image stored in", MiscUtils.currentTimeMillis() - start + "ms.");
    }

    private void writeFileHeader() {
        final long nextChunk = 0;
        final long specialObjectOop = 0;
        final long displaySize = 0;
        final long hdrFlags = 0 + // 0/1 fullscreen or not
                        0b10 + // 0/2 imageFloatsLittleEndian or not
                        0x10 + // preemption does not yield
                        0; // old finalization;

        position(0);
        writeWord(SqueakImageReaderNode.IMAGE_64BIT_VERSION);
        writeWord(IMAGE_HEADER_SIZE); // hdr size
        writeWord(nextChunk - IMAGE_HEADER_SIZE); // memory size
        writeWord(IMAGE_HEADER_SIZE); // start of memory
        writeWord(specialObjectOop);
        writeWord(0xffee); // last hash
        writeWord(displaySize);
        writeWord(hdrFlags);
        writeWord(0); // extra VM memory
        writeWord(0); // (num stack pages << 16) | cog code size
        writeWord(0); // eden bytes
        writeWord(0); // max ext semaphore size << 16
        writeWord(nextChunk - IMAGE_HEADER_SIZE); // first segment size
        writeWord(0); // free old space in image
        writeWord(0); // padding
        writeWord(0); // padding
    }

    private void writeBody(final VirtualFrame frame) {
        final AbstractCollection<AbstractSqueakObject> allInstances = graphNode.executeAllInstances();

    }

    private void clearChunktable() {
        // TODO Auto-generated method stub

    }

    private void writeBytes(final byte[] bytes) {
        try {
            stream.write(ByteBuffer.wrap(bytes));
        } catch (final IOException e) {
            throw SqueakAbortException.create("Unable to write bytes:", e.getMessage());
        }
    }

    private void writeShort(final short value) {
        writeBytes(new byte[]{(byte) value, (byte) (value >> 8)});
    }

    private void writeInt(final int value) {
        writeBytes(new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)});
    }

    private void writeLong(final long value) {
        writeBytes(new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24),
                        (byte) (value >> 32), (byte) (value >> 40), (byte) (value >> 48), (byte) (value >> 56)});
    }

    private void writeWord(final long value) {
        writeLong(value);
    }

    private void position(final int newPosition) {
        try {
            stream.position(newPosition);
        } catch (final IOException e) {
            throw SqueakAbortException.create("Unable to jump to", newPosition, ", error:", e.getMessage());
        }
    }

    private void close() {
        try {
            stream.close();
        } catch (final IOException e) {
            throw SqueakAbortException.create("Unable to close file:", e.getMessage());
        }
    }
}
