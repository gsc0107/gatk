package org.broadinstitute.hellbender.engine;

import org.broadinstitute.hellbender.utils.SimpleInterval;
import htsjdk.tribble.Feature;
import htsjdk.variant.vcf.VCFHeader;
import org.broadinstitute.hellbender.cmdline.Argument;
import org.broadinstitute.hellbender.cmdline.CommandLineProgram;
import org.broadinstitute.hellbender.cmdline.CommandLineProgramProperties;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.broadinstitute.hellbender.cmdline.programgroups.QCProgramGroup;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

public final class FeatureContextUnitTest extends BaseTest {

    @CommandLineProgramProperties(summary = "", oneLineSummary = "", programGroup = QCProgramGroup.class)
    private static class ArtificialFeatureContainingCommandLineProgram extends CommandLineProgram {
        @Argument(fullName = "featureArgument", shortName = "f")
        final
        FeatureInput<Feature> featureArgument;

        public ArtificialFeatureContainingCommandLineProgram() {
            featureArgument = new FeatureInput<>(publicTestDir + "org/broadinstitute/hellbender/engine/feature_data_source_test.vcf");
        }

        @Override
        protected Object doWork() {
            return null;
        }
    }

    @DataProvider(name = "EmptyFeatureContextDataProvider")
    public Object[][] getEmptyFeatureContextData() {
        // Default-constructed FeatureContexts and FeatureContexts constructed from null FeatureManagers
        // or intervals should behave as empty context objects.
        return new Object[][] {
                { new FeatureContext() },
                { new FeatureContext(null, null) },
                { new FeatureContext(null, new SimpleInterval("1", 1, 1) ) },
                { new FeatureContext(new FeatureManager(new ArtificialFeatureContainingCommandLineProgram()), null) }
        };
    }

    @Test(dataProvider = "EmptyFeatureContextDataProvider")
    public void testEmptyFeatureContext( final FeatureContext featureContext) {
        final ArtificialFeatureContainingCommandLineProgram toolInstance = new ArtificialFeatureContainingCommandLineProgram();

        Assert.assertFalse(featureContext.hasBackingDataSource() && featureContext.getInterval() != null,
                           "Empty FeatureContext reports having both a backing data source and an interval");
        Assert.assertTrue(featureContext.getValues(toolInstance.featureArgument).isEmpty(), "Empty FeatureContext should have returned an empty List from getValues()");
        Assert.assertTrue(featureContext.getValues(toolInstance.featureArgument, 1).isEmpty(), "Empty FeatureContext should have returned an empty List from getValues()");
        Assert.assertTrue(featureContext.getValues(Arrays.<FeatureInput<Feature>>asList(toolInstance.featureArgument)).isEmpty(), "Empty FeatureContext should have returned an empty List from getValues()");
        Assert.assertTrue(featureContext.getValues(Arrays.<FeatureInput<Feature>>asList(toolInstance.featureArgument), 1).isEmpty(), "Empty FeatureContext should have returned an empty List from getValues()");
    }

    @Test
    public void testGetHeader() {
        final ArtificialFeatureContainingCommandLineProgram toolInstance = new ArtificialFeatureContainingCommandLineProgram();
        try (final FeatureManager featureManager = new FeatureManager(toolInstance)) {
            final FeatureContext featureContext = new FeatureContext(featureManager, new SimpleInterval("1", 1, 1));
            final Object header = featureContext.getHeader(toolInstance.featureArgument);
            Assert.assertTrue(header instanceof VCFHeader, "Header for " + toolInstance.featureArgument.getFeaturePath() +
                    " not a VCFHeader");
        }
    }
}
