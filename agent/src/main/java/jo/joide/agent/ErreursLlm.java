package jo.joide.agent;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La taxonomie d'erreurs des fournisseurs LLM (ADR-É9, robustesse
 * étage 1) : quatorze genres, chaque genre sait s'il se retente, et
 * chaque message DIT QUOI FAIRE. La classification regarde d'abord
 * les CODES et EN-TÊTES (un 429 avec Retry-After est un fait), le
 * wording du corps ne sert qu'en repli — une reformulation du
 * fournisseur ne casse pas la classification (leçon n° 4).
 *
 * <p>Le cas d'école de l'ADR-É9 : un 429 peut être une limite à la
 * minute (attendre), la limite du jour (attendre longtemps) ou un
 * crédit épuisé (retenter ne sert à rien) — le code gagne, le
 * wording raffine.</p>
 */
public final class ErreursLlm {

    /** Les quatorze genres d'erreur. */
    public enum Genre {
        /** Réseau injoignable — retenter sert. */
        RESEAU(true),
        /** Délai dépassé avant la première réponse — retenter sert. */
        DELAI(true),
        /** Limite de débit (429) — attendre son délai puis retenter. */
        LIMITE_DEBIT(true),
        /** Limite journalière — attendre longtemps, dire lequel. */
        LIMITE_JOUR(false),
        /** Crédit ou quota épuisé — retenter ne sert à rien. */
        QUOTA_EPUISE(false),
        /** Contexte trop long — compacter ou réduire, puis retenter. */
        CONTEXTE_TROP_LONG(false),
        /** Modèle inconnu (404) — souvent l'URL de base, pas le modèle. */
        MODELE_INCONNU(false),
        /** Clé absente ou vide — la poser dans les réglages. */
        CLE_MANQUANTE(false),
        /** Clé invalide (401) — la vérifier. */
        CLE_INVALIDE(false),
        /** Clé sans droit sur ce modèle (403) — changer de clé ou de modèle. */
        CLE_SANS_DROIT(false),
        /** Contenu refusé par le garde-fou du fournisseur — reformuler. */
        CONTENU_REFUSE(false),
        /** Erreur serveur (5xx) — retenter sert. */
        ERREUR_SERVEUR(true),
        /** Réponse illisible (JSON cassé, événements incohérents). */
        REPONSE_ILLISIBLE(false),
        /** Flux coupé en pleine écriture — le tour s'arrête, jamais de doublon. */
        FLUX_INTERROMPU(false);

        /** Vrai si une retente automatique peut servir. */
        public final boolean retentable;

        Genre(boolean retentable) {
            this.retentable = retentable;
        }
    }

    /** L'erreur classée, avec son action. */
    public static final class ErreurLlm extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** Le genre classé. */
        public final Genre genre;
        /** Le délai de reprise en secondes, ou -1 (aucun énoncé). */
        public final long delaiSecondes;
        /**
         * Le quota ÉNONCÉ par le fournisseur (révision N/O pour le
         * lot Q : le régulateur apprend des limites énoncées), ou
         * null — additif, jamais exigé des tests existants.
         * Transient (l'erreur se reclasse toujours, la sérialisation
         * ne la traverse pas).
         */
        public final transient QuotaFournisseur quota;

        /** @param genre le genre @param message le diagnostic (anglais) @param delaiSecondes le délai énoncé ou -1 */
        public ErreurLlm(Genre genre, String message, long delaiSecondes) {
            this(genre, message, delaiSecondes, null);
        }

