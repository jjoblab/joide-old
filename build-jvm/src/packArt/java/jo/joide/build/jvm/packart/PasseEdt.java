package jo.joide.build.jvm.packart;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Fait de {@code EDT.isCurrentThreadEdt()} un {@code return true} —
 * le compilateur en ligne de commande prétend être son propre fil
 * de dispatch.
 *
 * <p>Pourquoi : le vrai corps retombe sur {@code
 * java.awt.EventQueue.isDispatchThread()} quand le champ statique
 * n'est pas posé — et sur ART le champ n'est JAMAIS posé (pas d'AWT)
 * : l'appel jette {@code NoClassDefFoundError: java.awt.EventQueue}.
 * La polarité compte : les assertions {@code
 * ThreadingAssertions.assertEventDispatchThread()} jettent si le
 * retour est faux — retourner vrai fait passer les assertions et
 * écrit/relit les actions synchronement sur le fil appelant, le
 * postulat correct d'un run en ligne de commande (la même polarité
 * que le bureau chez CodeAssist, où l'hôte enregistre son fil comme
 * EDT).</p>
 *
 * <p>Le corps ENTIER est remplacé (l'idiome canonique : écrire le
 * nouveau corps puis rendre null pour sauter l'original via l'API
 * tree), plus aucune référence à {@code java.awt.EventQueue} ne
 * reste dans {@code isCurrentThreadEdt}. Les trois autres classes
 * awt du jar ({@code ThreadingAssertions}, {@code InvocationUtil},
 * {@code EdtInvocationManager$SwingEdtInvocationManager}) appellent
 * EDT ou {@code EventQueue} indirectement : couvertes par cette
 * passe tant qu'elles passent par {@code isCurrentThreadEdt}, notées
 * à l'ADR pour le reste.</p>
 */
public final class PasseEdt implements Passe {

    private static final String CIBLE
            = "org.jetbrains.kotlin.com.intellij.util.ui.EDT";

    @Override
    public String nom() {
        return "edt-headless";
    }

    @Override
    public boolean cible(String fqn, byte[] octets) {
        return CIBLE.equals(fqn);
    }

    @Override
    public ClassVisitor visiteur(String fqn, ClassVisitor suivant) {
        return new CorpsRemplacé(suivant);
    }

    private static final class CorpsRemplacé extends ClassNode {
        private final ClassVisitor aval;

        CorpsRemplacé(ClassVisitor aval) {
            super(Opcodes.ASM9);
            this.aval = aval;
        }

        @Override
        public void visitEnd() {
            for (MethodNode méthode : methods) {
                if (méthode.name.equals("isCurrentThreadEdt")
                        && méthode.desc.equals("()Z")) {
                    InsnList corps = new InsnList();
                    corps.add(new InsnNode(Opcodes.ICONST_1));
                    corps.add(new InsnNode(Opcodes.IRETURN));
                    méthode.instructions = corps;
                    méthode.tryCatchBlocks = new java.util.ArrayList<>();
                    méthode.localVariables =
                            new java.util.ArrayList<>();
                    méthode.visibleLocalVariableAnnotations = null;
                    méthode.invisibleLocalVariableAnnotations = null;
                    méthode.maxStack = 1;
                    méthode.maxLocals = 0;
                }
            }
            accept(aval);
        }
    }
}
