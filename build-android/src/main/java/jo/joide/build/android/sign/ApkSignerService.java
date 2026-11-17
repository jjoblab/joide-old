package jo.joide.build.android.sign;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Service de signature et de vérification d'APK. Les implémentations
 * qui parlent à apksig vivent dans le source set tools ; l'interface
 * reste hors de toute référence tierce. Les clés viennent d'un
 * fichier de clés de type JKS/PKCS12 créé par keytool.
 */
public interface ApkSignerService {

    /** Paramètres de signature. */
    final class SigningRequest {
        /** APK en entrée. */
        public final Path inputApk;
        /** APK signé en sortie. */
        public final Path outputApk;
        /** Fichier de clés (JKS ou PKCS12). */
        public final Path keystore;
        /** Mot de passe du fichier de clés. */
        public final char[] keystorePassword;
        /** Alias de la clé. */
        public final String alias;
        /** Mot de passe de la clé (souvent le même que le fichier). */
        public final char[] keyPassword;
        /** Schémas activés (valeurs « v1 », « v2 », « v3 »). */
        public final List<String> schemes;
        /** Niveau d'API minimum (conditionne les avertissements). */
        public final int minSdk;

        /**
         * @param inputApk          APK en entrée
         * @param outputApk         APK signé en sortie
         * @param keystore          fichier de clés
         * @param keystorePassword  mot de passe du fichier
         * @param alias             alias de clé
         * @param keyPassword       mot de passe de la clé
         * @param schemes           schémas activés
         * @param minSdk            niveau d'API minimum
         */
        public SigningRequest(Path inputApk, Path outputApk, Path keystore,
                              char[] keystorePassword, String alias,
                              char[] keyPassword, List<String> schemes,
                              int minSdk) {
            this.inputApk = inputApk;
            this.outputApk = outputApk;
            this.keystore = keystore;
            this.keystorePassword = keystorePassword;
            this.alias = alias;
            this.keyPassword = keyPassword;
            this.schemes = List.copyOf(schemes);
            this.minSdk = minSdk;
        }
    }

    /** Résultat d'une vérification de signature. */
    final class Verification {
        /** Vérification globale réussie. */
        public final boolean verified;
        /** Vérifiée par le schéma V1. */
        public final boolean v1;
        /** Vérifiée par le schéma V2. */
        public final boolean v2;
        /** Vérifiée par le schéma V3. */
        public final boolean v3;
        /** Erreurs rencontrées, éventuellement vide. */
        public final List<String> errors;

        /**
         * @param verified vérification globale
         * @param v1       schéma V1
         * @param v2       schéma V2
         * @param v3       schéma V3
         * @param errors   erreurs
         */
        public Verification(boolean verified, boolean v1, boolean v2,
                            boolean v3, List<String> errors) {
            this.verified = verified;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.errors = List.copyOf(errors);
        }
    }

    /**
     * Signe l'APK.
     *
     * @param request paramètres de signature
     * @throws IOException si la signature échoue
     */
    void sign(SigningRequest request) throws IOException;

    /**
     * Vérifie la signature d'un APK.
     *
     * @param apk    APK à vérifier
     * @param minSdk niveau d'API minimum attendu
     * @return le résultat de la vérification
     * @throws IOException si la lecture échoue
     */
    Verification verify(Path apk, int minSdk) throws IOException;

    /** @return la version de l'outil (pour l'empreinte des tâches) */
    String toolVersion();
}
