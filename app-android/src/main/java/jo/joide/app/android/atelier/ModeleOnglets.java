package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lot L2 de l'atelier v2 (ADR-083) — le modèle PUR de la bande
 * d'onglets (§3.2 du prompt v2). Aucune vue : l'état des onglets
 * ouverts, l'onglet actif, les pastilles de type (Java « J » ambre,
 * Kotlin « K » violet, XML « ‹› » réussite, config « ⚙ » accent), le
 * point de diagnostic (erreur) et le point « modifié » (alerte), le
 * recalcul de l'actif après fermeture. La vue lit ce modèle et se
 * dessine depuis lui ; le moteur de build ne le touche jamais.
 */
public final class ModeleOnglets {

    /** Le type de pastille d'un onglet (tonale, §3.2). */
    public enum Badge {
        /** Java : « J », ambre. */
        JAVA("J"),
        /** Kotlin : « K », violet. */
        KOTLIN("K"),
        /** Mise en page XML : « ‹› », réussite. */
        XML("‹›"),
        /** Configuration (toml, properties…) : « ⚙ », accent. */
        CONFIG("⚙");

        /** Le glyphe de la pastille. */
        public final String glyphe;

        Badge(String glyphe) {
            this.glyphe = glyphe;
        }

        /** Le badge du nom de fichier (extension décide). */
        public static Badge de(String nomFichier) {
            String nom = nomFichier == null ? "" : nomFichier;
            String bas = nom.toLowerCase();
            if (bas.endsWith(".java")) {
                return JAVA;
            }
            if (bas.endsWith(".kt") || bas.endsWith(".kts")) {
                return KOTLIN;
            }
            if (bas.endsWith(".xml")) {
                return XML;
            }
            return CONFIG;
        }
    }

    /** Un onglet : chemin, nom affiché, badges et points. */
    public static final class EtatOnglet {
        /** Le chemin complet du fichier. */
        public final String chemin;
        /** Le nom affiché (nom de fichier). */
        public final String nom;
        /** La pastille de type. */
        public final Badge badge;
        /** Le fichier est modifié (point alerte). */
        public final boolean modifie;
        /** Un diagnostic erreur pointe dans ce fichier (point erreur). */
        public final boolean erreur;

        public EtatOnglet(String chemin, String nom, Badge badge,
                          boolean modifie, boolean erreur) {
            this.chemin = Objects.requireNonNull(chemin);
            this.nom = Objects.requireNonNull(nom);
            this.badge = badge == null ? Badge.CONFIG : badge;
            this.modifie = modifie;
            this.erreur = erreur;
        }
    }

    /** Les onglets ouverts, dans l'ordre d'affichage. */
    private final List<EtatOnglet> onglets = new ArrayList<>();
    /** Le chemin de l'onglet actif (null si aucun). */
    private String actif;

    /** @return les onglets, dans l'ordre. */
    public List<EtatOnglet> onglets() {
        return new ArrayList<>(onglets);
    }

    /** @return le chemin de l'onglet actif, ou null. */
    public String actif() {
        return actif;
    }

    /** Ouvre (ou active) l'onglet du chemin demandé. */
    public void ouvrir(String chemin, String nom, boolean modifie,
                       boolean erreur) {
        for (int i = 0; i < onglets.size(); i++) {
            EtatOnglet existant = onglets.get(i);
            if (existant.chemin.equals(chemin)) {
                onglets.set(i, new EtatOnglet(chemin, nom,
                        existant.badge, modifie, erreur));
                actif = chemin;
                return;
            }
        }
        onglets.add(new EtatOnglet(chemin, nom,
                Badge.de(nom), modifie, erreur));
        actif = chemin;
    }

    /** Met à jour les points d'un onglet ouvert (aucun effet s'il
     *  n'est pas ouvert). */
    public void mettreAJour(String chemin, boolean modifie,
                            boolean erreur) {
        for (int i = 0; i < onglets.size(); i++) {
            EtatOnglet existant = onglets.get(i);
            if (existant.chemin.equals(chemin)) {
                onglets.set(i, new EtatOnglet(chemin, existant.nom,
                        existant.badge, modifie, erreur));
            }
        }
    }

    /** Active l'onglet demandé (aucun effet s'il est absent). */
    public void activer(String chemin) {
        for (EtatOnglet onglet : onglets) {
            if (onglet.chemin.equals(chemin)) {
                actif = chemin;
                return;
            }
        }
    }

    /** Ferme l'onglet demandé ; l'actif devient le voisin à gauche,
     *  sinon le premier restant, sinon null.
     *  @return le chemin devenu actif, ou null (plus d'onglet). */
    public String fermer(String chemin) {
        int index = -1;
        for (int i = 0; i < onglets.size(); i++) {
            if (onglets.get(i).chemin.equals(chemin)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return actif;
        }
        onglets.remove(index);
        if (onglets.isEmpty()) {
            actif = null;
            return null;
        }
        if (actif != null && actif.equals(chemin)) {
            // le voisin à GAUCHE (le voisin du premier : le premier
            // restant), §3.2
            int voisin = Math.min(Math.max(index - 1, 0),
                    onglets.size() - 1);
            actif = onglets.get(voisin).chemin;
        }
        return actif;
    }

    /** Ferme tous les onglets SAUF celui-ci (menu d'appui long). */
    public void fermerAutres(String chemin) {
        actif = chemin;
        onglets.removeIf(onglet -> !onglet.chemin.equals(chemin));
    }

    /** @return l'index de l'onglet actif (-1 si aucun). */
    public int indexActif() {
        if (actif == null) {
            return -1;
        }
        for (int i = 0; i < onglets.size(); i++) {
            if (onglets.get(i).chemin.equals(actif)) {
                return i;
            }
        }
        return -1;
    }
}
