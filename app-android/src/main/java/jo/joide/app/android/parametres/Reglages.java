package jo.joide.app.android.parametres;

import java.util.Objects;

/**
 * Les réglages réels de l'application (lot M, ADR-É8/054) : un
 * réglage n'existe ici que s'il a un consommateur — le thème et
 * l'accent sont lus par {@code GraphiteVue}, la taille du code et les
 * commutateurs d'éditeur par la vue celeris à son ouverture, le
 * moteur et les fils par le lanceur de build. Classe PURE : les
 * valeurs vivent dans une {@link Source} (les préférences privées de
 * l'application côté Android), la validation, les bornes et les
 * défauts vivent ICI — l'UI reste mince.
 *
 * <p>Une valeur absente ou invalide retombe sur son défaut, jamais
 * sur une exception : des préférences corrompues par une version
 * ancienne ne doivent pas empêcher l'application de démarrer. Les
 * écritures sont immédiates (une par réglage) — l'écran n'accumule
 * jamais d'état non posé.</p>
 */
public final class Reglages {

    /** Le thème de l'application (ADR-É8 : sombre par défaut). */
    public enum Theme {
        /** Palette sombre, le défaut (ADR-É2 « sombre d'abord »). */
        SOMBRE,
        /** Suit le mode nuit du système. */
        SYSTEME,
        /** Palette claire (ADR-048). */
        CLAIR
    }

    /** Le moteur de build par défaut (l'ancienne case « en processus » du lot F, promue réglage). */
    public enum Moteur {
        /** Le service de premier plan « :build » (défaut). */
        SERVICE,
        /** Le moteur embarqué dans le processus de l'application. */
        PROCESSUS
    }

    /** Le fournisseur LLM par défaut de l'agent (lot P, D-2 ; lot Q : Gemini rejoint la v1). */
    public enum FournisseurIa {
        /** Anthropic (Messages API). */
        ANTHROPIC,
        /** OpenAI-compatible (Chat Completions, URL de base libre). */
        OPENAI,
        /** Google Gemini (streamGenerateContent, cache de contexte serveur, lot Q). */
        GEMINI
    }

    /** Lecture/écriture des valeurs persistées (préférences privées). */
    public interface Source {
        /**
         * @param cle clé du réglage
         * @return la valeur brute, ou {@code null} si absente
         */
        String lire(String cle);

        /**
         * Pose une valeur (écriture immédiate).
         *
         * @param cle    clé du réglage
         * @param valeur valeur brute
         */
        void ecrire(String cle, String valeur);
    }

    /** Clés persistées (schéma joide.reglages/1). */
    static final String CLE_THEME = "theme";
    static final String CLE_ACCENT = "accent";
    static final String CLE_ECHELLE_UI = "echelle.ui";
    static final String CLE_TAILLE_CODE = "editeur.taille";
    static final String CLE_NUMEROS = "editeur.numeros";
    static final String CLE_RETOUR_LIGNE = "editeur.retourLigne";
    static final String CLE_MOTEUR = "build.moteur";
    static final String CLE_FILS = "build.fils";
    static final String CLE_FOURNISSEUR_IA = "ia.fournisseur";
    static final String CLE_MODELE_IA = "ia.modele";
    static final String CLE_URL_IA = "ia.url";

    // ---- Bornes (les steppers de l'écran et les tests les partagent) ----
    /** Taille du code : plancher (sp). */
    public static final double TAILLE_CODE_MIN = 10d;
    /** Taille du code : plafond (sp). */
    public static final double TAILLE_CODE_MAX = 20d;
    /** Taille du code : pas (sp). */
    public static final double TAILLE_CODE_PAS = 0.5d;
    /** Taille du code : défaut (sp, Graphite.CODE). */
    public static final double TAILLE_CODE_DEFAUT = 13.5d;

    /** Échelle du texte d'interface : plancher (%). */
    public static final int ECHELLE_UI_MIN = 85;
    /** Échelle du texte d'interface : plafond (%). */
    public static final int ECHELLE_UI_MAX = 130;
    /** Échelle du texte d'interface : pas (%). */
    public static final int ECHELLE_UI_PAS = 5;
    /** Échelle du texte d'interface : défaut (%). */
    public static final int ECHELLE_UI_DEFAUT = 100;

    /** Fils par build : plancher. */
    public static final int FILS_MIN = 1;
    /** Fils par build : plafond (un téléphone milieu de gamme n'en exploite pas plus). */
    public static final int FILS_MAX = 8;
    /** Fils par build : défaut (celui du service P8). */
    public static final int FILS_DEFAUT = 2;

    /** Nombre d'accents du nuancier (ADR-É8 : restreint). */
    public static final int ACCENTS = 5;

