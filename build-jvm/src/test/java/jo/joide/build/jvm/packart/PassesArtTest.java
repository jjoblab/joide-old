package jo.joide.build.jvm.packart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import jo.joide.build.jvm.artcompat.ClassValueCompat;
import jo.joide.build.jvm.artcompat.IntrospectorCompat;
import jo.joide.build.jvm.artcompat.UnsafeCompat;

/**
 * Les essais du patcheur ART (lot XI) : les classes RÉELLES du
 * compilateur épinglé (classpath de test), réécrites par {@link
 * PassesArt} et relues par ASM — la même lecture que le
 * vérificateur d'ART fera sur l'appareil.
 */
class PassesArtTest {

    /** Patche la classe réelle nommée {@code fqn} (classpath de
     *  test) et rend les octets réécrits. */
    private static byte[] patcher(String fqn) throws Exception {
        byte[] originaux = octets(fqn);
        ClassReader lecteur = new ClassReader(originaux);
        ClassWriter ecrivain = new ClassWriter(lecteur,
                ClassWriter.COMPUTE_FRAMES);
        lecteur.accept(PassesArt.visiter(fqn, originaux, ecrivain), 0);
        return ecrivain.toByteArray();
    }

    /** Les octets d'une classe du compilateur (classpath de test). */
    private static byte[] octets(String fqn) throws Exception {
        try (var flux = PassesArtTest.class.getResourceAsStream(
                "/" + fqn.replace('.', '/') + ".class")) {
            if (flux == null) {
                throw new IllegalStateException(
                        "classe absente du classpath de test : " + fqn);
            }
            return flux.readAllBytes();
        }
    }

    /** Relit un fichier .class par l'API tree. */
    private static ClassNode relire(byte[] octets) {
        ClassNode noeud = new ClassNode();
        new ClassReader(octets).accept(noeud, 0);
        return noeud;
    }

    private static MethodNode méthode(ClassNode classe, String nom) {
        for (MethodNode m : classe.methods) {
            if (m.name.equals(nom)) {
                return m;
            }
        }
        throw new IllegalStateException(
                "méthode absente : " + classe.name + "." + nom);
    }

    private static boolean appelle(AbstractInsnNode début, String nom) {
        AbstractInsnNode insn = début;
        while (insn != null) {
            if (insn instanceof MethodInsnNode appel
                    && appel.owner.equals(nom)) {
                return true;
            }
            insn = insn.getNext();
        }
        return false;
    }

    /** Toute INSTRUCTION management (appel ou champ typé) a disparu
     *  des méthodes du PerformanceManager — les descripteurs de
     *  champs DÉCLARÉS restent (ART ne les résout qu'à l'usage, le
     *  constat CodeAssist). */
    private static boolean citeManagement(ClassNode classe) {
        for (MethodNode méthode : classe.methods) {
            AbstractInsnNode insn = méthode.instructions.getFirst();
            while (insn != null) {
                boolean touché = insn instanceof MethodInsnNode appel
                        && appel.owner.startsWith(
                                "java/lang/management/")
                        || insn instanceof FieldInsnNode champ
                        && (champ.owner.startsWith(
                                        "java/lang/management/")
                                || champ.desc.contains(
                                        "Ljava/lang/management/"));
                if (touché) {
                    return true;
                }
                insn = insn.getNext();
            }
        }
        return false;
    }

    /** La classe mortifère du compilateur est TOLÉRANTE après
     *  réécriture : find attrape tout et rend null, getAndAddInt
     *  délègue au shim CAS. */
    @Test
    void unsafeFindTolérantEtGetAndAddIntShimé() throws Exception {
        String fqn = "org.jetbrains.kotlin.com.intellij.util"
                + ".containers.Unsafe";
        ClassNode classe = relire(patcher(fqn));
        MethodNode find = méthode(classe, "find");
        assertFalse(find.tryCatchBlocks.isEmpty(),
                "find doit attraper Throwable");
        MethodNode getAndAdd = méthode(classe, "getAndAddInt");
        assertTrue(appelle(getAndAdd.instructions.getFirst(),
                        "jo/joide/build/jvm/artcompat/UnsafeCompat"),
                "getAndAddInt doit déléguer au shim");
    }

