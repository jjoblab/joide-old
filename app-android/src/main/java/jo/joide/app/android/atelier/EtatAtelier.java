package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * L'état de l'atelier (lot J, ADR-É5) : onglets multi-fichiers à état
 * faible, surcouches (console dépliée, tiroir, aperçu plein écran),
 * retour système qui ferme les surcouches une à une — jamais un état
 * zombie —, seuil 720 dp et pilule de build. Pur, testé JVM : la vue
 * ne décide rien, elle déroule.
 *
 * <p>L'état des onglets est faible par choix (ADR-É5) : le texte vit
 * sur le disque (écriture fusionnée du lot F, le dernier gagne) ;
 * changer d'onglet rouvre la session celeris du fichier — le coût est
 * le rebranchement, pas une reconstruction de moteur. Le point de
 * modification marque un fichier ÉDITÉ depuis l'ouverture de son
 * onglet (tout est déjà écrit sur le disque : il n'existe jamais de
 * perte possible, le point compte les fichiers touchés de la
 * session).</p>
 *
 * <p>Ordre de fermeture du retour système (ADR-É5, précisé au lot A7
 * de l'ADR-085) : popup ouvert (décision 12 — le popup se ferme EN
 * PREMIER, avant toute surcouche), tiroir agent (la surcouche la plus
 * récente, ADR-É11 point 1), console, tiroir (écran compact seulement :
 * amarré en grand écran, ce n'est pas une surcouche), aperçu plein
 * écran, confirmation de fermeture du projet, accueil — le dernier
 * état ne saute jamais d'étape.</p>
 */
public final class EtatAtelier {

    /** Plafond d'onglets ouverts (ADR-051) : au-delà, éviction honnête. */
    public static final int ONGLETS_MAX = 10;

    /** Seuil adaptatif de largeur utile (dp) — ADR-É5. */
    public static final int SEUIL_GRAND_ECRAN_DP = 720;

    /** Ce que le retour système doit faire — jamais deux choses. */
    public enum ActionRetour {
        /** Fermer le popup ouvert (lot A7, ADR-085 décision 12 : le
         *  popup se ferme EN PREMIER, avant feuille et tiroirs). */
        FERMER_POPUP,
        /** Fermer le tiroir agent (la surcouche la plus récente,
         *  ADR-É11 point 1 — l'ordre d'ADR-É5 le place avant les
         *  surcouches de l'état). */
        FERMER_TIROIR_AGENT,
        /** Replier la console dépliée. */
        FERMER_CONSOLE,
        /** Fermer le tiroir arborescence (écran compact). */
        FERMER_TIROIR,
        /** Quitter l'aperçu plein écran. */
        FERMER_APERCU,
        /** Demander la confirmation de fermeture du projet. */
        DEMANDER_FERMETURE,
        /** Quitter l'atelier (aucun projet monté). */
        QUITTER
    }

    /** Le résultat d'une demande d'ouverture d'onglet. */
    public record Ouverture(boolean faite, String evince, RaisonRefus raison) {

        /** L'ouverture a réussi sans éviction. */
        public static Ouverture ouverte() {
            return new Ouverture(true, null, null);
        }

        /** L'ouverture a réussi en évinçant cet onglet. */
        public static Ouverture avecEviction(String evince) {
            return new Ouverture(true, evince, null);
        }

        /** L'ouverture est refusée, avec sa raison. */
        public static Ouverture refus(RaisonRefus raison) {
            return new Ouverture(false, null, raison);
        }
    }

    /** Pourquoi un onglet ne peut pas s'ouvrir (traduit par la vue). */
    public enum RaisonRefus {
        /** Tous les onglets sont modifiés : aucun candidat à l'éviction. */
        TOUT_MODIFIE
    }

    /** L'état du build courant, tel que la pilule le raconte. */
    public record Build(long numero, String phase, boolean enCours,
                        boolean reussi, boolean interrompu) {
    }

    private final boolean grandEcran;
    private final List<String> onglets = new ArrayList<>();
    private final Set<String> modifies = new LinkedHashSet<>();
    private String actif;
    private boolean tiroirOuvert;
    private boolean consoleDepliee;
    private boolean apercuPleinEcran;
    private boolean voletApercu;
    private boolean projetMonte;
    private Build build;

    /**
     * @param grandEcran vrai à partir du seuil 720 dp de largeur utile
     */
    public EtatAtelier(boolean grandEcran) {
        this.grandEcran = grandEcran;
    }

    /** Vrai à partir du seuil {@link #SEUIL_GRAND_ECRAN_DP} (dp). */
    public static boolean estGrandEcran(int largeurUtileDp) {
        return largeurUtileDp >= SEUIL_GRAND_ECRAN_DP;
    }

    /** L'atelier est-il en disposition grand écran ? */
    public boolean grandEcran() {
        return grandEcran;
    }

    // ------------------------------------------------------------------
    // Onglets
    // ------------------------------------------------------------------

    /**
     * Ouvre un fichier en onglet : déjà ouvert, il s'active ; sinon il
     * s'ajoute en fin de barre. Au plafond {@link #ONGLETS_MAX}, le
     * plus ancien onglet NON MODIFIÉ est évincé (son texte vit sur le
     * disque : rien n'est perdu, le fichier reste dans l'arborescence) ;
     * si tous sont modifiés, l'ouverture est refusée avec sa raison —
     * jamais une perte silencieuse.
     */
    public Ouverture ouvrir(String relatif) {
        if (relatif == null || relatif.isEmpty()) {
            throw new IllegalArgumentException("relatif is required");
        }
        if (onglets.contains(relatif)) {
            actif = relatif;
            return Ouverture.ouverte();
        }
        String evince = null;
        if (onglets.size() >= ONGLETS_MAX) {
            evince = candidatEviction();
            if (evince == null) {
                return Ouverture.refus(RaisonRefus.TOUT_MODIFIE);
            }
            fermer(evince);
        }
        onglets.add(relatif);
        actif = relatif;
        return evince == null ? Ouverture.ouverte()
                : Ouverture.avecEviction(evince);
    }

    private String candidatEviction() {
        for (String onglet : onglets) {
            if (!modifies.contains(onglet)) {
                return onglet;
            }
        }
        return null;
    }

    /**
     * Ferme un onglet. Si c'était l'actif, le voisin de gauche devient
     * actif (sinon celui de droite, sinon plus aucun).
     *
     * @return vrai si l'onglet était ouvert
     */
    public boolean fermer(String relatif) {
        int rang = onglets.indexOf(relatif);
        if (rang < 0) {
            return false;
        }
        onglets.remove(rang);
        modifies.remove(relatif);
        if (relatif.equals(actif)) {
            actif = rang > 0 ? onglets.get(rang - 1)
                    : (onglets.isEmpty() ? null : onglets.get(0));
        }
        return true;
    }

    /** Active un onglet ouvert (un fichier de l'arborescence tapé). */
    public void activer(String relatif) {
        if (!onglets.contains(relatif)) {
            throw new IllegalArgumentException("not an open tab: "
                    + relatif);
        }
        actif = relatif;
    }

    /** Les onglets ouverts, dans l'ordre d'ouverture. */
    public List<String> onglets() {
        return List.copyOf(onglets);
    }

    /** Le fichier actif, ou null si aucun onglet. */
    public String actif() {
        return actif;
    }

    /** Vrai si le fichier est ouvert en onglet. */
    public boolean estOuvert(String relatif) {
        return onglets.contains(relatif);
    }

    /**
     * Marque un fichier édité depuis l'ouverture de son onglet (le
     * point de modification de la barre d'onglets).
     */
    public void marquerModifie(String relatif) {
        if (onglets.contains(relatif)) {
            modifies.add(relatif);
        }
    }

    /** Vrai si le fichier porte son point de modification. */
    public boolean estModifie(String relatif) {
        return modifies.contains(relatif);
    }

    /** Le nombre de fichiers modifiés de la session (fermeture du projet). */
    public int nombreModifies() {
        return modifies.size();
    }

    // ------------------------------------------------------------------
    // Surcouches et retour système
    // ------------------------------------------------------------------

    /** Ouvre le tiroir arborescence. */
    public void ouvrirTiroir() {
        tiroirOuvert = true;
    }

    /** Ferme le tiroir arborescence. */
    public void fermerTiroir() {
        tiroirOuvert = false;
    }

    /** Le tiroir arborescence est-il ouvert (poussé en compact) ? */
    public boolean tiroirOuvert() {
        return tiroirOuvert;
    }

    /** Déplie la console de build (dock). */
    public void deplierConsole() {
        consoleDepliee = true;
    }

    /** Replie la console de build. */
    public void replierConsole() {
        consoleDepliee = false;
    }

    /** Bascule la console. */
    public void basculerConsole() {
        consoleDepliee = !consoleDepliee;
    }

    /** La console est-elle dépliée ? */
    public boolean consoleDepliee() {
        return consoleDepliee;
    }

    /** Bascule l'aperçu en plein écran (surcouche par-dessus l'éditeur). */
    public void basculerApercuPleinEcran() {
        apercuPleinEcran = !apercuPleinEcran;
    }

    /** L'aperçu plein écran est-il actif ? */
    public boolean apercuPleinEcran() {
        return apercuPleinEcran;
    }

    /** Quitte l'aperçu plein écran (retour système). */
    public void fermerApercuPleinEcran() {
        apercuPleinEcran = false;
    }

    /**
     * Le volet d'aperçu côte à côte (grand écran) : ouvert
     * automatiquement quand l'onglet actif est un fichier de mise en
     * page, fermable par sa barre de volet, reproposé tant que
     * l'onglet actif est une mise en page.
     */
    public void surActifEstMiseEnPage(boolean miseEnPage) {
        if (miseEnPage) {
            voletApercu = true;
        }
    }

    /** Ferme le volet d'aperçu côte à côte (l'utilisateur l'a demandé). */
    public void fermerVoletApercu() {
        voletApercu = false;
        apercuPleinEcran = false;
    }

    /** Le volet d'aperçu côte à côte est-il ouvert ? */
    public boolean voletApercu() {
        return voletApercu;
    }

    /** Un projet natif est monté (faux pendant le chargement/Gradle). */
    public boolean projetMonte() {
        return projetMonte;
    }

    /** Marque le projet monté (ou non : erreur, compatibilité Gradle). */
    public void setProjetMonte(boolean monte) {
        projetMonte = monte;
    }

    /**
     * Ce que le retour système fait MAINTENANT : ferme la surcouche la
     * plus haute, une seule — l'ordre d'ADR-É5. Le dialogue ouvert est
     * absorbé par le cadre avant l'activité ; le tiroir n'est pas une
     * surcouche en grand écran (amarré, c'est un réglage du rail).
     */
    public ActionRetour surRetour() {
        if (consoleDepliee) {
            return ActionRetour.FERMER_CONSOLE;
        }
        if (!grandEcran && tiroirOuvert) {
            return ActionRetour.FERMER_TIROIR;
        }
        if (apercuPleinEcran) {
            return ActionRetour.FERMER_APERCU;
        }
        if (projetMonte) {
            return ActionRetour.DEMANDER_FERMETURE;
        }
        return ActionRetour.QUITTER;
    }

    // ------------------------------------------------------------------
    // Build et pilule
    // ------------------------------------------------------------------

    /** Un build démarre : la pilule remplace le FAB. */
    public void commencerBuild(long numero) {
        build = new Build(numero, null, true, false, false);
    }

    /** La phase courante du build (libellé court du lot K). */
    public void phaseBuild(String phase) {
        if (build != null && build.enCours()) {
            build = new Build(build.numero(), phase, true, false, false);
        }
    }

    /** Le build se termine (réussi ou non). */
    public void terminerBuild(boolean reussi) {
        if (build != null) {
            build = new Build(build.numero(), build.phase(), false,
                    reussi, false);
        }
    }

    /** Le build s'interrompt (mort du service : événement, pas zombie). */
    public void interrompreBuild() {
        if (build != null) {
            build = new Build(build.numero(), build.phase(), false,
                    false, true);
        }
    }

    /** Vrai pendant un build (le FAB est pilule). */
    public boolean buildEnCours() {
        return build != null && build.enCours();
    }

    /** L'état du build courant, ou null si aucun build lancé. */
    public Build build() {
        return build;
    }
}
