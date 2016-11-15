package org.broadinstitute.hellbender.utils.svd;

import org.apache.commons.math3.linear.RealMatrix;

/**
 * Simple implementation of the SVD interface for storing the matrices (and vector) of a SVD result.
 */
public final class SimpleSVD implements SVD {
    private final RealMatrix v;
    private final RealMatrix u;
    private final double [] singularValues;
    private final RealMatrix pinv;

    public SimpleSVD(final RealMatrix u, final double [] singularValues, final RealMatrix v, final RealMatrix pinv) {
        this.v = v;
        this.u = u;
        this.singularValues = singularValues;
        this.pinv = pinv;
    }

    @Override
    public RealMatrix getV() {
        return v;
    }

    @Override
    public RealMatrix getU() {
        return u;
    }

    @Override
    public double[] getSingularValues() {
        return singularValues;
    }

    @Override
    public RealMatrix getPinv() {
        return pinv;
    }
}
