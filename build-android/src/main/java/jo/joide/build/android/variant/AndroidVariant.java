package jo.joide.build.android.variant;

import java.util.Objects;

/**
 * Une variante de build Android : type de build (debug, release…),
 * identifiant d'application suffixé, versions, rétrécissement R8 et
 * schémas de signature. La variante est une valeur immuable ; deux
 * variantes ne différant d'aucune entrée produisent les mêmes sorties.
 */
public final class AndroidVariant {

    /** Nom du type de build (par ex. « debug », « release »). */
    public final String buildType;
    /** Identifiant d'application complet. */
    public final String applicationId;
    /** Code de version entier, ou -1 si absent. */
    public final int versionCode;
    /** Nom de version, ou {@code null}. */
    public final String versionName;
    /** Niveau d'API minimum. */
    public final int minSdk;
    /** Niveau d'API cible. */
    public final int targetSdk;
    /** Rétrecir avec R8 (release). */
    public final boolean minifyEnabled;
    /** Signature V1 (JAR) activée. */
    public final boolean v1Signing;
    /** Signature V2 activée. */
    public final boolean v2Signing;
    /** Signature V3 activée. */
    public final boolean v3Signing;
    /** Fichier de clés de signature, ou {@code null} (non signé). */
    public final String keystoreFile;
    /** Alias de la clé de signature, ou {@code null}. */
    public final String keystoreAlias;

    AndroidVariant(Builder b) {
        this.buildType = b.buildType;
        this.applicationId = b.applicationId;
        this.versionCode = b.versionCode;
        this.versionName = b.versionName;
        this.minSdk = b.minSdk;
        this.targetSdk = b.targetSdk;
        this.minifyEnabled = b.minifyEnabled;
        this.v1Signing = b.v1Signing;
        this.v2Signing = b.v2Signing;
        this.v3Signing = b.v3Signing;
        this.keystoreFile = b.keystoreFile;
        this.keystoreAlias = b.keystoreAlias;
    }

    /**
     * @param applicationId identifiant de base du paquet
     * @param buildType     nom du type de build
     * @return un bâtisseur avec les valeurs par défaut
     */
    public static Builder builder(String applicationId, String buildType) {
        return new Builder(applicationId, buildType);
    }

    /** Bâtisseur de variante. */
    public static final class Builder {
        private final String applicationId;
        private final String buildType;
        private int versionCode = 1;
        private String versionName = "1.0";
        private int minSdk = 28;
        private int targetSdk = 35;
        private boolean minifyEnabled;
        private boolean v1Signing;
        private boolean v2Signing = true;
        private boolean v3Signing;
        private String keystoreFile;
        private String keystoreAlias;

        private Builder(String applicationId, String buildType) {
            this.applicationId = Objects.requireNonNull(applicationId);
            this.buildType = Objects.requireNonNull(buildType);
        }

        /** @param versionCode code de version
         * @return ce bâtisseur */
        public Builder versionCode(int versionCode) {
            this.versionCode = versionCode;
            return this;
        }

        /** @param versionName nom de version
         * @return ce bâtisseur */
        public Builder versionName(String versionName) {
            this.versionName = versionName;
            return this;
        }

        /** @param minSdk niveau d'API minimum
         * @return ce bâtisseur */
        public Builder minSdk(int minSdk) {
            this.minSdk = minSdk;
            return this;
        }

        /** @param targetSdk niveau d'API cible
         * @return ce bâtisseur */
        public Builder targetSdk(int targetSdk) {
            this.targetSdk = targetSdk;
            return this;
        }

        /** @param minifyEnabled activer R8
         * @return ce bâtisseur */
        public Builder minifyEnabled(boolean minifyEnabled) {
            this.minifyEnabled = minifyEnabled;
            return this;
        }

        /** @param v1 signature V1
         * @return ce bâtisseur */
        public Builder v1Signing(boolean v1) {
            this.v1Signing = v1;
            return this;
        }

        /** @param v2 signature V2
         * @return ce bâtisseur */
        public Builder v2Signing(boolean v2) {
            this.v2Signing = v2;
            return this;
        }

        /** @param v3 signature V3
         * @return ce bâtisseur */
        public Builder v3Signing(boolean v3) {
            this.v3Signing = v3;
            return this;
        }

        /** @param keystoreFile fichier de clés
         * @return ce bâtisseur */
        public Builder keystoreFile(String keystoreFile) {
            this.keystoreFile = keystoreFile;
            return this;
        }

        /** @param keystoreAlias alias de clé
         * @return ce bâtisseur */
        public Builder keystoreAlias(String keystoreAlias) {
            this.keystoreAlias = keystoreAlias;
            return this;
        }

        /** @return la variante */
        public AndroidVariant build() {
            return new AndroidVariant(this);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AndroidVariant)) {
            return false;
        }
        AndroidVariant that = (AndroidVariant) other;
        return buildType.equals(that.buildType)
                && applicationId.equals(that.applicationId)
                && versionCode == that.versionCode
                && Objects.equals(versionName, that.versionName)
                && minSdk == that.minSdk
                && targetSdk == that.targetSdk
                && minifyEnabled == that.minifyEnabled
                && v1Signing == that.v1Signing
                && v2Signing == that.v2Signing
                && v3Signing == that.v3Signing
                && Objects.equals(keystoreFile, that.keystoreFile)
                && Objects.equals(keystoreAlias, that.keystoreAlias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildType, applicationId, versionCode,
                versionName, minSdk, targetSdk, minifyEnabled, v1Signing,
                v2Signing, v3Signing, keystoreFile, keystoreAlias);
    }

    @Override
    public String toString() {
        return "AndroidVariant[" + buildType + " " + applicationId
                + " minSdk=" + minSdk + " minify=" + minifyEnabled + "]";
    }
}
