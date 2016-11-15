package org.broadinstitute.hellbender.utils.test;

import htsjdk.samtools.ValidationStringency;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.utils.Utils;
import org.broadinstitute.hellbender.utils.text.XReadLines;
import org.testng.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class IntegrationTestSpec {
    public static final String DEFAULT_TEMP_EXTENSION = ".tmp";
    public static final String DEFAULT_TEMP_PREFIX = "walktest.tmp_param";

    private final String args;
    private final Class<?> expectedException;
    private final int nOutputFiles;
    private final List<String> expectedFileNames;

    //If this field is set to true, bam files will be compared after they get sorted.
    //This is needed as a workaround because Spark tools don't respect a pre-ordered BAMs
    // and so may create BAMs that are sorted differently than the input (though both orders will be valid).
    private boolean compareBamFilesSorted;

    //Stringency for validation of bams.
    private ValidationStringency validationStringency;

    public IntegrationTestSpec(final String args, final List<String> expectedFileNames) {
        this.args = args;
        this.nOutputFiles = expectedFileNames.size();
        this.expectedException = null;
        this.expectedFileNames = expectedFileNames;
        this.compareBamFilesSorted = false;
        this.validationStringency = ValidationStringency.DEFAULT_STRINGENCY;
    }

    public IntegrationTestSpec(final String args, final int nOutputFiles, final Class<?> expectedException) {
        if (expectedException == null) {
            throw new IllegalArgumentException("expected exception is null");
        }
        this.args = args;
        this.nOutputFiles = nOutputFiles;
        this.expectedException = expectedException;
        this.expectedFileNames = null;
        this.compareBamFilesSorted = false;
        this.validationStringency = ValidationStringency.DEFAULT_STRINGENCY;
    }

    public boolean expectsException() {
        return expectedException != null;
    }

    public final Class<?> getExpectedException() {
        if (!expectsException())
            throw new GATKException("Tried to get exception for walker test that doesn't expect one");
        return expectedException;
    }

    public void setCompareBamFilesSorted(final boolean compareBamFilesSorted) {
        this.compareBamFilesSorted = compareBamFilesSorted;
    }

    public void setValidationStringency(final ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    public String getArgs() {
        return args;
    }

    public Collection<String> expectedFileNames() {
        return expectedFileNames;
    }

    public void executeTest(final String name, final CommandLineProgramTester test) throws IOException {
        final List<File> tmpFiles = new ArrayList<>();
        for (int i = 0; i < nOutputFiles; i++) {
            final String ext = DEFAULT_TEMP_EXTENSION;
            final File fl = BaseTest.createTempFile(String.format(DEFAULT_TEMP_PREFIX + ".%d", i), ext);
            tmpFiles.add(fl);
        }

        final String preFormattedArgs = getArgs();
        final String formattedArgs = String.format(preFormattedArgs, tmpFiles.toArray());
        System.out.println(StringUtils.repeat('-', 80));

        if (expectsException()) {
            // this branch handles the case were we are testing that a walker will fail as expected
            executeTest(name, test, null, null, tmpFiles, formattedArgs, getExpectedException());
        } else {
            final List<String> expectedFileNames = new ArrayList<>(expectedFileNames());
            if (!expectedFileNames.isEmpty() && preFormattedArgs.equals(formattedArgs)) {
                throw new GATKException("Incorrect test specification - you're expecting " + expectedFileNames.size() + " file(s) the specified arguments do not contain the same number of \"%s\" placeholders");
            }

            executeTest(name, test, null, expectedFileNames, tmpFiles, formattedArgs, null);
        }
    }

    /**
     * execute the test, given the following:
     *
     * @param testName              the name of the test
     * @param testClass             the object that contains the test
     * @param expectedFileNames     the list of expectedFileNames
     * @param tmpFiles              the temp file corresponding to the expectedFileNames list
     * @param args                  the argument list
     * @param expectedException     the expected exception or null
     */
    private void executeTest(final String testName, final CommandLineProgramTester testClass, final File outputFileLocation, final List<String> expectedFileNames, final List<File> tmpFiles, String args, final Class<?> expectedException) throws IOException {
        if (outputFileLocation != null) {
            args += " -O " + outputFileLocation.getAbsolutePath();
        }
        executeTest(testName, testClass, args, expectedException);

        if (expectedException == null && !expectedFileNames.isEmpty()) {
            assertMatchingFiles(tmpFiles, expectedFileNames, compareBamFilesSorted, validationStringency);
        }
    }

    /**
     * execute the test, given the following:
     *
     * @param testName          the name of the test
     * @param testClass         the object that contains the test
     * @param args              the argument list
     * @param expectedException the expected exception or null
     */
    private void executeTest(final String testName, final CommandLineProgramTester testClass, final String args, final Class<?> expectedException) {
        final String[] command = Utils.escapeExpressions(args);
        // run the executable
        boolean gotAnException = false;
        try {
            final String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            System.out.println(String.format("[%s] Executing test %s:%s", now, testClass.getClass().getSimpleName(), testName));
            testClass.runCommandLine(command);
        } catch (final Exception e) {
            gotAnException = true;
            if (expectedException == null) {
                // we didn't expect an exception but we got one :-(
                throw new RuntimeException(e);
            }
            // we expect an exception
            if (!expectedException.isInstance(e)) {
                final String message = String.format("Test %s:%s expected exception %s but instead got %s with error message %s",
                        testClass, testName, expectedException, e.getClass(), e.getMessage());
                if (e.getCause() != null) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final PrintStream ps = new PrintStream(baos);
                    e.getCause().printStackTrace(ps);
                    BaseTest.log(message);
                    BaseTest.log(baos.toString());
                }
                Assert.fail(message);
            }
        }

        if (expectedException != null && !gotAnException) {
            // we expected an exception but didn't see it
            Assert.fail(String.format("Test %s:%s expected exception %s but none was thrown", testClass.getClass().getSimpleName(), testName, expectedException.toString()));
        }
    }

    public static void assertMatchingFiles(final List<File> resultFiles, final List<String> expectedFiles, final boolean compareBamFilesSorted, final ValidationStringency stringency) throws IOException {
        Assert.assertEquals(resultFiles.size(), expectedFiles.size());
        for (int i = 0; i < resultFiles.size(); i++) {
            final File resultFile = resultFiles.get(i);
            final String expectedFileName = expectedFiles.get(i);
            final File expectedFile = new File(expectedFileName);
            if (expectedFileName.endsWith(".bam")){
                SamAssertionUtils.assertEqualBamFiles(resultFile, expectedFile, compareBamFilesSorted, stringency);
            } else {
                assertEqualTextFiles(resultFile, expectedFile);
            }
        }
    }

    public static void assertEqualTextFiles(final File resultFile, final File expectedFile) throws IOException {
        assertEqualTextFiles(resultFile, expectedFile, null);
    }

    /**
     * Compares two text files and ignores all lines that start with the comment prefix.
     */
    public static void assertEqualTextFiles(final File resultFile, final File expectedFile, final String commentPrefix) throws IOException {
        final Predicate<? super String> startsWithComment;
        if (commentPrefix == null){
            startsWithComment = s -> false;
        } else {
            startsWithComment = s -> s.startsWith(commentPrefix);
        }
        final List<String> actualLines = new XReadLines(resultFile).readLines().stream().filter(startsWithComment.negate()).collect(Collectors.toList());
        final List<String> expectedLines = new XReadLines(expectedFile).readLines().stream().filter(startsWithComment.negate()).collect(Collectors.toList());

        //For ease of debugging, we look at the lines first and only then check their counts
        final int minLen = Math.min(actualLines.size(), expectedLines.size());
        for (int i = 0; i < minLen; i++) {
            Assert.assertEquals(actualLines.get(i).toString(), expectedLines.get(i).toString(), "Line number " + i + " (not counting comments)");
        }
        Assert.assertEquals(actualLines.size(), expectedLines.size(), "line counts");
    }

}
