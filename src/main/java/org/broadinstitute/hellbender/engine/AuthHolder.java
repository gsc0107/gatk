package org.broadinstitute.hellbender.engine;

import com.google.api.services.genomics.Genomics;
import com.google.api.services.storage.Storage;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.genomics.dataflow.utils.GCSOptions;
import com.google.cloud.genomics.utils.GenomicsFactory;

import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.GeneralSecurityException;

/**
 * Contains the authentication information for this application. This is used when accessing Google services such as GCS
 * or Google Genomics.
 *
 * Conveniently, makeStorageClient() gives you authenticated access to GCS and makeGenomicsService() to Google Genomics.
 */
public class AuthHolder implements Serializable {
    private static final long serialVersionUID = 1l;
    private final String appName;
    private final String apiKey;
    private final byte[] serializedOfflineAuth;

    private AuthHolder(final String appName, final String apiKey, final String secretsFile) throws IOException, GeneralSecurityException {
        Utils.validateArg(apiKey != null || secretsFile != null, "AuthHolder requires apiKey or secretsFile (neither was provided)");
        this.appName = appName;
        this.apiKey = apiKey;
        final GCSOptions options = PipelineOptionsFactory.as(GCSOptions.class);
        if (null!=apiKey) options.setApiKey(apiKey);
        if (null!=secretsFile) options.setSecretsFile(secretsFile);
        // this reads the secrets file
        final GenomicsFactory.OfflineAuth offlineAuth = GCSOptions.Methods.createGCSAuth(options);
        this.serializedOfflineAuth = serializeOfflineAuth(offlineAuth);
    }

    private AuthHolder(final String appName, final String apiKey, final GenomicsFactory.OfflineAuth auth) throws IOException {
        this.appName = appName;
        this.apiKey = apiKey;
        this.serializedOfflineAuth = serializeOfflineAuth(auth);
    }

    public AuthHolder(final String appName, final String apiKey) {
        this.appName = appName;
        this.apiKey = apiKey;
        this.serializedOfflineAuth = null;
    }

    public AuthHolder(final String appName, final GenomicsFactory.OfflineAuth auth) throws IOException {
        this.appName = appName;
        this.apiKey = null;
        this.serializedOfflineAuth = serializeOfflineAuth(auth);
    }

    public AuthHolder(final GCSOptions options) throws IOException, GeneralSecurityException {
        this(options.getAppName(), options.getApiKey(), options.getSecretsFile());
    }

    public String getAppName() {
        return appName;
    }

    public GenomicsFactory.OfflineAuth getOfflineAuth() {
        try {
            if (null==serializedOfflineAuth && apiKey!=null) {
                // fall back to using the API key only (even if a secrets file was also specified).
                final GenomicsFactory.Builder builder =
                        GenomicsFactory.builder(appName);
                return builder.build().getOfflineAuthFromApiKey(apiKey);
            }
            try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(serializedOfflineAuth))) {
                return (GenomicsFactory.OfflineAuth)(is.readObject());
            }
        } catch (IOException | ClassNotFoundException | GeneralSecurityException x) {
            throw new GATKException.ShouldNeverReachHereException("bug in AuthHolder.getOfflineAuth", x);
        }
    }

    /**
     * @return a Storage.Objects, authenticated using the information held in this object.
     */
    public Storage.Objects makeStorageClient() throws IOException {
        final GCSOptions options = PipelineOptionsFactory.as(GCSOptions.class);
        options.setAppName(appName);
        options.setApiKey(apiKey);
        return GCSOptions.Methods.createStorageClient(options, getOfflineAuth());
    }

    /**
     * @return a Genomics, authenticated using the information held in this object.
     */
    public Genomics makeGenomicsService() {
        try {
            final GenomicsFactory.OfflineAuth offlineAuth = getOfflineAuth();
            return offlineAuth.getGenomics(offlineAuth.getDefaultFactory());
        }
        catch ( final GeneralSecurityException e ) {
            throw new UserException("Authentication failed for Google genomics service", e);
        }
        catch ( final IOException e ) {
            throw new UserException("Unable to access Google genomics service", e);
        }
    }

    /**
     * @return a GCSOptions object authenticated with apiKey suitable for accessing files in GCS,
     * or null if no apiKey is present. This code completely ignores the secrets file, which is why
     * you shouldn't be using it. Instead, change the calling code to use AuthHolder directly.
     * (not putting @Deprecated because otherwise we don't compile anymore... the pitfall of -Werr)
     */
    public GCSOptions asPipelineOptionsDeprecated() {
        if (apiKey == null) {
            return null;
        }

        final GCSOptions options = PipelineOptionsFactory.as(GCSOptions.class);
        options.setApiKey(apiKey);
        return options;
    }

    private byte[] serializeOfflineAuth(final GenomicsFactory.OfflineAuth auth) throws IOException {
        if (null==auth) return null;
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(auth);
            oos.flush();
        }
        return os.toByteArray();
    }


}
