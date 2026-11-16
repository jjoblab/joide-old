package jo.joide.app.android.atelier.agent;

import java.util.ArrayList;
import java.util.List;

import jo.joide.agent.ErreursLlm;
import jo.joide.agent.EvenementAgent;
import jo.joide.agent.Permissions;
import jo.joide.agent.UsageLlm;
import jo.joide.agent.espace.QuestionUtilisateur;

/**
 * L'état du tiroir agent (lot P, ADR-É11) : le transcript visible se
 * construit à partir des événements du tour — l'UI ne déduit rien,
 * le service est le modèle d'état. Pur, testé JVM : la vue ne décide
 * rien. Le journal du service se REJOUE ici : un écran qui revient
 * retrouve l'état exact, même mi-outils.
 *
 * <p>Cartes plafonnées à {@link #PLAFOND_CARTES} avec éviction
 * annoncée des plus anciennes terminées — jamais une liste qui
 * grandit sans borne sur un appareil.</p>
 */
public final class EtatAgentChat {

    /** Cartes au plus (plafond annoncé). */
    public static final int PLAFOND_CARTES = 200;

    /** L'état du tour raconté par le tiroir. */
    public enum TourEtat {
        /** Aucun tour dans cette conversation. */
        VIDE,
        /** Un tour tourne (composer désactivé, arrêt disponible). */
        EN_COURS,
        /** Le dernier tour s'est terminé (réponse ou passation). */
        TERMINE,
        /** Le dernier tour a été annulé par l'utilisateur. */
        ANNULE,
        /** Le dernier tour s'est arrêté sur une erreur classée. */
        ERREUR
    }

    /** L'état d'une carte d'outil. */
    public enum EtatOutil {
        /** L'appel tourne (point accent). */
        EN_COURS,
        /** L'appel est fini, résultat borné disponible. */
        FINI,
        /** L'appel a été refusé (permission ou plan seul). */
        REFUSE
    }

    /** L'action proposée par une carte d'erreur — chaque genre a son verbe. */
    public enum ActionErreur {
        /** Ouvrir les réglages IA (clé, modèle, URL). */
        OUVRIR_REGLAGES,
        /** Changer de modèle (souvent l'URL de base, pas le modèle). */
        CHANGER_MODELE,
        /** Attendre : la reprise est déjà en cours (décompte). */
        ATTENDRE,
        /** Rien à proposer : le diagnostic se suffit. */
        AUCUNE
    }

    /** Une question de la carte ask_user, avec sa réponse. */
    public static final class Question {
        /** La question posée. */
        public final String question;
        /** Les options proposées (2 à 4). */
        public final List<String> options;
        /** L'index choisi, -1 si « Autre » ou passée, -2 pas répondue. */
        public int choix = -2;
        /** La réponse libre « Autre », ou null. */
        public String autre;

        /**
         * @param question la question
         * @param options  les options
         */
        public Question(String question, List<String> options) {
            this.question = question;
            this.options = List.copyOf(options);
        }

        /** @return la réponse rendue au modèle (« question : réponse ») */
        public String ligneReponse() {
            String reponse;
            if (choix >= 0 && choix < options.size()) {
                reponse = options.get(choix);
            } else if (autre != null && !autre.isEmpty()) {
                reponse = autre;
            } else {
                reponse = "(skipped)";
            }
            return question + " : " + reponse;
        }
    }

    /** Une carte du transcript. */
    public static final class Carte {
        /** Le genre de carte. */
        public final Genre genre;
        /** Message utilisateur (texte intégral). */
        public String texte;
        /** Le texte de la réponse en cours de composition. */
        public String reponse;
        /** Raisonnement : le texte intégral (réouverture). */
        public StringBuilder raisonnement = new StringBuilder();
        /** Raisonnement : nombre de lignes accumulées. */
        public int lignesRaisonnement;
        /** Outil : identifiant d'appel. */
        public String idAppel;
        /** Outil : nom. */
        public String outil;
        /** Outil : cible lisible. */
        public String cible;
        /** Outil : état. */
        public EtatOutil etatOutil;
        /** Outil : résultat borné. */
        public String resultat;
        /** Outil : nombre de lignes du résultat. */
        public int lignesResultat;
        /** Fichiers changés / erreur : détail. */
        public String detail;
        /** Attente : secondes restantes. */
        public long secondes;
        /** Compaction : avant / après. */
        public long avant;
        public long apres;
        /** Usage : les trois pieds + requêtes. */
        public UsageLlm usage = UsageLlm.zero();
        public int requetes;
        /** Usage : la raison de fin. */
        public EvenementAgent.RaisonFin raison;
        /** Erreur : le genre classé. */
        public ErreursLlm.Genre genreErreur;
        /** Erreur : l'action proposée. */
        public ActionErreur action;
        /** Questions : la liste. */
        public List<Question> questions;
        /** Pièces jointes du message utilisateur (noms d'affichage). */
        public List<String> pieces = java.util.List.of();
        /** Déplié (outils, raisonnement). */
        public boolean deplie;

