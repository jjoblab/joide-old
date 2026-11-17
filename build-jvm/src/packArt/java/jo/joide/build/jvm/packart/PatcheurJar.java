package jo.joide.build.jvm.packart;

import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * Le patcheur du pack : applique {@link PassesArt} à un jar du
 * compilateur Kotlin AVANT son dexage (la manière CodeAssist, sa
 * tâche {@code ArtPatchJarTask} — les classes ne passent PAS par la
 * dexation de l'APK, donc pas d'instrumentation AGP). Toute classe
 * ciblée est réécrite, tout le reste est copié tel quel.
 *
 * <p>Les frames et maxs sont recomputés ({@code COMPUTE_FRAMES}) :
 * plusieurs passes remplacent des corps de méthodes et l'écriture
 * sans frames serait invalide. Le processus qui exécute le patcheur
 * porte le jar du compilateur sur son classpath — la résolution des
 * super-classes communes d'ASM ({@code getCommonSuperClass}) y lit
 * les types réels. Les classes des shims ne vivent PAS ici : le
 * pack les dexé à part (tâche {@code shimsArtJar} de build-jvm).</p>
 *
 * <p>Usage : {@code java -cp <patcheur+asm+jars> jo.joide.build.jvm.
 * packart.PatcheurJar <in.jar> <out.jar>} — la sortie est
 * déterministe (horodatages fixes, ordre d'entrée du jar source) ;
 * la tâche d'assainissement du pack re-trie de toute façon.</p>
 */
public final class PatcheurJar {

    private PatcheurJar() {
    }

    /** @param argv {@code <in.jar> <out.jar>} */
    public static void main(String[] argv) throws Exception {
        if (argv.length != 2) {
            System.err.println("usage : PatcheurJar <in.jar> <out.jar>");
            System.exit(2);
        }
        Path entree = Path.of(argv[0]);
        Path sortie = Path.of(argv[1]);
        sortie.getParent().toFile().mkdirs();
        int reecrites = 0;
        int total = 0;
        try (ZipFile zip = new ZipFile(entree.toFile());
             ZipOutputStream zos = new ZipOutputStream(
                     new BufferedOutputStream(
                             Files.newOutputStream(sortie)))) {
            Enumeration<? extends ZipEntry> entrees = zip.entries();
            while (entrees.hasMoreElements()) {
                ZipEntry source = entrees.nextElement();
                if (source.isDirectory()) {
                    continue;
                }
                byte[] octets = zip.getInputStream(source)
                        .readAllBytes();
                String nom = source.getName();
                if (nom.endsWith(".class")) {
                    String fqn = nom.substring(0, nom.length() - 6)
                            .replace('/', '.');
                    if (PassesArt.cible(fqn, octets)) {
                        ClassReader lecteur = new ClassReader(octets);
                        ClassWriter ecrivain = new ClassWriter(lecteur,
                                ClassWriter.COMPUTE_FRAMES);
                        lecteur.accept(
                                PassesArt.visiter(fqn, octets, ecrivain),
                                0);
                        byte[] patchés = ecrivain.toByteArray();
                        if (!Arrays.equals(octets, patchés)) {
                            reecrites++;
                        }
                        octets = patchés;
                        // une classe réécrite ne doit PLUS citer les
                        // APIs absentes d'ART qu'une passe couvre
                        if (contient(octets, "java/lang/ClassValue")) {
                            System.err.println("PatcheurJar : " + fqn
                                    + " cite ENCORE java.lang.ClassValue"
                                    + " après réécriture");
                        }
                    }
                }
                total++;
                ZipEntry copie = new ZipEntry(nom);
                copie.setTime(946684800000L); // 2000-01-01, déterministe
                zos.putNextEntry(copie);
                zos.write(octets);
                zos.closeEntry();
            }
        }
        System.out.println("PatcheurJar : " + reecrites
                + " classe(s) réécrite(s) sur " + total + " — "
                + entree.getFileName());
    }

    /** La sous-séquence d'octets (le motif du pool de constantes) —
     *  un Utf8 du pool est contigu dans le fichier .class. */
    static boolean contient(byte[] octets, String motif) {
        byte[] aiguille = motif.getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        recherche:
        for (int i = 0; i <= octets.length - aiguille.length; i++) {
            for (int j = 0; j < aiguille.length; j++) {
                if (octets[i + j] != aiguille[j]) {
                    continue recherche;
                }
            }
            return true;
        }
        return false;
    }
}
