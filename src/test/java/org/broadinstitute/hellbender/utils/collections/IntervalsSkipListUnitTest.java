package org.broadinstitute.hellbender.utils.collections;

import com.google.common.collect.Lists;
import htsjdk.samtools.util.Locatable;
import org.broadinstitute.hellbender.utils.SimpleInterval;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public final class IntervalsSkipListUnitTest extends BaseTest {

    @DataProvider(name="intervals")
    public Object[][] intervals(){
        final ArrayList<Locatable> input = Lists.newArrayList(
                new SimpleInterval("1",  10, 100),
                new SimpleInterval("2", 200, 300)
        );
        final ArrayList<Locatable> empty = new ArrayList<>();
        final ArrayList<Locatable> contig1 = Lists.newArrayList(
                new SimpleInterval("1",  10, 100)
        );
        final ArrayList<Locatable> contig2 = Lists.newArrayList(
                new SimpleInterval("2", 200, 300)
                );

        // returns input, query range, expected SimpleIntervals
        return new Object[][]{
                // we already test elsewhere that it works within a contig, so here we just have to make sure that
                // it picks the correct contig and can deal with not-yet-mentioned contigs.
                new Object[]{input, new SimpleInterval("1", 100, 200), contig1},
                new Object[]{input, new SimpleInterval("1", 1, 5), empty},
                new Object[]{input, new SimpleInterval("2", 100, 200), contig2},
                new Object[]{input, new SimpleInterval("3", 100, 200), empty},
        };
    }

    @Test(dataProvider = "intervals")
    public void testOverlap(final ArrayList<Locatable> input, final SimpleInterval query, final ArrayList<Locatable> expected) throws Exception {
        final IntervalsSkipList<Locatable> ints = new IntervalsSkipList<>(input);
        final List<Locatable> actual = ints.getOverlapping(query);
        Assert.assertEquals(
                actual,
                expected
        );
    }
}