        /**
         * @param genre le genre
         */
        public Carte(Genre genre) {
            this.genre = genre;
        }
    }

    /** Les genres de carte. */
    public enum Genre {
        /** Message utilisateur. */
        MESSAGE_UTILISATEUR,
        /** Réponse de l'assistant (composée en direct). */
        REPONSE,
        /** Raisonnement (replié à la fin du message). */
        RAISONNEMENT,
        /** Carte d'outil. */
        OUTIL,
        /** Ligne tonale fichiers changés. */
        FICHIERS,
        /** Attente visible de limite de débit. */
        ATTENTE,
        /** Ligne tonale compaction. */
        COMPACTION,
        /** Ligne d'usage de fin de tour. */
        USAGE,
        /** Carte d'erreur actionnable. */
        ERREUR,
        /** Carte de questions ask_user. */
        QUESTIONS
    }

    private final List<Carte> cartes = new ArrayList<>();
    private TourEtat etat = TourEtat.VIDE;
    private Carte reponseCourante;
    private Carte raisonnementCourant;
    private int evictions;
    private boolean plafondAnnonce;
    private Permissions.Mode mode = Permissions.Mode.DEMANDE_CHAQUE;
    private String modele = "";
    private Permissions.DemandeEcriture permissionEnAttente;
    private String preRemplissage;

    /** @return les cartes du transcript (copie défensive pour la vue). */
    public List<Carte> cartes() {
        return new ArrayList<>(cartes);
    }

    /** @return l'état du tour */
    public TourEtat etat() {
        return etat;
    }

    /** @return le mode de permission affiché par la puce d'en-tête */
    public Permissions.Mode mode() {
        return mode;
    }

    /**
     * @param mode le mode persisté par projet
     */
    public void poserMode(Permissions.Mode mode) {
        this.mode = mode;
    }

    /** @return le nom de modèle affiché par la puce d'en-tête */
    public String modele() {
        return modele;
    }

    /**
     * @param modele le modèle courant (sélecteur)
     */
    public void poserModele(String modele) {
        this.modele = modele == null ? "" : modele;
    }

    /** @return la demande de permission en attente, ou null */
    public Permissions.DemandeEcriture permissionEnAttente() {
        return permissionEnAttente;
    }

    /** @return le préremplissage du composeur (« Demander à l'IA »), ou null */
    public String preRemplissage() {
        return preRemplissage;
    }

    /**
     * Pose le préremplissage du composeur (sélection quotée).
     *
     * @param fichier   le fichier de la sélection
     * @param selection le texte sélectionné
     */
    public void poserPreRemplissage(String fichier, String selection) {
        preRemplissage = "À propos de " + fichier + " (sélection) :\n"
                + "```\n" + selection + "\n```";
    }

    /** Consomme le préremplissage (l'envoi le prend). */
    public String prendrePreRemplissage() {
        String valeur = preRemplissage;
        preRemplissage = null;
        return valeur;
    }

    /**
     * Le message de l'utilisateur ouvre un tour.
     *
     * @param message ce que dit l'utilisateur
     */
    public void envoyer(String message) {
        envoyer(message, List.of());
    }

    /**
     * Le message de l'utilisateur ouvre un tour, avec ses pièces
     * jointes nommées (lot S) : la carte les montre en puces, le
     * service porte le contenu jusqu'au modèle.
     *
     * @param message ce que dit l'utilisateur
     * @param pieces  les noms des pièces jointes (affichage seul)
     */
    public void envoyer(String message, List<String> pieces) {
        Carte carte = new Carte(Genre.MESSAGE_UTILISATEUR);
        carte.texte = message;
        carte.pieces = List.copyOf(pieces);
        ajouter(carte);
        etat = TourEtat.EN_COURS;
        reponseCourante = null;
        raisonnementCourant = null;
    }

    /**
     * Une réponse RESTAURÉE (lot S) : la conversation reprise ou le
     * service qui revient rejouent le transcript persisté — la
     * réponse redevient une carte et le tour restauré est TERMINÉ
     * (le rejouage n'arrive jamais au milieu d'un tour vivant : la
     * reprise et le relancement sont gardés par le service).
     *
     * @param texte la réponse complète telle que persistée
     */
    public void restaurerReponse(String texte) {
        Carte carte = new Carte(Genre.REPONSE);
        carte.reponse = texte == null ? "" : texte;
        ajouter(carte);
        etat = TourEtat.TERMINE;
    }

