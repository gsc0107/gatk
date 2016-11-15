package org.broadinstitute.hellbender.tools;

import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.broadinstitute.hellbender.cmdline.Argument;
import org.broadinstitute.hellbender.cmdline.CommandLineProgramProperties;
import org.broadinstitute.hellbender.cmdline.StandardArgumentDefinitions;
import org.broadinstitute.hellbender.cmdline.programgroups.ReadProgramGroup;
import org.broadinstitute.hellbender.engine.FeatureContext;
import org.broadinstitute.hellbender.engine.ReadWalker;
import org.broadinstitute.hellbender.engine.ReferenceContext;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.BaseUtils;
import org.broadinstitute.hellbender.utils.clipping.ClippingOp;
import org.broadinstitute.hellbender.utils.clipping.ClippingRepresentation;
import org.broadinstitute.hellbender.utils.clipping.ReadClipper;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.SAMFileGATKReadWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read clipping based on quality, position or sequence matching
 *
 * <p>This tool provides simple, powerful read clipping capabilities that allow you to remove low quality strings of bases, sections of reads, and reads containing user-provided sequences.</p> 
 *
 * <p>There are three arguments for clipping (quality, position and sequence), which can be used alone or in combination. In addition, you can also specify a clipping representation, which determines exactly how ClipReads applies clips to the reads (soft clips, writing Q0 base quality scores, etc.). Please note that you MUST specify at least one of the three clipping arguments, and specifying a clipping representation is not sufficient. If you do not specify a clipping argument, the program will run but it will not do anything to your reads.</p>
 *
 * <dl>
 *     <dt>Quality score based clipping</dt>
 *     <dd>
 *         Clip bases from the read in clipper from
 *         <pre>argmax_x{ \sum{i = x + 1}^l (qTrimmingThreshold - qual)</pre>
 *         to the end of the read.  This is copied from BWA.
 *
 *         Walk through the read from the end (in machine cycle order) to the beginning, calculating the
 *         running sum of qTrimmingThreshold - qual.  While we do this, we track the maximum value of this
 *         sum where the delta > 0.  After the loop, clipPoint is either -1 (don't do anything) or the
 *         clipping index in the read (from the end).
 *     </dd><br />
 *     <dt>Cycle based clipping</dt>
 *     <dd>Clips machine cycles from the read. Accepts a string of ranges of the form start1-end1,start2-end2, etc.
 *     For each start/end pair, removes bases in machine cycles from start to end, inclusive. These are 1-based values (positions).
 *     For example, 1-5,10-12 clips the first 5 bases, and then three bases at cycles 10, 11, and 12.
 *     </dd><br />
 *     <dt>Sequence matching</dt>
 *     <dd>Clips bases from that exactly match one of a number of base sequences. This employs an exact match algorithm,
 *     filtering only bases whose sequence exactly matches SEQ.</dd>
 * </dl>
 *
 *
 * <h3>Input</h3>
 * <p>
 *     Any number of SAM/BAM/CRAM files.
 * </p>
 *
 * <h3>Output</h3>
 * <p>
 *     A new SAM/BAM/CRAM file containing all of the reads from the input SAM/BAM/CRAMs with the user-specified clipping
 *     operation applied to each read.
 * </p>
 * <p>
 *     <h4>Summary output (console)</h4>
 *     <pre>
 *     Number of examined reads              13
 *     Number of clipped reads               13
 *     Percent of clipped reads              100.00
 *     Number of examined bases              988
 *     Number of clipped bases               126
 *     Percent of clipped bases              12.75
 *     Number of quality-score clipped bases 126
 *     Number of range clipped bases         0
 *     Number of sequence clipped bases      0
 *     </pre>
 * </p>
 *
 * <h3>Example</h3>
 * <pre>
 *   gatk-launch \
 *     ClipReads \
 *     -R reference.fasta \
 *     -I original.bam \
 *     -O clipped.bam \
 *     -XF seqsToClip.fasta \
 *     -X CCCCC \
 *     -CT "1-5,11-15" \
 *     -QT 10
 * </pre>
 * <p>The command line shown above will apply all three arguments in combination. See the detailed examples below to see how the choice of clipping representation affects the output.</p>
 *
 *     <h4>Detailed clipping examples</h4>
 *     <p>Suppose we are given this read:</p>
 *     <pre>
 *     314KGAAXX090507:1:19:1420:1123#0        16      chrM    3116    29      76M     *       *       *
 *          TAGGACCCGGGCCCCCCTCCCCAATCCTCCAACGCATATAGCGGCCGCGCCTTCCCCCGTAAATGATATCATCTCA
 *          #################4?6/?2135;;;'1/=/<'B9;12;68?A79@,@==@9?=AAA3;A@B;A?B54;?ABA
 *     </pre>
 *
 *     <p>If we are clipping reads with -QT 10 and -CR WRITE_NS, we get:</p>
 *
 *     <pre>
 *     314KGAAXX090507:1:19:1420:1123#0        16      chrM    3116    29      76M     *       *       *
 *          NNNNNNNNNNNNNNNNNTCCCCAATCCTCCAACGCATATAGCGGCCGCGCCTTCCCCCGTAAATGATATCATCTCA
 *          #################4?6/?2135;;;'1/=/<'B9;12;68?A79@,@==@9?=AAA3;A@B;A?B54;?ABA
 *     </pre>
 *
 *     <p>Whereas with -QT 10 -CR WRITE_Q0S:</p>
 *     <pre>
 *     314KGAAXX090507:1:19:1420:1123#0        16      chrM    3116    29      76M     *       *       *
 *          TAGGACCCGGGCCCCCCTCCCCAATCCTCCAACGCATATAGCGGCCGCGCCTTCCCCCGTAAATGATATCATCTCA
 *          !!!!!!!!!!!!!!!!!4?6/?2135;;;'1/=/<'B9;12;68?A79@,@==@9?=AAA3;A@B;A?B54;?ABA
 *     </pre>
 *
 *     <p>Or -QT 10 -CR SOFTCLIP_BASES:</p>
 *     <pre>
 *     314KGAAXX090507:1:19:1420:1123#0        16      chrM    3133    29      17S59M  *       *       *
 *          TAGGACCCGGGCCCCCCTCCCCAATCCTCCAACGCATATAGCGGCCGCGCCTTCCCCCGTAAATGATATCATCTCA
 *          #################4?6/?2135;;;'1/=/<'B9;12;68?A79@,@==@9?=AAA3;A@B;A?B54;?ABA
 *     </pre>
 *
 */
@CommandLineProgramProperties(
        summary = "Read clipping based on quality, position or sequence matching.",
        oneLineSummary = "Clip reads in a SAM/BAM/CRAM file",
        programGroup = ReadProgramGroup.class
)
public final class ClipReads extends ReadWalker {

    private final Logger logger = LogManager.getLogger(ClipReads.class);

    /**
     * The output SAM/BAM/CRAM file will be written here
     */
    @Argument(doc = "BAM output file", shortName = StandardArgumentDefinitions.OUTPUT_SHORT_NAME, fullName = StandardArgumentDefinitions.OUTPUT_LONG_NAME)
    File OUTPUT;

    /**
     * If provided, ClipReads will write summary statistics about the clipping operations applied to the reads in this file.
     */
    @Argument(fullName = "outputStatistics", shortName = "os", doc = "File to output statistics", optional = true)
    File STATSOUTPUT;

    /**
     * If a value > 0 is provided, then the quality score based read clipper will be applied to the reads using this
     * quality score threshold.
     */
    @Argument(fullName = "qTrimmingThreshold", shortName = "QT", doc = "If provided, the Q-score clipper will be applied", optional = true)
    final
    int qTrimmingThreshold = -1;

    /**
     * Clips machine cycles from the read. Accepts a string of ranges of the form start1-end1,start2-end2, etc.
     * For each start/end pair, removes bases in machine cycles from start to end, inclusive. These are 1-based
     * values (positions). For example, 1-5,10-12 clips the first 5 bases, and then three bases at cycles 10, 11,
     * and 12.
     */
    @Argument(fullName = "cyclesToTrim", shortName = "CT", doc = "String indicating machine cycles to clip from the reads", optional = true)
    String cyclesToClipArg;

    /**
     * Reads the sequences in the provided FASTA file, and clip any bases that exactly match any of the
     * sequences in the file.
     */
    @Argument(fullName = "clipSequencesFile", shortName = "XF", doc = "Remove sequences within reads matching the sequences in this FASTA file", optional = true)
    String clipSequenceFile;

    /**
     * Clips bases from the reads matching the provided SEQ.
     */
    @Argument(fullName = "clipSequence", shortName = "X", doc = "Remove sequences within reads matching this sequence", optional = true)
    List<String> clipSequencesArgs;

    /**
     * The different values for this argument determines how ClipReads applies clips to the reads.  This can range
     * from writing Ns over the clipped bases to hard clipping away the bases from the BAM.
     */
    @Argument(fullName = "clipRepresentation", shortName = "CR", doc = "How should we actually clip the bases?", optional = true)
    final
    ClippingRepresentation clippingRepresentation = ClippingRepresentation.WRITE_NS;

    @Argument(fullName="read", doc="", optional = true)
    String onlyDoRead;

    /**
     * List of sequence that should be clipped from the reads
     */
    private final List<SeqToClip> sequencesToClip = new ArrayList<>();

    /**
     * List of cycle start / stop pairs (0-based, stop is included in the cycle to remove) to clip from the reads
     */
    private List<Pair<Integer, Integer>> cyclesToClip;

    /**
     * Output reads is written to this BAM.
     */
    private SAMFileGATKReadWriter outputBam;

    /**
     * Accumulator for the stats.
     */
    private ClippingData accumulator;

    /**
     * Output stream for the stats.
     */
    private PrintStream outputStats;

    /**
     * The initialize function.
     */
    @Override
    public void onTraversalStart() {
        if (qTrimmingThreshold >= 0) {
            logger.info(String.format("Creating Q-score clipper with threshold %d", qTrimmingThreshold));
        }

        //
        // Initialize the sequences to clip
        //
        if (clipSequencesArgs != null) {
            int i = 0;
            for (final String toClip : clipSequencesArgs) {
                i++;
                final ReferenceSequence rs = new ReferenceSequence("CMDLINE-" + i, -1, StringUtil.stringToBytes(toClip));
                addSeqToClip(rs.getName(), rs.getBases());
            }
        }

        if (clipSequenceFile != null) {
            final ReferenceSequenceFile rsf = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File(clipSequenceFile));

            while (true) {
                final ReferenceSequence rs = rsf.nextSequence();
                if (rs == null)
                    break;
                else {
                    addSeqToClip(rs.getName(), rs.getBases());
                }
            }
        }


        //
        // Initialize the cycle ranges to clip
        //
        if (cyclesToClipArg != null) {
            cyclesToClip = new ArrayList<>();
            for (final String range : cyclesToClipArg.split(",")) {
                try {
                    final String[] elts = range.split("-");
                    final int start = Integer.parseInt(elts[0]) - 1;
                    final int stop = Integer.parseInt(elts[1]) - 1;

                    if (start < 0) throw new Exception();
                    if (stop < start) throw new Exception();

                    logger.info(String.format("Creating cycle clipper %d-%d", start, stop));
                    cyclesToClip.add(new MutablePair<>(start, stop));
                } catch (final Exception e) {
                    throw new RuntimeException("Badly formatted cyclesToClip argument: " + cyclesToClipArg);
                }
            }
        }

        final boolean presorted = EnumSet.of(ClippingRepresentation.WRITE_NS, ClippingRepresentation.WRITE_NS_Q0S, ClippingRepresentation.WRITE_Q0S).contains(clippingRepresentation);
        outputBam = createSAMWriter(OUTPUT, presorted);
        
        accumulator = new ClippingData(sequencesToClip);
        try {
            outputStats = STATSOUTPUT == null ? null : new PrintStream(STATSOUTPUT);
        } catch (final FileNotFoundException e) {
            throw new UserException.CouldNotCreateOutputFile(STATSOUTPUT, e);
        }
    }

    @Override
    public void apply(GATKRead read, final ReferenceContext ref, final FeatureContext featureContext ) {
        if ( onlyDoRead == null || read.getName().equals(onlyDoRead) ) {
            if ( clippingRepresentation == ClippingRepresentation.HARDCLIP_BASES || clippingRepresentation == ClippingRepresentation.REVERT_SOFTCLIPPED_BASES )
                read = ReadClipper.revertSoftClippedBases(read);
            final ReadClipperWithData clipper = new ReadClipperWithData(read, sequencesToClip);

            //
            // run all three clipping modules
            //
            clipBadQualityScores(clipper);
            clipCycles(clipper);
            clipSequences(clipper);
            accumulate(clipper);
        }
    }

    @Override
    public ClippingData onTraversalSuccess(){
        if ( outputStats != null ){
            outputStats.printf(accumulator.toString());
        }
        return accumulator;
    }

    @Override
    public void closeTool() {
        if ( outputStats != null ){
            outputStats.close();
        }
        if ( outputBam != null ) {
            outputBam.close();
        }
    }

    /**
     * Helper function that adds a seq with name and bases (as bytes) to the list of sequences to be clipped
     *
     * @param name
     * @param bases
     */
    private void addSeqToClip(final String name, final byte[] bases) {
        final SeqToClip clip = new SeqToClip(name, bases);
        sequencesToClip.add(clip);
        logger.info(String.format("Creating sequence clipper %s: %s/%s", clip.name, clip.seq, clip.revSeq));
    }


    /**
     * clip sequences from the reads that match all of the sequences in the global sequencesToClip variable.
     * Adds ClippingOps for each clip to clipper.
     *
     * @param clipper
     */
    private void clipSequences(final ReadClipperWithData clipper) {
        // don't bother if we don't have any sequences to clip
        final GATKRead read = clipper.getRead();
        final ClippingData data = clipper.getData();

        for (final SeqToClip stc : sequencesToClip) {
            // we have a pattern for both the forward and the reverse strands
            final Pattern pattern = read.isReverseStrand() ? stc.revPat : stc.fwdPat;
            final String bases = read.getBasesString();
            final Matcher match = pattern.matcher(bases);

            // keep clipping until match.find() says it can't find anything else
            boolean found = true;   // go through at least once
            while (found) {
                found = match.find();
                //System.out.printf("Matching %s against %s/%s => %b%n", bases, stc.seq, stc.revSeq, found);
                if (found) {
                    final int start = match.start();
                    final int stop = match.end() - 1;
                    //ClippingOp op = new ClippingOp(ClippingOp.ClippingType.MATCHES_CLIP_SEQ, start, stop, stc.seq);
                    final ClippingOp op = new ClippingOp(start, stop);
                    clipper.addOp(op);
                    data.incSeqClippedBases(stc.seq, op.getLength());
                }
            }
        }
        clipper.setData(data);
    }

    /**
     * Convenence function that takes a read and the start / stop clipping positions based on the forward
     * strand, and returns start/stop values appropriate for the strand of the read.
     *
     * @param read
     * @param start
     * @param stop
     * @return
     */
    private Pair<Integer, Integer> strandAwarePositions(final GATKRead read, final int start, final int stop) {
        if (read.isReverseStrand())
            return new MutablePair<>(read.getLength() - stop - 1, read.getLength() - start - 1);
        else
            return new MutablePair<>(start, stop);
    }

    /**
     * clip bases at cycles between the ranges in cyclesToClip by adding appropriate ClippingOps to clipper.
     *
     * @param clipper
     */
    private void clipCycles(final ReadClipperWithData clipper) {
        if (cyclesToClip != null) {
            final GATKRead read = clipper.getRead();
            final ClippingData data = clipper.getData();

            for (final Pair<Integer, Integer> p : cyclesToClip) {   // iterate over each cycle range
                final int cycleStart = p.getLeft();
                int cycleStop = p.getRight();

                if (cycleStart < read.getLength()) {
                    // only try to clip if the cycleStart is less than the read's length
                    if (cycleStop >= read.getLength())
                        // we do tolerate [for convenience) clipping when the stop is beyond the end of the read
                        cycleStop = read.getLength() - 1;

                    final Pair<Integer, Integer> startStop = strandAwarePositions(read, cycleStart, cycleStop);
                    final int start = startStop.getLeft();
                    final int stop = startStop.getRight();

                    //ClippingOp op = new ClippingOp(ClippingOp.ClippingType.WITHIN_CLIP_RANGE, start, stop, null);
                    final ClippingOp op = new ClippingOp(start, stop);
                    clipper.addOp(op);
                    data.incNRangeClippedBases(op.getLength());
                }
            }
            clipper.setData(data);
        }
    }

    /**
     * Clip bases from the read in clipper from
     * <p/>
     * argmax_x{ \sum{i = x + 1}^l (qTrimmingThreshold - qual)
     * <p/>
     * to the end of the read.  This is blatantly stolen from BWA.
     * <p/>
     * Walk through the read from the end (in machine cycle order) to the beginning, calculating the
     * running sum of qTrimmingThreshold - qual.  While we do this, we track the maximum value of this
     * sum where the delta > 0.  After the loop, clipPoint is either -1 (don't do anything) or the
     * clipping index in the read (from the end).
     *
     * @param clipper
     */
    private void clipBadQualityScores(final ReadClipperWithData clipper) {
        final GATKRead read = clipper.getRead();
        final ClippingData data = clipper.getData();
        final int readLen = read.getLength();
        final byte[] quals = read.getBaseQualities();


        int clipSum = 0, lastMax = -1, clipPoint = -1; // -1 means no clip
        for (int i = readLen - 1; i >= 0; i--) {
            final int baseIndex = read.isReverseStrand() ? readLen - i - 1 : i;
            final byte qual = quals[baseIndex];
            clipSum += (qTrimmingThreshold - qual);
            if (clipSum >= 0 && (clipSum >= lastMax)) {
                lastMax = clipSum;
                clipPoint = baseIndex;
            }
        }

        if (clipPoint != -1) {
            final int start = read.isReverseStrand() ? 0 : clipPoint;
            final int stop = read.isReverseStrand() ? clipPoint : readLen - 1;
            //clipper.addOp(new ClippingOp(ClippingOp.ClippingType.LOW_Q_SCORES, start, stop, null));
            final ClippingOp op = new ClippingOp(start, stop);
            clipper.addOp(op);
            data.incNQClippedBases(op.getLength());
        }
        clipper.setData(data);
    }

    private void accumulate(final ReadClipperWithData clipper) {
        if ( clipper == null )
            return;

        final GATKRead clippedRead = clipper.clipRead(clippingRepresentation);
        outputBam.addRead(clippedRead);

        accumulator.nTotalReads++;
        accumulator.nTotalBases += clipper.getRead().getLength();
        if (clipper.wasClipped()) {
            accumulator.nClippedReads++;
            accumulator.addData(clipper.getData());
        }
    }

    // --------------------------------------------------------------------------------------------------------------
    //
    // utility classes
    //
    // --------------------------------------------------------------------------------------------------------------

    private static final class SeqToClip {
        final String name;
        final String seq;
        final String revSeq;
        final Pattern fwdPat;
        final Pattern revPat;

        public SeqToClip(final String name, final byte[] bytez) {
            this.name = name;
            this.seq = new String(bytez);
            this.fwdPat = Pattern.compile(seq, Pattern.CASE_INSENSITIVE);
            this.revSeq = new String(BaseUtils.simpleReverseComplement(bytez));
            this.revPat = Pattern.compile(revSeq, Pattern.CASE_INSENSITIVE);
        }
    }

    public static final class ClippingData {
        public long nTotalReads = 0;
        public long nTotalBases = 0;
        public long nClippedReads = 0;
        public long nClippedBases = 0;
        public long nQClippedBases = 0;
        public long nRangeClippedBases = 0;
        public long nSeqClippedBases = 0;

        final SortedMap<String, Long> seqClipCounts = new TreeMap<>();

        public ClippingData(final List<SeqToClip> clipSeqs) {
            for (final SeqToClip clipSeq : clipSeqs) {
                seqClipCounts.put(clipSeq.seq, 0L);
            }
        }

        public void incNQClippedBases(final int n) {
            nQClippedBases += n;
            nClippedBases += n;
        }

        public void incNRangeClippedBases(final int n) {
            nRangeClippedBases += n;
            nClippedBases += n;
        }

        public void incSeqClippedBases(final String seq, final int n) {
            nSeqClippedBases += n;
            nClippedBases += n;
            seqClipCounts.put(seq, seqClipCounts.get(seq) + n);
        }

        public void addData (final ClippingData data) {
            nTotalReads += data.nTotalReads;
            nTotalBases += data.nTotalBases;
            nClippedReads += data.nClippedReads;
            nClippedBases += data.nClippedBases;
            nQClippedBases += data.nQClippedBases;
            nRangeClippedBases += data.nRangeClippedBases;
            nSeqClippedBases += data.nSeqClippedBases;

            for (final String seqClip : data.seqClipCounts.keySet()) {
                Long count = data.seqClipCounts.get(seqClip);
                if (seqClipCounts.containsKey(seqClip))
                    count += seqClipCounts.get(seqClip);
                seqClipCounts.put(seqClip, count);
            }
        }

        public String toString() {
            final StringBuilder s = new StringBuilder();

            s.append(StringUtils.repeat('-', 80) + "\n")
                    .append(String.format("Number of examined reads              %d%n", nTotalReads))
                    .append(String.format("Number of clipped reads               %d%n", nClippedReads))
                    .append(String.format("Percent of clipped reads              %.2f%n", (100.0 * nClippedReads) / nTotalReads))
                    .append(String.format("Number of examined bases              %d%n", nTotalBases))
                    .append(String.format("Number of clipped bases               %d%n", nClippedBases))
                    .append(String.format("Percent of clipped bases              %.2f%n", (100.0 * nClippedBases) / nTotalBases))
                    .append(String.format("Number of quality-score clipped bases %d%n", nQClippedBases))
                    .append(String.format("Number of range clipped bases         %d%n", nRangeClippedBases))
                    .append(String.format("Number of sequence clipped bases      %d%n", nSeqClippedBases));

            for (final Map.Entry<String, Long> elt : seqClipCounts.entrySet()) {
                s.append(String.format("  %8d clip sites matching %s%n", elt.getValue(), elt.getKey()));
            }

            s.append(StringUtils.repeat('-', 80) + "\n");
            return s.toString();
        }
    }

    public static final class ReadClipperWithData extends ReadClipper {
        private ClippingData data;

        public ReadClipperWithData(final GATKRead read, final List<SeqToClip> clipSeqs) {
            super(read);
            data = new ClippingData(clipSeqs);
        }

        public ClippingData getData() {
            return data;
        }

        public void setData(final ClippingData data) {
            this.data = data;
        }

        public void addData(final ClippingData data) {
            this.data.addData(data);
        }
    }


}
