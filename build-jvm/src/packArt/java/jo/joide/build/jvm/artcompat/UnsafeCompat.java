package jo.joide.build.jvm.artcompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * L'opération {@code sun.misc.Unsafe} qu'ART refuse (API cachée
 * {@code getAndAddInt}, refusée sous targetSdk &gt; 30), recouverte
 * par celles qu'il laisse : la boucle CAS du JDK-8 ({@code
 * getIntVolatile} + {@code compareAndSwapInt}) — la manière
 * l'IDE de référence ({@code UnsafeFindTolerantPass} → son {@code
 * VarHandleCompat.getAndAddInt}).
 *
 * <p>Tout passe par RÉFLEXION : aucune référence de compilation à
 * {@code sun.misc.Unsafe} (un avertissement propriétaire que le
 * build du dépôt transforme en erreur), et l'instance se prend sur
 * le champ {@code theUnsafe} (liste grise, accessible). Le chemin
 * est rare — le read-unlock du TreeBin des {@code
 * Concurrent*ObjectHashMap}, un bin de 8+ clés — le coût par appel
 * d'une invocation réfléchie reste acceptable. Cette classe vit
 * DANS le pack.</p>
 */
public final class UnsafeCompat {

    private static final Object UNSAFE = instanceUnsafe();
    private static final Method INT_VOLATILE
            = méthode("getIntVolatile", Object.class, long.class);
    private static final Method CAS_INT
            = méthode("compareAndSwapInt", Object.class, long.class,
                    int.class, int.class);

    private UnsafeCompat() {
    }

    private static Object instanceUnsafe() {
        try {
            Class<?> classe = Class.forName("sun.misc.Unsafe");
            Field champ = classe.getDeclaredField("theUnsafe");
            champ.setAccessible(true);
            return champ.get(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(
                    "sun.misc.Unsafe indisponible — ART le fournit", e);
        }
    }

    private static Method méthode(String nom, Class<?>... types) {
        try {
            return UNSAFE.getClass().getMethod(nom, types);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(
                    "l'opération " + nom + " manque sur ART", e);
        }
    }

    /** Ajoute {@code delta} au int du champ, atomiquement, et rend
     *  l'ANCIENNE valeur — la sémantique exacte de {@code
     *  Unsafe.getAndAddInt} du JDK-8. */
    public static int getAndAddInt(Object objet, long champ,
                                   int delta) {
        try {
            int courant = (Integer) INT_VOLATILE.invoke(UNSAFE,
                    objet, champ);
            while (!((Boolean) CAS_INT.invoke(UNSAFE, objet, champ,
                    courant, courant + delta))) {
                courant = (Integer) INT_VOLATILE.invoke(UNSAFE,
                        objet, champ);
            }
            return courant;
        } catch (java.lang.reflect.InvocationTargetException
                | IllegalAccessException e) {
            throw new InternalError(
                    "la boucle CAS est impossible sur ART", e);
        }
    }
}
