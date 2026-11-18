package jo.joide.app.android.atelier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Lot L3 de l'atelier v2 (ADR-083) — l'état PUR de la section Logcat
 * (§3.6) : la sortie de l'APPLICATION de l'utilisateur (jamais celle
 * de l'IDE). Lignes colorées par niveau, pause du flux, filtre
 * (tag/texte), histogramme du débit (fenêtre glissante, la barre des
 * lignes de niveau E en erreur). Le MÉCANISME de collecte (relais de
 * journal — l'appli utilisateur est un autre UID) se tranche dans
 * l'ADR dédié : cet état reçoit les lignes, peu importe la voie.
 * Aucune vue.
 */
public final class EtatLogcat {

    /** Le plafond annoncé (franchise). */
    public static final int PLAFOND = 400;
    /** Le nombre de barres de l'histogramme du débit (§3.6). */
    public static final int BARRES_HISTOGRAMME = 9;

    /** Le niveau d'une ligne (la lettre du tag logcat). */
    public enum Niveau {
        /** Debug. */
        D,
        /** Information. */
        I,
        /** Avertissement. */
        W,
        /** Erreur. */
        E
    }

    /** Une ligne de logcat (avec son horodatage de réception). */
    public record Ligne(Niveau niveau, String tag, String message,
                        long horodatageMs) {
    }

    /** Le résultat de l'histogramme : la hauteur relative de chaque
     *  barre (0..22 dp) et son drapeau erreur (une barre portant
     *  une ligne de niveau E). */
    public record Histogramme(int[] hauteurs, boolean[] erreurs) {
    }

    /** Les lignes reçues, bornées. */
    private final Deque<Ligne> lignes = new ArrayDeque<>();
    /** La fenêtre de l'histogramme (millisecondes). */
    private final long fenetreMs;
    /** Le flux est-il en pause (bouton ⏸) ? */
    private boolean enPause;
    /** Le filtre texte (tag ou message, insensible à la casse). */
    private String filtre = "";

    /** Fenêtre par défaut : 9 barres ≈ 1 s chacune. */
    public EtatLogcat() {
        this(9000);
    }

    /** @param fenetreMs la fenêtre de l'histogramme. */
    public EtatLogcat(long fenetreMs) {
        this.fenetreMs = fenetreMs;
    }

    /** Une ligne arrive (en pause, le flux SAUTE la ligne — le
     *  comportement attendu d'un ⏸). */
    public void recevoir(Niveau niveau, String tag, String message,
                         long horodatageMs) {
        if (enPause) {
            return;
        }
        lignes.addLast(new Ligne(niveau, tag, message, horodatageMs));
        while (lignes.size() > PLAFOND) {
            lignes.removeFirst();
        }
    }

    /** La pause/repise du flux (bouton ⏸ de la barre d'outils). */
    public void poserPause(boolean pause) {
        enPause = pause;
    }

    /** @return vrai si le flux est en pause. */
    public boolean enPause() {
        return enPause;
    }

    /** Le filtre texte (tag/message). */
    public void poserFiltre(String texte) {
        filtre = texte == null ? "" : texte.toLowerCase(Locale.FRENCH);
    }

    /** @return les lignes visibles (filtre tag/texte appliqué), dans
     *  l'ordre de réception. */
    public List<Ligne> visibles() {
        List<Ligne> visibles = new ArrayList<>();
        for (Ligne ligne : lignes) {
            if (filtre.isEmpty()
                    || ligne.tag().toLowerCase(Locale.FRENCH)
                    .contains(filtre)
                    || ligne.message().toLowerCase(Locale.FRENCH)
                    .contains(filtre)) {
                visibles.add(ligne);
            }
        }
        return visibles;
    }

    /** La DERNIÈRE ligne visible (la zone vivante, colorée par
     *  niveau) ; null si rien (état vide honnête : « Application
     *  non lancée »). */
    public Ligne derniere() {
        List<Ligne> visibles = visibles();
        return visibles.isEmpty() ? null
                : visibles.get(visibles.size() - 1);
    }

    /** L'HISTOGRAMME du débit (§3.6) : les BARRES_HISTOGRAMME barres
     *  couvrent la fenêtre glissante se terminant au « maintenant »
     *  demandé ; chaque barre porte la hauteur relative de son débit
     *  (max 22 dp) et son drapeau erreur (une ligne de niveau E
     *  reçue dans cette barre). */
    public Histogramme histogramme(long maintenantMs) {
        int[] comptes = new int[BARRES_HISTOGRAMME];
        boolean[] erreurs = new boolean[BARRES_HISTOGRAMME];
        long largeurBarre = fenetreMs / BARRES_HISTOGRAMME;
        int total = 0;
        for (Ligne ligne : visibles()) {
            long age = maintenantMs - ligne.horodatageMs();
            if (age < 0 || age >= fenetreMs) {
                continue;
            }
            int barre = (int) (age / largeurBarre);
            // la fenêtre coule : l'horodatage le plus récent = la
            // DERNIÈRE barre (l'âge 0 est à droite, §3.6)
            int position = BARRES_HISTOGRAMME - 1 - barre;
            comptes[position]++;
            total++;
            if (ligne.niveau() == Niveau.E) {
                erreurs[position] = true;
            }
        }
        int[] hauteurs = new int[BARRES_HISTOGRAMME];
        int max = 1;
        for (int compte : comptes) {
            max = Math.max(max, compte);
        }
        for (int i = 0; i < BARRES_HISTOGRAMME; i++) {
            hauteurs[i] = total == 0 ? 0
                    : (int) Math.round(comptes[i] * 22.0 / max);
        }
        return new Histogramme(hauteurs, erreurs);
    }

    /** Vide (bouton ⌫). */
    public void vider() {
        lignes.clear();
    }

    /** @return le nombre de lignes retenues. */
    public int taille() {
        return lignes.size();
    }
}
