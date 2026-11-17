package jo.joide.build.jvm.packart;

import java.util.ListIterator;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.Type;

/**
 * Neutralise l'emploi de {@code java.lang.management.*} par le
 * compilateur — un paquet qui N'EXISTE pas sur ART (aucun
 * {@code ManagementFactory}, aucun {@code *MXBean}). Il ne se
 * laisse pas stubber (les classes d'app ne peuvent pas vivre dans
 * {@code java.*}) : le bytecode se réécrit.
 *
 * <p>Les quatre classes visées (scan du jar 2.3.0 épinglé) sont de
 * l'observabilité — métriques de perf, thread dumps, veille mémoire —
 * jamais du codegen, les vider est sûr. Les classes du daemon
 * ({@code kotlin.daemon.common.*Profiler*}, sept classes) ne sont
 * pas servies par notre ouvrière (le protocole ligne, pas le daemon) :
 * laissées, notées à l'ADR.</p>
 *
 * <p>Stratégie : remplacement de corps ENTIER (pas de pioche de
 * sites d'appel). Toute méthode qui référence {@code
 * java.lang.management} (par appel, champ typé ou {@code
 * checkcast}/{@code new}) est remplacée par un {@code return <défaut
 * du type>} — un corps vidé ne touche plus le paquet du tout, ce qui
 * écarte les pièges de contrôle de flux et d'assertions Kotlin. Seules
 * les méthodes NON-constructeur sont vidées ; {@code
 * PerformanceManager.initializeCurrentThread()} garde son
 * affectation de fil (lue plus tard par {@code
 * ensureNotFinalizedAndSameThread}) et perd le bean ; le retour non
 * nul de {@code currentTime()} ({@code org.jetbrains.kotlin.util.Time},
 * consommé par le constructeur) rend un vrai {@code new Time(0,0,0)}.</p>
 */
public final class PasseManagement implements Passe {

    private static final String PERFORMANCE_MANAGER
            = "org.jetbrains.kotlin.util.PerformanceManager";

    private static final java.util.Set<String> CIBLES =
            java.util.Set.of(
                    PERFORMANCE_MANAGER,
                    "org.jetbrains.kotlin.build.report.metrics"
                            + ".BuildMetricsReporterKt",
                    "org.jetbrains.kotlin.com.intellij.diagnostic"
                            + ".ThreadDumper",
                    "org.jetbrains.kotlin.com.intellij.openapi.util"
                            + ".LowMemoryWatcherManager$2");

    @Override
    public String nom() {
        return "management-stub";
    }

    @Override
    public boolean cible(String fqn, byte[] octets) {
        return CIBLES.contains(fqn);
    }

    @Override
    public ClassVisitor visiteur(String fqn, ClassVisitor suivant) {
        return new CorpsStubbés(suivant);
    }

    /** Tamponne la classe (API tree) et réécrit les corps à visitEnd. */
    private static final class CorpsStubbés extends ClassNode {
        private final ClassVisitor aval;

        CorpsStubbés(ClassVisitor aval) {
            super(Opcodes.ASM9);
            this.aval = aval;
        }

        @Override
        public void visitEnd() {
            for (MethodNode méthode : methods) {
                if (méthode.name.equals("<init>")
                        || méthode.name.equals("<clinit>")) {
                    continue;
                }
                if (!toucheLeManagement(méthode)) {
                    continue;
                }
                InsnList corps = corpsÉcrit(name, méthode);
                if (corps != null) {
                    appliquer(méthode, corps, 2);
                } else {
                    appliquer(méthode, corpsTrivial(
                            Type.getReturnType(méthode.desc)), 8);
                }
            }
            accept(aval);
        }

        /** La méthode EXÉCUTE-t-elle quelque chose de
         *  {@code java.lang.management} — un appel au paquet ou une
         *  lecture/écriture de champ typé ? (Les références de TYPE
         *  pures — checkcast/new d'un type management — ne vivent
         *  que dans des aides atteintes depuis ces sites : vider les
         *  premières rend les secondes mortes.) */
        private static boolean toucheLeManagement(MethodNode méthode) {
            AbstractInsnNode insn = méthode.instructions.getFirst();
            while (insn != null) {
                boolean touché = insn instanceof MethodInsnNode appel
                        && appel.owner.startsWith("java/lang/management/")
                        || insn instanceof FieldInsnNode champ
                        && (champ.owner.startsWith("java/lang/management/")
                                || champ.desc.contains("Ljava/lang/management/"));
                if (touché) {
                    return true;
                }
                insn = insn.getNext();
            }
            return false;
        }

