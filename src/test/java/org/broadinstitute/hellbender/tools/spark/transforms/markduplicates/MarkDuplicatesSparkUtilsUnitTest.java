package org.broadinstitute.hellbender.tools.spark.transforms.markduplicates;

import com.google.api.client.util.Lists;
import com.google.common.collect.ImmutableList;
import htsjdk.samtools.SAMFileHeader;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.broadinstitute.hellbender.engine.spark.SparkContextFactory;
import org.broadinstitute.hellbender.utils.read.ArtificialReadUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.markduplicates.ReadsKey;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MarkDuplicatesSparkUtilsUnitTest extends BaseTest {
    @Test(groups = "spark")
    public void testSpanningIterator() {
        check(Collections.emptyIterator(), Collections.emptyList());
        check(ImmutableList.of(pair(1, "a")).iterator(),
                ImmutableList.of(pairIterable(1, "a")));
        check(ImmutableList.of(pair(1, "a"), pair(1, "b")).iterator(),
                ImmutableList.of(pairIterable(1, "a", "b")));
        check(ImmutableList.of(pair(1, "a"), pair(2, "b")).iterator(),
                ImmutableList.of(pairIterable(1, "a"), pairIterable(2, "b")));
        check(ImmutableList.of(pair(1, "a"), pair(1, "b"), pair(2, "c")).iterator(),
                ImmutableList.of(pairIterable(1, "a", "b"), pairIterable(2, "c")));
        check(ImmutableList.of(pair(1, "a"), pair(2, "b"), pair(2, "c")).iterator(),
                ImmutableList.of(pairIterable(1, "a"), pairIterable(2, "b", "c")));
        check(ImmutableList.of(pair(1, "a"), pair(2, "b"), pair(1, "c")).iterator(),
                ImmutableList.of(pairIterable(1, "a"), pairIterable(2, "b"), pairIterable(1, "c")));
    }

    @Test(groups = "spark")
    public void testSpanReadsByKeyWithAlternatingGroups() {
        final SAMFileHeader header = ArtificialReadUtils.createArtificialSamHeaderWithGroups(1, 1, 1000, 2);
        final GATKRead read1 = ArtificialReadUtils.createArtificialRead(header, "N", 0, 1, 20);
        read1.setReadGroup(getReadGroupId(header, 0));
        final GATKRead read2 = ArtificialReadUtils.createArtificialRead(header, "N", 0, 2, 20);
        read2.setReadGroup(getReadGroupId(header, 1));
        final GATKRead read3 = ArtificialReadUtils.createArtificialRead(header, "N", 0, 3, 20);
        read3.setReadGroup(getReadGroupId(header, 0));
        final GATKRead read4 = ArtificialReadUtils.createArtificialRead(header, "N", 0, 4, 20);
        read4.setReadGroup(getReadGroupId(header, 1));

        final String key1 = ReadsKey.keyForRead(header, read1);
        final String key2 = ReadsKey.keyForRead(header, read2);
        final String key3 = ReadsKey.keyForRead(header, read3);
        final String key4 = ReadsKey.keyForRead(header, read4);

        Assert.assertEquals("ReadGroup0|N", key1);
        Assert.assertEquals("ReadGroup1|N", key2);
        Assert.assertEquals("ReadGroup0|N", key3);
        Assert.assertEquals("ReadGroup1|N", key4);

        final JavaSparkContext ctx = SparkContextFactory.getTestSparkContext();
        final JavaRDD<GATKRead> reads = ctx.parallelize(ImmutableList.of(read1, read2, read3, read4), 1);
        final JavaPairRDD<String, Iterable<GATKRead>> groupedReads = MarkDuplicatesSparkUtils.spanReadsByKey(header, reads);
        Assert.assertEquals(groupedReads.collect(),
                ImmutableList.of(pairIterable(key1, read1, read3), pairIterable(key2, read2, read4)));
    }

    private String getReadGroupId(final SAMFileHeader header, final int index) {
        return header.getReadGroups().get(index).getReadGroupId();
    }

    private static <K, V> void check(final Iterator<Tuple2<K, V>> it, final List<Tuple2<K, Iterable<V>>> expected) {
        final Iterator<Tuple2<K, Iterable<V>>> spanning = MarkDuplicatesSparkUtils.spanningIterator(it);
        final ArrayList<Tuple2<K, Iterable<V>>> actual = Lists.newArrayList(spanning);
        Assert.assertEquals(actual, expected);
    }

    private static <K, V> Tuple2<K, V> pair(final K key, final V value) {
        return new Tuple2<>(key, value);
    }

    private static Tuple2<Integer, Iterable<String>> pairIterable(final int i, final String... s) {
        return new Tuple2<>(i, ImmutableList.copyOf(s));
    }

    private static Tuple2<String, Iterable<GATKRead>> pairIterable(final String key, final GATKRead... reads) {
        return new Tuple2<>(key, ImmutableList.copyOf(reads));
    }

}
