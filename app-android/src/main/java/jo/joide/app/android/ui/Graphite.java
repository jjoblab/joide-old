package jo.joide.app.android.ui;

/**
 * Le système de design « Graphite » en constantes — jetons de
 * l'ADR-É2 (palette sombre) et de l'ADR-048 (palette claire, mêmes
 * rôles ; rayons, mouvement, typographie et espace de l'ADR-É2).
 *
 * <p>Classe PURE : aucun type du framework, aucune dépendance — les
 * vues du kit la lisent, les tests JVM la vérifient (contrastes AA
 * par la mathématique de luminance, conformité aux jetons de la
 * maquette {@code design/index.html}). Les couleurs sont des entiers
 * ARGB {@code 0xAARRGGBB} ; un changement de palette se fait ICI,
 * une fois (ADR-É2 : les jetons sont des rôles, pas des valeurs
 * câblées dans les vues).</p>
 */
public final class Graphite {

    private Graphite() {
    }

    /** Mode d'affichage : sombre par défaut (« sombre d'abord », ADR-É2). */
    public enum Mode {
        SOMBRE, CLAIR
    }

    /** Syntaxe de code (éditeur, extraits) — sept rôles, ADR-É2 § palette. */
    public static final class Syntaxe {
        public final int motCle;
        public final int type;
        public final int fonction;
        public final int chaine;
        public final int nombre;
        public final int commentaire;
        public final int defaut;

        Syntaxe(int motCle, int type, int fonction, int chaine,
                int nombre, int commentaire, int defaut) {
            this.motCle = motCle;
            this.type = type;
            this.fonction = fonction;
            this.chaine = chaine;
            this.nombre = nombre;
            this.commentaire = commentaire;
            this.defaut = defaut;
        }
    }

    /** Une palette : les seize rôles d'UI plus la syntaxe. */
    public static final class Palette {
        public final int fond;
        public final int surface;
        public final int surfaceHaute;
        public final int puits;
        public final int bord;
        public final int bordFin;
        public final int texte;
        public final int texteSecondaire;
        public final int texteEstompe;
        public final int accent;
        public final int reussite;
        public final int alerte;
        public final int erreur;
        public final int violet;
        public final int texteSurAccent;
        public final int texteSurReussite;
        public final int rideau;
        public final Syntaxe syntaxe;

        Palette(int fond, int surface, int surfaceHaute, int puits,
                int bord, int bordFin, int texte, int texteSecondaire,
                int texteEstompe, int accent, int reussite, int alerte,
                int erreur, int violet, int texteSurAccent,
                int texteSurReussite, int rideau, Syntaxe syntaxe) {
            this.fond = fond;
            this.surface = surface;
            this.surfaceHaute = surfaceHaute;
            this.puits = puits;
            this.bord = bord;
            this.bordFin = bordFin;
            this.texte = texte;
            this.texteSecondaire = texteSecondaire;
            this.texteEstompe = texteEstompe;
            this.accent = accent;
            this.reussite = reussite;
            this.alerte = alerte;
            this.erreur = erreur;
            this.violet = violet;
            this.texteSurAccent = texteSurAccent;
            this.texteSurReussite = texteSurReussite;
            this.rideau = rideau;
            this.syntaxe = syntaxe;
        }
    }

    /** Un style de texte : taille et interligne en sp, graisse, famille. */
    public static final class StyleTexte {
        public final float taille;
        public final float interligne;
        public final int graisse;
        public final boolean mono;

        StyleTexte(float taille, float interligne, int graisse,
                   boolean mono) {
            this.taille = taille;
            this.interligne = interligne;
            this.graisse = graisse;
            this.mono = mono;
        }
    }

    // ---- Typographie (ADR-É2, deux familles : sans système / mono) ----
    /** Légende 12/16 : compteurs, méta de carte. */
    public static final StyleTexte LEGENDE = new StyleTexte(12f, 16f, 400, false);
    /** Note 13/18 : seconde ligne, champs. */
    public static final StyleTexte NOTE = new StyleTexte(13f, 18f, 400, false);
    /** Corps 15/22 : texte courant. */
    public static final StyleTexte CORPS = new StyleTexte(15f, 22f, 400, false);
    /** Sous-titre 16/22 : noms de projet, lignes de liste. */
    public static final StyleTexte SOUS_TITRE = new StyleTexte(16f, 22f, 600, false);
    /** Titre 17/22 demi-gras : titres de barre. */
    public static final StyleTexte TITRE = new StyleTexte(17f, 22f, 600, false);
    /** Titre d'écran 28/34 gras. */
    public static final StyleTexte TITRE_ECRAN = new StyleTexte(28f, 34f, 700, false);
    /** Code 13,5/22 : éditeur, console, extraits (monospace). */
    public static final StyleTexte CODE = new StyleTexte(13.5f, 22f, 400, true);
    /** Code compact 12,5/20 : consoles denses (monospace). */
    public static final StyleTexte CODE_COMPACT = new StyleTexte(12.5f, 20f, 400, true);

