package org.broadinstitute.hellbender.cmdline.argumentcollections;

import org.broadinstitute.hellbender.cmdline.ArgumentCollection;
import org.broadinstitute.hellbender.cmdline.CommandLineParser;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public final class ReadInputArgumentCollectionTest {

    @Test(expectedExceptions = UserException.CommandLineException.class)
    public void testRequiredIsRequired(){
        final Object req = new Object(){
            @ArgumentCollection
            private ReadInputArgumentCollection ric = new RequiredReadInputArgumentCollection();
        };
        final CommandLineParser clp = new CommandLineParser(req);
        final String[] args = {};
        clp.parseArguments(System.out, args);
    }

    @Test
    public void testOptionalIsOptional(){
        final Object req = new Object(){
            @ArgumentCollection
            private ReadInputArgumentCollection ric = new OptionalReadInputArgumentCollection();
        };
        final CommandLineParser clp = new CommandLineParser(req);
        final String[] args = {};
        clp.parseArguments(System.out, args);
    }
}