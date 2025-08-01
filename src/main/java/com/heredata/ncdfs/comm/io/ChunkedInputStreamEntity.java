package com.heredata.ncdfs.comm.io;

import com.heredata.ncdfs.HttpHeaders;
import com.heredata.ncdfs.comm.ServiceClient;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.heredata.ncdfs.utils.LogUtils.logException;

public class ChunkedInputStreamEntity extends BasicHttpEntity {

    private boolean firstAttempt = true;
    private ReleasableInputStreamEntity notClosableRequestEntity;
    private InputStream content;

    public ChunkedInputStreamEntity(ServiceClient.Request request) {
        setChunked(true);

        long contentLength = -1;
        try {
            String contentLengthString = request.getHeaders().get(HttpHeaders.CONTENT_LENGTH);
            if (contentLengthString != null) {
                contentLength = Long.parseLong(contentLengthString);
            }
        } catch (NumberFormatException nfe) {
            logException("Unable to parse content length from request.", nfe);
        }

        String contentType = request.getHeaders().get(HttpHeaders.CONTENT_TYPE);

        notClosableRequestEntity = new ReleasableInputStreamEntity(request.getContent(), contentLength);
        notClosableRequestEntity.setCloseDisabled(true);
        notClosableRequestEntity.setContentType(contentType);
        content = request.getContent();

        setContent(content);
        setContentType(contentType);
        setContentLength(contentLength);
    }

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public boolean isRepeatable() {
        return content.markSupported() || notClosableRequestEntity.isRepeatable();
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        if (!firstAttempt && isRepeatable()) {
            content.reset();
        }

        firstAttempt = false;
        notClosableRequestEntity.writeTo(output);
    }

    /**
     * A releaseable HTTP entity that can control its inner inputstream's
     * auto-close functionality on/off, and it will try to close its inner
     * inputstream by default when the inputstream reaches EOF.
     */
    public static class ReleasableInputStreamEntity extends AbstractHttpEntity implements Releasable {

        private final InputStream content;
        private final long length;

        private boolean closeDisabled;

        public ReleasableInputStreamEntity(final InputStream instream) {
            this(instream, -1);
        }

        public ReleasableInputStreamEntity(final InputStream instream, final long length) {
            this(instream, length, null);
        }

        public ReleasableInputStreamEntity(final InputStream instream, final ContentType contentType) {
            this(instream, -1, contentType);
        }

        public ReleasableInputStreamEntity(final InputStream instream, final long length,
                                           final ContentType contentType) {
            super();
            this.content = Args.notNull(instream, "Source input stream");
            this.length = length;
            if (contentType != null) {
                setContentType(contentType.toString());
            }
            closeDisabled = false;
        }

        @Override
        public boolean isRepeatable() {
            return this.content.markSupported();
        }

        @Override
        public long getContentLength() {
            return this.length;
        }

        @Override
        public InputStream getContent() throws IOException {
            return this.content;
        }

        @Override
        public void writeTo(final OutputStream outstream) throws IOException {
            Args.notNull(outstream, "Output stream");
            final InputStream instream = this.content;
            try {
                final byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
                int l;
                if (this.length < 0) {
                    // consume until EOF
                    while ((l = instream.read(buffer)) != -1) {
                        outstream.write(buffer, 0, l);
                    }
                } else {
                    // consume no more than length
                    long remaining = this.length;
                    while (remaining > 0) {
                        l = instream.read(buffer, 0, (int) Math.min(OUTPUT_BUFFER_SIZE, remaining));
                        if (l == -1) {
                            break;
                        }
                        outstream.write(buffer, 0, l);
                        remaining -= l;
                    }
                }
            } finally {
                close();
            }
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

        public void close() {
            if (!closeDisabled) {
                doRelease();
            }
        }

        @Override
        public void release() {
            doRelease();
        }

        private void doRelease() {
            try {
                this.content.close();
            } catch (Exception ex) {
                logException("Unexpected io exception when trying to close input stream", ex);
            }
        }

        public boolean isCloseDisabled() {
            return closeDisabled;
        }

        public void setCloseDisabled(boolean closeDisabled) {
            this.closeDisabled = closeDisabled;
        }
    }
}
