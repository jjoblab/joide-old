package jo.joide.build.jvm.artcompat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Le remplacement ART de {@code java.lang.ClassValue} (un type JDK-7
 * absent d'ART) : même contrat, mémoïsation par {@code
 * ConcurrentHashMap} — la manière CodeAssist ({@code
 * ClassValueArtPass}). Les classes réécrites par {@code
 * PasseClassValue} étendent CE type : leur {@code computeValue}
 * (jamais renommé) est appelé une fois par classe par {@link #get},
 * le résultat est mémoïsé jusqu'à {@link #remove}.
 *
 * <p>Cette classe vit DANS le pack (dexée à côté du compilateur
 * patché qui l'appelle) — jamais dans l'APK ni sur le bureau, où le
 * vrai {@code java.lang.ClassValue} reste.</p>
 */
public abstract class ClassValueCompat {

    /** Sentinelle pour les valeurs null (computeValue peut rendre
     *  null — ConcurrentHashMap n'en stocke pas). */
    private static final Object NULL = new Object();

    private final ConcurrentMap<Class<?>, Object[]> valeurs
            = new ConcurrentHashMap<>();

    /** Rend la valeur de cette ClassValue pour la classe donnée :
     *  calculée par {@link #computeValue} au premier accès, gardée
     *  ensuite (le contrat de {@code ClassValue.get}). */
    public final Object get(Class<?> clé) {
        Object[] boîte = valeurs.get(clé);
        if (boîte != null) {
            return boîte[0] == NULL ? null : boîte[0];
        }
        synchronized (this) {
            boîte = valeurs.get(clé);
            if (boîte == null) {
                Object valeur = computeValue(clé);
                boîte = new Object[] {valeur == null ? NULL : valeur};
                valeurs.put(clé, boîte);
            }
        }
        return boîte[0] == NULL ? null : boîte[0];
    }

    /** Le calcul différé (le sous-type du compilateur le redéfinit —
     *  le bytecode réécrit l'appelle par invocation virtuelle). */
    protected Object computeValue(Class<?> clé) {
        return null;
    }

    /** Abandonne la valeur de la classe (le contrat de {@code
     *  ClassValue.remove} : le prochain get recalcule). */
    public void remove(Class<?> clé) {
        valeurs.remove(clé);
    }
}
