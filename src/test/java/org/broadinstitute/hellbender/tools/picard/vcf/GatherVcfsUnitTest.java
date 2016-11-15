package org.broadinstitute.hellbender.tools.picard.vcf;

import com.beust.jcommander.internal.Lists;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GatherVcfsUnitTest {
    //tests for GatherVcfs, specifically areAllBlockCompressed()

    @DataProvider(name = "files")
    public Object[][] files(){
        final File nullFile = null;


        return new Object[][]{
                {Lists.newArrayList(nullFile), false},
                {Lists.newArrayList(new File("test.vcf")), false},
                {Lists.newArrayList(new File("test.bcf")), false},
                {Lists.newArrayList(new File("test.vcf.gz")), true},
                {Lists.newArrayList(new File("test1.vcf"), new File("test2.vcf.gz")), false}
        };
    }

    @Test(dataProvider = "files")
    public void testareAllBlockCompressed(final List<File> files, final boolean expected) throws IOException {
        Assert.assertEquals(GatherVcfs.areAllBlockCompressed(files), expected);
    }

}
