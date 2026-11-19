package jo.joide.build.jvm.packart;

import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Prend le constructeur d'arbres PSI d'ART et son dispatch
 * d'interface fastutil, en routant chaque {@code invokeinterface}
 * sur {@code IntSet}/{@code Int2ObjectMap} des classes de parsing
 * vers le shim à appel direct {@code FastutilDirectCalls}
 * (récepteur en premier argument).
 *
 * <p>Pourquoi : le plus gros cluster de crash natif du terrain
 * l'IDE de référence est un SIGSEGV dans {@code artInstanceOfFromCode} sous
 * {@code PsiBuilderImpl.bind} → {@code ProductionMarker.isCollapsed},
 * Android 12/12L seulement. {@code isCollapsed} est UN {@code
 * invokeinterface IntSet.contains(I)Z} sur un {@code IntOpenHashSet},
 * et la frame suivante du tombstone est
 * {@code AbstractIntCollection.removeAll(Collection)} au dex pc 0 :
 * ART a dérouté {@code contains(int)} vers {@code
 * removeAll(Collection)} en lui passant l'id du marqueur comme
 * référence objet. Le shim fait un {@code instanceof} sur la classe
 * concrète puis un {@code invokevirtual} — dispatch par vtable,
 * jamais par la table d'interfaces ; tout AUTRE récepteur garde le
 * repli du même appel interface (correct, non dérouté).</p>
 *
 * <p>Le parsing de CHAQUE compilation Kotlin passe par {@code
 * PsiBuilderImpl} — les classes visées (scan du jar 2.3.0 épinglé :
 * {@code MarkerOptionalData} porte exactement les six sites ;
 * {@code PsiBuilderImpl$MyTreeStructure} n'en porte aucun, il reste
 * couvert si un site apparaît). Tout appel fastutil NON cartographié
 * dans une classe visée échoue le patch — un site nouveau se voit
 * ici, pas sur un appareil.</p>
 */
public final class PasseFastutil implements Passe {

    static final String SET = "org/jetbrains/kotlin/it/unimi"
            + "/dsi/fastutil/ints/IntSet";
    static final String MAP = "org/jetbrains/kotlin/it/unimi"
            + "/dsi/fastutil/ints/Int2ObjectMap";
    static final String SHIM
            = "jo/joide/build/jvm/artcompat/FastutilDirectCalls";

    /** « propriétaire.nom+descripteur » → la méthode du shim (le
     *  récepteur devient le premier argument ; l'effet de pile est
     *  identique). */
    static final Map<String, String> SHIMS = Map.of(
            SET + ".contains(I)Z", "intSetContains",
            SET + ".add(I)Z", "intSetAdd",
            SET + ".remove(I)Z", "intSetRemove",
            MAP + ".get(I)Ljava/lang/Object;", "int2ObjectGet",
            MAP + ".put(ILjava/lang/Object;)Ljava/lang/Object;",
            "int2ObjectPut",
            MAP + ".remove(I)Ljava/lang/Object;", "int2ObjectRemove");

    private static final java.util.Set<String> CIBLES = java.util.Set.of(
            "org.jetbrains.kotlin.com.intellij.lang.impl"
                    + ".MarkerOptionalData",
            "org.jetbrains.kotlin.com.intellij.lang.impl"
                    + ".PsiBuilderImpl$MyTreeStructure");

    @Override
    public String nom() {
        return "fastutil-interface-dispatch";
    }

    @Override
    public boolean cible(String fqn, byte[] octets) {
        return CIBLES.contains(fqn);
    }

    @Override
    public ClassVisitor visiteur(String fqn, ClassVisitor suivant) {
        return new ClassVisitor(Opcodes.ASM9, suivant) {
            @Override
            public MethodVisitor visitMethod(int accès, String nom,
                                             String desc, String sig,
                                             String[] exceptions) {
                MethodVisitor mv = super.visitMethod(accès, nom, desc,
                        sig, exceptions);
                if (mv == null) {
                    return null;
                }
                return new Reécrivain(mv, fqn + "." + nom + desc);
            }
        };
    }

    private static final class Reécrivain extends MethodVisitor {
        private final String où;

        Reécrivain(MethodVisitor suivant, String où) {
            super(Opcodes.ASM9, suivant);
            this.où = où;
        }

        @Override
        public void visitMethodInsn(int opcode, String propriétaire,
                                    String nom, String desc,
                                    boolean interface_) {
            if (opcode != Opcodes.INVOKEINTERFACE
                    || !propriétaire.startsWith(
                            "org/jetbrains/kotlin/it/unimi/dsi/fastutil/")) {
                super.visitMethodInsn(opcode, propriétaire, nom, desc,
                        interface_);
                return;
            }
            String shim = SHIMS.get(propriétaire + "." + nom + desc);
            if (shim == null) {
                throw new IllegalStateException(où + " appelle "
                        + propriétaire + "." + nom + desc
                        + " par invokeinterface, sans shim dans "
                        + SHIM + " — ajoutez-le avant le dexage");
            }
            super.visitMethodInsn(Opcodes.INVOKESTATIC, SHIM, shim,
                    "(L" + propriétaire + ";" + desc.substring(1),
                    false);
        }
    }
}
