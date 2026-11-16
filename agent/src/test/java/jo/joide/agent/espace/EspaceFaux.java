package jo.joide.agent.espace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le faux espace de travail des tests (lot O) : un répertoire
 * temporaire réel + un overlay en mémoire (le texte de l'éditeur
 * ouvert prime) — les outils s'éprouvent sur des VRAIES écritures
 * disque, sans aucune brique.
 */
public final class EspaceFaux implements EspaceTravailAgent {

    /** La racine du faux projet. */
    public final Path racine;

    /** L'overlay : chemin vers le texte ouvert dans l'éditeur. */
    public final Map<String, String> overlay =
            new ConcurrentHashMap<>();

    /** Les diagnostics programmes (chemin vers liste). */
    public final Map<String, List<DiagnosticEspace>> diagnosticsProgrammes =
            new ConcurrentHashMap<>();

    /** La définition programmée. */
    public Optional<PositionEspace> definitionProgrammee =
            Optional.empty();

    /** Le résultat d'exécution programmé. */
    public ResultatExecution resultatExecution =
            new ResultatExecution(true, 0, 10, "ok", false);

    /** Les dépendances ajoutées (module vers coordonnées). */
    public final List<String> dependancesAjoutees =
            new ArrayList<>();

    /** La réponse ask_user programmée. */
    public String reponseUtilisateur = "reponse libre";

    /** @param racine le répertoire racine (créé s'il n'existe pas) */
    public EspaceFaux(Path racine) throws IOException {
        this.racine = racine;
        Files.createDirectories(racine);
    }

    @Override
    public String lireFichier(String chemin) throws IOException {
        String texte = overlay.get(chemin);
        if (texte != null) {
            return texte;
        }
        Path cible = racine.resolve(chemin);
        if (!Files.isRegularFile(cible)) {
            throw new IOException("file not found: " + chemin);
        }
        return new String(Files.readAllBytes(cible),
                StandardCharsets.UTF_8);
    }

    @Override
    public List<String> listerDossier(String chemin) throws IOException {
        Path cible = racine.resolve(chemin);
        if (!Files.isDirectory(cible)) {
            throw new IOException("directory not found: " + chemin);
        }
        List<String> entrees = new ArrayList<>();
        try (var fluide = Files.list(cible)) {
            for (Path enfant : fluide.sorted().toList()) {
                entrees.add(enfant.getFileName().toString()
                        + (Files.isDirectory(enfant) ? "/" : ""));
            }
        }
        return entrees;
    }

    @Override
    public List<OccurrenceEspace> chercherTexte(String motif,
                                                boolean expressionReguliere,
                                                int plafond)
            throws IOException {
        Pattern motifCompile = expressionReguliere
                ? Pattern.compile(motif)
                : Pattern.compile(motif, Pattern.LITERAL);
        List<OccurrenceEspace> occurrences = new ArrayList<>();
        List<Path> fichiers;
        try (var fluide = Files.walk(racine)) {
            fichiers = fluide.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java")
                            || f.toString().endsWith(".kt")
                            || f.toString().endsWith(".xml")
                            || f.toString().endsWith(".md")
                            || f.toString().endsWith(".toml"))
                    .sorted()
                    .toList();
        }
        for (Path fichier : fichiers) {
            if (occurrences.size() >= plafond) {
                break;
            }
            String relatif = racine.relativize(fichier).toString()
                    .replace('\\', '/');
            String contenu = overlay.getOrDefault(relatif,
                    new String(Files.readAllBytes(fichier),
                            StandardCharsets.UTF_8));
            String[] lignes = contenu.split("\n", -1);
            for (int i = 0; i < lignes.length
                    && occurrences.size() < plafond; i++) {
                Matcher chercheur = motifCompile.matcher(lignes[i]);
                if (chercheur.find()) {
                    occurrences.add(new OccurrenceEspace(relatif,
                            i + 1, lignes[i].trim()));
                }
            }
        }
        return occurrences;
    }

    @Override
    public List<OccurrenceEspace> trouverSymbole(String nom) {
        throw new EspaceNonSupporte("symbol search");
    }

    @Override
    public List<DiagnosticEspace> diagnostics(String chemin) {
        List<DiagnosticEspace> programmes =
                diagnosticsProgrammes.get(chemin);
        if (programmes != null) {
            return programmes;
        }
        throw new EspaceNonSupporte("diagnostics");
    }

    @Override
    public String apercuProjet() {
        return "1 module, " + "2 files, engine ready";
    }

    @Override
    public void ecrireFichier(String chemin, String contenu)
            throws IOException {
        Path cible = racine.resolve(chemin);
        Files.createDirectories(cible.getParent());
        Files.write(cible, contenu.getBytes(StandardCharsets.UTF_8));
        overlay.remove(chemin);
    }

    @Override
    public void creerFichier(String chemin, String contenu)
            throws IOException {
        Path cible = racine.resolve(chemin);
        if (Files.exists(cible)) {
            throw new IOException("file already exists: " + chemin);
        }
        ecrireFichier(chemin, contenu);
    }

    @Override
    public void editerFichier(String chemin, String ancien,
                              String nouveau) throws IOException {
        String contenu = lireFichier(chemin);
        int premiere = contenu.indexOf(ancien);
        if (premiere < 0) {
            throw new IOException("old text not found in " + chemin);
        }
        if (contenu.indexOf(ancien, premiere + 1) >= 0) {
            throw new IOException("old text is not unique in "
                    + chemin);
        }
        ecrireFichier(chemin,
                contenu.substring(0, premiere) + nouveau
                        + contenu.substring(premiere + ancien.length()));
    }

    @Override
    public void renommerChemin(String de, String vers)
            throws IOException {
        Path source = racine.resolve(de);
        Path cible = racine.resolve(vers);
        if (!Files.exists(source)) {
            throw new IOException("source not found: " + de);
        }
        if (Files.exists(cible)) {
            throw new IOException("target already exists: " + vers);
        }
        Files.createDirectories(cible.getParent());
        Files.move(source, cible);
    }

    @Override
    public void supprimerChemin(String chemin) throws IOException {
        Path cible = racine.resolve(chemin);
        if (Files.isRegularFile(cible)) {
            Files.delete(cible);
        } else if (Files.isDirectory(cible)) {
            try (var fluide = Files.walk(cible)) {
                for (Path p : fluide.sorted(
                        Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            throw new IOException("not found: " + chemin);
        }
    }

    @Override
    public boolean ajouterDependance(String module, String coordonnees) {
        if (dependancesAjoutees.contains(coordonnees)) {
            return false;
        }
        dependancesAjoutees.add(coordonnees);
        return true;
    }

    @Override
    public Optional<PositionEspace> definition(String fichier, int ligne,
                                               int colonne) {
        return definitionProgrammee;
    }

    @Override
    public ResultatExecution executer(String cible, long delaiMaxSecondes) {
        return resultatExecution;
    }

    @Override
    public String lireMemoire() throws IOException {
        if (!overlay.containsKey("AGENTS.md")
                && !Files.isRegularFile(racine.resolve("AGENTS.md"))) {
            return "";
        }
        return lireFichier("AGENTS.md");
    }

    @Override
    public void ecrireMemoire(String contenu) throws IOException {
        ecrireFichier("AGENTS.md", contenu);
    }

    @Override
    public String demanderUtilisateur(
            List<QuestionUtilisateur> questions) {
        return reponseUtilisateur;
    }

    /** Écrit un fichier directement (préparation des tests). */
    public void preparer(String chemin, String contenu)
            throws IOException {
        ecrireFichier(chemin, contenu);
    }
}
