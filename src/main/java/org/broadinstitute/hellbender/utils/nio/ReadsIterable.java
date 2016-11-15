package org.broadinstitute.hellbender.utils.nio;

import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import org.broadinstitute.hellbender.utils.gcs.BucketUtils;
import org.broadinstitute.hellbender.utils.io.IOUtils;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * ReadsIterable gives you all the reads for a given genomic interval.
 *
 * QueryInterval + header --> iterable SAMRecords
 */
public class ReadsIterable implements Iterable<SAMRecord>, Serializable {

    private static final long serialVersionUID = 1L;
    private final String path;
    private final byte[] index;
    private final QueryInterval interval;
    private final boolean removeHeader = true;

    class ReadsIterator implements CloseableIterator<SAMRecord> {
        private final static int BUFSIZE = 200 * 1024 * 1024;
        private SamReader bam;
        private SAMRecordIterator query;
        private SAMRecord nextRecord = null;
        private boolean done = false;

        public ReadsIterator() throws IOException {
            final Path fpath = IOUtils.getPath(path);
            final byte[] indexData = index;
            final SeekableStream indexInMemory = new ByteArraySeekableStream(indexData);
            final SeekableByteChannelPrefetcher chan = new SeekableByteChannelPrefetcher(Files.newByteChannel(fpath), BUFSIZE);
            final ChannelAsSeekableStream bamOverNIO = new ChannelAsSeekableStream(chan, path);
            bam = SamReaderFactory.makeDefault()
                    .validationStringency(ValidationStringency.LENIENT)
                    .enable(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES)
                    .open(SamInputResource.of(bamOverNIO).index(indexInMemory));

            final QueryInterval[] array = new QueryInterval[1];
            array[0] = interval;
            query = bam.query(array, false);
        }

        /**
         * Returns {@code true} if the iteration has more elements.
         * (In other words, returns {@code true} if {@link #next} would
         * return an element rather than throwing an exception.)
         *
         * @return {@code true} if the iteration has more elements
         */
        @Override
        public boolean hasNext() {
            if (done) return false;

            if (nextRecord!=null) return true;

            nextRecord = fetchRecord();

            final boolean ret = (nextRecord != null);
            if (!ret) {
                done = true;
                close();
            }
            return ret;
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        public SAMRecord next() {
            if (!hasNext()) throw new NoSuchElementException();
            final SAMRecord ret = nextRecord;
            nextRecord = null;
            return ret;
        }

        private SAMRecord fetchRecord() {
            while (query.hasNext()) {
                final SAMRecord sr = query.next();
                final int start = sr.getAlignmentStart();
                if (start >= interval.start && start <= interval.end) {
                    // read starts in the interval
                    if (removeHeader) {
                        sr.setHeader(null);
                    }
                    return sr;
                }
            }
            return null;
        }

        @Override
        public void close() {
            if (null==query) return;
            try {
                query.close();
                query = null;
                bam.close();
                bam = null;
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ReadsIterable(final String path, final byte[] index, final QueryInterval in) {
        this.path = path;
        this.index = index;
        this.interval = in;
    }

    @Override
    public Iterator<SAMRecord> iterator() {
        try {
            return new ReadsIterator();
        } catch (final IOException x) {
            throw new RuntimeException(x);
        }
    }

}
