
package org.broadinstitute.hellbender.tools.walkers.genotyper;

import htsjdk.samtools.*;
import org.broadinstitute.hellbender.engine.AlignmentContext;
import org.broadinstitute.hellbender.engine.ReferenceContext;
import org.broadinstitute.hellbender.engine.ReferenceDataSource;
import org.broadinstitute.hellbender.utils.*;
import org.broadinstitute.hellbender.utils.pileup.PileupElement;
import org.broadinstitute.hellbender.utils.pileup.ReadPileup;
import org.broadinstitute.hellbender.utils.read.ArtificialReadUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.reference.ReferenceBases;

import java.util.*;


public final class ArtificialReadPileupTestProvider {
    final String refBases = "ACAGAGCTGACCCTCCCTCCCCTCTCCCAGTGCAACAGCACGGGCGGCGACTGCTTTTACCGAGGCTACACGTCAGGCGTGGCGGCTGTCCAGGACTGGTACCACTTCCACTATGTGGATCTCTGCTGAGGACCAGGAAAGCCAGCACCCGCAGAGACTCTTCCCCAGTGCTCCATACGATCACCATTCTCTGCAGAAGGTCAGACGTCACTGGTGGCCCCCCAGCCTCCTCAGCAGGGAAGGATACTGTCCCGCAGATGAGATGAGCGAGAGCCGCCAGACCCACGTGACGCTGCACGACATCGACCCTCAGGCCTTGGACCAGCTGGTGCAGTTTGCCTACACGGCTGAGATTGTGGTGGGCGAGGGC";
    final int contigStart = 1;
    final int contigStop = refBases.length();
    final SAMFileHeader header = ArtificialReadUtils.createArtificialSamHeader(1, 1, contigStop - contigStart + 1);
    final String artificialContig = "1";
    final String artificialReadName = "synth";
    final int artificialRefStart = 1;
    final int artificialMappingQuality = 60;
    final Map<String, SAMReadGroupRecord> sample2RG = new LinkedHashMap<>();
    final List<SAMReadGroupRecord> sampleRGs;
    final List<String> sampleNames = new ArrayList<>();
    private String sampleName(final int i) { return sampleNames.get(i); }
    private SAMReadGroupRecord sampleRG(final String name) { return sample2RG.get(name); }
    public final int locStart = 105; // start position where we desire artificial variant
    private final int readLength = 10; // desired read length in pileup
    public final int readOffset = 4;
    private final int readStart = locStart - readOffset;
    public final GenomeLocParser genomeLocParser = new GenomeLocParser(header.getSequenceDictionary());
    public final GenomeLoc loc = genomeLocParser.createGenomeLoc(artificialContig,locStart,locStart);

    private static final int lead = 100;
    private static final int trail = 100;
    public final GenomeLoc window = genomeLocParser.createGenomeLoc(artificialContig,locStart-lead,locStart+trail);
    public final String windowBases = refBases.substring(locStart - lead - 1, locStart + trail);

    final SimpleInterval interval1 = new SimpleInterval(artificialContig, 1, windowBases.length());
    final ReferenceBases ref = new ReferenceBases(windowBases.getBytes(), interval1);

    final SAMSequenceDictionary dict = new SAMSequenceDictionary(Arrays.asList(new SAMSequenceRecord(artificialContig, windowBases.length())));
    public final ReferenceContext referenceContext = new ReferenceContext(ReferenceDataSource.of(ref, dict), new SimpleInterval(loc), lead, trail);

    byte BASE_QUAL = 50;

    public ArtificialReadPileupTestProvider(final int numSamples, final String SAMPLE_PREFIX) {
        sampleRGs = new ArrayList<>();

        for ( int i = 0; i < numSamples; i++ ) {
            sampleNames.add(String.format("%s%04d", SAMPLE_PREFIX, i));
            final SAMReadGroupRecord rg = createRG(sampleName(i));
            sampleRGs.add(rg);
            sample2RG.put(sampleName(i), rg);
        }

    }

    public ArtificialReadPileupTestProvider(final int numSamples, final String SAMPLE_PREFIX, final byte q) {
        this(numSamples,SAMPLE_PREFIX);
        BASE_QUAL = q;
    }
    public List<String> getSampleNames() {
        return sampleNames;
    }
    public byte getRefByte() {
        return referenceContext.getBase();
    }

    public ReferenceContext getReferenceContext()   { return referenceContext;}
    public GenomeLocParser getGenomeLocParser()     { return genomeLocParser; }

