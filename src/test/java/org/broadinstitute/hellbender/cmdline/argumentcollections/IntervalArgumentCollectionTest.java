package org.broadinstitute.hellbender.cmdline.argumentcollections;

import org.broadinstitute.hellbender.cmdline.ArgumentCollection;
import org.broadinstitute.hellbender.cmdline.CommandLineParser;
import org.broadinstitute.hellbender.engine.TraversalParameters;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.GenomeLoc;
import org.broadinstitute.hellbender.utils.IntervalSetRule;
import org.broadinstitute.hellbender.utils.SimpleInterval;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

public final class IntervalArgumentCollectionTest extends BaseTest{

    @DataProvider(name = "optionalOrNot")
    public Object[][] optionalOrNot(){
        return new Object[][]{
                { new OptionalIntervalArgumentCollection()},
                { new RequiredIntervalArgumentCollection()}
        };
    }

    private static class WithOptionalIntervals{
        @ArgumentCollection
        IntervalArgumentCollection iac = new OptionalIntervalArgumentCollection();
    }

    private static class WithRequiredIntervals{
        @ArgumentCollection
        IntervalArgumentCollection iac = new RequiredIntervalArgumentCollection();
    }

    @Test
    public void testOptionalIsOptional(){
        final WithOptionalIntervals opt = new WithOptionalIntervals();
        final CommandLineParser clp = new CommandLineParser(opt);
        final String[] args = {};
        clp.parseArguments(System.out, args);
    }

    @Test(expectedExceptions = UserException.class)
    public void testRequiredIsRequired(){
        final WithRequiredIntervals opt = new WithRequiredIntervals();
        final CommandLineParser clp = new CommandLineParser(opt);
        final String[] args = {};
        clp.parseArguments(System.out, args);
    }

