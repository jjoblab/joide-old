package jo.joide.build.android.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import jo.joide.build.android.sign.ApkSignerService;

/**
 * Signature et vérification d'APK par apksig, en processus. Les clés
 * sont lues d'un fichier de clés JKS/PKCS12 (keytool) ; les schémas
 * activés sont explicites — jamais de signature silencieuse d'un
 * schéma que la variante n'a pas demandée.
 */
public final class ApksigSigner implements ApkSignerService {

    /** Version d'apksig, alignée sur le catalogue de versions du dépôt. */
    public static final String APKSIG_VERSION = "9.5.1";

    private final String version;

    /** Enregistrement ServiceLoader : version du catalogue. */
    public ApksigSigner() {
        this(APKSIG_VERSION);
    }

    /** @param version libellé de version pour l'empreinte des tâches */
    public ApksigSigner(String version) {
        this.version = "apksig:" + version;
    }

    @Override
    public void sign(SigningRequest request) throws IOException {
        try {
            KeyStore store = KeyStore.getInstance(
                    request.keystore.toFile(),
                    request.keystorePassword);
            PrivateKey key = (PrivateKey) store.getKey(request.alias,
                    request.keyPassword);
            if (key == null) {
                throw new IOException("alias not found in keystore: "
                        + request.alias);
            }
            X509Certificate certificate = (X509Certificate)
                    store.getCertificate(request.alias);
            if (certificate == null) {
                throw new IOException("certificate not found for alias: "
                        + request.alias);
            }
            ApkSigner.SignerConfig config = new ApkSigner.SignerConfig
                    .Builder("joide",
                            new com.android.apksig.KeyConfig.Jca(key),
                            List.of(certificate))
                    .build();
            ApkSigner.Builder builder = new ApkSigner.Builder(List.of(config))
                    .setInputApk(request.inputApk.toFile())
                    .setOutputApk(request.outputApk.toFile())
                    .setMinSdkVersion(request.minSdk)
                    .setV1SigningEnabled(request.schemes.contains("v1"))
                    .setV2SigningEnabled(request.schemes.contains("v2"))
                    .setV3SigningEnabled(request.schemes.contains("v3"));
            builder.build().sign();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("signing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Verification verify(Path apk, int minSdk) throws IOException {
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(
                    apk.toFile())
                    .setMinCheckedPlatformVersion(Math.max(1, minSdk))
                    .build()
                    .verify();
            List<String> errors = new ArrayList<>();
            for (ApkVerifier.IssueWithParams issue : result.getErrors()) {
                errors.add(issue.toString());
            }
            return new Verification(result.isVerified(),
                    result.isVerifiedUsingV1Scheme(),
                    result.isVerifiedUsingV2Scheme(),
                    result.isVerifiedUsingV3Scheme(),
                    errors);
        } catch (Exception e) {
            throw new IOException("verification failed: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public String toolVersion() {
        return version;
    }
}
