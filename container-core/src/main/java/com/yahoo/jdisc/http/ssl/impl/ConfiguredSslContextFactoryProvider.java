// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.ClientAuth;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.AutoReloadingX509KeyManager;
import com.yahoo.security.tls.TlsContext;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.jdisc.http.ssl.impl.SslContextFactoryUtils.setEnabledCipherSuites;
import static com.yahoo.jdisc.http.ssl.impl.SslContextFactoryUtils.setEnabledProtocols;

/**
 * An implementation of {@link SslContextFactoryProvider} that uses the {@link ConnectorConfig} to construct a {@link SslContextFactory}.
 *
 * @author bjorncs
 */
public class ConfiguredSslContextFactoryProvider implements SslContextFactoryProvider {

    private volatile AutoReloadingX509KeyManager keyManager;
    private final ConnectorConfig connectorConfig;

    public ConfiguredSslContextFactoryProvider(ConnectorConfig connectorConfig) {
        validateConfig(connectorConfig.ssl());
        this.connectorConfig = connectorConfig;
    }

    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        ConnectorConfig.Ssl sslConfig = connectorConfig.ssl();
        if (!sslConfig.enabled()) throw new IllegalStateException();

        SslContextBuilder builder = new SslContextBuilder();
        if (sslConfig.certificateFile().isBlank() || sslConfig.privateKeyFile().isBlank()) {
            PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(getPrivateKey(sslConfig));
            List<X509Certificate> certificates = X509CertificateUtils.certificateListFromPem(getCertificate(sslConfig));
            builder.withKeyStore(privateKey, certificates);
        } else {
            keyManager = AutoReloadingX509KeyManager.fromPemFiles(Paths.get(sslConfig.privateKeyFile()), Paths.get(sslConfig.certificateFile()));
            builder.withKeyManager(keyManager);
        }
        List<X509Certificate> caCertificates = getCaCertificates(sslConfig)
                .map(X509CertificateUtils::certificateListFromPem)
                .orElse(List.of());
        builder.withTrustStore(caCertificates);

        SSLContext sslContext = builder.build();

        SslContextFactory.Server factory = new SslContextFactory.Server();
        factory.setSslContext(sslContext);

        factory.setNeedClientAuth(sslConfig.clientAuth() == ClientAuth.Enum.NEED_AUTH);
        factory.setWantClientAuth(sslConfig.clientAuth() == ClientAuth.Enum.WANT_AUTH);

        List<String> protocols = !sslConfig.enabledProtocols().isEmpty()
                ? sslConfig.enabledProtocols()
                : new ArrayList<>(TlsContext.getAllowedProtocols(sslContext));
        setEnabledProtocols(factory, sslContext, protocols);

        List<String> ciphers = !sslConfig.enabledCipherSuites().isEmpty()
                ? sslConfig.enabledCipherSuites()
                : new ArrayList<>(TlsContext.getAllowedCipherSuites(sslContext));
        setEnabledCipherSuites(factory, sslContext, ciphers);

        return factory;
    }

    @Override
    public void close() {
        if (keyManager != null) {
            keyManager.close();
        }
    }

    private static void validateConfig(ConnectorConfig.Ssl config) {
        if (!config.enabled()) return;

        if(hasBoth(config.certificate(), config.certificateFile()))
            throw new IllegalArgumentException("Specified both certificate and certificate file.");

        if(hasBoth(config.privateKey(), config.privateKeyFile()))
            throw new IllegalArgumentException("Specified both private key and private key file.");

        if(hasNeither(config.certificate(), config.certificateFile()))
            throw new IllegalArgumentException("Specified neither certificate or certificate file.");

        if(hasNeither(config.privateKey(), config.privateKeyFile()))
            throw new IllegalArgumentException("Specified neither private key or private key file.");
    }

    private static boolean hasBoth(String a, String b) { return !a.isBlank() && !b.isBlank(); }
    private static boolean hasNeither(String a, String b) { return a.isBlank() && b.isBlank(); }

    private static Optional<String> getCaCertificates(ConnectorConfig.Ssl sslConfig) {
        if (!sslConfig.caCertificate().isBlank()) {
            return Optional.of(sslConfig.caCertificate());
        } else if (!sslConfig.caCertificateFile().isBlank()) {
            return Optional.of(readToString(sslConfig.caCertificateFile()));
        } else {
            return Optional.empty();
        }
    }

    private static String getPrivateKey(ConnectorConfig.Ssl config) {
        if(!config.privateKey().isBlank()) return config.privateKey();
        return readToString(config.privateKeyFile());
    }

    private static String getCertificate(ConnectorConfig.Ssl config) {
        if(!config.certificate().isBlank()) return config.certificate();
        return readToString(config.certificateFile());
    }

    private static String readToString(String filename) {
        try {
            return Files.readString(Paths.get(filename), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