    /**
     * LE point d'entrée : un événement du tour devient une carte (ou
     * complète la carte en cours) — l'UI ne déduit rien.
     *
     * @param evenement l'événement du service
     */
    public void surEvenement(EvenementAgent evenement) {
        switch (evenement.genre) {
            case TEXTE_DELTA -> {
                if (reponseCourante == null) {
                    reponseCourante = new Carte(Genre.REPONSE);
                    ajouter(reponseCourante);
                }
                reponseCourante.reponse =
                        (reponseCourante.reponse == null ? ""
                                : reponseCourante.reponse)
                                + evenement.texte;
            }
            case RAISONNEMENT_DELTA -> {
                if (raisonnementCourant == null) {
                    raisonnementCourant = new Carte(Genre.RAISONNEMENT);
                    ajouter(raisonnementCourant);
                }
                raisonnementCourant.raisonnement
                        .append(evenement.texte);
                raisonnementCourant.lignesRaisonnement = compterLignes(
                        raisonnementCourant.raisonnement.toString());
            }
            case OUTIL_DEBUT -> {
                Carte carte = new Carte(Genre.OUTIL);
                carte.idAppel = evenement.idAppel;
                carte.outil = evenement.outil;
                carte.cible = "";
                carte.etatOutil = EtatOutil.EN_COURS;
                ajouter(carte);
            }
            case OUTIL_FIN -> {
                Carte carte = chercherOutil(evenement.idAppel);
                if (carte != null) {
                    carte.etatOutil = EtatOutil.FINI;
                    carte.resultat = evenement.texte;
                    carte.lignesResultat = compterLignes(
                            evenement.texte);
                }
            }
            case OUTIL_REFUSE -> {
                Carte carte = chercherOutil(evenement.idAppel);
                if (carte != null) {
                    carte.etatOutil = EtatOutil.REFUSE;
                    carte.resultat = evenement.texte;
                }
            }
            case FICHIERS_CHANGES -> {
                Carte carte = new Carte(Genre.FICHIERS);
                carte.detail = evenement.texte;
                ajouter(carte);
            }
            case ATTENTE -> {
                Carte carte = new Carte(Genre.ATTENTE);
                carte.secondes = evenement.secondes;
                ajouter(carte);
            }
            case COMPACTATION -> {
                Carte carte = new Carte(Genre.COMPACTION);
                carte.avant = evenement.avant;
                carte.apres = evenement.apres;
                ajouter(carte);
            }
            case FIN_TOUR -> {
                fermerCartesOuvertes();
                Carte carte = new Carte(Genre.USAGE);
                carte.raison = evenement.raison;
                ajouter(carte);
                etat = switch (evenement.raison) {
                    case ANNULE -> TourEtat.ANNULE;
                    case ERREUR -> TourEtat.ERREUR;
                    default -> TourEtat.TERMINE;
                };
            }
            case ERREUR -> {
                fermerCartesOuvertes();
                Carte carte = new Carte(Genre.ERREUR);
                carte.genreErreur = evenement.erreur.genre;
                carte.action = actionDe(evenement.erreur.genre);
                carte.detail = evenement.texte;
                ajouter(carte);
                etat = TourEtat.ERREUR;
            }
            default -> {
                // rien : les genres futurs ne cassent pas l'état
            }
        }
    }

    /**
     * L'usage du tour, sommé par le service (la ligne d'usage se
     * complète — ce qui est facturé est facturé, même en erreur).
     *
     * @param usage    l'usage total du tour
     * @param requetes le nombre de requêtes du tour
     */
    public void surUsage(UsageLlm usage, int requetes) {
        for (int i = cartes.size() - 1; i >= 0; i--) {
            Carte carte = cartes.get(i);
            if (carte.genre == Genre.USAGE) {
                carte.usage = usage;
                carte.requetes = requetes;
                return;
            }
        }
    }

    /**
     * La carte de questions arrive par le port (le fil de l'outil
     * attend) : l'utilisateur répond, le tour repart.
     *
     * @param questions une à quatre questions
     */
    public void poserQuestions(List<QuestionUtilisateur> questions) {
        Carte carte = new Carte(Genre.QUESTIONS);
        List<Question> liste = new ArrayList<>();
        for (QuestionUtilisateur q : questions) {
            liste.add(new Question(q.question, q.options));
        }
        carte.questions = liste;
        ajouter(carte);
    }

    /**
     * Répond à une question de la dernière carte ouverte.
     *
     * @param index l'index de la question
     * @param choix l'index d'option, ou -1 pour « Autre »
     * @param autre le texte libre (si choix == -1)
     */
    public void repondre(int index, int choix, String autre) {
        Question q = question(index);
        if (q == null) {
            return;
        }
        q.choix = autre != null && !autre.isEmpty() ? -1 : choix;
        q.autre = autre;
    }

