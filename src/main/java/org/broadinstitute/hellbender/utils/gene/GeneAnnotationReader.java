package org.broadinstitute.hellbender.utils.gene;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.OverlapDetector;

import java.io.File;

/**
 * Load gene annotations into an OverlapDetector of Gene objects.
 * Currently only refFlat format is accepted.
 */
public final class GeneAnnotationReader {
    public static OverlapDetector<Gene> loadRefFlat(final File refFlatFile, final SAMSequenceDictionary sequenceDictionary) {
        return RefFlatReader.load(refFlatFile, sequenceDictionary);
    }
}
