package jo.joide.app.android.accueil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.AtomicFiles;

/**
 * Les projets récents de l'accueil (ADR-É3, ADR-049) : la liste des
 * projets ouverts ou importés, persistée dans un fichier privé au
 * format JSON, écrite atomiquement (ADR : {@link AtomicFiles}).
 *
 * <p>Classe PURE (aucun type Android) : les tests JVM couvrent
 * l'aller-retour sérialisation, la tolérance aux documents corrompus
 * (une entrée invalide est signalée et sautée, jamais réparée en
 * silence) et les opérations de liste. La clé de stabilité d'une
 * entrée est son identifiant de dossier sous {@code projets/} ; le
 * nom affiché, lui, peut être renommé sans toucher au dossier.</p>
 */
public final class ProjetsRecents {

    /** Nom du fichier privé (à la racine des fichiers internes). */
    public static final String FICHIER = "projets-recents.json";

    /** Schéma versionné du fichier. */
    static final String SCHEMA = "joide.recents/1";

    /**
     * Une entrée : un projet connu de l'accueil. Le champ
     * {@code dossier} est le nom du répertoire sous
     * {@code filesDir/projets/} ; {@code nbModules} vaut 0 pour les
     * projets sans modules comptables (Gradle en compatibilité).
     */
    public record Entree(String id, String nomAffiche, TypeProjet type,
                         String dossier, long dernierOuverture,
                         int nbModules) {

        /**
         * Valide les champs obligatoires.
         *
         * @throws IllegalArgumentException si un champ obligatoire est
         *         absent ou vide
         */
        public Entree {
            if (id == null || id.isEmpty() || nomAffiche == null
                    || nomAffiche.isEmpty() || type == null
                    || dossier == null || dossier.isEmpty()) {
                throw new IllegalArgumentException(
                        "recent entry requires id, name, type, folder");
            }
        }
    }

    /** Résultat d'une lecture : les entrées valides et les problèmes. */
    public record Chargement(List<Entree> entrees, List<String> problemes) {
        /** Copie défensive des listes. */
        public Chargement {
            entrees = List.copyOf(entrees);
            problemes = List.copyOf(problemes);
        }
    }

    private ProjetsRecents() {
    }

    /**
     * Lit le fichier des projets récents.
     *
     * @param fichier chemin du fichier privé (peut ne pas exister :
     *        première ouverture, liste vide sans problème)
     * @return les entrées valides et les problèmes rencontrés
     */
    public static Chargement lire(Path fichier) {
        if (!Files.isRegularFile(fichier)) {
            return new Chargement(List.of(), List.of());
        }
        String texte;
        try {
            // Files.readString exige le niveau 33 : la lecture passe
            // par les octets (readAllBytes d'un fichier est niveau 26)
            texte = new String(Files.readAllBytes(fichier),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new Chargement(List.of(),
                    List.of("projets-recents illisible : "
                            + e.getMessage()));
        }
        return analyser(texte);
    }

    /**
     * Analyse un document de projets récents.
     *
     * @param texte le contenu JSON
     * @return les entrées valides et les problèmes (une entrée
     *         invalide est signalée et sautée)
     */
    public static Chargement analyser(String texte) {
        List<String> problemes = new ArrayList<>();
        Json.Document document = Json.parse(texte == null ? "" : texte);
        problemes.addAll(document.problems.stream()
                .map(p -> "projets-recents : " + p.message)
                .collect(java.util.stream.Collectors.toList()));
        JsonValue racine = document.root;
        if (racine.type() != JsonValue.Type.OBJECT) {
            if (problemes.isEmpty()) {
                problemes.add("projets-recents : racine absente");
            }
            return new Chargement(List.of(), problemes);
        }
        String schema = racine.member("schema").asString();
        if (!SCHEMA.equals(schema)) {
            problemes.add("projets-recents : schema attendu " + SCHEMA
                    + ", trouve " + schema);
            return new Chargement(List.of(), problemes);
        }
        List<Entree> entrees = new ArrayList<>();
        int rang = 0;
        for (JsonValue brut : racine.member("projets").items()) {
            rang++;
            Entree lue = lireEntree(brut);
            if (lue == null) {
                problemes.add("projets-recents : entree " + rang
                        + " invalide, sautee");
            } else if (entrees.stream().anyMatch(
                    e -> e.id().equals(lue.id()))) {
                problemes.add("projets-recents : entree " + rang
                        + " en doublon (" + lue.id() + "), sautee");
            } else {
                entrees.add(lue);
            }
        }
        return new Chargement(entrees, problemes);
    }

    private static Entree lireEntree(JsonValue brut) {
        if (brut.type() != JsonValue.Type.OBJECT) {
            return null;
        }
        String id = brut.member("id").asString();
        String nom = brut.member("nom").asString();
        String type = brut.member("type").asString();
        String dossier = brut.member("dossier").asString();
        long ouvert = brut.member("ouvert").asLong();
        long modules = brut.member("modules").asLong();
        if (id == null || nom == null || dossier == null || ouvert < 0L
                || modules < 0L) {
            return null;
        }
        return new Entree(id, nom,
                TypeProjet.deJson(type).orElse(TypeProjet.VIDE),
                dossier, ouvert, (int) Math.min(modules, Integer.MAX_VALUE));
    }

    /**
     * Écrit la liste dans le fichier privé, atomiquement.
     *
     * @param fichier chemin du fichier privé
     * @param entrees la liste complète à persister
     * @throws IOException si l'écriture échoue
     */
    public static void ecrire(Path fichier, List<Entree> entrees)
            throws IOException {
        AtomicFiles.writeString(fichier, serialiser(entrees));
    }

    /**
     * @param entrees la liste à sérialiser
     * @return le document JSON canonique (clés dans un ordre fixe,
     *         chaînes échappées, terminé par un saut de ligne)
     */
    public static String serialiser(List<Entree> entrees) {
        Objects.requireNonNull(entrees, "entrees");
        StringBuilder out = new StringBuilder(128 + entrees.size() * 96);
        out.append("{\"schema\":\"").append(SCHEMA).append("\",\"projets\":[");
        for (int i = 0; i < entrees.size(); i++) {
            Entree e = entrees.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"id\":").append(echapper(e.id()))
                    .append(",\"nom\":").append(echapper(e.nomAffiche()))
                    .append(",\"type\":\"").append(e.type().idJson())
                    .append("\",\"dossier\":").append(echapper(e.dossier()))
                    .append(",\"ouvert\":").append(e.dernierOuverture())
                    .append(",\"modules\":").append(e.nbModules())
                    .append('}');
        }
        out.append("]}\n");
        return out.toString();
    }

