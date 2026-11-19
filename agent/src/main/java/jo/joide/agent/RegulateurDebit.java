package jo.joide.agent;

import java.util.ArrayList;
import java.util.Objects;

import jo.joide.deps.json.JsonEcrire;
import jo.joide.platform.Clock;

/**
 * Le régulateur préventif de débit (lot Q, ADR-062 ; le
 * « RequestPacer » de l'ADR-É9) : ATTENDRE avant la requête au lieu
 * d'envoyer et récolter un 429.
 *
 * <p>Un tour d'agent est une rafale : un message utilisateur se
 * démultiplie en une douzaine de requêtes en quelques secondes, chaque
 * une renvoyant tout le contexte. Sur un palier mesuré, la rafale est
 * exactement ce qui déclenche les plafonds requêtes/minute et
 * jetons/minute — et chaque refus est un aller-retour gaspillé qui
 * fait croire la clé « cassée ». La régulation transforme la même
 * charge en un run légèrement plus lent qui FINIT.</p>
 *
 * <p>La fenêtre glissante de 60 s porte les requêtes envoyées
 * (horodatage + estimation de jetons d'entrée). {@link #delaiPour}
 * calcule combien de temps attendre AVANT une requête (sans rien
 * enregistrer) ; {@link #enregistrer} pose la requête réellement
 * envoyée. Les limites viennent du plus spécifique au plus général :
 * les valeurs configurées par l'utilisateur, puis les limites
 * ÉNONCÉES par le fournisseur dans une erreur de quota
 * ({@link #apprendre}), puis — sans énoncé — le plafond OBSERVÉ au
 * moment du déclenchement (jetons ×9/10, requêtes en vol − 1), pour
 * rester juste sous ce qui a déclenché. Sans limite connue, le
 * régulateur n'attend jamais.</p>
 *
 * <p>Un régulateur par clé + modèle — la portée que le fournisseur
 * compte. Partagé par toutes les conversations du même couple ;
 * thread-safe ; horloge injectée (les tests à horloge fausse
 * prouvent l'apprentissage et la fenêtre).</p>
 */
public final class RegulateurDebit {

    /** La fenêtre du débit (millisecondes). */
    public static final long FENETRE_MS = 60_000L;

    /** Une requête envoyée : instant + estimation de jetons. */
    private static final class Envoyee {
        final long instant;
        final long jetons;

        Envoyee(long instant, long jetons) {
            this.instant = instant;
            this.jetons = jetons;
        }
    }

    private final Integer rpmConfiguree;
    private final Long tpmConfigure;
    private final Clock horloge;
    private final ArrayList<Envoyee> fenetre = new ArrayList<>();
    private Integer rpmAppris;
    private Long tpmAppris;

    /**
     * @param rpmConfiguree les requêtes/minute configurées (null ou
     *                      ≤ 0 : laisser à l'apprentissage)
     * @param tpmConfigure  les jetons d'entrée/minute configurés
     *                      (null ou ≤ 0 : laisser à l'apprentissage)
     * @param horloge       l'horloge injectée (null : système)
     */
    public RegulateurDebit(Integer rpmConfiguree, Long tpmConfigure,
                           Clock horloge) {
        this.rpmConfiguree = rpmConfiguree != null && rpmConfiguree > 0
                ? rpmConfiguree : null;
        this.tpmConfigure = tpmConfigure != null && tpmConfigure > 0
                ? tpmConfigure : null;
        this.horloge = horloge == null ? Clock.system() : horloge;
    }

    /** Le régulateur sans configuration (apprentissage seul). */
    public RegulateurDebit() {
        this(null, null, null);
    }

    /** @return le plafond requêtes/minute en vigueur, ou null */
    public synchronized Integer rpm() {
        return rpmConfiguree != null ? rpmConfiguree : rpmAppris;
    }

    /** @return le plafond jetons/minute en vigueur, ou null */
    public synchronized Long tpm() {
        return tpmConfigure != null ? tpmConfigure : tpmAppris;
    }

    /**
     * Combien de temps attendre avant une requête d'environ
     * {@code jetonsEstimes} jetons d'entrée (millisecondes ; 0 :
     * envoyer maintenant). N'enregistre rien : appeler
     * {@link #enregistrer} une fois la requête envoyée.
     *
     * @param jetonsEstimes l'estimation des jetons d'entrée
     * @return le délai d'attente en millisecondes
     */
    public synchronized long delaiPour(long jetonsEstimes) {
        long maintenant = horloge.wallTimeMillis();
        elaguer(maintenant);
        long attendreJusqua = maintenant;
        Integer rpm = rpm();
        if (rpm != null && fenetre.size() >= rpm) {
            // la plus ancienne requête qui doit vieillir avant qu'une
            // de plus tienne
            Envoyee e = fenetre.get(fenetre.size() - rpm);
            attendreJusqua = Math.max(attendreJusqua,
                    e.instant + FENETRE_MS);
        }
        Long tpm = tpm();
        if (tpm != null) {
            long uses = jetonsDansLaFenetre();
            if (uses + jetonsEstimes > tpm
                    && jetonsEstimes <= tpm) {
                for (Envoyee e : fenetre) {
                    uses -= e.jetons;
                    attendreJusqua = Math.max(attendreJusqua,
                            e.instant + FENETRE_MS);
                    if (uses + jetonsEstimes <= tpm) {
                        break;
                    }
                }
            }
        }
        return Math.max(0, attendreJusqua - maintenant);
    }

