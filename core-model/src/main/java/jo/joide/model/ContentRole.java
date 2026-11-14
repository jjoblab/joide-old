package jo.joide.model;

/**
 * Rôle d'une racine de contenu : ce qu'elle contient, sans lien avec
 * un domaine particulier.
 *
 * <p>Les rôles standards couvrent les usages connus ; d'autres rôles
 * peuvent être déclarés par identifiant.</p>
 */
public interface ContentRole {

    /** @return l'identifiant du rôle, en anglais et minuscules */
    String id();

    /** Sources compilables. */
    ContentRole SOURCE = constant("source");

    /** Ressources Java classiques. */
    ContentRole RESOURCES = constant("resources");

    /** Ressources Android (mises en page, valeurs, dessins). */
    ContentRole ANDROID_RES = constant("android-res");

    /** Définitions AIDL. */
    ContentRole AIDL = constant("aidl");

    /** Éléments embarqués tels quels dans le paquet. */
    ContentRole ASSETS = constant("assets");

    /** Bibliothèques natives par ABI. */
    ContentRole JNI_LIBS = constant("jniLibs");

    /** Sources produites par un outil. */
    ContentRole GENERATED = constant("generated");

    /** Contenu ignoré par le build. */
    ContentRole EXCLUDED = constant("excluded");

    /**
     * @param id identifiant du rôle : lettres minuscules, chiffres et
     *           tirets
     * @return le rôle correspondant
     * @throws IllegalArgumentException si l'identifiant est mal formé
     */
    static ContentRole of(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9-]{0,30}")) {
            throw new IllegalArgumentException("invalid content role: " + id);
        }
        return constant(id);
    }

    private static ContentRole constant(String id) {
        return () -> id;
    }
}
