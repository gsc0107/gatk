package org.broadinstitute.hellbender.utils.locusiterator;

import org.broadinstitute.hellbender.utils.MathUtils;
import org.broadinstitute.hellbender.utils.read.ArtificialReadUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

/**
 * testing of the new (non-legacy) version of LocusIteratorByState
 */
public final class PerSampleReadStateManagerUnitTest extends LocusIteratorByStateBaseTest {
    private static final class PerSampleReadStateManagerTester extends TestDataProvider {
        private List<Integer> readCountsPerAlignmentStart;
        private List<GATKRead> reads;
        private List<ArrayList<AlignmentStateMachine>> recordStatesByAlignmentStart;
        private final int removalInterval;

        public PerSampleReadStateManagerTester(final List<Integer> readCountsPerAlignmentStart, final int removalInterval ) {
            super(PerSampleReadStateManagerTester.class);

            this.readCountsPerAlignmentStart = readCountsPerAlignmentStart;
            this.removalInterval = removalInterval;

            reads = new ArrayList<>();
            recordStatesByAlignmentStart = new ArrayList<>();

            setName(String.format("%s: readCountsPerAlignmentStart: %s  removalInterval: %d",
                    getClass().getSimpleName(), readCountsPerAlignmentStart, removalInterval));
        }

        public void run() {
            final PerSampleReadStateManager perSampleReadStateManager = new PerSampleReadStateManager(LocusIteratorByState.NO_DOWNSAMPLING);

            makeReads();

            for ( final ArrayList<AlignmentStateMachine> stackRecordStates : recordStatesByAlignmentStart ) {
                perSampleReadStateManager.addStatesAtNextAlignmentStart(new LinkedList<>(stackRecordStates));
            }

            // read state manager should have the right number of reads
            Assert.assertEquals(reads.size(), perSampleReadStateManager.size());

            Iterator<GATKRead> originalReadsIterator = reads.iterator();
            Iterator<AlignmentStateMachine> recordStateIterator = perSampleReadStateManager.iterator();
            int recordStateCount = 0;
            int numReadStatesRemoved = 0;

            // Do a first-pass validation of the record state iteration by making sure we get back everything we
            // put in, in the same order, doing any requested removals of read states along the way
            while ( recordStateIterator.hasNext() ) {
                final AlignmentStateMachine readState = recordStateIterator.next();
                recordStateCount++;
                final GATKRead readFromPerSampleReadStateManager = readState.getRead();

                Assert.assertTrue(originalReadsIterator.hasNext());
                final GATKRead originalRead = originalReadsIterator.next();

                // The read we get back should be literally the same read in memory as we put in
                Assert.assertTrue(originalRead == readFromPerSampleReadStateManager);

                // If requested, remove a read state every removalInterval states
                if ( removalInterval > 0 && recordStateCount % removalInterval == 0 ) {
                    recordStateIterator.remove();
                    numReadStatesRemoved++;
                }
            }

            Assert.assertFalse(originalReadsIterator.hasNext());

            // If we removed any read states, do a second pass through the read states to make sure the right
            // states were removed
            if ( numReadStatesRemoved > 0 ) {
                Assert.assertEquals(perSampleReadStateManager.size(), reads.size() - numReadStatesRemoved);

                originalReadsIterator = reads.iterator();
                recordStateIterator = perSampleReadStateManager.iterator();
                int readCount = 0;
                int readStateCount = 0;

                // Match record states with the reads that should remain after removal
                while ( recordStateIterator.hasNext() ) {
                    final AlignmentStateMachine readState = recordStateIterator.next();
                    readStateCount++;
                    final GATKRead readFromPerSampleReadStateManager = readState.getRead();

                    Assert.assertTrue(originalReadsIterator.hasNext());

                    GATKRead originalRead = originalReadsIterator.next();
                    readCount++;

                    if ( readCount % removalInterval == 0 ) {
                        originalRead = originalReadsIterator.next(); // advance to next read, since the previous one should have been discarded
                        readCount++;
                    }

                    // The read we get back should be literally the same read in memory as we put in (after accounting for removals)
                    Assert.assertTrue(originalRead == readFromPerSampleReadStateManager);
                }

                Assert.assertEquals(readStateCount, reads.size() - numReadStatesRemoved);
            }

            // Allow memory used by this test to be reclaimed
            readCountsPerAlignmentStart = null;
            reads = null;
            recordStatesByAlignmentStart = null;
        }

        private void makeReads() {
            final int alignmentStart = 1;

            for ( final int readsThisStack : readCountsPerAlignmentStart ) {
                final ArrayList<GATKRead> stackReads = new ArrayList<>(ArtificialReadUtils.createIdenticalArtificialReads(readsThisStack, header, "foo", 0, alignmentStart, MathUtils.randomIntegerInRange(50, 100)));
                final ArrayList<AlignmentStateMachine> stackRecordStates = new ArrayList<>();

                for ( final GATKRead read : stackReads ) {
                    stackRecordStates.add(new AlignmentStateMachine(read));
                }

                reads.addAll(stackReads);
                recordStatesByAlignmentStart.add(stackRecordStates);
            }
        }
    }

    @DataProvider(name = "PerSampleReadStateManagerTestDataProvider")
    public Object[][] createPerSampleReadStateManagerTests() {
        for ( final List<Integer> thisTestReadStateCounts : Arrays.asList( Arrays.asList(1),
                Arrays.asList(2),
                Arrays.asList(10),
                Arrays.asList(1, 1),
                Arrays.asList(2, 2),
                Arrays.asList(10, 10),
                Arrays.asList(1, 10),
                Arrays.asList(10, 1),
                Arrays.asList(1, 1, 1),
                Arrays.asList(2, 2, 2),
                Arrays.asList(10, 10, 10),
                Arrays.asList(1, 1, 1, 1, 1, 1),
                Arrays.asList(10, 10, 10, 10, 10, 10),
                Arrays.asList(1, 2, 10, 1, 2, 10)
        ) ) {

            for ( final int removalInterval : Arrays.asList(0, 2, 3) ) {
                new PerSampleReadStateManagerTester(thisTestReadStateCounts, removalInterval);
            }
        }

        return PerSampleReadStateManagerTester.getTests(PerSampleReadStateManagerTester.class);
    }

    @Test(dataProvider = "PerSampleReadStateManagerTestDataProvider")
    public void runPerSampleReadStateManagerTest(final PerSampleReadStateManagerTester test ) {
        logger.warn("Running test: " + test);

        test.run();
    }
}
