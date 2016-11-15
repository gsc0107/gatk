package org.broadinstitute.hellbender.utils.io;

import org.broadinstitute.hellbender.exceptions.UserException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import org.broadinstitute.hellbender.utils.test.BaseTest;


/**
 * Tests selected functionality in the CommandLineExecutable class
 */
public class ListFileUtilsUnitTest extends BaseTest {


    @Test
    public void testUnpackSet() throws Exception {
        final Set<String> expected = new LinkedHashSet<>(Arrays.asList(publicTestDir + "exampleBAM.bam"));
        Set<String> actual = ListFileUtils.unpackSet(Arrays.asList(publicTestDir + "exampleBAM.bam"));
        Assert.assertEquals(actual, expected);

        final File tempListFile = createTempListFile("testUnpackSet",
                "#",
                publicTestDir + "exampleBAM.bam",
                "#" + publicTestDir + "foo.bam",
                "      # " + publicTestDir + "bar.bam"
        );
        actual = ListFileUtils.unpackSet(Arrays.asList(tempListFile.getAbsolutePath()));
        Assert.assertEquals(actual, expected);
    }

    @DataProvider(name="includeMatchingTests")
    public Object[][] getIncludeMatchingTests() {
        return new Object[][] {
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a"), true, new LinkedHashSet<>(Arrays.asList("a")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a"), false, new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("b"), true, Collections.EMPTY_SET },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("b"), false, new LinkedHashSet<>(Arrays.asList("ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "b"), true, new LinkedHashSet<>(Arrays.asList("a")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "b"), false, new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "ab"), true, new LinkedHashSet<>(Arrays.asList("a", "ab")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "ab"), false, new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*b.*"), true, Collections.EMPTY_SET },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*b.*"), false, new LinkedHashSet<>(Arrays.asList("ab", "abc") )},
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*"), true, Collections.EMPTY_SET },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*"), false, new LinkedHashSet<>(Arrays.asList("a", "ab", "abc") )}
        };
    }

    @Test(dataProvider = "includeMatchingTests")
    public void testIncludeMatching(final Set<String> values, final Collection<String> filters, final boolean exactMatch, final Set<String> expected) {
        final Set<String> actual = ListFileUtils.includeMatching(values, ListFileUtils.IDENTITY_STRING_CONVERTER, filters, exactMatch);
        Assert.assertEquals(actual, expected);
    }

    @DataProvider(name="excludeMatchingTests")
    public Object[][] getExcludeMatchingTests() {
        return new Object[][] {
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a"), true, new LinkedHashSet<>(Arrays.asList("ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a"), false, Collections.EMPTY_SET },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("b"), true, new LinkedHashSet<>(Arrays.asList("a", "ab", "abc") )},
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("b"), false, new LinkedHashSet<>(Arrays.asList("a")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "b"), true, new LinkedHashSet<>(Arrays.asList("ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "b"), false, Collections.EMPTY_SET },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "ab"), true, new LinkedHashSet<>(Arrays.asList("abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList("a", "ab"), false, Collections.EMPTY_SET },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*b.*"), true, new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*b.*"), false, new LinkedHashSet<>(Arrays.asList("a")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*"), true, new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")) },
                new Object[] { new LinkedHashSet<>(Arrays.asList("a", "ab", "abc")), Arrays.asList(".*"), false, Collections.EMPTY_SET }
        };
    }

    @Test(dataProvider = "excludeMatchingTests")
    public void testExcludeMatching(final Set<String> values, final Collection<String> filters, final boolean exactMatch, final Set<String> expected) {
        final Set<String> actual = ListFileUtils.excludeMatching(values, ListFileUtils.IDENTITY_STRING_CONVERTER, filters, exactMatch);
        Assert.assertEquals(actual, expected);
    }

    private static File createTempListFile(final String tempFilePrefix, final String... lines) {
        try {
            final File tempListFile = createTempFile(tempFilePrefix, ".list");

            try (final PrintWriter out = new PrintWriter(tempListFile)) {
                for (final String line : lines) {
                    out.println(line);
                }
            }
            return tempListFile;
        } catch (final IOException ex) {
            throw new UserException("Cannot create temp file: " + ex.getMessage(), ex);
        }
    }

}
