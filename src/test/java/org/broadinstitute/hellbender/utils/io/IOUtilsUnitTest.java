package org.broadinstitute.hellbender.utils.io;

import org.apache.logging.log4j.core.util.FileUtils;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.Utils;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

public final class IOUtilsUnitTest extends BaseTest {

    @Test
    public void testTempDir() {
        final File tempDir = IOUtils.tempDir("Q-Unit-Test", "", new File("queueTempDirToDelete"));
        Assert.assertTrue(tempDir.exists());
        Assert.assertFalse(tempDir.isFile());
        Assert.assertTrue(tempDir.isDirectory());
        final boolean deleted = IOUtils.tryDelete(tempDir);
        Assert.assertTrue(deleted);
        Assert.assertFalse(tempDir.exists());
    }

    @Test
    public void testAbsolute() {
        File dir = IOUtils.absolute(new File("/path/./to/./directory/."));
        Assert.assertEquals(dir, new File("/path/to/directory"));

        dir = IOUtils.absolute(new File("/"));
        Assert.assertEquals(dir, new File("/"));

        dir = IOUtils.absolute(new File("/."));
        Assert.assertEquals(dir, new File("/"));

        dir = IOUtils.absolute(new File("/././."));
        Assert.assertEquals(dir, new File("/"));

        dir = IOUtils.absolute(new File("/./directory/."));
        Assert.assertEquals(dir, new File("/directory"));

        dir = IOUtils.absolute(new File("/./directory/./"));
        Assert.assertEquals(dir, new File("/directory"));

        dir = IOUtils.absolute(new File("/./directory./"));
        Assert.assertEquals(dir, new File("/directory."));

        dir = IOUtils.absolute(new File("/./.directory/"));
        Assert.assertEquals(dir, new File("/.directory"));
    }

    @Test
    public void testIsSpecialFile() {
        Assert.assertTrue(IOUtils.isSpecialFile(new File("/dev")));
        Assert.assertTrue(IOUtils.isSpecialFile(new File("/dev/null")));
        Assert.assertTrue(IOUtils.isSpecialFile(new File("/dev/full")));
        Assert.assertTrue(IOUtils.isSpecialFile(new File("/dev/stdout")));
        Assert.assertTrue(IOUtils.isSpecialFile(new File("/dev/stderr")));
        Assert.assertFalse(IOUtils.isSpecialFile(null));
        Assert.assertFalse(IOUtils.isSpecialFile(new File("/home/user/my.file")));
        Assert.assertFalse(IOUtils.isSpecialFile(new File("/devfake/null")));
    }

    @DataProvider( name = "ByteArrayIOTestData")
    public Object[][] byteArrayIOTestDataProvider() {
        return new Object[][] {
                // file size, read buffer size
                { 0,     4096 },
                { 1,     4096 },
                { 2000,  4096 },
                { 4095,  4096 },
                { 4096,  4096 },
                { 4097,  4096 },
                { 6000,  4096 },
                { 8191,  4096 },
                { 8192,  4096 },
                { 8193,  4096 },
                { 10000, 4096 }
        };
    }

    @Test( dataProvider = "ByteArrayIOTestData" )
    public void testWriteThenReadFileIntoByteArray (final int fileSize, final int readBufferSize ) throws Exception {
        final File tempFile = createTempFile(String.format("testWriteThenReadFileIntoByteArray_%d_%d", fileSize, readBufferSize), "tmp");

        final byte[] dataWritten = getDeterministicRandomData(fileSize);
        IOUtils.writeByteArrayToFile(dataWritten, tempFile);
        final byte[] dataRead = IOUtils.readFileIntoByteArray(tempFile, readBufferSize);

        Assert.assertEquals(dataRead.length, dataWritten.length);
        Assert.assertTrue(Arrays.equals(dataRead, dataWritten));
    }

