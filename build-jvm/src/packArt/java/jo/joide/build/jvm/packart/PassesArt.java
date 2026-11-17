package jo.joide.build.jvm.packart;

import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * Le registre ORDONNÉ des passes ART appliquées au pack du
 * compilateur Kotlin (lot XI — la manière CodeAssist, ses passes
 * {@code kotlinc-art}, portées sur les classes RELOCALISÉES de
 * {@code kotlin-compiler-embeddable 2.3.0}). Chaque passe ne naît que
 * pour un breakage ART confirmé — par le scan du jar épinglé
 * ({@code scan_art_hostile}) ou par le terrain.
 *
 * <p>Les passes à détection large (ClassValue, Introspector)
 * relisent leur motif sur les octets BRUTS de la classe — pas de
 * liste de cibles à maintenir : un jar qui gagne une nouvelle classe
 * touchée se couvre seul. Les passes chirurgicales (management,
 * Unsafe, fastutil, EDT, PathUtil) visent des FQNs exacts.</p>
 */
public final class PassesArt {

    private static final List<Passe> TOUTES = List.of(
            new PasseManagement(),
            new PasseUnsafeFind(),
            new PasseClassValue(),
            new PasseFastutil(),
            new PasseIntrospector(),
            new PasseEdt(),
            new PassePathUtil());

    private PassesArt() {
    }

    /** Vrai si AU MOINS une passe réécrit cette classe. */
    public static boolean cible(String fqn, byte[] octets) {
        for (Passe passe : TOUTES) {
            if (passe.cible(fqn, octets)) {
                return true;
            }
        }
        return false;
    }

    /** Chaîne toutes les passes qui visent la classe devant le
     *  {@code ClassWriter} — l'ordre de déclaration tient. Soulève
     *  si aucune ne cible (l'appelant vérifie d'abord {@link
     *  #cible}). */
    public static ClassVisitor visiter(String fqn, byte[] octets,
                                       ClassWriter writer) {
        ClassVisitor chainé = writer;
        boolean auMoinsUne = false;
        for (Passe passe : TOUTES) {
            if (passe.cible(fqn, octets)) {
                chainé = passe.visiteur(fqn, chainé);
                auMoinsUne = true;
            }
        }
        if (!auMoinsUne) {
            throw new IllegalStateException(
                    "aucune passe pour une classe ciblée : " + fqn);
        }
        return chainé;
    }

    /** Les passes, pour le journal du patcheur. */
    public static List<Passe> toutes() {
        return TOUTES;
    }
}
