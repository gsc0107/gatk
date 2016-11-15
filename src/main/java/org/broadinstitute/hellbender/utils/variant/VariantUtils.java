package org.broadinstitute.hellbender.utils.variant;

/**
 * VariantUtils contains utility methods for the Variant interface.
 **/
public class VariantUtils {
    public static boolean variantsAreEqual(final GATKVariant v1, final GATKVariant v2) {
        if (v1.getStart() != v2.getStart()) {
            return false;
        }
        if (v1.getEnd() != v2.getEnd()) {
            return false;
        }
        if (v1.getContig() == null || v2.getContig() == null) {
            return false;
        }
        if (!v1.getContig().equals(v2.getContig())) {
            return false;
        }
        if (v1.isSnp() != v2.isSnp()) {
            return false;
        }
        return v1.isIndel() == v2.isIndel();
    }
}