    /** Le thème posé. */
    public final Theme theme;
    /** L'accent posé (index dans le nuancier, 0 = celui de la palette). */
    public final int accent;
    /** L'échelle du texte d'interface (%). */
    public final int echelleUi;
    /** La taille du code (sp). */
    public final double tailleCode;
    /** Les numéros de ligne sont visibles. */
    public final boolean numerosLignes;
    /** Le retour à la ligne est actif. */
    public final boolean retourLigne;
    /** Les bornes du modèle et de l'URL IA (des chaînes, mais pas non bornées). */
    public static final int MODELE_IA_MAX = 100;
    public static final int URL_IA_MAX = 500;

    /** Le modèle IA par défaut (le sélecteur du tiroir le change par conversation). */
    public static final String MODELE_IA_DEFAUT = "claude-sonnet-4-5";

    /** Le moteur de build par défaut. */
    public final Moteur moteur;
    /** Les fils par build. */
    public final int filsParBuild;
    /** Le fournisseur LLM par défaut de l'agent. */
    public final FournisseurIa fournisseurIa;
    /** Le modèle par défaut de l'agent. */
    public final String modeleIa;
    /** L'URL de base OpenAI-compatible (vide : l'URL officielle). */
    public final String urlIa;

    private Reglages(Theme theme, int accent, int echelleUi,
                     double tailleCode, boolean numerosLignes,
                     boolean retourLigne, Moteur moteur, int filsParBuild,
                     FournisseurIa fournisseurIa, String modeleIa,
                     String urlIa) {
        this.theme = theme;
        this.accent = accent;
        this.echelleUi = echelleUi;
        this.tailleCode = tailleCode;
        this.numerosLignes = numerosLignes;
        this.retourLigne = retourLigne;
        this.moteur = moteur;
        this.filsParBuild = filsParBuild;
        this.fournisseurIa = fournisseurIa;
        this.modeleIa = modeleIa;
        this.urlIa = urlIa;
    }

    /** Les réglages par défaut (aucune préférence posée). */
    public static Reglages defauts() {
        return new Reglages(Theme.SOMBRE, 0, ECHELLE_UI_DEFAUT,
                TAILLE_CODE_DEFAUT, true, false, Moteur.SERVICE,
                FILS_DEFAUT, FournisseurIa.ANTHROPIC,
                MODELE_IA_DEFAUT, "");
    }

    /**
     * Lit les réglages depuis la source : chaque valeur absente ou
     * invalide retombe sur son défaut (jamais d'exception — des
     * préférences d'une version ancienne ne cassent rien).
     *
     * @param source les valeurs persistées
     * @return les réglages validés
     */
    public static Reglages lire(Source source) {
        Objects.requireNonNull(source, "source");
        Reglages defauts = defauts();
        Theme theme = defauts.theme;
        String brutTheme = source.lire(CLE_THEME);
        if (brutTheme != null) {
            try {
                theme = Theme.valueOf(brutTheme);
            } catch (IllegalArgumentException e) {
                // valeur d'une version future ou corrompue : le défaut
            }
        }
        int accent = entier(source.lire(CLE_ACCENT), defauts.accent);
        if (accent < 0 || accent >= ACCENTS) {
            accent = defauts.accent;
        }
        int echelle = entier(source.lire(CLE_ECHELLE_UI),
                defauts.echelleUi);
        if (echelle < ECHELLE_UI_MIN || echelle > ECHELLE_UI_MAX
                || echelle % ECHELLE_UI_PAS != 0) {
            echelle = defauts.echelleUi;
        }
        double taille = decimal(source.lire(CLE_TAILLE_CODE),
                defauts.tailleCode);
        if (taille < TAILLE_CODE_MIN || taille > TAILLE_CODE_MAX) {
            taille = defauts.tailleCode;
        }
        Boolean numeros = booleen(source.lire(CLE_NUMEROS));
        Boolean retour = booleen(source.lire(CLE_RETOUR_LIGNE));
        Moteur moteur = defauts.moteur;
        String brutMoteur = source.lire(CLE_MOTEUR);
        if (brutMoteur != null) {
            try {
                moteur = Moteur.valueOf(brutMoteur);
            } catch (IllegalArgumentException e) {
                // le défaut, jamais une exception
            }
        }
        int fils = entier(source.lire(CLE_FILS), defauts.filsParBuild);
        if (fils < FILS_MIN || fils > FILS_MAX) {
            fils = defauts.filsParBuild;
        }
        FournisseurIa fournisseur = defauts.fournisseurIa;
        String brutFournisseur = source.lire(CLE_FOURNISSEUR_IA);
        if (brutFournisseur != null) {
            try {
                fournisseur = FournisseurIa.valueOf(brutFournisseur);
            } catch (IllegalArgumentException e) {
                // valeur d'une version future : le défaut
            }
        }
        String modele = source.lire(CLE_MODELE_IA);
        if (modele == null || modele.isBlank()
                || modele.length() > MODELE_IA_MAX) {
            modele = defauts.modeleIa;
        }
        String url = source.lire(CLE_URL_IA);
        if (url == null || url.length() > URL_IA_MAX
                || (!url.isEmpty() && !url.startsWith("http://")
                        && !url.startsWith("https://"))) {
            url = "";
        }
        return new Reglages(theme, accent, echelle, taille,
                numeros == null ? defauts.numerosLignes : numeros,
                retour == null ? defauts.retourLigne : retour,
                moteur, fils, fournisseur, modele, url);
    }