    @Test( dataProvider = "ByteArrayIOTestData" )
    public void testWriteThenReadStreamIntoByteArray (final int fileSize, final int readBufferSize ) throws Exception {
        final File tempFile = createTempFile(String.format("testWriteThenReadStreamIntoByteArray_%d_%d", fileSize, readBufferSize), "tmp");

        final byte[] dataWritten = getDeterministicRandomData(fileSize);
        IOUtils.writeByteArrayToStream(dataWritten, new FileOutputStream(tempFile));
        final byte[] dataRead = IOUtils.readStreamIntoByteArray(new FileInputStream(tempFile), readBufferSize);

        Assert.assertEquals(dataRead.length, dataWritten.length);
        Assert.assertTrue(Arrays.equals(dataRead, dataWritten));
    }

    @Test( expectedExceptions = UserException.CouldNotReadInputFile.class )
    public void testReadNonExistentFileIntoByteArray() {
        final File nonExistentFile = BaseTest.getSafeNonExistentFile("djfhsdkjghdfk");
        Assert.assertFalse(nonExistentFile.exists());

        IOUtils.readFileIntoByteArray(nonExistentFile);
    }

    @Test( expectedExceptions = IllegalArgumentException.class )
    public void testReadStreamIntoByteArrayInvalidBufferSize() throws Exception {
        IOUtils.readStreamIntoByteArray(new FileInputStream(createTempFile("testReadStreamIntoByteArrayInvalidBufferSize", "tmp")),
                -1);
    }

    private byte[] getDeterministicRandomData (final int size ) {
        Utils.resetRandomGenerator();
        final Random rand = Utils.getRandomGenerator();

        final byte[] randomData = new byte[size];
        rand.nextBytes(randomData);

        return randomData;
    }

    @Test
    public void testDeleteDirOnExit() throws IOException {
        //This just tests that the code runs without crashing.
        //It runs at jvm shutdown so there isn't a good way to test it properly.
        //If you see a directory in the hellbender main folder called

        final File dir = new File(BaseTest.publicTestDir + "I_SHOULD_HAVE_BEEN_DELETED");
        IOUtils.deleteRecursivelyOnExit(dir);

        FileUtils.mkdir(dir, true);
        final File subdir = new File(dir, "subdir");
        FileUtils.mkdir(subdir, true);
        final File someFile = new File(dir, "someFile");
        someFile.createNewFile();
        final File anotherFile = new File(subdir, "anotherFile");
        anotherFile.createNewFile();
    }

    @DataProvider(name = "extensionsToReplace")
    public Object[][] getExtensionsToReplace(){
        return new Object[][] {
                {"file.old", "file.new"},
                {"file.something.old", "file.something.new"},
                {"src/test.something/file", "src/test.something/file.new"},
                {"/.src.folder/some/thing/.secret/file.old", "/.src.folder/some/thing/.secret/file.new" }
        };
    }

    @Test(dataProvider = "extensionsToReplace")
    public void testReplaceExtension(final String input, final String expected){
        Assert.assertEquals(IOUtils.replaceExtension(input, "new"), expected);
        Assert.assertEquals(IOUtils.replaceExtension(input, "new"), IOUtils.replaceExtension(input,"..new"));
        Assert.assertEquals(IOUtils.replaceExtension(new File(input), "new"), new File(expected));
    }

    @Test
    public void testAssertFileIsReadableExistingFile() {
        IOUtils.assertFileIsReadable(new File(hg19MiniReference));
    }

    @Test(expectedExceptions = UserException.CouldNotReadInputFile.class)
    public void testAssertFileIsReadableNonExistentFile() {
        IOUtils.assertFileIsReadable(new File(publicTestDir + "foo/bar/NON_EXISTENT_FILE_FOR_IOUTILS_AssertFileIsReadableNonExistentFile"));
    }


    @Test(groups={"bucket"})
    public void testGetPath() throws IOException {
        innerTestGetPath(getGCPTestInputPath() + "large/CEUTrio.HiSeq.WGS.b37.NA12878.20.21.bam");
        innerTestGetPath("file://" + NA12878_20_21_WGS_bam);
        innerTestGetPath(NA12878_20_21_WGS_bam);
    }

    private void innerTestGetPath(final String s) throws IOException {
        final Path p = IOUtils.getPath(s);
        final long size = Files.size(p);
        Assert.assertTrue(size>0);
    }
}
