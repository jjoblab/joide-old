package jo.joide.build.jvm.artcompat;

/**
 * Les deux statiques {@code java.beans.Introspector} que le
 * compilateur emploie, sans le paquet (absent d'ART — Android ne
 * livre que la tranche {@code PropertyChange*}) : la manière
 * l'IDE de référence ({@code IntrospectorArtPass} → son {@code
 * IntrospectorCompat}). La sémantique de {@code decapitalize} est
 * celle de {@code java.beans.Introspector.decapitalize}, que
 * {@code StringUtil.decapitalize} d'IntelliJ rejoue pour ses
 * propriétés de beans. Cette classe vit DANS le pack.
 */
public final class IntrospectorCompat {

    private IntrospectorCompat() {
    }

    /** {@code Introspector.decapitalize} : « FooBah » → « fooBah »,
     *  « X » → « x », « URL » → « URL » (deux majuscules de suite
     *  restent). */
    public static String decapitalize(String nom) {
        if (nom == null || nom.isEmpty()) {
            return nom;
        }
        if (nom.length() > 1
                && Character.isUpperCase(nom.charAt(0))
                && Character.isUpperCase(nom.charAt(1))) {
            return nom;
        }
        char[] lettres = nom.toCharArray();
        lettres[0] = Character.toLowerCase(lettres[0]);
        return new String(lettres);
    }

    /** Les caches de l'introspecteur n'existent pas ici : rien à
     *  vider (l'appel ne sert qu'à les purger après tests). */
    public static void flushCaches() {
        // rien : aucun cache à vider
    }
}
