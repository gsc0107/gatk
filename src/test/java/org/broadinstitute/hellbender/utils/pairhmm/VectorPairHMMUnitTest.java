package org.broadinstitute.hellbender.utils.pairhmm;

import org.broadinstitute.gatk.nativebindings.pairhmm.PairHMMNativeArguments;
import org.broadinstitute.hellbender.utils.genotyper.LikelihoodMatrix;
import org.broadinstitute.hellbender.utils.haplotype.Haplotype;
import org.broadinstitute.hellbender.utils.read.ArtificialReadUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.ReadUtils;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.broadinstitute.hellbender.utils.text.parsers.BasicInputParser;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

public final class VectorPairHMMUnitTest extends BaseTest {

    private static final String pairHMMTestData = publicTestDir + "pairhmm-testdata.txt";

   @BeforeClass
    public void initialize() {
       try {
           new VectorLoglessPairHMM(null);
       } catch (final Exception e) {
           throw new SkipException("AVX PairHMM is not supported on this system or the library is not available");
       }
    }

    private List<N2MemoryPairHMM> getHMMs() {
        final PairHMMNativeArguments args = new PairHMMNativeArguments();
        args.useDoublePrecision = false;
        args.maxNumberOfThreads = 1;
        final N2MemoryPairHMM avxPairHMM = new VectorLoglessPairHMM(args);
        avxPairHMM.doNotUseTristateCorrection();
        return Collections.singletonList(avxPairHMM);
    }

    // --------------------------------------------------------------------------------
    //
    // Provider
    //
    // --------------------------------------------------------------------------------

    @DataProvider(name = "JustHMMProvider")
    public Object[][] makeJustHMMProvider() {
        final List<Object[]> tests = new ArrayList<>();

        for ( final PairHMM hmm : getHMMs() ) {
            tests.add(new Object[]{hmm});
        }

        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "JustHMMProvider")
    public void testLikelihoodsFromHaplotypes(final PairHMM hmm){

        BasicInputParser parser = null;
        try {
            parser = new BasicInputParser(true, new FileInputStream(pairHMMTestData));
        } catch (final FileNotFoundException e) {
            Assert.fail("PairHMM test data not found : " + pairHMMTestData);
        }

        while (parser.hasNext()) {
            final String[] tokens = parser.next();

            final Haplotype hap = new Haplotype(tokens[0].getBytes(), true);

            final byte[] bases = tokens[1].getBytes();
            final byte[] baseQuals = normalize(tokens[2].getBytes(), 6);
            final byte[] insertionQuals = normalize(tokens[3].getBytes());
            final byte[] deletionQuals = normalize(tokens[4].getBytes());
            final byte[] gcp = normalize(tokens[5].getBytes());
            final double expectedResult = Double.parseDouble(tokens[6]);

            final int readLength = bases.length;
            final GATKRead read = ArtificialReadUtils.createArtificialRead(bases, baseQuals, readLength + "M");
            ReadUtils.setInsertionBaseQualities(read, insertionQuals);
            ReadUtils.setDeletionBaseQualities(read, deletionQuals);

            final Map<GATKRead,byte[]> gpcs = new LinkedHashMap<>(readLength);
            gpcs.put(read, gcp);

            hmm.initialize(Arrays.asList(hap), null, 0, 0);
            hmm.computeLog10Likelihoods(matrix(Arrays.asList(hap)), Arrays.asList(read), gpcs);

            final double[] la = hmm.getLogLikelihoodArray();

            Assert.assertEquals(la[0], expectedResult, 1e-5, "Likelihood not in expected range.");
        }

        hmm.close();
    }

    private static byte[] normalize(final byte[] scores) {
        return normalize(scores, 0);
    }

    private static byte[] normalize(final byte[] scores, final int min) {
        for (int i = 0; i < scores.length; i++) {
            scores[i] -= 33;
            scores[i] = scores[i] < min ? (byte)min : scores[i];
        }
        return scores;
    }

    private LikelihoodMatrix<Haplotype> matrix(final List<Haplotype> haplotypes) {
        return new LikelihoodMatrix<Haplotype>() {
            @Override
            public List<GATKRead> reads() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Haplotype> alleles() {
                return haplotypes;
            }

            @Override
            public void set(final int alleleIndex, final int readIndex, final double value) {
//                throw new UnsupportedOperationException();
            }

            @Override
            public double get(final int alleleIndex, final int readIndex) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int indexOfAllele(final Haplotype allele) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int indexOfRead(final GATKRead read) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int numberOfAlleles() {
                return haplotypes.size();
            }

            @Override
            public int numberOfReads() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Haplotype getAllele(final int alleleIndex) {
                throw new UnsupportedOperationException();
            }

            @Override
            public GATKRead getRead(final int readIndex) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void copyAlleleLikelihoods(final int alleleIndex, final double[] dest, final int offset) {
                throw new UnsupportedOperationException();
            }
        };
    }

}
