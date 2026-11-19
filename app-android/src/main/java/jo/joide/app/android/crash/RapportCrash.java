package jo.joide.app.android.crash;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Lot C1 — le RAPPORT DE CRASH : le cœur PUR du garde-crash (testé
 * JVM). Il formate le rapport textuel d'un plantage, en CLASSIFIE le
 * TYPE (le « détecter tout le type crash » demandé : une exception
 * non interceptée porte sa nature — {@link Genre#FRACTURE} avec la
 * classe exacte —, un gel du fil d'interface porte
 * {@link Genre#GEL_INTERFACE}), et porte les métadonnées
 * (version, Android, appareil) que l'activité de crash affiche.
 *
 * <p>AUCUNE dépendance Android ici : l'écriture du rapport doit
 * survivre à un plantage de l'APPAREIL LUI-MÊME. Le composant Android
 * ({@code GardienCrash}) fournit les entrées (fil, exception, pile du
 * fil principal) et consomme le texte ; la classification et le
 * format vivent ici, testés sans émulateur.</p>
 *
 * <p>Format {@code joide.crash/1} : en-tête lisible (le fichier se
 * reconnaît), bloc identité (moment, fil, type, message), pile
 * complète du plantage, pile du FIL PRINCIPAL pour un plantage
 * venu d'un autre fil (le contexte où l'UI vivait), métadonnées
 * (version, Android, appareil). Lisible à l'œil nu, parsable à la
 * ligne près par l'activité de crash.</p>
 */
public final class RapportCrash {

    /** L'en-tête du format (le fichier se reconnaît, l'audit s'en sert). */
    public static final String ENTETE = "joide.crash/1";

    /**
     * Le GENRE de plantage — la réponse au « détecter tout le type de
     * crash » : chaque panne se NOMME, jamais un « erreur inconnue ».
     */
    public enum Genre {
        /**
         * Une exception ou une erreur non interceptée : porte la
         * classe exacte (NullPointerException,
         * InflateException, NoSuchMethodError, OutOfMemoryError…).
         */
        FRACTURE,
        /** Le fil d'interface est gelé : l'application ne répond
         *  plus (classe ANR — la pile du fil principal est LE
         *  diagnostic : où il est bloqué). */
        GEL_INTERFACE
    }

    /** Le moment du plantage (epoch ms). */
    public final long moment;
    /** Le nom du fil qui a planté (ou « main » pour le gel). */
    public final String fil;
    /** Le genre de plantage. */
    public final Genre genre;
    /** La classe exacte de l'exception (vide pour le gel). */
    public final String classe;
    /** Le message de l'exception (vide s'il n'y en a pas). */
    public final String message;
    /** La pile complète du plantage (formatée). */
    public final String pile;
    /** La pile du fil principal au moment du plantage (contexte). */
    public final String pileFond;

    /**
     * @param moment    le moment du plantage (epoch ms)
     * @param fil       le nom du fil qui a planté
     * @param classe    la classe de l'exception (la marque
     *                  {@link #GEL_MARK} porte le genre gel)
     * @param message   le message de l'exception
     * @param pile      la pile complète formatée du plantage
     * @param pileFond  la pile du fil principal (peut être vide)
     */
    public RapportCrash(long moment, String fil, String classe,
                        String message, String pile, String pileFond) {
        this.moment = moment;
        this.fil = fil == null ? "?" : fil;
        Genre calcule = Genre.FRACTURE;
        if (classe == null || classe.isBlank()
                || classe.trim().equals(GEL_MARK)) {
            calcule = Genre.GEL_INTERFACE;
        }
        this.genre = calcule;
        this.classe = classe == null ? "" : classe.trim();
        this.message = message == null ? "" : message.trim();
        this.pile = pile == null ? "" : pile;
        this.pileFond = pileFond == null ? "" : pileFond;
    }

    /** La marque de classe pour un gel du fil principal (pas
     *  d'exception : le genre se porte par elle). */
    static final String GEL_MARK = "gel-interface";

    /**
     * Le rapport d'une EXCEPTION non interceptée : le genre
     * FRACTURE, la classe et le message de la cause première
     * (les enveloppes disent leur cause, c'est elle qui intéresse).
     *
     * @param moment   le moment (epoch ms)
     * @param fil      le nom du fil
     * @param panne    l'exception non interceptée (peut être nulle :
     *                 un throwable inconnu se dit « Throwable »)
     * @param pileFond la pile du fil principal (contexte, peut être
     *                 vide si LE fil principal a planté)
     * @return le rapport formaté pour l'écriture
     */
    public static RapportCrash de(long moment, String fil,
                                  Throwable panne, String pileFond) {
        Throwable cause = causePremiere(panne);
        StringWriter tampon = new StringWriter();
        if (panne != null) {
            panne.printStackTrace(new PrintWriter(tampon));
        }
        return new RapportCrash(moment, fil,
                cause == null ? "Throwable"
                        : cause.getClass().getName(),
                cause == null ? "" : String.valueOf(
                        cause.getMessage()),
                tampon.toString(), pileFond);
    }

    /**
     * Le rapport d'un GEL du fil principal (classe ANR) : le genre
     * GEL_INTERFACE, la pile du fil principal est LE diagnostic.
     *
     * @param moment          le moment (epoch ms)
     * @param pileFond        la pile du fil principal au gel
     * @param seuilSecondes  le seuil de gel dépassé (le rapport le
     *                       dit honnêtement)
     * @return le rapport formaté pour l'écriture
     */
    public static RapportCrash gel(long moment, String pileFond,
                                   int seuilSecondes) {
        String pile = pileFond == null ? "" : pileFond;
        return new RapportCrash(moment, "main", GEL_MARK,
                "le fil d'interface ne répond plus depuis plus de "
                        + seuilSecondes + " s", pile, pile);
    }

    /** La cause première d'une chaîne d'exceptions (elle-même sinon). */
    private static Throwable causePremiere(Throwable panne) {
        if (panne == null) {
            return null;
        }
        Throwable curseur = panne;
        while (curseur.getCause() != null
                && curseur.getCause() != curseur) {
            curseur = curseur.getCause();
        }
        return curseur;
    }

    /**
     * Le texte intégral du rapport ({@code joide.crash/1}) — ce qui
     * s'écrit sur le disque et s'affiche dans l'activité de crash.
     *
     * @param version  la version de l'application (BuildConfig)
     * @param android  la version d'Android de l'appareil
     * @param appareil le nom de l'appareil
     * @return le rapport textuel complet
     */
    public String texte(String version, String android, String appareil) {
        DateTimeFormatter horodatage = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        StringBuilder texte = new StringBuilder();
        texte.append(ENTETE).append('\n');
        texte.append("moment: ").append(horodatage.format(
                Instant.ofEpochMilli(moment))).append('\n');
        texte.append("fil: ").append(fil).append('\n');
        texte.append("genre: ").append(genre()).append('\n');
        if (!classe.isEmpty()) {
            texte.append("classe: ").append(classe).append('\n');
        }
        if (!message.isEmpty()) {
            texte.append("message: ").append(message).append('\n');
        }
        texte.append("version: ").append(version).append('\n');
        texte.append("android: ").append(android).append('\n');
        texte.append("appareil: ").append(appareil).append('\n');
        texte.append("--- pile du plantage ---\n");
        texte.append(pile.trim()).append('\n');
        if (!pileFond.isBlank() && genre == Genre.FRACTURE) {
            texte.append("--- pile du fil principal ---\n");
            texte.append(pileFond.trim()).append('\n');
        }
        return texte.toString();
    }

    /** Le genre du rapport (la vue affiche le type, nommé). */
    public String genre() {
        return genre == Genre.GEL_INTERFACE
                ? "gel de l'interface (ANR)" : "exception non interceptée";
    }

    /**
     * Le LIBELLÉ COURT du type pour l'activité de crash : la classe
     * exacte de la fracture, ou « interface gelée » pour un ANR.
     */
    public String libelleType() {
        return genre == Genre.GEL_INTERFACE
                ? "gel de l'interface (ANR)" : classe;
    }

    /**
     * Le nom de fichier du rapport (classé par moment, lisible).
     *
     * @return {@code crash-20261119-101431-123.txt}
     */
    public String nomFichier() {
        DateTimeFormatter motif = DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault());
        return String.format(Locale.ROOT, "crash-%s-%d.txt",
                motif.format(Instant.ofEpochMilli(moment)), moment % 1000);
    }
}
