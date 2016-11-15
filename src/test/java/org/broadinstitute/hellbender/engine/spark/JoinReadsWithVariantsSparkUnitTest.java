package org.broadinstitute.hellbender.engine.spark;

import com.google.api.services.genomics.model.Read;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import htsjdk.samtools.SAMRecord;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.broadinstitute.hellbender.utils.variant.GATKVariant;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class JoinReadsWithVariantsSparkUnitTest extends BaseTest {
    @DataProvider(name = "pairedReadsAndVariants")
    public Object[][] pairedReadsAndVariants(){
        final List<Object[]> testCases = new ArrayList<>();

        for ( final JoinStrategy joinStrategy : JoinStrategy.values() ) {
            for ( final Class<?> readImplementation : Arrays.asList(Read.class, SAMRecord.class) ) {
                final ReadsPreprocessingPipelineSparkTestData testData = new ReadsPreprocessingPipelineSparkTestData(readImplementation);
                final List<GATKRead> reads = testData.getReads();
                final List<GATKVariant> variantList = testData.getVariants();
                final List<KV<GATKRead, Iterable<GATKVariant>>> kvReadiVariant = testData.getKvReadiVariant();

                testCases.add(new Object[]{reads, variantList, kvReadiVariant, joinStrategy});
            }
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test(dataProvider = "pairedReadsAndVariants", groups = "spark")
    public void pairReadsAndVariantsTest(final List<GATKRead> reads, final List<GATKVariant> variantList, final List<KV<GATKRead, Iterable<GATKVariant>>> kvReadiVariant, final JoinStrategy joinStrategy) {
        final JavaSparkContext ctx = SparkContextFactory.getTestSparkContext();

        final JavaRDD<GATKRead> rddReads = ctx.parallelize(reads);
        final JavaRDD<GATKVariant> rddVariants = ctx.parallelize(variantList);
        final JavaPairRDD<GATKRead, Iterable<GATKVariant>> actual = joinStrategy == JoinStrategy.SHUFFLE ?
                                                          ShuffleJoinReadsWithVariants.join(rddReads, rddVariants) :
                                                          BroadcastJoinReadsWithVariants.join(rddReads, rddVariants);
        final Map<GATKRead, Iterable<GATKVariant>> gatkReadIterableMap = actual.collectAsMap();

        Assert.assertEquals(gatkReadIterableMap.size(), kvReadiVariant.size());
        for (final KV<GATKRead, Iterable<GATKVariant>> kv : kvReadiVariant) {
            final List<GATKVariant> variants = Lists.newArrayList(gatkReadIterableMap.get(kv.getKey()));
            Assert.assertTrue(variants.stream().noneMatch( v -> v == null));
            final HashSet<GATKVariant> hashVariants = new LinkedHashSet<>(variants);
            final Iterable<GATKVariant> iVariants = kv.getValue();
            final HashSet<GATKVariant> expectedHashVariants = Sets.newLinkedHashSet(iVariants);
            Assert.assertEquals(hashVariants, expectedHashVariants);
        }
    }
}