        /** @param genre le genre @param message le diagnostic @param delaiSecondes le délai énoncé ou -1 @param quota le quota énoncé ou null */
        public ErreurLlm(Genre genre, String message, long delaiSecondes,
                         QuotaFournisseur quota) {
            super(message);
            this.genre = genre;
            this.delaiSecondes = delaiSecondes;
            this.quota = quota;
        }
    }

    private static final Pattern REESSAYER_DANS = Pattern.compile(
            "try again in (\\d+)");
    private static final Pattern REESSAYER_DANS_MS = Pattern.compile(
            "try again in ([\\d.]+)ms");
    private static final Pattern RETRY_AFTER_ISO = Pattern.compile(
            "(\\d+)s");

    private ErreursLlm() {
    }

    /**
     * Classe une réponse d'erreur HTTP.
     *
     * @param statut le code HTTP
     * @param entetes les en-têtes de la réponse
     * @param corps  le corps d'erreur (JSON ou texte brut)
     * @return l'erreur classée avec son action
     */
    public static ErreurLlm classifier(int statut,
                                       Map<String, List<String>> entetes,
                                       String corps) {
        long delai = delaiEnTetes(entetes);
        String texte = corps == null ? "" : corps;
        switch (statut) {
            case 401:
                return new ErreurLlm(Genre.CLE_INVALIDE,
                        "API key rejected (401): check the key in "
                                + "settings", -1);
            case 403:
                return new ErreurLlm(Genre.CLE_SANS_DROIT,
                        "key not allowed for this model (403): use "
                                + "another key or model", -1);
            case 404:
                return new ErreurLlm(Genre.MODELE_INCONNU,
                        "not found (404): if the model name is right, "
                                + "check the base URL", -1);
            case 413:
            case 422:
                return new ErreurLlm(Genre.CONTEXTE_TROP_LONG,
                        "request too large (" + statut + "): compact "
                                + "the conversation and retry", -1);
            case 429: {
                // le code gagne : 429 = transitoire ; le wording
                // raffine : quota du jour ? plafond zéro (hors plan) ?
                // crédit ? délai énoncé ? — le raffinement des quotas
                // porte la métrique et la fenêtre au régulateur (lot Q)
                String bas = texte.toLowerCase(Locale.ROOT);
                if (QuotaFournisseur.plafondZero(texte)) {
                    // le cas d'école de l'ADR-É9 : plafond zéro = le
                    // modèle n'est pas dans le plan de la clé —
                    // retenter ne sert à rien, changer de clé oui
                    return new ErreurLlm(Genre.QUOTA_EPUISE,
                            "quota is 0 for this model on this key "
                                    + "(429): the key's plan does not "
                                    + "include it — use another model "
                                    + "or key", -1);
                }
                if (bas.contains("daily") || bas.contains("per day")
                        || bas.contains("until tomorrow")) {
                    return new ErreurLlm(Genre.LIMITE_JOUR,
                            "daily limit reached (429): wait for the "
                                    + "daily window to reset", -1,
                            QuotaFournisseur.raffiner(texte));
                }
                if (bas.contains("credit") || bas.contains("billing")
                        || (bas.contains("quota exceeded")
                        && !bas.contains("rate"))) {
                    return new ErreurLlm(Genre.QUOTA_EPUISE,
                            "billing limit reached (429): the account "
                                    + "cannot pay this request", -1);
                }
                if (delai < 0) {
                    delai = delaiDansLeCorps(texte);
                }
                return new ErreurLlm(Genre.LIMITE_DEBIT,
                        "rate limited (429): wait "
                                + (delai >= 0 ? delai + "s"
                                : "the announced delay") + " then retry",
                        delai, QuotaFournisseur.raffiner(texte));
            }
            case 500:
            case 502:
            case 503:
            case 504:
            case 529:
                return new ErreurLlm(Genre.ERREUR_SERVEUR,
                        "provider error (" + statut + "): retrying "
                                + "usually helps", -1);
            default:
                if (statut >= 500) {
                    return new ErreurLlm(Genre.ERREUR_SERVEUR,
                            "provider error (" + statut + ")", -1);
                }
                if (texte.toLowerCase(Locale.ROOT).contains("too long")
                        || texte.toLowerCase(Locale.ROOT)
                        .contains("context length")) {
                    return new ErreurLlm(Genre.CONTEXTE_TROP_LONG,
                            "context too long (" + statut
                                    + "): compact and retry", -1);
                }
                return new ErreurLlm(Genre.REPONSE_ILLISIBLE,
                        "unexpected response (" + statut + "): "
                                + resume(texte), -1);
        }
    }

    /**
     * Classe une erreur d'entrée/sortie AVANT émission ou pendant le
     * flux.
     *
     * @param e l'exception transport
     * @param fluxDemarre vrai si des octets ont déjà été reçus
     * @return l'erreur classée
     */
    public static ErreurLlm classifier(IOException e, boolean fluxDemarre) {
        String message = String.valueOf(e.getMessage());
        if (fluxDemarre) {
            return new ErreurLlm(Genre.FLUX_INTERROMPU,
                    "stream cut mid-response: the turn stops here, "
                            + "already-shown text is never duplicated",
                    -1);
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return new ErreurLlm(Genre.DELAI,
                    "timeout before first byte: retrying helps", -1);
        }
        return new ErreurLlm(Genre.RESEAU,
                "network unreachable: " + resume(message), -1);
    }

    /** Une réponse illisible (JSON cassé, événements incohérents). */
    public static ErreurLlm illisible(String detail) {
        return new ErreurLlm(Genre.REPONSE_ILLISIBLE,
                "unreadable response: " + resume(detail), -1);
    }

    /** La clé d'API est absente — l'hôte la demande dans ses réglages. */
    public static ErreurLlm cleManquante(String fournisseur) {
        return new ErreurLlm(Genre.CLE_MANQUANTE,
                "no API key set for " + fournisseur
                        + ": set it in settings", -1);
    }

    /** Le contexte dépasse la fenêtre du modèle. */
    public static ErreurLlm contexteTropLong(long jetons) {
        return new ErreurLlm(Genre.CONTEXTE_TROP_LONG,
                "conversation exceeds the model window (~" + jetons
                        + " tokens): compact the conversation", -1);
    }

    /** Le garde-fou du fournisseur refuse le contenu. */
    public static ErreurLlm contenuRefuse(String detail) {
        return new ErreurLlm(Genre.CONTENU_REFUSE,
                "content refused by the provider safety filter: "
                        + resume(detail), -1);
    }

    private static long delaiEnTetes(Map<String, List<String>> entetes) {
        if (entetes == null) {
            return -1;
        }
        for (Map.Entry<String, List<String>> entree : entetes.entrySet()) {
            if (entree.getKey() == null
                    || !"retry-after".equalsIgnoreCase(entree.getKey())) {
                continue;
            }
            for (String valeur : entree.getValue()) {
                try {
                    return Long.parseLong(valeur.trim());
                } catch (NumberFormatException e) {
                    Matcher iso = RETRY_AFTER_ISO.matcher(valeur);
                    if (iso.find()) {
                        return Long.parseLong(iso.group(1));
                    }
                }
            }
        }
        return -1;
    }

    private static long delaiDansLeCorps(String corps) {
        Matcher milliseconde = REESSAYER_DANS_MS.matcher(corps);
        if (milliseconde.find()) {
            try {
                return Math.max(1L,
                        (long) Double.parseDouble(milliseconde.group(1))
                                / 1000L);
            } catch (NumberFormatException e) {
                // tombé plus bas
            }
        }
        Matcher seconde = REESSAYER_DANS.matcher(corps);
        if (seconde.find()) {
            try {
                return Long.parseLong(seconde.group(1));
            } catch (NumberFormatException e) {
                // tombé plus bas
            }
        }
        return -1;
    }

    private static String resume(String texte) {
        String net = texte == null ? "" : texte.replace('\n', ' ')
                .trim();
        return net.length() > 160 ? net.substring(0, 160) + "…" : net;
    }
}
