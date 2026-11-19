package jo.joide.build.jvm.packart;

import org.objectweb.asm.ClassVisitor;

/**
 * Une réécriture de compatibilité ART appliquée au bytecode du
 * compilateur Kotlin avant le dexage du pack (la manière éprouvée,
 * ses passes {@code kotlinc-art}) : le compilateur est construit pour
 * un JVM de bureau et emploie quelques APIs absentes ou cassées sur
 * ART — {@code java.lang.management}, {@code java.lang.ClassValue},
 * {@code java.beans.Introspector}, {@code java.awt.EventQueue}, le
 * dispatch d'interface fastutil d'Android 12, le {@code find} strict
 * du wrapper Unsafe d'IntelliJ, la localisation de son propre jar.
 * Chaque passe déclare les classes qu'elle touche et rend un visiteur
 * qui réécrit ; tout le reste passe sans coût.
 *
 * <p>La découverte est EMPIRIQUE, pas devinée : le scan des pools de
 * constantes du jar épinglé ({@code scan_art_hostile}) dit quelles
 * classes portent quels motifs ; une passe ne naît que pour un
 * breakage confirmé. Les méthodes réécrites à corps remplacé
 * recomputent frames et maxs (le patcheur écrit avec
 * {@code COMPUTE_FRAMES} et tourne avec le jar du compilateur sur
 * son classpath).</p>
 */
public interface Passe {

    /** Un identifiant court pour le journal du patcheur. */
    String nom();

    /**
     * @param fqn    nom pleinement qualifié (points) de la classe
     * @param octets les octets BRUTS du fichier .class — une passe à
     *               détection large (ClassValue, Introspector) y
     *               cherche son motif de pool de constantes
     * @return vrai si cette passe réécrit la classe
     */
    boolean cible(String fqn, byte[] octets);

    /**
     * Le visiteur réécrivain, chaîné DEVANT {@code suivant} (le
     * {@code ClassWriter} final). Appelé seulement si {@link
     * #cible} a répondu vrai.
     */
    ClassVisitor visiteur(String fqn, ClassVisitor suivant);
}
