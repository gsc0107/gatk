package org.broadinstitute.hellbender.cmdline.argumentcollections;

import org.broadinstitute.hellbender.cmdline.ArgumentCollection;
import org.broadinstitute.hellbender.cmdline.CommandLineParser;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.test.BaseTest;
import org.testng.annotations.Test;

public final class FeatureInputArgumentCollectionTest extends BaseTest{

    @Test(expectedExceptions = UserException.CommandLineException.class)
    public void testRequiredIsRequired(){
        final Object req = new Object(){
            @ArgumentCollection
            private RequiredFeatureInputArgumentCollection ric = new RequiredFeatureInputArgumentCollection();
        };
        final CommandLineParser clp = new CommandLineParser(req);
        final String[] args = {};
        clp.parseArguments(System.out, args);
    }

    @Test
    public void testOptionalIsOptional(){
        final Object req = new Object(){
            @ArgumentCollection
            private OptionalFeatureInputArgumentCollection ric = new OptionalFeatureInputArgumentCollection();
        };
        final CommandLineParser clp = new CommandLineParser(req);
        final String[] args = {};
        clp.parseArguments(System.out, args);
    }
}