    /** Échappe une chaîne pour JSON (quotes, barres obliques inverses, contrôles). */
    private static String echapper(String texte) {
        StringBuilder out = new StringBuilder(texte.length() + 8);
        out.append('"');
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT,
                                "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    /**
     * Assainit un nom brut en identifiant de dossier : lettres,
     * chiffres, points, tirets conservés, tout le reste remplacé par
     * un souligné (la convention d'import de P8).
     *
     * @param brut le nom affiché ou le segment final de l'URI SAF
     * @return un identifiant jamais vide (« projet » en secours)
     */
    public static String assainirId(String brut) {
        String net = brut == null ? "" : brut.trim()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (net.isEmpty() || net.equals("_")) {
            return "projet";
        }
        return net;
    }

    /**
     * Cherche un identifiant libre à partir d'une base : « x », puis
     * « x-2 », « x-3 »… (le nom par défaut de l'assistant et
     * l'import en dépendent — jamais de collision silencieuse).
     *
     * @param existantes les entrées actuelles
     * @param base l'identifiant assaini voulu
     * @return un identifiant absent de la liste
     */
    public static String idUnique(List<Entree> existantes, String base) {
        String candidate = base;
        int suffixe = 2;
        while (idPris(existantes, candidate)) {
            candidate = base + "-" + suffixe;
            suffixe++;
        }
        return candidate;
    }

    private static boolean idPris(List<Entree> existantes,
                                  String candidate) {
        for (Entree entree : existantes) {
            if (entree.id().equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enregistre une ouverture : l'entrée existe (dernière ouverture
     * actualisée) ou est ajoutée ; la liste reste triée par dernière
     * ouverture décroissante.
     *
     * @param entrees la liste actuelle
     * @param aJour l'entrée à jour (identifiant connu ou nouveau)
     * @return la nouvelle liste triée
     */
    public static List<Entree> ouvrir(List<Entree> entrees, Entree aJour) {
        Objects.requireNonNull(aJour, "aJour");
        List<Entree> out = new ArrayList<>(entrees.size() + 1);
        boolean vu = false;
        for (Entree e : entrees) {
            if (e.id().equals(aJour.id())) {
                out.add(aJour);
                vu = true;
            } else {
                out.add(e);
            }
        }
        if (!vu) {
            out.add(aJour);
        }
        out.sort(OrdreRecent.INSTANCE);
        return out;
    }

    /**
     * Renomme l'affichage d'une entrée (le dossier sous
     * {@code projets/} ne bouge jamais).
     *
     * @param entrees la liste actuelle
     * @param id l'identifiant de l'entrée
     * @param nouveauNom le nouveau nom affiché (non vide)
     * @return la nouvelle liste, ou l'ancienne si l'entrée est absente
     */
    public static List<Entree> renommer(List<Entree> entrees, String id,
                                        String nouveauNom) {
        if (nouveauNom == null || nouveauNom.trim().isEmpty()) {
            return entrees;
        }
        List<Entree> out = new ArrayList<>(entrees);
        for (int i = 0; i < out.size(); i++) {
            Entree e = out.get(i);
            if (e.id().equals(id)) {
                out.set(i, new Entree(e.id(), nouveauNom.trim(), e.type(),
                        e.dossier(), e.dernierOuverture(), e.nbModules()));
            }
        }
        return out;
    }

    /**
     * Retire une entrée de la liste (le dossier reste sur le disque —
     * l'effacement du stockage est une autre action, distincte).
     *
     * @param entrees la liste actuelle
     * @param id l'identifiant à retirer
     * @return la nouvelle liste sans l'entrée
     */
    public static List<Entree> retirer(List<Entree> entrees, String id) {
        List<Entree> out = new ArrayList<>(entrees.size());
        for (Entree e : entrees) {
            if (!e.id().equals(id)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Tri par dernière ouverture décroissante, puis par identifiant. */
    static final class OrdreRecent
            implements java.util.Comparator<Entree> {
        static final OrdreRecent INSTANCE = new OrdreRecent();

        @Override
        public int compare(Entree a, Entree b) {
            int parMoment = Long.compare(b.dernierOuverture(),
                    a.dernierOuverture());
            return parMoment != 0 ? parMoment
                    : a.id().compareTo(b.id());
        }
    }
}
