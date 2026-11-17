package jo.joide.build.jvm.packart;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Rend le wrapper {@code com.intellij.util.containers.Unsafe}
 * relocalisé initialisable sur ART.
 *
 * <p>Son {@code <clinit>} résout UN MethodHandle par opération
 * {@code sun.misc.Unsafe} qu'il enrobe (via {@code find}, qui jette
 * une {@code java.lang.Error} au premier échec). ART fournit neuf
 * des dix ({@code compareAndSwap*}, {@code get*Volatile},
 * {@code putObjectVolatile}, {@code getAndAddInt},
 * {@code objectFieldOffset}, {@code arrayBaseOffset/IndexScale}) mais
 * PAS le {@code copyMemory(Object,long,Object,long,long)} : la
 * classe entière échouait à s'initialiser, et avec elle
 * {@code ConcurrentLongObjectHashMap.<clinit>} puis tout
 * l'environnement du compilateur — aucun build Kotlin ne pouvait
 * démarrer.</p>
 *
 * <p>Correctifs, la manière CodeAssist (sa {@code
 * UnsafeFindTolerantPass}) : le corps de {@code find} est enveloppé
 * d'un catch-tout qui rend {@code null} au lieu de jeter (les neuf
 * handles se résolvent normalement — le chemin rapide des
 * MethodHandles reste, {@code copyMemory} devient un handle nul) ;
 * et {@code getAndAddInt} — l'API cachée que ART refuse sous
 * targetSdk &gt; 30, EMPLOYÉE par le TreeBin des deux
 * {@code Concurrent*ObjectHashMap} — part sur la boucle CAS du shim
 * {@code UnsafeCompat} ({@code getIntVolatile} +
 * {@code compareAndSwapInt}, les opérations ART laisse passer). Le
 * {@code copyMemory} du wrapper rendrait null puis NPE — rien du
 * chemin de compilation ne l'appelle (même constat qu'aa-runtime
 * chez CodeAssist : leur shim ne l'implémente pas du tout).</p>
 */
public final class PasseUnsafeFind implements Passe {

    private static final String CIBLE
            = "org.jetbrains.kotlin.com.intellij.util.containers.Unsafe";

    private static final String DESC_GET_AND_ADD
            = "(Ljava/lang/Object;JI)I";

    @Override
    public String nom() {
        return "unsafe-find-tolerant";
    }

    @Override
    public boolean cible(String fqn, byte[] octets) {
        return CIBLE.equals(fqn);
    }

    @Override
    public ClassVisitor visiteur(String fqn, ClassVisitor suivant) {
        return new WrapperTolérant(suivant);
    }

    /** Tamponne la classe, enveloppe {@code find} et remplace
     *  {@code getAndAddInt} à visitEnd. */
    private static final class WrapperTolérant extends ClassNode {
        private final ClassVisitor aval;

        WrapperTolérant(ClassVisitor aval) {
            super(Opcodes.ASM9);
            this.aval = aval;
        }

        @Override
        public void visitEnd() {
            for (MethodNode méthode : methods) {
                if (méthode.name.equals("find")
                        && méthode.desc.equals(
                        "(Ljava/lang/String;Ljava/lang/Class;"
                                + "[Ljava/lang/Class;)"
                                + "Ljava/lang/invoke/MethodHandle;")) {
                    envelopper(méthode);
                }
                if (méthode.name.equals("getAndAddInt")
                        && méthode.desc.equals(DESC_GET_AND_ADD)) {
                    remplacerParShim(méthode);
                }
            }
            accept(aval);
        }

        /** try { <corps original> } catch (Throwable) { return null } —
         *  un handle non résolu, pas une Error. */
        private static void envelopper(MethodNode méthode) {
            LabelNode début = new LabelNode();
            LabelNode fin = new LabelNode();
            LabelNode rattrape = new LabelNode();
            méthode.instructions.insertBefore(
                    méthode.instructions.getFirst(), début);
            méthode.instructions.add(fin);
            méthode.instructions.add(rattrape);
            méthode.instructions.add(new InsnNode(Opcodes.POP));
            méthode.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            méthode.instructions.add(new InsnNode(Opcodes.ARETURN));
            méthode.tryCatchBlocks = méthode.tryCatchBlocks == null
                    ? new java.util.ArrayList<>()
                    : méthode.tryCatchBlocks;
            méthode.tryCatchBlocks.add(new TryCatchBlockNode(début, fin,
                    rattrape, "java/lang/Throwable"));
            // les frames sont recomputés par le ClassWriter (les
            // types du corps se résolvent sur le classpath du
            // patcheur) ; le handler tient dans 1 d'empilement
            if (méthode.maxStack < 1) {
                méthode.maxStack = 1;
            }
        }

        /** this.getAndAddInt(o, champ, delta) → UnsafeCompat. */
        private static void remplacerParShim(MethodNode méthode) {
            méthode.instructions = new InsnList();
            méthode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            méthode.instructions.add(new VarInsnNode(Opcodes.LLOAD, 1));
            méthode.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
            méthode.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "jo/joide/build/jvm/artcompat/UnsafeCompat",
                    "getAndAddInt", DESC_GET_AND_ADD, false));
            méthode.instructions.add(new InsnNode(Opcodes.IRETURN));
            méthode.tryCatchBlocks = new java.util.ArrayList<>();
            méthode.localVariables = new java.util.ArrayList<>();
            méthode.maxStack = 4;
            méthode.maxLocals = 4;
        }
    }
}