    /** Le shim CAS marche sur le JDK de test (le même contrat qu'ART
     *  attend : l'ancienne valeur, l'addition posée). */
    @Test
    void leShimGetAndAddIntRendLAncienneValeur() throws Exception {
        Field champ = Boîte.class.getDeclaredField("compteur");
        Object unsafe = unsafeDuJdk();
        Method adresse = unsafe.getClass().getMethod(
                "objectFieldOffset", Field.class);
        Method cas = unsafe.getClass().getMethod("compareAndSwapInt",
                Object.class, long.class, int.class, int.class);
        long offset = (Long) adresse.invoke(unsafe, champ);
        Boîte boîte = new Boîte();
        assertTrue((Boolean) cas.invoke(unsafe, boîte, offset, 0, 41),
                "la pose initiale doit réussir");
        assertEquals(41, UnsafeCompat.getAndAddInt(boîte, offset, 2));
        assertEquals(43, boîte.compteur);
    }

    /** L'instance Unsafe du JDK, par réflexion (aucune référence de
     *  compilation propriétaire — la règle -Werror du dépôt). */
    static Object unsafeDuJdk() throws Exception {
        Class<?> classe = Class.forName("sun.misc.Unsafe");
        Field champ = classe.getDeclaredField("theUnsafe");
        champ.setAccessible(true);
        return champ.get(null);
    }

    static class Boîte {
        int compteur;
    }

    /** Toute référence de TYPE à java.lang.ClassValue disparaît des
     *  deux classes du MessageBus (le champ CACHE du porteur, la
     *  super-classe de la classe interne qui le redéfinit) — au
     *  niveau INSTRUCTION (les entrées orphelines du pool de
     *  constantes d'une réécriture sont inertes, ART ne les résout
     *  jamais). */
    @Test
    void classValueEstRelocaliséPartout() throws Exception {
        String porteur = "org.jetbrains.kotlin.com.intellij.util"
                + ".messages.impl.MethodHandleCache";
        String interne = porteur + "$ConcurrentMapClassValue";
        ClassNode porteurPatché = relire(patcher(porteur));
        assertFalse(citeType(porteurPatché, "java/lang/ClassValue"),
                "une référence de type ClassValue subsiste "
                        + "dans le porteur");
        assertTrue(citeType(porteurPatché,
                        "jo/joide/build/jvm/artcompat/ClassValueCompat"),
                "le shim n'est pas appelé dans le porteur");
        ClassNode internePatchée = relire(patcher(interne));
        assertFalse(citeType(internePatchée, "java/lang/ClassValue"),
                "une référence de type ClassValue subsiste "
                        + "dans l'interne");
        assertEquals("jo/joide/build/jvm/artcompat/ClassValueCompat",
                internePatchée.superName,
                "la super-classe de l'interne doit être le shim");
    }

    /** Une classe cite-t-elle le type donné — appel, champ, new,
     *  checkcast (niveau instruction, pas les orphelins du pool) ? */
    private static boolean citeType(ClassNode classe, String type) {
        for (MethodNode méthode : classe.methods) {
            AbstractInsnNode insn = méthode.instructions.getFirst();
            while (insn != null) {
                boolean touché = insn instanceof MethodInsnNode appel
                        && appel.owner.equals(type)
                        || insn instanceof FieldInsnNode champ
                        && (champ.owner.equals(type)
                                || champ.desc.contains(
                                        "L" + type + ";"))
                        || insn instanceof org.objectweb.asm.tree
                                        .TypeInsnNode typeInsn
                        && typeInsn.desc.equals(type);
                if (touché) {
                    return true;
                }
                insn = insn.getNext();
            }
        }
        return false;
    }