        /** Quelques méthodes font du travail légitime à côté de
         *  l'appel management qu'on retire — un corps vide perdrait un
         *  état que la classe relit. {@code
         *  PerformanceManager.initializeCurrentThread()} affecte
         *  {@code lateinit var thread} : garder le fil, perdre le
         *  bean. {@code currentTime()} rend un {@code Time} non nul :
         *  {@code new Time(0,0,0)}. Le nom de classe visité est
         *  INTERNE (barres) — comparé sous sa forme à points. */
        private static InsnList corpsÉcrit(String classeInterne,
                                           MethodNode méthode) {
            String classe = classeInterne.replace('/', '.');
            if (classe.equals(PERFORMANCE_MANAGER)
                    && méthode.name.equals("initializeCurrentThread")
                    && méthode.desc.equals("()V")) {
                InsnList corps = new InsnList();
                corps.add(new VarInsnNode(Opcodes.ALOAD, 0));
                corps.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "java/lang/Thread", "currentThread",
                        "()Ljava/lang/Thread;", false));
                corps.add(new FieldInsnNode(Opcodes.PUTFIELD,
                        PERFORMANCE_MANAGER.replace('.', '/'),
                        "thread", "Ljava/lang/Thread;"));
                corps.add(new InsnNode(Opcodes.RETURN));
                return corps;
            }
            if (classe.equals(PERFORMANCE_MANAGER)
                    && Type.getReturnType(méthode.desc)
                            .equals(Type.getObjectType(
                                    "org/jetbrains/kotlin/util/Time"))) {
                InsnList corps = new InsnList();
                corps.add(new TypeInsnNode(Opcodes.NEW,
                        "org/jetbrains/kotlin/util/Time"));
                corps.add(new InsnNode(Opcodes.DUP));
                corps.add(new InsnNode(Opcodes.LCONST_0));
                corps.add(new InsnNode(Opcodes.LCONST_0));
                corps.add(new InsnNode(Opcodes.LCONST_0));
                corps.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                        "org/jetbrains/kotlin/util/Time", "<init>",
                        "(JJJ)V", false));
                corps.add(new InsnNode(Opcodes.ARETURN));
                return corps;
            }
            return null;
        }

        /** Remplace le corps par {@code return <défaut>}. */
        private static void appliquer(MethodNode méthode, InsnList corps,
                                      int maxStack) {
            méthode.instructions = corps;
            méthode.tryCatchBlocks = new java.util.ArrayList<>();
            méthode.localVariables = new java.util.ArrayList<>();
            méthode.visibleLocalVariableAnnotations = null;
            méthode.invisibleLocalVariableAnnotations = null;
            méthode.maxStack = maxStack;
            méthode.maxLocals =
                    (Type.getArgumentsAndReturnSizes(méthode.desc)
                            >>> 2) + 1;
        }

        private static InsnList corpsTrivial(Type retour) {
            InsnList corps = new InsnList();
            switch (retour.getSort()) {
                case Type.VOID ->
                        corps.add(new InsnNode(Opcodes.RETURN));
                case Type.LONG -> {
                    corps.add(new InsnNode(Opcodes.LCONST_0));
                    corps.add(new InsnNode(Opcodes.LRETURN));
                }
                case Type.FLOAT -> {
                    corps.add(new InsnNode(Opcodes.FCONST_0));
                    corps.add(new InsnNode(Opcodes.FRETURN));
                }
                case Type.DOUBLE -> {
                    corps.add(new InsnNode(Opcodes.DCONST_0));
                    corps.add(new InsnNode(Opcodes.DRETURN));
                }
                case Type.OBJECT, Type.ARRAY -> {
                    corps.add(new InsnNode(Opcodes.ACONST_NULL));
                    corps.add(new InsnNode(Opcodes.ARETURN));
                }
                default -> {
                    // boolean/char/byte/short/int
                    corps.add(new InsnNode(Opcodes.ICONST_0));
                    corps.add(new InsnNode(Opcodes.IRETURN));
                }
            }
            return corps;
        }
    }
}
