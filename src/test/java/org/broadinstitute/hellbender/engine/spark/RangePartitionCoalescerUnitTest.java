package org.broadinstitute.hellbender.engine.spark;

import com.google.common.collect.ImmutableList;
import org.apache.spark.Partition;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.rdd.PartitionGroup;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import scala.collection.JavaConversions;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class RangePartitionCoalescerUnitTest extends BaseTest {
    private JavaRDD<String> rdd;
    private Partition[] partitions;

    @BeforeTest
    public void setup() {
        final JavaSparkContext ctx = SparkContextFactory.getTestSparkContext();
        rdd = ctx.parallelize(ImmutableList.of("a", "b", "c"), 3);
        partitions = rdd.rdd().partitions();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testThrowsExceptionIfNumPartitionsDifferent() {
        final List<Integer> maxEndPartitionIndexes = ImmutableList.of(0, 1, 2);
        final RangePartitionCoalescer coalescer = new RangePartitionCoalescer(maxEndPartitionIndexes);
        coalescer.coalesce(rdd.getNumPartitions() - 1, rdd.rdd());
    }

    @Test
    public void testIdentity() {
        final List<Integer> maxEndPartitionIndexes = ImmutableList.of(0, 1, 2);
        final RangePartitionCoalescer coalescer = new RangePartitionCoalescer(maxEndPartitionIndexes);
        final PartitionGroup[] groups = coalescer.coalesce(rdd.getNumPartitions(), rdd.rdd());
        assertEquals(groups.length, 3);
        assertEquals(groups[0].partitions(), JavaConversions.asScalaBuffer(ImmutableList.of(partitions[0])));
        assertEquals(groups[1].partitions(), JavaConversions.asScalaBuffer(ImmutableList.of(partitions[1])));
        assertEquals(groups[2].partitions(), JavaConversions.asScalaBuffer(ImmutableList.of(partitions[2])));
    }

    @Test
    public void testNonIdentity() {
        final List<Integer> maxEndPartitionIndexes = ImmutableList.of(1, 2, 2);
        final RangePartitionCoalescer coalescer = new RangePartitionCoalescer(maxEndPartitionIndexes);
        final PartitionGroup[] groups = coalescer.coalesce(rdd.getNumPartitions(), rdd.rdd());
        assertEquals(groups.length, 3);
        assertEquals(groups[0].partitions(), JavaConversions.asScalaBuffer(ImmutableList.of(partitions[0], partitions[1])));
        assertEquals(groups[1].partitions(), JavaConversions.asScalaBuffer(ImmutableList.of(partitions[1], partitions[2])));
        assertEquals(groups[2].partitions(), JavaConversions.asScalaBuffer(ImmutableList.of(partitions[2])));
    }
}
