package org.broadinstitute.hellbender.engine.spark;

import com.google.api.services.genomics.model.Read;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;
import com.google.cloud.dataflow.sdk.values.KV;
import htsjdk.samtools.SAMRecord;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.broadinstitute.hellbender.engine.datasources.ReferenceWindowFunctions;
import org.broadinstitute.hellbender.engine.datasources.ReferenceMultiSource;
import org.broadinstitute.hellbender.utils.SimpleInterval;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.reference.ReferenceBases;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.broadinstitute.hellbender.utils.test.FakeReferenceSource;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class JoinReadsWithRefBasesSparkUnitTest extends BaseTest {
    @DataProvider(name = "bases")
    public Object[][] bases(){
        final Object[][] data = new Object[2][];
        final List<Class<?>> classes = Arrays.asList(Read.class, SAMRecord.class);
        for (int i = 0; i < classes.size(); ++i) {
            final Class<?> c = classes.get(i);
            final ReadsPreprocessingPipelineSparkTestData testData = new ReadsPreprocessingPipelineSparkTestData(c);

            final List<GATKRead> reads = testData.getReads();
            final List<SimpleInterval> intervals = testData.getAllIntervals();
            final List<KV<GATKRead, ReferenceBases>> kvReadRefBases = testData.getKvReadsRefBases();
            data[i] = new Object[]{reads, kvReadRefBases, intervals};
        }
        return data;
    }

    @Test(dataProvider = "bases", groups = "spark")
    public void refBasesShuffleTest(final List<GATKRead> reads, final List<KV<GATKRead, ReferenceBases>> kvReadRefBases,
                                    final List<SimpleInterval> intervals) throws IOException {
        final JavaSparkContext ctx = SparkContextFactory.getTestSparkContext();

        final JavaRDD<GATKRead> rddReads = ctx.parallelize(reads);

        final ReferenceMultiSource mockSource = mock(ReferenceMultiSource.class, withSettings().serializable());
        for (final SimpleInterval i : intervals) {
            when(mockSource.getReferenceBases(any(PipelineOptions.class), eq(i))).thenReturn(FakeReferenceSource.bases(i));
        }
        when(mockSource.getReferenceWindowFunction()).thenReturn(ReferenceWindowFunctions.IDENTITY_FUNCTION);

        final JavaPairRDD<GATKRead, ReferenceBases> rddResult = ShuffleJoinReadsWithRefBases.addBases(mockSource, rddReads);
        final Map<GATKRead, ReferenceBases> result = rddResult.collectAsMap();

        for (final KV<GATKRead, ReferenceBases> kv : kvReadRefBases) {
            final ReferenceBases referenceBases = result.get(kv.getKey());
            Assert.assertNotNull(referenceBases);
            Assert.assertEquals(kv.getValue(),referenceBases);
        }
    }

    @Test(dataProvider = "bases", groups = "spark")
    public void refBasesBroadcastTest(final List<GATKRead> reads, final List<KV<GATKRead, ReferenceBases>> kvReadRefBases,
                                      final List<SimpleInterval> intervals) throws IOException {
        final JavaSparkContext ctx = SparkContextFactory.getTestSparkContext();

        final JavaRDD<GATKRead> rddReads = ctx.parallelize(reads);

        final ReferenceMultiSource mockSource = mock(ReferenceMultiSource.class, withSettings().serializable());
        for (final SimpleInterval i : intervals) {
            when(mockSource.getReferenceBases(any(PipelineOptions.class), eq(i))).thenReturn(FakeReferenceSource.bases(i));
        }
        when(mockSource.getReferenceWindowFunction()).thenReturn(ReferenceWindowFunctions.IDENTITY_FUNCTION);

        final JavaPairRDD<GATKRead, ReferenceBases> rddResult = BroadcastJoinReadsWithRefBases.addBases(mockSource, rddReads);
        final Map<GATKRead, ReferenceBases> result = rddResult.collectAsMap();

        for (final KV<GATKRead, ReferenceBases> kv : kvReadRefBases) {
            final ReferenceBases referenceBases = result.get(kv.getKey());
            Assert.assertNotNull(referenceBases);
            Assert.assertEquals(kv.getValue(),referenceBases);
        }
    }
}