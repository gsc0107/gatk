package org.broadinstitute.hellbender.cmdline.argumentcollections;


import org.broadinstitute.hellbender.cmdline.ArgumentCollection;
import org.broadinstitute.hellbender.cmdline.CommandLineParser;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.testng.annotations.Test;

public final class ReferenceInputArgumentCollectionTest {
    private static class WithOptionalReferenceCollection {
        @ArgumentCollection
        ReferenceInputArgumentCollection ric = new OptionalReferenceInputArgumentCollection();
    }

    private static class WithRequiredReferenceCollection {
        @ArgumentCollection
        ReferenceInputArgumentCollection ric = new RequiredReferenceInputArgumentCollection();
    }

    @Test
    public void testOptionalIsOptional(){
        final String[] args = {};
        final WithOptionalReferenceCollection optional = new WithOptionalReferenceCollection();
        final CommandLineParser clp = new CommandLineParser(optional);
        clp.parseArguments(System.out, args);
    }

    @Test(expectedExceptions = UserException.CommandLineException.class)
    public void testRequiredIsRequired(){
        final String[] args = {};
        final WithRequiredReferenceCollection required = new WithRequiredReferenceCollection();
        final CommandLineParser clp = new CommandLineParser(required);
        clp.parseArguments(System.out, args);
    }
}