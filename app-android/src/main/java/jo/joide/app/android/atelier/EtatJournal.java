package jo.joide.app.android.atelier;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Lot L3 de l'atelier v2 (ADR-083) — l'état PUR de la section
 * Journal (§3.6) : le journal de l'IDE LUI-MÊME (INDEX, SYNC, AGENT,
 * MCP, BUILD — jamais un message de build ni de LSP, leurs sections
 * ont leurs sources). Événements bornés (plafond 400), filtre de
 * niveau. Aucune vue.
 */
public final class EtatJournal {

    /** Les sources du journal de l'IDE. */
    public enum Source {
        /** L'indexation du projet. */
        INDEX,
        /** La synchronisation des modèles de langage. */
        SYNC,
        /** Les tours de l'agent. */
        AGENT,
        /** Les serveurs MCP (alerte si échec). */
        MCP,
        /** Le service :build (démarré/arrêté). */
        BUILD
    }

    /** Le niveau de gravité d'un événement. */
    public enum Niveau {
        /** Information (défaut). */
        INFO,
        /** Avertissement (filtrable). */
        AVERTISSEMENT,
        /** Erreur (filtrable). */
        ERREUR
    }

    /** Un événement du journal. */
    public record Evenement(Source source, Niveau niveau,
                            String message, String heure) {
    }

    /** Le plafond annoncé (franchise : le plafond se DIT). */
    public static final int PLAFOND = 400;

    /** Les événements, les plus récents EN DERNIER (le transcript
     *  lit dans l'ordre). */
    private final Deque<Evenement> evenements = new ArrayDeque<>();

    /** Le filtre de niveau courant (Tout par défaut). */
    private Niveau filtre = null;

    /** Ajoute un événement (borné au plafond : les plus anciens
     *  sortent) ; l'heure est posée ici (la vue ne décide rien). */
    public void ajouter(Source source, Niveau niveau, String message) {
        evenements.addLast(new Evenement(source, niveau, message,
                LocalTime.now().toString().substring(0, 8)));
        while (evenements.size() > PLAFOND) {
            evenements.removeFirst();
        }
    }

    /** Le filtre de niveau (null = tout). */
    public void poserFiltre(Niveau demandee) {
        filtre = demandee;
    }

    /** @return les événements visibles (filtre appliqué), dans
     *  l'ordre chronologique. */
    public List<Evenement> visibles() {
        List<Evenement> visibles = new ArrayList<>();
        for (Evenement evenement : evenements) {
            if (filtre == null
                    || evenement.niveau() == Niveau.ERREUR
                    || (filtre == Niveau.AVERTISSEMENT
                    && evenement.niveau() == Niveau.AVERTISSEMENT)) {
                visibles.add(evenement);
            }
        }
        return visibles;
    }

    /** Le DERNIER événement (la zone vivante de l'en-tête, §3.6). */
    public Evenement dernier() {
        return evenements.peekLast();
    }

    /** Vide le journal (bouton de la barre d'outils). */
    public void vider() {
        evenements.clear();
    }

    /** @return le nombre d'événements retenus. */
    public int taille() {
        return evenements.size();
    }
}
