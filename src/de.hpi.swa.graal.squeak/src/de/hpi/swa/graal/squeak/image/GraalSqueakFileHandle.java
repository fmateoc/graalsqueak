package de.hpi.swa.graal.squeak.image;

import java.nio.channels.SeekableByteChannel;

import com.oracle.truffle.api.TruffleFile;

public class GraalSqueakFileHandle {
    private TruffleFile truffleFile;
    private SeekableByteChannel byteChannel;

    public GraalSqueakFileHandle(final TruffleFile p, final SeekableByteChannel f) {
        setTruffleFile(p);
        setFile(f);
    }

    public SeekableByteChannel getFile() {
        return byteChannel;
    }

    public void setFile(final SeekableByteChannel file) {
        this.byteChannel = file;
    }

    public TruffleFile getTruffleFile() {
        return truffleFile;
    }

    public void setTruffleFile(final TruffleFile path) {
        this.truffleFile = path;
    }

}