    /** Pose le thème. */
    public static void ecrireTheme(Source source, Theme valeur) {
        source.ecrire(CLE_THEME, valeur.name());
    }

    /** Pose l'accent (index du nuancier, refusé hors bornes). */
    public static void ecrireAccent(Source source, int valeur) {
        if (valeur < 0 || valeur >= ACCENTS) {
            throw new IllegalArgumentException("accent out of range: "
                    + valeur);
        }
        source.ecrire(CLE_ACCENT, String.valueOf(valeur));
    }

    /** Pose l'échelle du texte d'interface (bornée au pas). */
    public static void ecrireEchelleUi(Source source, int valeur) {
        int bornee = Math.max(ECHELLE_UI_MIN,
                Math.min(ECHELLE_UI_MAX, valeur));
        bornee = Math.round((float) bornee / ECHELLE_UI_PAS)
                * ECHELLE_UI_PAS;
        source.ecrire(CLE_ECHELLE_UI, String.valueOf(bornee));
    }

    /** Pose la taille du code (bornée). */
    public static void ecrireTailleCode(Source source, double valeur) {
        double bornee = Math.max(TAILLE_CODE_MIN,
                Math.min(TAILLE_CODE_MAX, valeur));
        source.ecrire(CLE_TAILLE_CODE, String.valueOf(bornee));
    }

    /** Pose la visibilité des numéros de ligne. */
    public static void ecrireNumerosLignes(Source source, boolean valeur) {
        source.ecrire(CLE_NUMEROS, String.valueOf(valeur));
    }

    /** Pose le retour à la ligne. */
    public static void ecrireRetourLigne(Source source, boolean valeur) {
        source.ecrire(CLE_RETOUR_LIGNE, String.valueOf(valeur));
    }

    /** Pose le moteur de build par défaut. */
    public static void ecrireMoteur(Source source, Moteur valeur) {
        source.ecrire(CLE_MOTEUR, valeur.name());
    }

    /** Pose les fils par build (bornés). */
    public static void ecrireFils(Source source, int valeur) {
        int bornes = Math.max(FILS_MIN, Math.min(FILS_MAX, valeur));
        source.ecrire(CLE_FILS, String.valueOf(bornes));
    }

    /** Pose le fournisseur IA par défaut. */
    public static void ecrireFournisseurIa(Source source,
                                           FournisseurIa valeur) {
        source.ecrire(CLE_FOURNISSEUR_IA, valeur.name());
    }

    /** Pose le modèle IA par défaut (borné, jamais vide). */
    public static void ecrireModeleIa(Source source, String valeur) {
        String borne = valeur == null ? "" : valeur.trim();
        if (borne.isEmpty() || borne.length() > MODELE_IA_MAX) {
            throw new IllegalArgumentException(
                    "model name out of range");
        }
        source.ecrire(CLE_MODELE_IA, borne);
    }

    /** Pose l'URL de base OpenAI-compatible (vide ou http(s), bornée). */
    public static void ecrireUrlIa(Source source, String valeur) {
        String bornee = valeur == null ? "" : valeur.trim();
        if (bornee.length() > URL_IA_MAX
                || (!bornee.isEmpty() && !bornee.startsWith("http://")
                        && !bornee.startsWith("https://"))) {
            throw new IllegalArgumentException(
                    "base url must be http(s) or empty");
        }
        source.ecrire(CLE_URL_IA, bornee);
    }

    private static int entier(String brut, int defaut) {
        if (brut == null) {
            return defaut;
        }
        try {
            return Integer.parseInt(brut.trim());
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    private static double decimal(String brut, double defaut) {
        if (brut == null) {
            return defaut;
        }
        try {
            return Double.parseDouble(brut.trim());
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    private static Boolean booleen(String brut) {
        if (brut == null) {
            return null;
        }
        return Boolean.parseBoolean(brut.trim());
    }
}