    // ---- Rayons, mouvement, espace (ADR-É2) ----
    /** Rayon des cartes. */
    public static final int RAYON_CARTE = 16;
    /** Rayon des tuiles, boutons, champs et composants. */
    public static final int RAYON_COMPOSANT = 12;
    /** Rayon des puces (pilule). */
    public static final int RAYON_PUCE = 999;
    /** Durée standard d'animation (ms). */
    public static final int MOUVEMENT_STANDARD = 180;
    /** Durée des transitions d'écran (ms). */
    public static final int MOUVEMENT_ECRAN = 240;
    /** Grille d'espacement. */
    public static final int GRILLE = 4;
    /** Marges d'écran. */
    public static final int MARGE = 20;
    /** Gouttières de liste. */
    public static final int GOUTTIERE = 20;
    /** Côté de la tuile tonale. */
    public static final int TUILE = 46;

    // ---- Palette sombre (ADR-É2) ----
    private static final Palette SOMBRE = new Palette(
            0xFF0E1116, 0xFF151920, 0xFF1B212A, 0xFF0B0E12,
            0xFF262D38, 0xFF1D232C, 0xFFE7ECF3, 0xFF9AA6B5,
            0xFF64707F, 0xFF82AAFF, 0xFF3DDC84, 0xFFE5C07B,
            0xFFE06C75, 0xFFC678DD, 0xFF0B1524, 0xFF07130C,
            0x7A0B0E12,
            new Syntaxe(0xFFC678DD, 0xFFE5C07B, 0xFF61AFEF, 0xFF98C379,
                    0xFFD19A66, 0xFF7C8695, 0xFFD6DAE3));

    // ---- Palette claire (ADR-048) ----
    private static final Palette CLAIR = new Palette(
            0xFFF3F6FA, 0xFFFFFFFF, 0xFFE9EEF6, 0xFFFFFFFF,
            0xFFD9E1EC, 0xFFE5EBF3, 0xFF1C2430, 0xFF5A6B80,
            0xFF8A97A8, 0xFF2E63D6, 0xFF0D7D45, 0xFF8A6A16,
            0xFFC73540, 0xFFA026AA, 0xFFFFFFFF, 0xFFFFFFFF,
            0x3D1C2430,
            new Syntaxe(0xFFA026AA, 0xFFB45309, 0xFF2E63D6, 0xFF22863A,
                    0xFFC25314, 0xFF667385, 0xFF384556));

    /** La palette sombre (ADR-É2, maquettes contractuelles). */
    public static Palette sombre() {
        return SOMBRE;
    }

    /** La palette claire (ADR-048). */
    public static Palette clair() {
        return CLAIR;
    }

    /** La palette du mode demandé. */
    public static Palette pour(Mode mode) {
        return mode == Mode.CLAIR ? CLAIR : SOMBRE;
    }

    /** Cette palette est-elle la sombre ? (luminance du fond.) */
    public static boolean estSombre(Palette palette) {
        return luminance(palette.fond) < 0.5f;
    }

    /**
     * Mélange additif : {@code couleur} à {@code part} sur
     * {@code support} (part bornée dans [0, 1]), résultat opaque.
     * Utilisé par les tuiles tonales (12 %), le liseré (5 %), les
     * fonds de puce sélectionnée (16 %) et les états appuyés.
     */
    public static int melange(int couleur, float part, int support) {
        if (part < 0f) {
            part = 0f;
        } else if (part > 1f) {
            part = 1f;
        }
        float reste = 1f - part;
        int r = Math.round(((couleur >>> 16) & 0xFF) * part
                + ((support >>> 16) & 0xFF) * reste);
        int g = Math.round(((couleur >>> 8) & 0xFF) * part
                + ((support >>> 8) & 0xFF) * reste);
        int b = Math.round((couleur & 0xFF) * part
                + (support & 0xFF) * reste);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Tuile tonale : le rôle à 12 % sur la surface (ADR-É2). */
    public static int tuileTonale(int role, int surface) {
        return melange(role, 0.12f, surface);
    }

    /**
     * Luminance relative perçue (0..1) — WCAG. Sert au contraste et
     * au sens sombre/clair.
     */
    public static float luminance(int couleur) {
        float r = canal(((couleur >>> 16) & 0xFF));
        float g = canal(((couleur >>> 8) & 0xFF));
        float b = canal(couleur & 0xFF);
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static float canal(int octet) {
        float c = octet / 255f;
        return c <= 0.04045f ? c / 12.92f
                : (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Rapport de contraste WCAG (1..21) entre deux couleurs opaques. */
    public static float contraste(int a, int b) {
        float la = luminance(a);
        float lb = luminance(b);
        float haut = Math.max(la, lb);
        float bas = Math.min(la, lb);
        return (haut + 0.05f) / (bas + 0.05f);
    }

    /** Le couple atteint-il le AA texte (4,5:1) ? */
    public static boolean aa(int a, int b) {
        return contraste(a, b) >= 4.5f;
    }
}
