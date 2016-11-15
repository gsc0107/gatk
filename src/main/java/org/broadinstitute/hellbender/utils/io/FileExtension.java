package org.broadinstitute.hellbender.utils.io;

import java.io.File;

public interface FileExtension {
    /**
     * Returns a clone of the FileExtension with a new path.
     * @param path New path.
     * @return New FileExtension
     */
    File withPath(String path);
}
