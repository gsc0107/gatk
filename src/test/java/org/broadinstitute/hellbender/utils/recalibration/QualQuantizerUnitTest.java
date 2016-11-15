package org.broadinstitute.hellbender.utils.recalibration;


import org.broadinstitute.hellbender.utils.recalibration.QualQuantizer;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.broadinstitute.hellbender.utils.QualityUtils;
import org.broadinstitute.hellbender.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public final class QualQuantizerUnitTest extends BaseTest {
    @BeforeSuite
    public void before() {

    }

    // --------------------------------------------------------------------------------
    //
    // merge case Provider
    //
    // --------------------------------------------------------------------------------

    private final class QualIntervalTestProvider extends BaseTest.TestDataProvider {
        final QualQuantizer.QualInterval left, right;
        int exError, exTotal, exQual;
        double exErrorRate;

        private QualIntervalTestProvider(final int leftE, final int leftN, final int rightE, final int rightN, final int exError, final int exTotal) {
            super(QualIntervalTestProvider.class);

            final QualQuantizer qq = new QualQuantizer(0);
            left = qq.new QualInterval(10, 10, leftN, leftE, 0);
            right = qq.new QualInterval(11, 11, rightN, rightE, 0);

            this.exError = exError;
            this.exTotal = exTotal;
            this.exErrorRate = (leftE + rightE + 1) / (1.0 * (leftN + rightN + 1));
            this.exQual = QualityUtils.errorProbToQual(this.exErrorRate);
        }
    }

    @DataProvider(name = "QualIntervalTestProvider")
    public Object[][] makeQualIntervalTestProvider() {
        new QualIntervalTestProvider(10, 100, 10, 1000, 20, 1100);
        new QualIntervalTestProvider(0, 100, 10, 900,   10, 1000);
        new QualIntervalTestProvider(10, 900, 0, 100,   10, 1000);
        new QualIntervalTestProvider(0, 0, 10, 100,     10, 100);
        new QualIntervalTestProvider(1, 10, 9, 90,      10, 100);
        new QualIntervalTestProvider(1, 10, 9, 100000,  10, 100010);
        new QualIntervalTestProvider(1, 10, 9, 1000000, 10,1000010);

        return QualIntervalTestProvider.getTests(QualIntervalTestProvider.class);
    }

    @Test(dataProvider = "QualIntervalTestProvider")
    public void testQualInterval(final QualIntervalTestProvider cfg) {
        final QualQuantizer.QualInterval merged = cfg.left.merge(cfg.right);
        Assert.assertEquals(merged.nErrors, cfg.exError);
        Assert.assertEquals(merged.nObservations, cfg.exTotal);
        Assert.assertEquals(merged.getErrorRate(), cfg.exErrorRate);
        Assert.assertEquals(merged.getQual(), cfg.exQual);
    }

    @Test
    public void testMinInterestingQual() {
        for ( int q = 0; q < 15; q++ ) {
            for ( int minQual = 0; minQual <= 10; minQual ++ ) {
                final QualQuantizer qq = new QualQuantizer(minQual);
                final QualQuantizer.QualInterval left = qq.new QualInterval(q, q, 100, 10, 0);
                final QualQuantizer.QualInterval right = qq.new QualInterval(q+1, q+1, 1000, 100, 0);

                final QualQuantizer.QualInterval merged = left.merge(right);
                final boolean shouldBeFree = q+1 <= minQual;
                if ( shouldBeFree )
                    Assert.assertEquals(merged.getPenalty(), 0.0);
                else
                    Assert.assertTrue(merged.getPenalty() > 0.0);
            }
        }
    }


    // --------------------------------------------------------------------------------
    //
    // High-level case Provider
    //
    // --------------------------------------------------------------------------------

    private final class QuantizerTestProvider extends TestDataProvider {
        final List<Long> nObservationsPerQual = new ArrayList<>();
        final int nLevels;
        final List<Integer> expectedMap;

        private QuantizerTestProvider(final List<Integer> nObservationsPerQual, final int nLevels, final List<Integer> expectedMap) {
            super(QuantizerTestProvider.class);

            for ( final int x : nObservationsPerQual )
                this.nObservationsPerQual.add((long)x);
            this.nLevels = nLevels;
            this.expectedMap = expectedMap;
        }

        @Override
        public String toString() {
            return String.format("QQTest nLevels=%d nObs=[%s] map=[%s]",
                    nLevels, Utils.join(",", nObservationsPerQual), Utils.join(",", expectedMap));
        }
    }

    @DataProvider(name = "QuantizerTestProvider")
    public Object[][] makeQuantizerTestProvider() {
        final List<Integer> allQ2 = Arrays.asList(0, 0, 1000, 0, 0);

        new QuantizerTestProvider(allQ2, 5, Arrays.asList(0, 1, 2, 3, 4));
        new QuantizerTestProvider(allQ2, 1, Arrays.asList(2, 2, 2, 2, 2));

        new QuantizerTestProvider(Arrays.asList(0, 0, 1000, 0, 1000), 2, Arrays.asList(2, 2, 2, 2, 4));
        new QuantizerTestProvider(Arrays.asList(0, 0, 1000, 1, 1000), 2, Arrays.asList(2, 2, 2, 4, 4));
        new QuantizerTestProvider(Arrays.asList(0, 0, 1000, 10, 1000), 2, Arrays.asList(2, 2, 2, 2, 4));

        return QuantizerTestProvider.getTests(QuantizerTestProvider.class);
    }

    @Test(dataProvider = "QuantizerTestProvider")
    public void testQuantizer(final QuantizerTestProvider cfg) {
        final QualQuantizer qq = new QualQuantizer(cfg.nObservationsPerQual, cfg.nLevels, 0);
        logger.warn("cfg: " + cfg);
        for ( int i = 0; i < cfg.expectedMap.size(); i++) {
            final int expected = cfg.expectedMap.get(i);
            final int observed = qq.originalToQuantizedMap.get(i);
            //logger.warn(String.format("  qq map: %s : %d => %d", i, expected, observed));
            Assert.assertEquals(observed, expected);
        }
    }
}