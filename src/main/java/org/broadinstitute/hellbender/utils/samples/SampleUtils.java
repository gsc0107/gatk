package org.broadinstitute.hellbender.utils.samples;

import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.text.XReadLines;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sample utilities class.
 */
public class SampleUtils {
    /**
     * Given a list of files with sample names it reads all files and creates a list of unique samples from all these files.
     * @param files list of files with sample names in
     * @return a collection of unique samples from all files
     */
    public static Collection<String> getSamplesFromFiles (final Collection<File> files) {
        final Set<String> samplesFromFiles = new LinkedHashSet<>();
        if (files != null) {
            for (final File file : files) {
                try (XReadLines reader = new XReadLines(file)) {
                    final List<String> lines = reader.readLines();
                    samplesFromFiles.addAll(lines.stream().collect(Collectors.toList()));
                } catch (final IOException e) {
                    throw new UserException.CouldNotReadInputFile(file, e);
                }
            }
        }
        return samplesFromFiles;
    }
}