    public Map<String,AlignmentContext> getAlignmentContextFromAlleles(final int eventLength, final String altBases, final int[] numReadsPerAllele) {
        return getAlignmentContextFromAlleles(eventLength, altBases, numReadsPerAllele, false, BASE_QUAL);
    }
    public Map<String,AlignmentContext> getAlignmentContextFromAlleles(final int eventLength,
                                                                       final String altBases,
                                                                       final int[] numReadsPerAllele,
                                                                       final boolean addBaseErrors,
                                                                       final int phredScaledBaseErrorRate) {
        final String refChar = new String(new byte[]{referenceContext.getBase()});

        final String refAllele;
        String altAllele;
        if (eventLength == 0)  {
            // SNP case
            refAllele = refChar;
            altAllele = altBases.substring(0,1);

        } else if (eventLength>0){
            // insertion
            refAllele = refChar;
            altAllele = refChar+altBases/*.substring(0,eventLength)*/;
        }
        else {
            // deletion
            refAllele = new String(referenceContext.getForwardBases()).substring(0, Math.abs(eventLength)+1);
            altAllele = refChar;
        }

        final Map<String,AlignmentContext> contexts = new LinkedHashMap<>();

        for (final String sample: sampleNames) {
            final AlignmentContext context = new AlignmentContext(loc, generateRBPForVariant(loc, refAllele, altAllele, altBases, numReadsPerAllele, sample, addBaseErrors, phredScaledBaseErrorRate));
            contexts.put(sample,context);

        }

        return contexts;
    }

    private SAMReadGroupRecord createRG(final String name) {
        final SAMReadGroupRecord rg = new SAMReadGroupRecord(name);
        rg.setPlatform("ILLUMINA");
        rg.setSample(name);
        return rg;
    }

    private ReadPileup generateRBPForVariant(final GenomeLoc loc, final String refAllele, final String altAllele, final String altBases,
                                             final int[] numReadsPerAllele, final String sample, final boolean addErrors, final int phredScaledErrorRate) {
        final List<PileupElement> pileupElements = new ArrayList<>();
        final int refAlleleLength = refAllele.length();

        pileupElements.addAll(createPileupElements(refAllele, loc, numReadsPerAllele[0], sample, readStart, altBases, addErrors, phredScaledErrorRate, refAlleleLength, true));
        pileupElements.addAll(createPileupElements(altAllele, loc, numReadsPerAllele[1], sample, readStart, altBases, addErrors, phredScaledErrorRate, refAlleleLength, false));
        return new ReadPileup(loc,pileupElements);
    }

    private List<PileupElement> createPileupElements(final String allele, final GenomeLoc loc, final int numReadsPerAllele, final String sample, final int readStart, final String altBases, final boolean addErrors, final int phredScaledErrorRate, final int refAlleleLength, final boolean isReference) {

        final int alleleLength = allele.length();
        final List<PileupElement> pileupElements = new ArrayList<>();

        int readCounter = 0;
        for ( int d = 0; d < numReadsPerAllele; d++ ) {
            final byte[] readBases = trueHaplotype(allele, refAlleleLength, readLength);
            if (addErrors)
                addBaseErrors(readBases, phredScaledErrorRate);

            final byte[] readQuals = new byte[readBases.length];
            Arrays.fill(readQuals, (byte) phredScaledErrorRate);

            final GATKRead read = ArtificialReadUtils.createArtificialRead(TextCigarCodec.decode(SAMRecord.NO_ALIGNMENT_CIGAR));
            read.setBaseQualities(readQuals);
            read.setBases(readBases);
            read.setName(artificialReadName+readCounter++);

            final boolean isBeforeDeletion = alleleLength<refAlleleLength;
            final boolean isBeforeInsertion = alleleLength>refAlleleLength;

            final int eventLength = alleleLength - refAlleleLength;
            if (isReference)
                read.setCigar(readBases.length + "M");
            else {
                if (isBeforeDeletion || isBeforeInsertion)
                    read.setCigar((readOffset+1)+"M"+ Math.abs(eventLength) + (isBeforeDeletion?"D":"I") +
                            (readBases.length-readOffset)+"M");
                else // SNP case
                    read.setCigar(readBases.length+"M");
            }

            read.setIsPaired(false);
            read.setPosition(loc.getContig(), readStart);
            read.setMappingQuality(artificialMappingQuality);
            read.setIsReverseStrand(false);
            read.setReadGroup(sampleRG(sample).getId());

            pileupElements.add(PileupElement.createPileupForReadAndOffset(read, readOffset));
        }

        return pileupElements;
    }

    /**
     * Create haplotype with desired allele and reference context
     * @param allele                             Desired allele string
     * @param refAlleleLength                    Length of reference allele.
     * @param desiredLength                      Desired haplotype length
     * @return                                   String with haplotype formed by (prefix)+allele bases + postfix
     */
    private byte[] trueHaplotype(final String allele, final int refAlleleLength, final int desiredLength) {
        // create haplotype based on a particular allele
        final int startIdx= locStart - readOffset-1;

        final String prefix = refBases.substring(startIdx, locStart-1);
        final String postfix = refBases.substring(locStart+refAlleleLength-1,startIdx + desiredLength);

        return (prefix+allele+postfix).getBytes();
    }

    private void addBaseErrors(final byte[] readBases, final int phredScaledErrorRate) {
        final double errorProbability = QualityUtils.qualToErrorProb((byte) phredScaledErrorRate);

        for (int k=0; k < readBases.length; k++) {
            if (Utils.getRandomGenerator().nextDouble() < errorProbability) {
                // random offset
                int offset = BaseUtils.simpleBaseToBaseIndex(readBases[k]);          //0..3
                offset += (Utils.getRandomGenerator().nextInt(3)+1);  // adds 1,2 or 3
                offset %= 4;
                readBases[k] = BaseUtils.baseIndexToSimpleBase(offset);

            }

        }

    }
}
