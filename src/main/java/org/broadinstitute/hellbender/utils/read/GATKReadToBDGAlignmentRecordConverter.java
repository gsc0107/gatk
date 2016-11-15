package org.broadinstitute.hellbender.utils.read;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import org.bdgenomics.formats.avro.AlignmentRecord;
import org.bdgenomics.adam.converters.SAMRecordConverter;
import org.bdgenomics.adam.models.SequenceDictionary;
import org.bdgenomics.adam.models.RecordGroupDictionary;

/**
 * Converts a GATKRead to a BDG AlignmentRecord
 */
public class GATKReadToBDGAlignmentRecordConverter {
    private static final SAMRecordConverter converter = new SAMRecordConverter();

    private final SAMFileHeader header;
    private final SequenceDictionary dict;
    private final RecordGroupDictionary readGroups;

    public GATKReadToBDGAlignmentRecordConverter(final SAMFileHeader header) {
        this.header = header;
        this.dict = SequenceDictionary.fromSAMSequenceDictionary(header.getSequenceDictionary());
        this.readGroups = RecordGroupDictionary.fromSAMHeader(header);
    }

    public static AlignmentRecord convert( final GATKRead gatkRead, final SAMFileHeader header ) {
        final SequenceDictionary dict = SequenceDictionary.fromSAMSequenceDictionary(header.getSequenceDictionary());
        final RecordGroupDictionary readGroups = RecordGroupDictionary.fromSAMHeader(header);
        return GATKReadToBDGAlignmentRecordConverter.convert(gatkRead, header, dict, readGroups);
    }

    public static AlignmentRecord convert(
            final GATKRead gatkRead, final SAMFileHeader header, final SequenceDictionary dict, final RecordGroupDictionary readGroups ) {
        return converter.convert(gatkRead.convertToSAMRecord(header));
    }

    public static AlignmentRecord convert(
            final SAMRecord sam, final SequenceDictionary dict, final RecordGroupDictionary readGroups ) {
        return converter.convert(sam);
    }
}
