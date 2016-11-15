package org.broadinstitute.hellbender.engine.filters;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.read.ArtificialReadUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.ReadUtils;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReadGroupBlackListReadFilterUnitTest extends BaseTest {

    private static final int CHR_COUNT = 1;
    private static final int CHR_START = 1;
    private static final int CHR_SIZE = 1000;
    private static final int GROUP_COUNT = 5;

    private SAMFileHeader header= ArtificialReadUtils.createArtificialSamHeaderWithGroups(CHR_COUNT, CHR_START, CHR_SIZE, GROUP_COUNT);

    @Test(expectedExceptions=UserException.class)
    public void testBadFilter() throws IOException {
        final List<String> badFilters = Collections.singletonList("bad");
        new ReadGroupBlackListReadFilter(badFilters, header);
    }

    @Test(expectedExceptions=UserException.class)
    public void testBadFilterTag() throws IOException {
        final List<String> badFilters = Collections.singletonList("bad:filter");
        new ReadGroupBlackListReadFilter(badFilters, header);
    }

    @Test(expectedExceptions=UserException.class)
    public void testBadFilterFile() throws IOException {
        final List<String> badFilters = Collections.singletonList("/foo/bar/rgbl.txt");
        new ReadGroupBlackListReadFilter(badFilters, header);
    }

    private String getReadGroupId(final int index) {
        return header.getReadGroups().get(index).getReadGroupId();
    }

    private String getPlatformUnit(final int index) {
        return header.getReadGroups().get(index).getPlatformUnit();
    }

    @Test
    public void testFilterReadGroup() throws IOException {
        final GATKRead filteredRecord = ArtificialReadUtils.createArtificialRead(header, "readUno", 0, 1, 20);
        filteredRecord.setReadGroup(getReadGroupId(1));

        final GATKRead unfilteredRecord = ArtificialReadUtils.createArtificialRead(header, "readDos", 0, 2, 20);
        unfilteredRecord.setReadGroup(getReadGroupId(2));

        final List<String> filterList = new ArrayList<>();
        filterList.add("RG:" + getReadGroupId(1));

        final ReadGroupBlackListReadFilter filter = new ReadGroupBlackListReadFilter(filterList, header);
        Assert.assertTrue(!filter.test(filteredRecord));
        Assert.assertFalse(!filter.test(unfilteredRecord));
    }

    @Test
    public void testFilterPlatformUnit() throws IOException {
        final GATKRead filteredRecord = ArtificialReadUtils.createArtificialRead(header, "readUno", 0, 1, 20);
        filteredRecord.setReadGroup(getReadGroupId(1));

        final GATKRead unfilteredRecord = ArtificialReadUtils.createArtificialRead(header, "readDos", 0, 2, 20);
        unfilteredRecord.setReadGroup(getReadGroupId(2));

        final List<String> filterList = new ArrayList<>();
        filterList.add("PU:" + getPlatformUnit(1));

        final ReadGroupBlackListReadFilter filter = new ReadGroupBlackListReadFilter(filterList, header);
        Assert.assertTrue(!filter.test(filteredRecord));
        Assert.assertFalse(!filter.test(unfilteredRecord));
    }

    @Test
    public void testFilterOutByReadGroup() throws IOException {
        final int recordsPerGroup = 3;
        final List<GATKRead> records = new ArrayList<>();
        int alignmentStart = 0;
        for (int x = 0; x < GROUP_COUNT; x++) {
            final SAMReadGroupRecord groupRecord = header.getReadGroup(getReadGroupId(x));
            for (int y = 1; y <= recordsPerGroup; y++) {
                final GATKRead record = ArtificialReadUtils.createArtificialRead(header, "readUno", 0, ++alignmentStart, 20);
                record.setReadGroup(groupRecord.getReadGroupId());
                records.add(record);
            }
        }

        final List<String> filterList = new ArrayList<>();
        filterList.add("RG:" + getReadGroupId(1));
        filterList.add("RG:" + getReadGroupId(3));

        final ReadGroupBlackListReadFilter filter = new ReadGroupBlackListReadFilter(filterList, header);
        int filtered = 0;
        int unfiltered = 0;
        for (final GATKRead record : records) {
            final String readGroupName = record.getReadGroup();
            if (!filter.test(record)) {
                if (!filterList.contains("RG:" + readGroupName))
                    Assert.fail("Read group " + readGroupName + " was filtered");
                filtered++;
            } else {
                if (filterList.contains("RG:" + readGroupName))
                    Assert.fail("Read group " + readGroupName + " was not filtered");
                unfiltered++;
            }
        }

        final int filteredExpected = recordsPerGroup * 2;
        final int unfilteredExpected = recordsPerGroup * (GROUP_COUNT - 2);
        Assert.assertEquals(filtered, filteredExpected, "Filtered");
        Assert.assertEquals(unfiltered, unfilteredExpected, "Uniltered");
    }

    @Test
    public void testFilterOutByAttribute() throws IOException {
        final int recordsPerGroup = 3;
        final List<GATKRead> records = new ArrayList<>();
        int alignmentStart = 0;
        for (int x = 0; x < GROUP_COUNT; x++) {
            final SAMReadGroupRecord groupRecord = header.getReadGroup(getReadGroupId(x));
            for (int y = 1; y <= recordsPerGroup; y++) {
                final GATKRead record = ArtificialReadUtils.createArtificialRead(header, "readUno", 0, ++alignmentStart, 20);
                record.setReadGroup(groupRecord.getReadGroupId());
                records.add(record);
            }
        }

        final List<String> filterList = new ArrayList<>();
        filterList.add("PU:" + getPlatformUnit(1));

        final ReadGroupBlackListReadFilter filter = new ReadGroupBlackListReadFilter(filterList, header);
        int filtered = 0;
        int unfiltered = 0;
        for (final GATKRead record : records) {
            final String platformUnit = ReadUtils.getPlatformUnit(record, header);
            if (!filter.test(record)) {
                if (!filterList.contains("PU:" + platformUnit))
                    Assert.fail("Platform unit " + platformUnit + " was filtered");
                filtered++;
            } else {
                if (filterList.contains("PU:" + platformUnit))
                    Assert.fail("Platform unit " + platformUnit + " was not filtered");
                unfiltered++;
            }
        }

        final int filteredExpected = 6;
        final int unfilteredExpected = 9;
        Assert.assertEquals(filtered, filteredExpected, "Filtered");
        Assert.assertEquals(unfiltered, unfilteredExpected, "Uniltered");
    }

    @Test
    public void testFilterOutByFile() throws IOException {
        final int recordsPerGroup = 3;
        final List<GATKRead> records = new ArrayList<>();
        int alignmentStart = 0;
        for (int x = 0; x < GROUP_COUNT; x++) {
            final SAMReadGroupRecord groupRecord = header.getReadGroup(getReadGroupId(x));
            for (int y = 1; y <= recordsPerGroup; y++) {
                final GATKRead record = ArtificialReadUtils.createArtificialRead(header, "readUno", 0, ++alignmentStart, 20);
                record.setReadGroup(groupRecord.getReadGroupId());
                records.add(record);
            }
        }

        final List<String> filterList = new ArrayList<>();
        filterList.add(publicTestDir + "readgroupblacklisttest.txt");

        final ReadGroupBlackListReadFilter filter = new ReadGroupBlackListReadFilter(filterList, header);
        int filtered = 0;
        int unfiltered = 0;
        for (final GATKRead record : records) {
            final String readGroup = record.getReadGroup();
            if (!filter.test(record)) {
                if (!("ReadGroup3".equals(readGroup) || "ReadGroup4".equals(readGroup)))
                    Assert.fail("Read group " + readGroup + " was filtered");
                filtered++;
            } else {
                if ("ReadGroup3".equals(readGroup) || "ReadGroup4".equals(readGroup))
                    Assert.fail("Read group " + readGroup + " was not filtered");
                unfiltered++;
            }
        }

        final int filteredExpected = recordsPerGroup * 2;
        final int unfilteredExpected = recordsPerGroup * (GROUP_COUNT - 2);
        Assert.assertEquals(filtered, filteredExpected, "Filtered");
        Assert.assertEquals(unfiltered, unfilteredExpected, "Uniltered");
    }

    @Test
    public void testFilterOutByListFile() throws IOException {
        final int recordsPerGroup = 3;
        final List<GATKRead> records = new ArrayList<>();
        int alignmentStart = 0;
        for (int x = 0; x < GROUP_COUNT; x++) {
            final SAMReadGroupRecord groupRecord = header.getReadGroup(getReadGroupId(x));
            for (int y = 1; y <= recordsPerGroup; y++) {
                final GATKRead record = ArtificialReadUtils.createArtificialRead(header, "readUno", 0, ++alignmentStart, 20);
                record.setReadGroup(groupRecord.getReadGroupId());
                records.add(record);
            }
        }

        final List<String> filterList = new ArrayList<>();
        filterList.add(publicTestDir + "readgroupblacklisttestlist.txt");

        final ReadGroupBlackListReadFilter filter = new ReadGroupBlackListReadFilter(filterList, header);
        int filtered = 0;
        int unfiltered = 0;
        for (final GATKRead record : records) {
            final String readGroup = record.getReadGroup();
            if (!filter.test(record)) {
                if (!("ReadGroup3".equals(readGroup) || "ReadGroup4".equals(readGroup)))
                    Assert.fail("Read group " + readGroup + " was filtered");
                filtered++;
            } else {
                if ("ReadGroup3".equals(readGroup) || "ReadGroup4".equals(readGroup))
                    Assert.fail("Read group " + readGroup + " was not filtered");
                unfiltered++;
            }
        }

        final int filteredExpected = recordsPerGroup * 2;
        final int unfilteredExpected = recordsPerGroup * (GROUP_COUNT - 2);
        Assert.assertEquals(filtered, filteredExpected, "Filtered");
        Assert.assertEquals(unfiltered, unfilteredExpected, "Unfiltered");
    }
}