    /** Enregistre une requête réellement envoyée. */
    public synchronized void enregistrer(long jetonsEstimes) {
        long maintenant = horloge.wallTimeMillis();
        elaguer(maintenant);
        fenetre.add(new Envoyee(maintenant, jetonsEstimes));
    }

    /**
     * Apprend d'une erreur de quota. Une limite ÉNONCÉE est adoptée
     * telle quelle ; sans énoncé, le plafond devient ce qui était en
     * vol dans la fenêtre — ×9/10 pour les jetons, en vol − 1 pour
     * les requêtes. Une erreur de fenêtre journalière n'apprend rien
     * (la fenêtre du régulateur est la minute).
     *
     * @param quota le quota énoncé par l'erreur, ou null
     */
    public synchronized void apprendre(QuotaFournisseur quota) {
        if (quota == null
                || quota.fenetre == QuotaFournisseur.Fenetre.JOUR) {
            return;
        }
        elaguer(horloge.wallTimeMillis());
        long enoncee = quota.limite;
        if (quota.metrique == QuotaFournisseur.Metrique.JETONS_ENTREE) {
            long enVol = jetonsDansLaFenetre();
            tpmAppris = enoncee > 0 ? enoncee
                    : (enVol > 0 ? enVol * 9 / 10 : null);
        } else {
            int taille = fenetre.size();
            rpmAppris = enoncee > 0 ? (int) Math.min(Integer.MAX_VALUE,
                    enoncee)
                    : (taille > 1 ? taille - 1 : null);
        }
    }

    /**
     * @return l'occupation de la fenêtre requêtes (0..1 ; 0 sans
     *         plafond connu) — l'entrée des bandes d'usage
     */
    public synchronized double occupationRpm() {
        Integer rpm = rpm();
        if (rpm == null) {
            return 0;
        }
        elaguer(horloge.wallTimeMillis());
        return Math.min(1, fenetre.size() / (double) rpm);
    }

    /**
     * Lot C4 (l'écran Usages « N de M » de CodeAssist) : les requêtes
     * comptées dans la fenêtre et le plafond en vigueur.
     *
     * @return {requêtes envoyées dans la fenêtre, plafond rpm ou -1}
     */
    public synchronized long[] compteurRpm() {
        Integer rpm = rpm();
        elaguer(horloge.wallTimeMillis());
        return new long[]{fenetre.size(),
                rpm == null ? -1 : rpm};
    }

    /**
     * Lot C4 : les jetons comptés dans la fenêtre et le plafond en
     * vigueur.
     *
     * @return {jetons dans la fenêtre, plafond tpm ou -1}
     */
    public synchronized long[] compteurTpm() {
        Long tpm = tpm();
        elaguer(horloge.wallTimeMillis());
        return new long[]{jetonsDansLaFenetre(),
                tpm == null ? -1 : tpm};
    }

    /**
     * Lot C4 (le « réinitialisé dans X » de CodeAssist) : l'instant
     * où la PLUS ANCIENNE requête sort de la fenêtre glissante — la
     * fenêtre se vide au fil de l'eau, c'est la prochaine RELANCE
     * honnête d'un compte qui décroît.
     *
     * @return l'instant epoch ms de la première sortie, -1 si vide
     */
    public synchronized long premiereSortieMs() {
        elaguer(horloge.wallTimeMillis());
        return fenetre.isEmpty() ? -1
                : fenetre.get(0).instant + FENETRE_MS;
    }

    /**
     * @return l'occupation de la fenêtre jetons (0..1 ; 0 sans
     *         plafond connu)
     */
    public synchronized double occupationTpm(long jetonsEstimes) {
        Long tpm = tpm();
        if (tpm == null) {
            return 0;
        }
        elaguer(horloge.wallTimeMillis());
        return Math.min(1, (jetonsDansLaFenetre()
                + Math.max(0, jetonsEstimes)) / (double) tpm);
    }

    /**
     * Estime les jetons d'entrée d'une requête : ≈ 4 caractères par
     * jeton sur système + outils + messages, chaque image compte
     * ~1 500 jetons (l'estimation brute du préfixe suffit : elle ne
     * sert qu'à tenir sous un plafond, pas à facturer).
     *
     * @param requete la requête
     * @return l'estimation en jetons
     */
    public static long estimerJetons(RequeteLlm requete) {
        long caracteres = requete.systeme == null ? 0
                : requete.systeme.length();
        for (DescOutil outil : requete.outils) {
            caracteres += outil.nom.length()
                    + outil.description.length();
            if (outil.schema != null && !outil.schema.isNull()) {
                caracteres += JsonEcrire.valeur(outil.schema).length();
            }
        }
        long images = 0;
        for (MessageAgent message : requete.messages) {
            for (PartieContenu partie : message.parties) {
                switch (partie.genre) {
                    case TEXTE, RAISONNEMENT -> caracteres
                            += partie.texte.length();
                    case APPEL_OUTIL -> caracteres
                            += partie.arguments.length();
                    case RESULTAT_OUTIL -> caracteres
                            += partie.texte.length();
                    case IMAGE -> images++;
                    default -> {
                    }
                }
            }
        }
        return caracteres / 4 + images * 1_500L;
    }

    private long jetonsDansLaFenetre() {
        long total = 0;
        for (Envoyee e : fenetre) {
            total += e.jetons;
        }
        return total;
    }

    private void elaguer(long maintenant) {
        while (!fenetre.isEmpty()
                && fenetre.get(0).instant + FENETRE_MS
                <= maintenant) {
            fenetre.remove(0);
        }
    }
}
