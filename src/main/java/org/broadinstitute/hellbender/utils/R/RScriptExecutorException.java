package org.broadinstitute.hellbender.utils.R;

import org.broadinstitute.hellbender.exceptions.GATKException;

public final class RScriptExecutorException extends GATKException {
    private static final long serialVersionUID = 0L;

    public RScriptExecutorException(final String msg) {
        super(msg);
    }
}
