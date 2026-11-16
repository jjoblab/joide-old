package jo.joide.app.android.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.deps.json.JsonValue;

/**
 * L'état du panneau console de build (lot K, ADR-É6) : transcript
 * plafonné et plafond ANNOCÉ, compte d'erreurs exact, bande de phases
 * du pipeline avec durées mesurées, filtres « Tout / Erreurs (n) /
 * Système », état de l'en-tête (vide, en cours, réussi, échoué,
 * interrompu) et résumé final chiffré — jamais un « succès » sans
 * mesures. Pur, testé JVM : la vue ne décide rien.
 *
 * <p>Franchise du lot F conservée : les écarts s'ajoutent AVANT le
 * lancement (« jamais d'omission muette »), « Rien à construire »
 * reste un refus motivé, la mort du service est un événement
 * (interrompu), pas un zombie.</p>
 */
public final class EtatConsole {

    /** Le filtre du transcript. */
    public enum Filtre {
        /** Toutes les lignes. */
        TOUT,
        /** Les lignes d'erreur seulement (compteur exact). */
        ERREURS,
        /** Les lignes système seulement. */
        SYSTEME
    }

    /** L'état du build raconté par la pilule d'en-tête. */
    public enum Etat {
        /** Aucun build dans cette session. */
        VIDE,
        /** Un build tourne. */
        EN_COURS,
        /** Le dernier build a réussi. */
        REUSSI,
        /** Le dernier build a échoué. */
        ECHOUE,
        /** Le service est mort : événement, jamais un zombie. */
        INTERROMPU
    }

    /** Une phase de la bande : état et durée mesurée. */
    public record PhaseEtat(ConsoleBuild.Phase phase, boolean commencee,
                            boolean terminee, long dureeMs) {
    }

    private static final ConsoleBuild.Phase[] ORDRE_PHASES = {
            ConsoleBuild.Phase.PREPARATION, ConsoleBuild.Phase.COMPILATION,
            ConsoleBuild.Phase.ASSEMBLAGE};

    private final List<ConsoleBuild.Ligne> lignes = new ArrayList<>();
    private final List<PhaseEtat> phases = new ArrayList<>();
    private Filtre filtre = Filtre.TOUT;
    private ConsoleBuild.Phase filtrePhase;
    private Etat etat = Etat.VIDE;
    private long numeroBuild = -1;
    private long debutBuildMs = -1;
    private long debutPhaseMs = -1;
    private long resumeWallMillis = -1;
    private boolean plafonne;

    /**
     * L'événement du démon du protocole P6 : LE point d'entrée de la
     * console v2 — tâches, phases, bilan, refus, tout passe ici
     * (l'écran ne formate rien).
     *
     * @param type          le type d'événement (« task »…)
     * @param id            l'identifiant de requête lié
     * @param corps         le cadre complet de l'événement
     * @param maintenantMs  l'instant de réception (durées mesurées)
     */
    public void surEvenement(String type, long id, JsonValue corps,
                             long maintenantMs) {
        String typeSur = type == null ? "" : type;
        if ("task".equals(typeSur)) {
            surTache(corps.member("name").asString(),
                    corps.member("status").asString(), maintenantMs);
            return;
        }
        if ("build-finished".equals(typeSur)) {
            List<String> problemes = new ArrayList<>();
            for (JsonValue probleme : corps.member("problems").items()) {
                problemes.add(probleme.asString());
            }
            surTermine(id, corps.member("success").asBoolean(),
                    corps.member("executed").asLong(),
                    corps.member("fromCache").asLong(),
                    corps.member("upToDate").asLong(),
                    corps.member("wallMillis").asNumber() == null ? -1L
                            : corps.member("wallMillis").asLong(),
                    problemes, maintenantMs);
            return;
        }
        if ("build-started".equals(typeSur)) {
            surDemarrage(id, maintenantMs);
            return;
        }
        for (ConsoleBuild.Ligne ligne : ConsoleBuild.structurer(typeSur,
                id, corps)) {
            ajouter(ligne);
        }
    }