    /** Le shim ClassValueCompat mémoïse par classe : computeValue une
     *  fois, remove oublie. */
    @Test
    void classValueCompatMémoïse() {
        class Cache extends ClassValueCompat {
            int calculs;

            @Override
            protected Object computeValue(Class<?> clé) {
                calculs++;
                return clé.getName();
            }
        }
        Cache cache = new Cache();
        assertEquals(0, cache.calculs, "compteur initial");
        assertEquals("java.lang.String", cache.get(String.class));
        assertEquals("java.lang.String", cache.get(String.class));
        assertEquals(1, cache.calculs, "un seul calcul");
        cache.remove(String.class);
        assertEquals("java.lang.String", cache.get(String.class),
                "après remove : recalculé");
        assertEquals(2, cache.calculs);
    }

    /** Les corps management du PerformanceManager sont vidés : plus
     *  AUCUNE instruction management, initializeCurrentThread garde
     *  le fil, currentTime rend un vrai Time. */
    @Test
    void managementEstVidé() throws Exception {
        String fqn = "org.jetbrains.kotlin.util.PerformanceManager";
        ClassNode classe = relire(patcher(fqn));
        assertFalse(citeManagement(classe),
                "une instruction management subsiste");
        MethodNode initialiser = méthode(classe,
                "initializeCurrentThread");
        assertTrue(appelle(initialiser.instructions.getFirst(), "java/lang/Thread"),
                "l'affectation du fil doit rester");
        MethodNode temps = méthode(classe, "currentTime");
        assertTrue(appelle(temps.instructions.getFirst(),
                        "org/jetbrains/kotlin/util/Time"),
                "currentTime doit rendre un vrai Time");
    }

    /** Les six sites fastutil du parsing sont partis en appels
     *  directs : plus AUCUN invokeinterface fastutil, le shim est
     *  appelé. */
    @Test
    void fastutilEstDispatchéEnVtable() throws Exception {
        String fqn = "org.jetbrains.kotlin.com.intellij.lang.impl"
                + ".MarkerOptionalData";
        byte[] patchés = patcher(fqn);
        ClassNode classe = relire(patchés);
        for (MethodNode méthode : classe.methods) {
            AbstractInsnNode insn = méthode.instructions.getFirst();
            while (insn != null) {
                if (insn instanceof MethodInsnNode appel
                        && appel.getOpcode() == Opcodes.INVOKEINTERFACE
                        && appel.owner.startsWith(
                                "org/jetbrains/kotlin/it/unimi"
                                        + "/dsi/fastutil")) {
                    throw new AssertionError("site interface restant : "
                            + classe.name + "." + méthode.name
                            + " → " + appel.owner + "." + appel.name);
                }
                insn = insn.getNext();
            }
        }
        assertTrue(PatcheurJar.contient(patchés,
                        "jo/joide/build/jvm/artcompat"
                                + "/FastutilDirectCalls"),
                "le shim doit être appelé");
    }

    /** Les deux statiques Introspector partent au shim — instructions
     *  ET références de méthodes (BootstrapMethods), avec la
     *  sémantique exacte du decapitalize du JDK. */
    @Test
    void introspectorEstRelocalisé() throws Exception {
        String fqn = "org.jetbrains.kotlin.com.intellij.openapi.util"
                + ".text.StringUtil";
        byte[] patchés = patcher(fqn);
        assertTrue(PatcheurJar.contient(patchés,
                        "jo/joide/build/jvm/artcompat/IntrospectorCompat"),
                "le shim n'est pas appelé");
        ClassNode classe = relire(patchés);
        assertFalse(citeType(classe, "java/beans/Introspector"),
                "une instruction Introspector subsiste");
        // les références de MÉTHODES vivent dans les arguments de
        // bootstrap des invokedynamic
        for (MethodNode méthode : classe.methods) {
            AbstractInsnNode insn = méthode.instructions.getFirst();
            while (insn != null) {
                if (insn instanceof org.objectweb.asm.tree
                                .InvokeDynamicInsnNode indy) {
                    for (Object argument : indy.bsmArgs) {
                        assertTrue(!(argument instanceof org
                                .objectweb.asm.Handle h)
                                        || !h.getOwner().equals(
                                                "java/beans/Introspector"),
                                "un bootstrap Introspector subsiste");
                    }
                }
                insn = insn.getNext();
            }
        }
        assertEquals("fooBah", IntrospectorCompat.decapitalize("FooBah"));
        assertEquals("x", IntrospectorCompat.decapitalize("X"));
        assertEquals("URL", IntrospectorCompat.decapitalize("URL"));
        assertNull(IntrospectorCompat.decapitalize(null));
    }

