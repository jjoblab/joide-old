package jo.joide.build.jvm.packart;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Redirige la sonde « où est mon propre jar » du compilateur vers le
 * répertoire de ressources provisionné par le pilote.
 *
 * <p>{@code PathUtil.getResourcePathForClass(Class)} résout le
 * jar/répertoire d'où une classe a été chargée, en mappant la classe
 * vers un chemin de ressource {@code .class}. Un pack de dex n'a
 * AUCUNE ressource {@code .class} : sur ART le regard rend null et
 * la méthode jette. Le compilateur en fait deux choses : chercher
 * {@code $KOTLIN_HOME/lib} (joide n'en a pas besoin — {@code
 * -no-stdlib -no-reflect} et un classpath explicite) et surtout
 * localiser les descripteurs d'extensions XML du registre IntelliJ
 * ({@code META-INF/extensions/*.xml}), qui doivent se lire d'un
 * chemin de système de fichiers RÉEL.</p>
 *
 * <p>Le pilote (KotlinForkedDriver) extrait donc les RESSOURCES du
 * pack (le zip moins les dex) dans {@code <outillage>/kotlin-home/}
 * et passe {@code -Djoide.kotlinc.home=<ce répertoire>} à la VM
 * ouvrière. Le corps réécrit devient {@code return new File(
 * System.getProperty("joide.kotlinc.home", ""))} — la valeur par
 * défaut (répertoire courant, sans descripteurs) dégrade proprement
 * la branche {@code $KOTLIN_HOME/lib}, la seule branche qui exige un
 * vrai chemin reste le registre d'extensions.</p>
 */
public final class PassePathUtil implements Passe {

    private static final String CIBLE
            = "org.jetbrains.kotlin.utils.PathUtil";

    private static final String PROPRIÉTÉ = "joide.kotlinc.home";

    @Override
    public String nom() {
        return "pathutil-self-locate-redirect";
    }

    @Override
    public boolean cible(String fqn, byte[] octets) {
        return CIBLE.equals(fqn);
    }

    @Override
    public ClassVisitor visiteur(String fqn, ClassVisitor suivant) {
        return new CorpsRedirigé(suivant);
    }

    private static final class CorpsRedirigé extends ClassNode {
        private final ClassVisitor aval;

        CorpsRedirigé(ClassVisitor aval) {
            super(Opcodes.ASM9);
            this.aval = aval;
        }

        @Override
        public void visitEnd() {
            for (MethodNode méthode : methods) {
                if (méthode.name.equals("getResourcePathForClass")
                        && méthode.desc.equals(
                        "(Ljava/lang/Class;)Ljava/io/File;")) {
                    InsnList corps = new InsnList();
                    corps.add(new TypeInsnNode(Opcodes.NEW,
                            "java/io/File"));
                    corps.add(new InsnNode(Opcodes.DUP));
                    corps.add(new LdcInsnNode(PROPRIÉTÉ));
                    corps.add(new LdcInsnNode(""));
                    corps.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "java/lang/System", "getProperty",
                            "(Ljava/lang/String;Ljava/lang/String;)"
                                    + "Ljava/lang/String;", false));
                    corps.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                            "java/io/File", "<init>",
                            "(Ljava/lang/String;)V", false));
                    corps.add(new InsnNode(Opcodes.ARETURN));
                    méthode.instructions = corps;
                    méthode.tryCatchBlocks = new java.util.ArrayList<>();
                    méthode.localVariables = new java.util.ArrayList<>();
                    méthode.visibleLocalVariableAnnotations = null;
                    méthode.invisibleLocalVariableAnnotations = null;
                    méthode.maxStack = 3;
                    méthode.maxLocals = 1;
                }
            }
            accept(aval);
        }
    }
}