    /** Les écarts s'affichent AVANT le lancement (conservation lot F). */
    public void surEcarts(List<String> ecarts) {
        if (ecarts == null || ecarts.isEmpty()) {
            return;
        }
        ajouter(ConsoleBuild.Ligne.phase(
                "écarts — jamais d'omission muette", null));
        for (String ecart : ecarts) {
            ajouter(ConsoleBuild.Ligne.systeme(ecart));
        }
    }

    /** « Rien à construire » : un refus motivé, pas une ligne vague. */
    public void surRienAConstruire() {
        etat = Etat.ECHOUE;
        ajouter(ConsoleBuild.Ligne.erreur(
                "rien à construire (voir les écarts ci-dessus)",
                null, null));
    }

    /** Un build démarre (le numéro vient du compteur de l'atelier). */
    public void surDemarrage(long numero, long maintenantMs) {
        numeroBuild = numero;
        etat = Etat.EN_COURS;
        debutBuildMs = maintenantMs;
        resumeWallMillis = -1;
        phases.clear();
        ajouter(ConsoleBuild.Ligne.systeme(
                "build #" + numero + " démarré"));
    }

    /** Le mode en processus s'annonce à chaque lancement (lot F). */
    public void surAnnonceEnProcessus() {
        ajouter(ConsoleBuild.Ligne.systeme("build EN PROCESSUS (annoncé)"));
    }

    /**
     * Un événement de tâche : la phase se met à jour (bande de
     * phases), la ligne rejoint le transcript.
     */
    public void surTache(String nom, String statut, long maintenantMs) {
        ConsoleBuild.Phase phase = ConsoleBuild.phaseDe(nom);
        if (phase != null) {
            majPhases(phase, maintenantMs);
        }
        String texte = "  " + nom + " : " + statutLisible(statut);
        ajouter("FAILED".equals(statut)
                ? ConsoleBuild.Ligne.erreur(texte.trim(), nom, null)
                : ConsoleBuild.Ligne.tache(texte.trim(), phase, nom));
    }

    private void majPhases(ConsoleBuild.Phase phase, long maintenantMs) {
        PhaseEtat ouverte = phaseOuverte();
        if (ouverte != null && ouverte.phase() == phase) {
            return;
        }
        if (ouverte != null) {
            remplacer(new PhaseEtat(ouverte.phase(), true, true,
                    Math.max(0L, maintenantMs - debutPhaseMs)));
        }
        for (ConsoleBuild.Phase attendue : ORDRE_PHASES) {
            if (chercher(attendue) == null) {
                phases.add(new PhaseEtat(attendue, attendue == phase,
                        false, 0L));
            }
        }
        PhaseEtat cible = chercher(phase);
        if (cible != null && !cible.commencee()) {
            remplacer(new PhaseEtat(phase, true, false, 0L));
        }
        debutPhaseMs = maintenantMs;
    }

    private PhaseEtat phaseOuverte() {
        for (PhaseEtat etatPhase : phases) {
            if (etatPhase.commencee() && !etatPhase.terminee()) {
                return etatPhase;
            }
        }
        return null;
    }

    private PhaseEtat chercher(ConsoleBuild.Phase phase) {
        for (PhaseEtat etatPhase : phases) {
            if (etatPhase.phase() == phase) {
                return etatPhase;
            }
        }
        return null;
    }