    /** Passe une question (« passer » est toujours proposé). */
    public void passer(int index) {
        Question q = question(index);
        if (q != null) {
            q.choix = -1;
        }
    }

    /**
     * @return les réponses rendues au modèle, une par ligne, ou null
     *         si la carte n'est pas entièrement répondue
     */
    public List<String> reponses() {
        for (int i = cartes.size() - 1; i >= 0; i--) {
            Carte carte = cartes.get(i);
            if (carte.genre == Genre.QUESTIONS) {
                List<String> lignes = new ArrayList<>();
                for (Question q : carte.questions) {
                    if (q.choix == -2) {
                        return null;
                    }
                    lignes.add(q.ligneReponse());
                }
                return lignes;
            }
        }
        return null;
    }

    /**
     * La demande de permission (mode DEMANDE_CHAQUE) : le dialogue
     * la pose, fermer = REFUSER (défaut sûr).
     *
     * @param demande la demande avec son diff déplié
     */
    public void poserPermission(Permissions.DemandeEcriture demande) {
        permissionEnAttente = demande;
    }

    /**
     * @param accordee la décision de l'utilisateur
     * @return la décision enregistrée (la carte d'outil suit)
     */
    public Permissions.Decision repondrePermission(boolean accordee) {
        permissionEnAttente = null;
        return accordee ? Permissions.Decision.ACCORDEE
                : Permissions.Decision.REFUSEE;
    }

    /** @return le nombre d'évictions (le plafond s'annonce). */
    public int evictions() {
        return evictions;
    }

    /** @return vrai si le plafond de cartes a été annoncé */
    public boolean plafondAnnonce() {
        return plafondAnnonce;
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    private void ajouter(Carte carte) {
        cartes.add(carte);
        if (cartes.size() > PLAFOND_CARTES) {
            // éviction de la plus ancienne carte TERMINÉE (jamais la
            // réponse en cours, un outil qui tourne ou une question
            // ouverte)
            for (int i = 0; i < cartes.size() - 1; i++) {
                Carte candidate = cartes.get(i);
                boolean ouverte = candidate == reponseCourante
                        || candidate == raisonnementCourant
                        || (candidate.genre == Genre.OUTIL
                                && candidate.etatOutil
                                        == EtatOutil.EN_COURS)
                        || (candidate.genre == Genre.QUESTIONS
                                && questionsOuvertes(candidate));
                if (!ouverte) {
                    cartes.remove(i);
                    evictions++;
                    plafondAnnonce = true;
                    break;
                }
            }
        }
    }

    /** Compte les lignes (String#lines exige l'API 33 — min 28). */
    private static int compterLignes(String texte) {
        if (texte == null || texte.isEmpty()) {
            return 0;
        }
        int lignes = 1;
        for (int i = 0; i < texte.length(); i++) {
            if (texte.charAt(i) == '\n') {
                lignes++;
            }
        }
        return lignes;
    }

    private static boolean questionsOuvertes(Carte carte) {
        for (Question q : carte.questions) {
            if (q.choix == -2) {
                return true;
            }
        }
        return false;
    }

    private Carte chercherOutil(String idAppel) {
        for (int i = cartes.size() - 1; i >= 0; i--) {
            Carte carte = cartes.get(i);
            if (carte.genre == Genre.OUTIL
                    && idAppel.equals(carte.idAppel)) {
                return carte;
            }
        }
        return null;
    }

    private void fermerCartesOuvertes() {
        // la réflexion SE REPLIE quand le tour passe à autre chose
        // (lot T, la manière CodeAssist) — le tap la rouvre
        if (raisonnementCourant != null) {
            raisonnementCourant.deplie = false;
        }
        reponseCourante = null;
        raisonnementCourant = null;
    }

    private Question question(int index) {
        for (int i = cartes.size() - 1; i >= 0; i--) {
            Carte carte = cartes.get(i);
            if (carte.genre == Genre.QUESTIONS) {
                if (index >= 0 && index < carte.questions.size()) {
                    return carte.questions.get(index);
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Chaque genre d'erreur a son verbe (ADR-É11 point 7) — jamais
     * une carte qui ne propose rien.
     */
    private static ActionErreur actionDe(ErreursLlm.Genre genre) {
        return switch (genre) {
            case CLE_MANQUANTE, CLE_INVALIDE, CLE_SANS_DROIT,
                    QUOTA_EPUISE, LIMITE_JOUR ->
                    ActionErreur.OUVRIR_REGLAGES;
            case MODELE_INCONNU -> ActionErreur.CHANGER_MODELE;
            case LIMITE_DEBIT, RESEAU, DELAI, ERREUR_SERVEUR ->
                    ActionErreur.ATTENDRE;
            default -> ActionErreur.AUCUNE;
        };
    }
}