    /** EDT prétend être le fil de dispatch (le corps réécrit ne
     *  cite plus awt — les AUTRES méthodes de la classe peuvent
     *  garder des refs awt, résolues paresseusement par ART et
     *  jamais appelées du CLI) ; PathUtil rend le répertoire
     *  provisionné (la propriété, défaut gracieux). */
    @Test
    void edtEtPathUtilSontReécrits() throws Exception {
        String edtFqn = "org.jetbrains.kotlin.com.intellij.util.ui.EDT";
        ClassNode edt = relire(patcher(edtFqn));
        MethodNode isEdt = méthode(edt, "isCurrentThreadEdt");
        assertEquals(2, isEdt.instructions.size(),
                "le corps doit être iconst_1 ; ireturn");
        assertFalse(appelle(isEdt.instructions.getFirst(),
                        "java/awt/EventQueue"),
                "le corps doit ignorer java.awt.EventQueue");

        ClassNode pathUtil = relire(patcher(
                "org.jetbrains.kotlin.utils.PathUtil"));
        MethodNode chemin = méthode(pathUtil, "getResourcePathForClass");
        assertTrue(appelle(chemin.instructions.getFirst(), "java/io/File"),
                "le corps doit rendre un java.io.File");
        assertTrue(appelle(chemin.instructions.getFirst(), "java/lang/System"),
                "le corps doit lire la propriété provisionnée");
    }

    /** Le registre : sept passes, chacune ciblant son domaine. */
    @Test
    void leRegistrePorteSeptPasses() {
        List<Passe> toutes = PassesArt.toutes();
        assertEquals(7, toutes.size());
        assertEquals(List.of("management-stub", "unsafe-find-tolerant",
                "class-value-shim", "fastutil-interface-dispatch",
                "beans-introspector", "edt-headless",
                "pathutil-self-locate-redirect"),
                toutes.stream().map(Passe::nom).toList());
    }

    /** PatcheurJar bout-en-bout : un mini-jar avec une classe réelle
     *  est réécrit, les autres entrées passent intactes. */
    @Test
    void lePatcheurRéécritUnJarBoutEnBout(@TempDir Path dossier)
            throws Exception {
        String fqn = "org.jetbrains.kotlin.com.intellij.util"
                + ".containers.Unsafe";
        Path entrée = dossier.resolve("mini.jar");
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(entrée)))) {
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write("Manifest-Version: 1.0\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(
                    fqn.replace('.', '/') + ".class"));
            zos.write(octets(fqn));
            zos.closeEntry();
        }
        Path sortie = dossier.resolve("mini-art.jar");
        PatcheurJar.main(new String[] {entrée.toString(),
                sortie.toString()});
        try (ZipFile zip = new ZipFile(sortie.toFile())) {
            byte[] classe = zip.getInputStream(
                    zip.getEntry(fqn.replace('.', '/') + ".class"))
                    .readAllBytes();
            assertTrue(PatcheurJar.contient(classe,
                            "jo/joide/build/jvm/artcompat/UnsafeCompat"),
                    "la classe doit être réécrite");
            byte[] manifeste = zip.getInputStream(
                    zip.getEntry("META-INF/MANIFEST.MF")).readAllBytes();
            assertEquals("Manifest-Version: 1.0\n",
                    new String(manifeste),
                    "les entrées non class passent intactes");
        }
    }
}
