package org.broadinstitute.hellbender.utils.io;

import org.apache.commons.io.output.ThresholdingOutputStream;

import java.io.IOException;

/**
 * An output stream which stops at the threshold
 * instead of potentially triggering early.
 */
public abstract class HardThresholdingOutputStream extends ThresholdingOutputStream {
    protected HardThresholdingOutputStream(final int threshold) {
        super(threshold);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        final int remaining = this.getThreshold() - (int)this.getByteCount();
        if (!isThresholdExceeded() && len > remaining) {
            super.write(b, off, remaining);
            super.write(b, off + remaining, len - remaining);
        } else {
            super.write(b, off, len);
        }
    }
}
