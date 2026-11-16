package jo.joide.app.android.atelier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'écriture disque fusionnée multi-fichiers (lot J, ADR-051) : la
 * doctrine du lot F — l'écriture fusionnée, le dernier texte gagne —
 * étendue aux onglets. Chaque frappe pose (fichier, texte) ; le fil
 * écrivain draine les paires EN ATTENTE et écrit chaque fichier avec
 * SON dernier texte : jamais d'entrelacement, jamais de texte d'un
 * fichier écrit dans un autre. Pur, testé JVM.
 *
 * <p>La pose et le drain se font de fils différents : la carte est
 * concurrente ; une pose PENDANT un drain repart au tour suivant
 * (écrite au plus tard au tour d'après) — aucune perte, au pire un
 * tour de retard.</p>
 */
public final class EcrituresFusionnees {

    private final Map<String, String> enAttente =
            new ConcurrentHashMap<>();

    /** Pose le texte courant d'un fichier (le dernier gagne). */
    public void poser(String relatif, String texte) {
        if (relatif == null || relatif.isEmpty() || texte == null) {
            return;
        }
        enAttente.put(relatif, texte);
    }

    /**
     * Draine les écritures en attente : le dernier texte de CHAQUE
     * fichier, l'ordre d'insertion conservé. Le retrait est conditionné
     * à la valeur lue : une pose concomitante repart au tour suivant —
     * jamais perdue.
     */
    public Map<String, String> drainer() {
        Map<String, String> sortie = new LinkedHashMap<>();
        for (Map.Entry<String, String> entree : enAttente.entrySet()) {
            sortie.put(entree.getKey(), entree.getValue());
            enAttente.remove(entree.getKey(), entree.getValue());
        }
        return sortie;
    }

    /** Le nombre d'écritures en attente (sonde des tests). */
    public int enAttente() {
        return enAttente.size();
    }
}
