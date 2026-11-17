package jo.joide.build.jvm.packart;

import java.util.ListIterator;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Relocalise les deux statiques {@code java.beans.Introspector} que
 * le compilateur appelle — {@code decapitalize(String)} et {@code
 * flushCaches()} — vers le shim ART {@code IntrospectorCompat}.
 *
 * <p>{@code java.beans.Introspector} est absent d'ART (Android ne
 * livre que la tranche {@code PropertyChange*} de {@code
 * java.beans}, pas l'introspection) et ART résout les références à
 * eager : la première exécution d'un {@code INVOKESTATIC java/beans/
 * Introspector.*} jette {@code NoClassDefFoundError}. Trois classes
 * relocalisées du jar épinglé l'appellent (scan) : {@code
 * openapi.util.text.StringUtil}, {@code psi.search.searches
 * .ExtensibleQueryFactory}, {@code psi.util.PropertyUtilBase}. Les
 * deux méthodes sont statiques avec les signatures exactes du shim :
 * seul le propriétaire change, l'effet de pile est identique.</p>
 *
 * <p>La réécriture vit sur l'API TREE pour atteindre DEUX niveaux :
 * les instructions (le {@code INVOKESTATIC} direct) ET les {@code
 * BootstrapMethods} — les références de méthodes {@code
 * Introspector::decapitalize} compilent en invokedynamic dont le
 * cible est un {@code Handle} que le visiteur d'instructions ne voit
 * JAMAIS (constat du débogage de {@code StringUtil}). Détection
 * LARGE (les octets citent {@code java/beans/Introspector}) : un jar
 * qui gagne un utilisateur se couvre seul. Un appel AUTRE que les
 * deux méthodes shimées reste tel quel — la classe était de toute
 * façon cassée sur ART (bean introspection complet hors de portée
 * d'un shim).</p>
 */
public final class PasseIntrospector implements Passe {

    static final String TYPE = "java/beans/Introspector";
    static final String SHIM
            = "jo/joide/build/jvm/artcompat/IntrospectorCompat";

    @Override
    public String nom() {
        return "beans-introspector";
    }

    @Override
    public boolean cible(String fqn, byte[] octets) {
        return PatcheurJar.contient(octets, TYPE);
    }

    @Override
    public ClassVisitor visiteur(String fqn, ClassVisitor suivant) {
        return new SitesRelocalisés(suivant);
    }

    private static final class SitesRelocalisés extends ClassNode {
        private final ClassVisitor aval;

        SitesRelocalisés(ClassVisitor aval) {
            super(Opcodes.ASM9);
            this.aval = aval;
        }

        @Override
        public void visitEnd() {
            for (MethodNode méthode : methods) {
                ListIterator<org.objectweb.asm.tree.AbstractInsnNode>
                        curseur = méthode.instructions.iterator();
                while (curseur.hasNext()) {
                    org.objectweb.asm.tree.AbstractInsnNode insn =
                            curseur.next();
                    if (insn instanceof MethodInsnNode appel
                            && appel.getOpcode()
                                    == Opcodes.INVOKESTATIC
                            && appel.owner.equals(TYPE)) {
                        boolean decapitalize =
                                appel.name.equals("decapitalize")
                                && appel.desc.equals(
                                        "(Ljava/lang/String;)"
                                                + "Ljava/lang/String;");
                        boolean flushCaches =
                                appel.name.equals("flushCaches")
                                && appel.desc.equals("()V");
                        if (decapitalize || flushCaches) {
                            appel.owner = SHIM;
                        }
                    }
                    // les références de MÉTHODES (lambdas, Introspector::
                    // decapitalize) vivent dans les arguments de
                    // bootstrap des invokedynamic (l'API tree d'ASM 9
                    // n'a pas de BootstrapMethodNode : chaque
                    // InvokeDynamicInsnNode porte le sien)
                    if (insn instanceof org.objectweb.asm.tree
                                    .InvokeDynamicInsnNode indy) {
                        Object[] arguments = indy.bsmArgs;
                        for (int i = 0; i < arguments.length; i++) {
                            if (arguments[i] instanceof Handle cible
                                    && cible.getOwner().equals(TYPE)
                                    && (cible.getName()
                                            .equals("decapitalize")
                                            || cible.getName().equals(
                                                    "flushCaches"))) {
                                arguments[i] = new Handle(cible.getTag(),
                                        SHIM, cible.getName(),
                                        cible.getDesc(),
                                        cible.isInterface());
                            }
                        }
                    }
                }
            }
            accept(aval);
        }
    }
}
