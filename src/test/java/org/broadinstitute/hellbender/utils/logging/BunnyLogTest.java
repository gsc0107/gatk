package org.broadinstitute.hellbender.utils.logging;

import org.broadinstitute.hellbender.utils.logging.BunnyLog;
import org.testng.annotations.Test;

import org.testng.Assert;

public class BunnyLogTest {

    @Test
    public void testStart() throws Exception {
        final String start = new BunnyLog().start("eating carrots");
        final String[] p = start.split(" ");
        Assert.assertEquals(p[0], BunnyLog.bunny);
        Assert.assertEquals(p[1], "START");
        Assert.assertEquals(p[3], "eating");
    }

    @Test
    public void testStepEnd() throws Exception {
        final String stepEnd = new BunnyLog().stepEnd("digging");
        final String[] p = stepEnd.split(" ");
        Assert.assertEquals(p[0], BunnyLog.bunny);
        Assert.assertEquals(p[1], "STEPEND");
        Assert.assertEquals(p[3], "digging");
    }

    @Test
    public void testEnd() throws Exception {
        final BunnyLog log = new BunnyLog();
        final String start = log.start("eating carrots");
        final String id = start.split(" ")[2];
        final String end = log.end();
        final String[] p = end.split(" ");
        Assert.assertEquals(p[0], BunnyLog.bunny);
        Assert.assertEquals(p[1], "END");
        Assert.assertEquals(p[2], id);
    }
}