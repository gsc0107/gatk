package org.broadinstitute.hellbender.tools.spark.sv;

import com.google.common.collect.Iterables;
import htsjdk.variant.variantcontext.VariantContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.broadinstitute.hellbender.cmdline.Argument;
import org.broadinstitute.hellbender.cmdline.CommandLineProgramProperties;
import org.broadinstitute.hellbender.cmdline.programgroups.StructuralVariationSparkProgramGroup;
import org.broadinstitute.hellbender.engine.datasources.ReferenceMultiSource;
import org.broadinstitute.hellbender.engine.spark.GATKSparkTool;
import scala.Tuple2;

import java.util.stream.Collectors;

import static org.broadinstitute.hellbender.tools.spark.sv.BreakpointAllele.Strandedness.SAME_STRAND;
import static org.broadinstitute.hellbender.tools.spark.sv.ContigsCollection.loadContigsCollectionKeyedByAssemblyId;

@CommandLineProgramProperties(summary="Filter breakpoint alignments and call variants.",
        oneLineSummary="Filter breakpoint alignments and call variants",
        programGroup = StructuralVariationSparkProgramGroup.class)
public final class CallVariantsFromAlignedContigsSpark extends GATKSparkTool {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LogManager.getLogger(CallVariantsFromAlignedContigsSpark.class);

    @Argument(doc = "URI of the output path", shortName = "outputPath",
            fullName = "outputPath", optional = false)
    private String outputPath;

    @Argument(doc = "output file name for called variants", shortName = "outputName",
            fullName = "outputName", optional = true)
    private String outputName = SVConstants.INVERSIONS_OUTPUT_VCF;

    @Argument(doc = "Input file of contig alignments", shortName = "inputAlignments",
            fullName = "inputAlignments", optional = false)
    private String inputAlignments;

    @Argument(doc = "Input file of assembled contigs", shortName = "inputAssemblies",
            fullName = "inputAssemblies", optional = false)
    private String inputAssemblies;

    // This class requires a reference parameter in 2bit format (to broadcast) and a reference in FASTA format
    // (to get a good sequence dictionary).
    // todo: document this better
    @Argument(doc = "FASTA formatted reference", shortName = "fastaReference",
            fullName = "fastaReference", optional = false)
    private String fastaReference;

    @Override
    public boolean requiresReference() {
        return true;
    }

    @Override
    protected void runTool(final JavaSparkContext ctx) {

        final JavaPairRDD<Iterable<AlignmentRegion>, byte[]> alignmentRegionsWithContigSequences = prepAlignmentRegionsForCalling(ctx, inputAlignments, inputAssemblies);

        final JavaRDD<VariantContext> variants = callVariantsFromAlignmentRegions(ctx.broadcast(getReference()), alignmentRegionsWithContigSequences).cache();
        log.info("Called " + variants.count() + " variants");

        SVVCFWriter.writeVCF(getAuthenticatedGCSOptions(), outputPath, outputName, fastaReference, variants);
    }

    /**
     * Loads the alignment regions from the text file they are in; converts them to a PairRDD keyed by assembly and contig ID;
     * loads the assembled contigs for all assemblies and uses them to add the contig sequence to each item in the PairRDD.
     */
    private static JavaPairRDD<Iterable<AlignmentRegion>, byte[]> prepAlignmentRegionsForCalling(final JavaSparkContext ctx,
                                                                                                 final String pathToInputAlignments,
                                                                                                 final String pathToInputAssemblies) {

        final JavaRDD<AlignmentRegion> inputAlignedContigs = ctx.textFile(pathToInputAlignments).map(AlignmentRegion::parseAlignedAssembledContigLine);

        final JavaPairRDD<Tuple2<String, String>, Iterable<AlignmentRegion>> alignmentRegionsKeyedByAssemblyAndContigId = inputAlignedContigs.mapToPair(alignmentRegion -> new Tuple2<>(new Tuple2<>(alignmentRegion.assemblyId, alignmentRegion.contigId), alignmentRegion)).groupByKey();

        final JavaPairRDD<String, ContigsCollection> assemblyIdsToContigCollections = loadContigsCollectionKeyedByAssemblyId(ctx, pathToInputAssemblies);

        final JavaPairRDD<Tuple2<String, String>, byte[]> contigSequences = assemblyIdsToContigCollections.flatMapToPair(assemblyIdAndContigsCollection -> {
            final String assemblyId = assemblyIdAndContigsCollection._1;
            final ContigsCollection contigsCollection = assemblyIdAndContigsCollection._2;
            return contigsCollection.getContents().stream().map(pair -> new Tuple2<>(new Tuple2<>(assemblyId, pair._1.toString()), pair._2.toString().getBytes())).collect(Collectors.toList());
        });

        return alignmentRegionsKeyedByAssemblyAndContigId
                .join(contigSequences)
                .mapToPair(tuple2 -> new Tuple2<>(tuple2._2()._1(), tuple2._2()._2()));
    }

    /**
     * This method processes an RDD containing alignment regions, scanning for split alignments which match a set of filtering
     * criteria, and emitting a list of VariantContexts representing SVs for split alignments that pass.
     *
     * The input RDD is of the form:
     * Tuple2 of:
     *     A byte array with the sequence content of the contig
     *     {@code Iterable<AlignmentRegion>} AlignmentRegion objects representing all alignments for one contig
     *
     * @param broadcastReference The broadcast handle to the reference (used to populate reference bases)
     * @param alignmentRegionsWithContigSequences A data structure as described above, where a list of AlignmentRegions and the sequence of the contig are keyed by a tuple of Assembly ID and Contig ID
     * @return An RDD of VariantContext's representing SVs called from breakpoint alignments
     */
    static JavaRDD<VariantContext> callVariantsFromAlignmentRegions(final Broadcast<ReferenceMultiSource> broadcastReference,
                                                                    final JavaPairRDD<Iterable<AlignmentRegion>, byte[]> alignmentRegionsWithContigSequences) {

        return alignmentRegionsWithContigSequences
                .filter(pair -> Iterables.size(pair._1())>1)                        // added a filter step to filter out any contigs that has less than two alignment records
                .flatMap(SVVariantCallerInternal::findChimericAlignments)
                .mapToPair(SVVariantCallerInternal::extractBreakpointAllele)        // prepare for consensus (generate key that will be used in consensus step)     1 -> 1
                .filter(tuple2 -> tuple2._1().determineStrandedness() != SAME_STRAND)   // TODO: hack right now for debugging during development
                .groupByKey()                                                       // consensus                                                                    variable -> 1
                .map(tuple2 -> SVVariantCallerInternal.callVariantsFromConsensus(tuple2, broadcastReference));
    }
}
