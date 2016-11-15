package org.broadinstitute.hellbender.cmdline;

import java.util.Comparator;

/**
 * Interface for groups of CommandLinePrograms.
 * @author Nils Homer
 */
public interface CommandLineProgramGroup {

    /** Gets the name of this program. **/
    String getName();
    /** Gets the description of this program. **/
    String getDescription();
    /** Compares two program groups by name. **/
    Comparator<CommandLineProgramGroup> comparator = (a, b) -> a.getName().compareTo(b.getName());
}