    @Test(dataProvider = "optionalOrNot",expectedExceptions = GATKException.class)
    public void emptyIntervalsTest(final IntervalArgumentCollection iac){
        Assert.assertFalse(iac.intervalsSpecified());
        iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary());  //should throw an exception
    }

    @Test(dataProvider = "optionalOrNot")
    public void testExcludeWithNoIncludes(final IntervalArgumentCollection iac){
        iac.excludeIntervalStrings.addAll(Arrays.asList("1", "2", "3"));
        Assert.assertTrue(iac.intervalsSpecified());
        final GenomeLoc chr4GenomeLoc = hg19GenomeLocParser.createOverEntireContig("4");
        Assert.assertEquals(iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary()), Arrays.asList(new SimpleInterval(chr4GenomeLoc)));
    }

    @Test(dataProvider = "optionalOrNot")
    public void testExcludeWithPadding(final IntervalArgumentCollection iac){
        iac.intervalExclusionPadding = 10;
        iac.addToIntervalStrings("1:1-100");
        iac.excludeIntervalStrings.add("1:90-100");
        Assert.assertTrue(iac.intervalsSpecified());
        Assert.assertEquals(iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary()), Arrays.asList(new SimpleInterval("1", 1, 79)));
    }

    @Test(dataProvider = "optionalOrNot")
    public void testIncludeWithExclude(final IntervalArgumentCollection iac){
        iac.addToIntervalStrings("1:1-100");
        iac.excludeIntervalStrings.add("1:90-200");
        Assert.assertTrue(iac.intervalsSpecified());
        Assert.assertEquals(iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary()), Arrays.asList(new SimpleInterval("1", 1, 89)));
    }

    @Test(dataProvider = "optionalOrNot")
    public void testIncludeWithPadding(final IntervalArgumentCollection iac){
        iac.addToIntervalStrings("1:20-30");
        iac.intervalPadding = 10;
        Assert.assertEquals(iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary()), Arrays.asList(new SimpleInterval("1", 10, 40)));
    }

    @Test(dataProvider = "optionalOrNot")
    public void testIntervalSetRuleIntersection(final IntervalArgumentCollection iac){
        iac.addToIntervalStrings("1:1-100");
        iac.addToIntervalStrings("1:90-200");
        iac.intervalSetRule = IntervalSetRule.INTERSECTION;
        Assert.assertEquals(iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary()), Arrays.asList(new SimpleInterval("1", 90, 100)));
    }

    @Test(dataProvider = "optionalOrNot")
    public void testIntervalSetRuleUnion(final IntervalArgumentCollection iac){
        iac.addToIntervalStrings("1:1-100");
        iac.addToIntervalStrings("1:90-200");
        iac.intervalSetRule = IntervalSetRule.UNION;
        Assert.assertEquals(iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary()), Arrays.asList(new SimpleInterval("1", 1, 200)));
    }


    @Test(dataProvider = "optionalOrNot", expectedExceptions = UserException.BadArgumentValue.class)
    public void testAllExcluded(final IntervalArgumentCollection iac){
        iac.addToIntervalStrings("1:10-20");
        iac.excludeIntervalStrings.add("1:1-200");
        iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary());
    }

    @Test(dataProvider = "optionalOrNot", expectedExceptions= UserException.BadArgumentValue.class)
    public void testNoIntersection(final IntervalArgumentCollection iac){
        iac.addToIntervalStrings("1:10-20");
        iac.addToIntervalStrings("1:50-200");
        iac.intervalSetRule = IntervalSetRule.INTERSECTION;
        iac.getIntervals(hg19GenomeLocParser.getSequenceDictionary());
    }

    @Test(dataProvider = "optionalOrNot")
    public void testUnmappedInclusion(final IntervalArgumentCollection iac) {
        iac.addToIntervalStrings("unmapped");
        final TraversalParameters traversalParameters = iac.getTraversalParameters(hg19GenomeLocParser.getSequenceDictionary());
        Assert.assertTrue(traversalParameters.traverseUnmappedReads());
        Assert.assertTrue(traversalParameters.getIntervalsForTraversal().isEmpty());
    }

    @Test(dataProvider = "optionalOrNot")
    public void testUnmappedAndMappedInclusion(final IntervalArgumentCollection iac) {
        iac.addToIntervalStrings("1:10-20");
        iac.addToIntervalStrings("2:1-5");
        iac.addToIntervalStrings("unmapped");
        final TraversalParameters traversalParameters = iac.getTraversalParameters(hg19GenomeLocParser.getSequenceDictionary());
        Assert.assertTrue(traversalParameters.traverseUnmappedReads());
        Assert.assertEquals(traversalParameters.getIntervalsForTraversal().size(), 2);
        Assert.assertEquals(traversalParameters.getIntervalsForTraversal().get(0), new SimpleInterval("1", 10, 20));
        Assert.assertEquals(traversalParameters.getIntervalsForTraversal().get(1), new SimpleInterval("2", 1, 5));
    }

    @Test(dataProvider = "optionalOrNot")
    public void testUnmappedAndMappedInclusionPlusMappedExclusion(final IntervalArgumentCollection iac) {
        iac.addToIntervalStrings("1:10-20");
        iac.addToIntervalStrings("2:1-5");
        iac.addToIntervalStrings("unmapped");
        iac.excludeIntervalStrings.addAll(Arrays.asList("1"));
        final TraversalParameters traversalParameters = iac.getTraversalParameters(hg19GenomeLocParser.getSequenceDictionary());
        Assert.assertTrue(traversalParameters.traverseUnmappedReads());
        Assert.assertEquals(traversalParameters.getIntervalsForTraversal().size(), 1);
        Assert.assertEquals(traversalParameters.getIntervalsForTraversal().get(0), new SimpleInterval("2", 1, 5));
    }

    @Test(dataProvider = "optionalOrNot", expectedExceptions = UserException.class)
    public void testThrowOnUnmappedExclusion(final IntervalArgumentCollection iac) {
        iac.excludeIntervalStrings.addAll(Arrays.asList("unmapped"));
        iac.getTraversalParameters(hg19GenomeLocParser.getSequenceDictionary());
    }

    @Test(dataProvider = "optionalOrNot")
    public void testMultipleUnmappedInclusion(final IntervalArgumentCollection iac) {
        iac.addToIntervalStrings("unmapped");
        iac.addToIntervalStrings("1:10-20");
        iac.addToIntervalStrings("unmapped");
        iac.addToIntervalStrings("unmapped");
        final TraversalParameters traversalParameters = iac.getTraversalParameters(hg19GenomeLocParser.getSequenceDictionary());
        Assert.assertTrue(traversalParameters.traverseUnmappedReads());
        Assert.assertEquals(traversalParameters.getIntervalsForTraversal().size(), 1);
        Assert.assertEquals(traversalParameters.getIntervalsForTraversal().get(0), new SimpleInterval("1", 10, 20));
    }
}
