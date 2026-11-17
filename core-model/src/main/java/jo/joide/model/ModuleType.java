package jo.joide.model;

/**
 * Type d'un module : la nature de ce qu'il produit.
 *
 * <p>Les types standards couvrent les cibles du noyau ; d'autres
 * types peuvent être déclarés par identifiant, l'extensibilité est
 * le contrat.</p>
 */
public interface ModuleType {

    /** @return l'identifiant du type, en anglais et minuscules */
    String id();

    /** Bibliothèque Java pure, sans Android. */
    ModuleType JVM_LIBRARY = constant("jvm-library");

    /** Bibliothèque Android (archive de ressources + classes). */
    ModuleType ANDROID_LIBRARY = constant("android-library");

    /** Application Android (paquet installable). */
    ModuleType ANDROID_APPLICATION = constant("android-application");

    /**
     * @param id identifiant du type : lettres minuscules, chiffres et
     *           tirets
     * @return le type correspondant
     * @throws IllegalArgumentException si l'identifiant est mal formé
     */
    static ModuleType of(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9-]{0,30}")) {
            throw new IllegalArgumentException("invalid module type: " + id);
        }
        return constant(id);
    }

    private static ModuleType constant(String id) {
        return () -> id;
    }
}