    private void remplacer(PhaseEtat neuve) {
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).phase() == neuve.phase()) {
                phases.set(i, neuve);
                return;
            }
        }
    }

    /** Le build se termine : résumé chiffré + problèmes en lignes. */
    public void surTermine(long numero, boolean reussi,
                           long executees, long duCache, long aJour,
                           long wallMillis, List<String> problemes,
                           long maintenantMs) {
        terminerPhases(maintenantMs);
        numeroBuild = numero;
        resumeWallMillis = wallMillis;
        etat = reussi ? Etat.REUSSI : Etat.ECHOUE;
        StringBuilder resume = new StringBuilder("build #").append(numero)
                .append(reussi ? " réussi" : " échoué")
                .append(" — ")
                .append(executees).append(" ")
                .append(pluriel(executees, "exécutée", "exécutées"))
                .append(" · ").append(duCache).append(" du cache · ")
                .append(aJour).append(" ").append(pluriel(aJour,
                        "à jour", "à jour"));
        if (wallMillis >= 0) {
            resume.append(" · ").append(secondes(wallMillis));
        }
        ajouter(ConsoleBuild.Ligne.systeme(resume.toString()));
        for (String probleme : problemes == null ? List.<String>of()
                : problemes) {
            ajouter(ConsoleBuild.Ligne.erreur(probleme,
                    ConsoleBuild.tacheDe(probleme),
                    ConsoleBuild.cibleDe(probleme)));
        }
    }

    /** Le mode en processus rend son bilan par la même porte. */
    public void surBilanEnProcessus(long numero, boolean reussi,
                                    long executees, long duCache,
                                    long aJour, List<String> problemes,
                                    long maintenantMs) {
        surTermine(numero, reussi, executees, duCache, aJour, -1,
                problemes, maintenantMs);
    }

    /** La mort du service : un événement, l'UI ne meurt pas avec lui. */
    public void surInterrompu() {
        terminerPhases(debutBuildMs < 0 ? 0L : debutBuildMs);
        etat = Etat.INTERROMPU;
        ajouter(ConsoleBuild.Ligne.erreur("build interrompu : le service "
                + "est mort, l'IDE survit ; le prochain build le relance",
                null, null));
    }

    /** Le service de build est injoignable (avant tout lancement). */
    public void surInjoignable() {
        etat = Etat.INTERROMPU;
        ajouter(ConsoleBuild.Ligne.erreur(
                "service de build injoignable — relancez le build",
                null, null));
    }

    /**
     * Vide le transcript et la bande de phases (le bouton « Vider »)
     * — le build en cours, lui, continue d'écrire : son état est
     * conservé (numéro, phase courante, en-cours) et les lignes
     * reviennent avec les événements suivants.
     */
    public void vider() {
        boolean enCours = etat == Etat.EN_COURS;
        ConsoleBuild.Phase phase = phaseCourante();
        long numero = numeroBuild;
        lignes.clear();
        phases.clear();
        plafonne = false;
        if (enCours) {
            etat = Etat.EN_COURS;
            numeroBuild = numero;
            debutPhaseMs = -1;
            if (phase != null) {
                majPhases(phase, System.currentTimeMillis());
            }
        } else {
            etat = Etat.VIDE;
            numeroBuild = -1;
            debutBuildMs = -1;
            debutPhaseMs = -1;
            resumeWallMillis = -1;
        }
    }

    private void terminerPhases(long maintenantMs) {
        PhaseEtat ouverte = phaseOuverte();
        if (ouverte != null) {
            long reference = debutPhaseMs >= 0 ? debutPhaseMs
                    : (debutBuildMs >= 0 ? debutBuildMs : maintenantMs);
            remplacer(new PhaseEtat(ouverte.phase(), true, true,
                    Math.max(0L, maintenantMs - reference)));
        }
    }

    private void ajouter(ConsoleBuild.Ligne ligne) {
        lignes.add(ligne);
        if (lignes.size() > ConsoleBuild.PLAFOND_LIGNES) {
            lignes.remove(0);
            plafonne = true;
        }
    }

    /** Toutes les lignes du transcript (plafonnées aux 400 dernières). */
    public List<ConsoleBuild.Ligne> lignes() {
        return List.copyOf(lignes);
    }

    /** Les lignes visibles selon les filtres courants (nature ET phase). */
    public List<ConsoleBuild.Ligne> lignesFiltrees() {
        List<ConsoleBuild.Ligne> visibles = new ArrayList<>();
        for (ConsoleBuild.Ligne ligne : lignes) {
            if (filtre == Filtre.ERREURS
                    && ligne.nature() != ConsoleBuild.Nature.ERREUR) {
                continue;
            }
            if (filtre == Filtre.SYSTEME
                    && ligne.nature() != ConsoleBuild.Nature.SYSTEME) {
                continue;
            }
            if (filtrePhase != null && ligne.phase() != filtrePhase) {
                continue;
            }
            visibles.add(ligne);
        }
        return visibles;
    }

    /** Le compte d'erreurs — exact, c'est celui du filtre. */
    public int compteErreurs() {
        int compte = 0;
        for (ConsoleBuild.Ligne ligne : lignes) {
            if (ligne.nature() == ConsoleBuild.Nature.ERREUR) {
                compte++;
            }
        }
        return compte;
    }

    /** Le filtre change ; le transcript se relit. */
    public void setFiltre(Filtre nouveau) {
        filtre = nouveau == null ? Filtre.TOUT : nouveau;
    }

    /** Le filtre courant. */
    public Filtre filtre() {
        return filtre;
    }

    /**
     * Le filtre par phase (lot K : tap sur une pilule de la bande) —
     * null retire le filtre (retap sur la même pilule).
     */
    public void setFiltrePhase(ConsoleBuild.Phase phase) {
        filtrePhase = filtrePhase == phase ? null : phase;
    }

    /** La phase filtrée, ou null. */
    public ConsoleBuild.Phase filtrePhase() {
        return filtrePhase;
    }

    /** L'état de l'en-tête. */
    public Etat etat() {
        return etat;
    }

    /** La bande de phases (préparation, compilation, assemblage). */
    public List<PhaseEtat> phases() {
        return List.copyOf(phases);
    }

    /** La phase en cours (pilule d'en-tête), ou null. */
    public ConsoleBuild.Phase phaseCourante() {
        for (int i = phases.size() - 1; i >= 0; i--) {
            PhaseEtat etatPhase = phases.get(i);
            if (etatPhase.commencee() && !etatPhase.terminee()) {
                return etatPhase.phase();
            }
        }
        return null;
    }

    /** Le numéro du dernier build lancé, ou -1. */
    public long numeroBuild() {
        return numeroBuild;
    }

    /**
     * Le premier problème LOCALISÉ du transcript (bouton « Ouvrir le
     * fichier à la ligne ») — null si aucun problème n'est localisé :
     * le bouton ne ment jamais.
     */
    public ConsoleBuild.Ligne premierProblemeLocalise() {
        for (ConsoleBuild.Ligne ligne : lignes) {
            if (ligne.nature() == ConsoleBuild.Nature.ERREUR
                    && ligne.cible() != null) {
                return ligne;
            }
        }
        return null;
    }

    /** Vrai quand le plafond a rogné le transcript — annoncé, jamais muet. */
    public boolean plafonne() {
        return plafonne;
    }

    /**
     * Le libellé court de l'état pour la poignée du dock (« réussi ·
     * 6,2 s », « en cours »…) — la durée est celle du dernier bilan.
     */
    public String libelleCourt() {
        return switch (etat) {
            case VIDE -> "prêt";
            case EN_COURS -> phaseCourante() == null
                    ? "en cours"
                    : "en cours · " + phaseLisible(phaseCourante());
            case REUSSI -> "réussi" + dureeCourt();
            case ECHOUE -> "échoué" + dureeCourt();
            case INTERROMPU -> "interrompu";
        };
    }

    private static String phaseLisible(ConsoleBuild.Phase phase) {
        return switch (phase) {
            case PREPARATION -> "préparation";
            case COMPILATION -> "compilation";
            case ASSEMBLAGE -> "assemblage";
        };
    }

    private String dureeCourt() {
        return resumeWallMillis >= 0
                ? " · " + secondes(resumeWallMillis) : "";
    }

    /** « 6,2 s » — les durées se lisent comme les parle la maquette. */
    public static String secondes(long millis) {
        if (millis < 0) {
            return "—";
        }
        return String.format(Locale.FRANCE, "%.1f s",
                millis / 1000.0);
    }

    private static String pluriel(long compte, String singulier,
                                  String pluriel) {
        return Math.abs(compte) > 1 ? pluriel : singulier;
    }

    private static String statutLisible(String statut) {
        return switch (statut == null ? "" : statut) {
            case "STARTED" -> "démarrée";
            case "SUCCEEDED" -> "réussie";
            case "FROM_CACHE" -> "restaurée du cache";
            case "UP_TO_DATE" -> "à jour";
            case "FAILED" -> "échouée";
            case "CANCELLED" -> "annulée";
            default -> statut == null ? "?" : statut;
        };
    }
}
