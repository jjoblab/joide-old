package jo.joide.ide.langages;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jo.codeide.lsp.model.Empreintes;
import jo.codeide.lsp.model.LecteurStatistiquesSysteme;
import jo.codeide.lsp.model.ModuleLsp;
import jo.codeide.lsp.model.ProjetLsp;
import jo.codeide.lsp.model.ClasspathInstantane;
import jo.codeide.lsp.model.EntreeClasspathInstantane;
import jo.codeide.lsp.model.KindEntree;

/**
 * Union indexable des classpaths d'un projet — le périmètre réellement
 * indexé par CIDX : les entrées JAR et AAR des classpaths de TOUS les
 * modules (première occurrence par empreinte gardée) plus la plateforme
 * {@code android.jar} de chaque module actif (même déduplication).
 *
 * <p>Règle du §6.8.5 : CIDX est RÉUTILISÉ, jamais réécrit — cette classe
 * ne fait que composer le périmètre, exactement comme l'hôte de
 * référence de la brique (OrchestrateurLsp, unionIndexable).</p>
 */
final class UnionIndexable {

    private final ClasspathInstantane union;

    private UnionIndexable(ClasspathInstantane union) {
        this.union = union;
    }

    /** Construit l'union indexable d'un projet. */
    static UnionIndexable de(ProjetLsp projet) {
        LecteurStatistiquesSysteme stats = new LecteurStatistiquesSysteme();
        Map<String, EntreeClasspathInstantane> entrees =
                new LinkedHashMap<>();
        for (ModuleLsp module : projet.getModules()) {
            for (EntreeClasspathInstantane entree
                    : module.getClasspath().getEntrees()) {
                if (entree.getKind() == KindEntree.JAR
                        || entree.getKind() == KindEntree.AAR) {
                    entrees.putIfAbsent(entree.getEmpreinte(), entree);
                }
            }
        }
        for (ModuleLsp module : projet.getModules()) {
            if (module.getIgnore()) {
                continue;
            }
            String plateforme = module.getAndroidJar();
            if (plateforme == null || !stats.existe(plateforme)) {
                continue;
            }
            String empreinte = Empreintes.INSTANCE.empreinteFichier(
                    plateforme, stats);
            entrees.putIfAbsent(empreinte, new EntreeClasspathInstantane(
                    KindEntree.JAR, plateforme, null, null, null,
                    empreinte, true, stats.taille(plateforme),
                    stats.mtime(plateforme)));
        }
        List<EntreeClasspathInstantane> liste = new ArrayList<>(
                entrees.values());
        return new UnionIndexable(new ClasspathInstantane(liste,
                empreinteDe(liste)));
    }

    /** L'union en instantané du modèle (consommable par CIDX). */
    ClasspathInstantane instantane() {
        return union;
    }

    /** Nombre d'entrées de l'union. */
    int taille() {
        return union.getEntrees().size();
    }

    /** Empreinte sha256 des identités des entrées (clé de changement). */
    String empreinte() {
        return union.getEmpreinte();
    }

    /** Budget mémoire auto de CIDX : max(30 Mo, 512 Kio par entrée). */
    long budgetAuto() {
        return Math.max(BUDGET_MIN_OCTETS,
                OCTETS_PAR_ENTREE * Math.max(1, taille()));
    }

    private static String empreinteDe(List<EntreeClasspathInstantane> l) {
        try {
            MessageDigest condense =
                    MessageDigest.getInstance("SHA-256");
            for (EntreeClasspathInstantane entree : l) {
                condense.update((entree.getKind() + "|"
                        + entree.getChemin() + "|"
                        + entree.getEmpreinte() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hex = new StringBuilder();
            for (byte octet : condense.digest()) {
                hex.append(String.format("%02x", octet));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Budget plancher de CIDX (30 Mo, contrat de la brique). */
    private static final long BUDGET_MIN_OCTETS = 30L * 1024L * 1024L;

    /** Budget par entrée indexée (512 Kio, leçon banc F2 de la brique). */
    private static final long OCTETS_PAR_ENTREE = 512L * 1024L;
}
