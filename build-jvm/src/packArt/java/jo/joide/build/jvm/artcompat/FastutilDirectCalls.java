package jo.joide.build.jvm.artcompat;

import org.jetbrains.kotlin.it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.kotlin.it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.kotlin.it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.kotlin.it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Les appels fastutil des classes de parsing, en dispatch PAR VTABLE
 * (appel direct sur la classe concrète après {@code instanceof})
 * plutôt que PAR TABLE D'INTERFACES — Android 12/12L déroute le
 * {@code invokeinterface IntSet.contains(I)Z} vers {@code
 * AbstractIntCollection.removeAll(Collection)} et le marqueur part en
 * référence objet (SIGSEGV dans {@code artInstanceOfFromCode}, le
 * plus gros cluster natif du terrain CodeAssist). La manière
 * CodeAssist ({@code FastutilInterfaceDispatchArtPass} → son {@code
 * FastutilDirectCalls}).
 *
 * <p>Le récepteur arrive en premier argument ({@code
 * PasseFastutil} réécrit {@code invokeinterface} en {@code
 * invokestatic} avec le même effet de pile). Tout récepteur qui
 * n'est pas l'implémentation concrète attendue garde le repli du
 * MÊME appel interface — correct partout, déroutable seulement là
 * où le bug d'ART le permettait déjà. Les types sont les
 * RELOCALISÉS du pack ({@code org.jetbrains.kotlin.it.unimi…}).</p>
 */
public final class FastutilDirectCalls {

    private FastutilDirectCalls() {
    }

    public static boolean intSetContains(IntSet ensemble, int valeur) {
        if (ensemble instanceof IntOpenHashSet) {
            return ((IntOpenHashSet) ensemble).contains(valeur);
        }
        return ensemble.contains(valeur);
    }

    public static boolean intSetAdd(IntSet ensemble, int valeur) {
        if (ensemble instanceof IntOpenHashSet) {
            return ((IntOpenHashSet) ensemble).add(valeur);
        }
        return ensemble.add(valeur);
    }

    public static boolean intSetRemove(IntSet ensemble, int valeur) {
        if (ensemble instanceof IntOpenHashSet) {
            return ((IntOpenHashSet) ensemble).remove(valeur);
        }
        return ensemble.remove(valeur);
    }

    public static Object int2ObjectGet(Int2ObjectMap<Object> carte,
                                       int clé) {
        if (carte.getClass() == Int2ObjectOpenHashMap.class) {
            return concrète(carte).get(clé);
        }
        return carte.get(clé);
    }

    public static Object int2ObjectPut(Int2ObjectMap<Object> carte,
                                       int clé, Object valeur) {
        if (carte.getClass() == Int2ObjectOpenHashMap.class) {
            return concrète(carte).put(clé, valeur);
        }
        return carte.put(clé, valeur);
    }

    public static Object int2ObjectRemove(Int2ObjectMap<Object> carte,
                                          int clé) {
        if (carte.getClass() == Int2ObjectOpenHashMap.class) {
            return concrète(carte).remove(clé);
        }
        return carte.remove(clé);
    }

    /** Le récepteur est un {@code Int2ObjectOpenHashMap} (testé par
     *  {@code getClass} exact) — le typage générique de la vue
     *  importe peu au dispatch. */
    @SuppressWarnings("unchecked")
    private static Int2ObjectOpenHashMap<Object> concrète(
            Int2ObjectMap<Object> carte) {
        return (Int2ObjectOpenHashMap<Object>) carte;
    }
}
