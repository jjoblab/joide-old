package jo.joide.ide.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jo.joide.agent.espace.CheminsConfines;
import jo.joide.agent.espace.DiagnosticEspace;
import jo.joide.agent.espace.EspaceTravailAgent;
import jo.joide.agent.espace.OccurrenceEspace;
import jo.joide.agent.espace.PositionEspace;
import jo.joide.agent.espace.ResultatExecution;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Exposure;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.resolve.ResolutionResult;
import jo.joide.deps.resolve.Resolver;
import jo.joide.deps.resolve.ResolverConfig;
import jo.joide.ide.langages.CibleNavigation;
import jo.joide.ide.langages.DiagnosticIde;
import jo.joide.ide.langages.MoteursLangage;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Module;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceEdit;
import jo.joide.platform.Problem;

/**
 * L'espace de travail de l'agent SUR LES VRAIES BRIQUES (lot O,
 * ADR-059) : fichiers par {@code java.nio} sous la racine du projet
 * (confinement revérifié ici — la défense en profondeur),
 * diagnostics et définitions par {@link MoteursLangage} (lot C),
 * l'overlay de l'éditeur injecté par l'hôte (le texte ouvert prime),
 * l'exécution bornée et la résolution de dépendances injectées —
 * l'atelier (lot P) y branchera le lanceur de build et le résolveur
 * P4 avec lockfile.
 *
 * <p>Ce que les briques ne servent pas encore reste au DÉFAUT
 * DÉGRADÉ du port (références, renommage, formatage — vides honnêtes
 * du lot D) : l'agent le SAIT, il ne devine pas.</p>
 */
public final class EspaceTravailIde implements EspaceTravailAgent {

    /** Les extensions cherchées par search_text (celles de l'éditeur). */
    private static final Set<String> CHERCHABLES = Set.of(
            "java", "kt", "kts", "gradle", "groovy", "xml", "json",
            "md", "toml", "properties", "txt", "cfg", "pro");

    /** Les répertoires jamais parcourus (sorties, caches, VCS). */
    private static final Set<String> EXCLUS = Set.of(
            "build", "build-joide", ".git", ".gradle", ".joide",
            "node_modules");

    /** Le nom du fichier mémoire du projet (constant du port). */
    public static final String FICHIER_MEMOIRE =
            EspaceTravailAgent.FICHIER_MEMOIRE;

    /** L'exécuteur borné injecté (l'hôte branche le moteur de build). */
    public interface ExecuteurBorne {
        /**
         * @param cible            la cible demandée
         * @param delaiMaxSecondes le plafond
         * @return le résultat de l'exécution
         */
        ResultatExecution executer(String cible, long delaiMaxSecondes);
    }

    /** La résolution de dépendances injectée (résolveur P4 + lockfile). */
    public interface PortDependances {
        /**
         * @param coordonnees « groupe:artefact:version »
         * @return vrai si la résolution réussit (lockfile écrit)
         */
        boolean resoudre(String coordonnees);
    }

    private final Path racine;
    private Workspace modele;
    private final Map<String, String> overlay = new LinkedHashMap<>();
    private MoteursLangage moteurs;
    private Function<String, String> overlayEditeur;
    private ExecuteurBorne executeur;
    private PortDependances dependances;
    private Function<WorkspaceEdit, Workspace> editeurModele;

    /**
     * @param racine la racine du projet (le confinement s'y ancre)
     * @param modele le modèle déclaratif courant (aperçu, dépendances)
     */
    public EspaceTravailIde(Path racine, Workspace modele) {
        this.racine = racine.toAbsolutePath().normalize();
        this.modele = modele;
    }

    /** Pose les moteurs de langage (diagnostics, définitions). */
    public EspaceTravailIde poserMoteurs(MoteursLangage moteurs) {
        this.moteurs = moteurs;
        return this;
    }

    /**
     * Pose l'overlay de l'éditeur : chemin relatif vers le texte
     * ouvert (le tampon prime sur le disque — ADR-É9).
     */
    public EspaceTravailIde poserOverlayEditeur(
            Function<String, String> overlayEditeur) {
        this.overlayEditeur = overlayEditeur;
        return this;
    }

    /** Pose l'exécuteur borné (moteur de build). */
    public EspaceTravailIde poserExecuteur(ExecuteurBorne executeur) {
        this.executeur = executeur;
        return this;
    }

    /** Pose la résolution de dépendances (résolveur P4 + lockfile). */
    public EspaceTravailIde poserDependances(
            PortDependances dependances) {
        this.dependances = dependances;
        return this;
    }

    /**
     * Pose l'éditeur de modèle : comment appliquer un
     * {@link WorkspaceEdit} (l'hôte recharge l'espace après).
     */
    public EspaceTravailIde poserEditeurModele(
            Function<WorkspaceEdit, Workspace> editeurModele) {
        this.editeurModele = editeurModele;
        return this;
    }

    /** L'overlay local (les écritures de l'agent y passent aussi). */
    public Map<String, String> overlay() {
        return overlay;
    }

    // ------------------------------------------------------------------
    // Confinement : la défense en profondeur côté implémentation
    // ------------------------------------------------------------------

    private Path confine(String chemin) throws IOException {
        String confine = CheminsConfines.confiner(chemin);
        Path cible = racine.resolve(confine).normalize();
        if (!cible.startsWith(racine)) {
            throw new IOException("path escapes the project: " + chemin);
        }
        return cible;
    }

    // ------------------------------------------------------------------
    // Lectures
    // ------------------------------------------------------------------

    @Override
    public String lireFichier(String chemin) throws IOException {
        String texte = texteVivant(chemin);
        if (texte != null) {
            return texte;
        }
        Path cible = confine(chemin);
        if (!Files.isRegularFile(cible)) {
            throw new IOException("file not found: " + chemin);
        }
        return new String(Files.readAllBytes(cible),
                StandardCharsets.UTF_8);
    }

    /** Le texte VIVANT : l'overlay de l'éditeur d'abord, le nôtre ensuite. */
    private String texteVivant(String chemin) {
        if (overlayEditeur != null) {
            String texte = overlayEditeur.apply(chemin);
            if (texte != null) {
                return texte;
            }
        }
        return overlay.get(chemin);
    }

    @Override
    public List<String> listerDossier(String chemin) throws IOException {
        Path cible = chemin == null || chemin.isEmpty()
                ? racine : confine(chemin);
        if (!Files.isDirectory(cible)) {
            throw new IOException("directory not found: " + chemin);
        }
        List<String> entrees = new ArrayList<>();
        try (var fluide = Files.list(cible)) {
            for (Path enfant : fluide.sorted().collect(
                    java.util.stream.Collectors.toList())) {
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
        java.util.regex.Pattern pattern = expressionReguliere
                ? java.util.regex.Pattern.compile(motif)
                : java.util.regex.Pattern.compile(motif,
                        java.util.regex.Pattern.LITERAL);
        List<OccurrenceEspace> occurrences = new ArrayList<>();
        parcourir(racine, pattern, occurrences, plafond);
        return occurrences;
    }

    private void parcourir(Path dossier,
                           java.util.regex.Pattern pattern,
                           List<OccurrenceEspace> occurrences,
                           int plafond) throws IOException {
        if (occurrences.size() >= plafond) {
            return;
        }
        List<Path> enfants;
        try (var fluide = Files.list(dossier)) {
            enfants = fluide.sorted().collect(
                    java.util.stream.Collectors.toList());
        }
        for (Path enfant : enfants) {
            if (occurrences.size() >= plafond) {
                return;
            }
            String nom = enfant.getFileName().toString();
            if (Files.isDirectory(enfant)) {
                if (!EXCLUS.contains(nom)) {
                    parcourir(enfant, pattern, occurrences, plafond);
                }
            } else {
                int point = nom.lastIndexOf('.');
                if (point < 0 || !CHERCHABLES.contains(
                        nom.substring(point + 1)
                                .toLowerCase(java.util.Locale.ROOT))) {
                    continue;
                }
                String relatif = racine.relativize(enfant).toString()
                        .replace('\\', '/');
                String contenu = texteVivant(relatif);
                if (contenu == null) {
                    contenu = new String(Files.readAllBytes(enfant),
                            StandardCharsets.UTF_8);
                }
                String[] lignes = contenu.split("\n", -1);
                for (int i = 0; i < lignes.length
                        && occurrences.size() < plafond; i++) {
                    if (pattern.matcher(lignes[i]).find()) {
                        occurrences.add(new OccurrenceEspace(relatif,
                                i + 1, lignes[i].trim()));
                    }
                }
            }
        }
    }

    @Override
    public List<DiagnosticEspace> diagnostics(String chemin) {
        if (moteurs == null) {
            throw new jo.joide.agent.espace.EspaceNonSupporte(
                    "diagnostics");
        }
        try {
            Path cible = confine(chemin);
            String texte = lireFichier(chemin);
            List<DiagnosticIde> diagnostiques = moteurs.diagnostiquer(
                    cible.toString(), texte);
            List<DiagnosticEspace> rendus = new ArrayList<>();
            for (DiagnosticIde diagnostic : diagnostiques) {
                rendus.add(new DiagnosticEspace(
                        diagnostic.gravite().name(),
                        diagnostic.message(), chemin,
                        ligneDe(cible.toString(),
                                diagnostic.debut())));
            }
            return rendus;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "cannot read " + chemin + ": " + e.getMessage());
        }
    }

    @Override
    public String apercuProjet() {
        if (modele == null) {
            throw new jo.joide.agent.espace.EspaceNonSupporte(
                    "project overview");
        }
        StringBuilder apercu = new StringBuilder();
        apercu.append("project: ").append(modele.name());
        apercu.append(", modules: ").append(modele.allModules().size());
        for (Module module : modele.allModules().values()) {
            apercu.append("\n").append(module.path())
                    .append(" (").append(module.type().id())
                    .append(')');
        }
        return apercu.toString();
    }

    // ------------------------------------------------------------------
    // Écritures
    // ------------------------------------------------------------------

    @Override
    public void ecrireFichier(String chemin, String contenu)
            throws IOException {
        Path cible = confine(chemin);
        if (cible.getParent() != null) {
            Files.createDirectories(cible.getParent());
        }
        Files.write(cible, contenu.getBytes(StandardCharsets.UTF_8));
        overlay.remove(chemin);
    }

    @Override
    public void creerFichier(String chemin, String contenu)
            throws IOException {
        Path cible = confine(chemin);
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
    public void renommerChemin(String de, String vers) throws IOException {
        Path source = confine(de);
        Path cible = confine(vers);
        if (!Files.exists(source)) {
            throw new IOException("source not found: " + de);
        }
        if (Files.exists(cible)) {
            throw new IOException("target already exists: " + vers);
        }
        if (cible.getParent() != null) {
            Files.createDirectories(cible.getParent());
        }
        Files.move(source, cible);
    }

    @Override
    public void supprimerChemin(String chemin) throws IOException {
        Path cible = confine(chemin);
        if (Files.isRegularFile(cible)) {
            Files.delete(cible);
        } else if (Files.isDirectory(cible)) {
            try (var fluide = Files.walk(cible)) {
                for (Path p : fluide.sorted(
                        Comparator.reverseOrder()).collect(
                        java.util.stream.Collectors.toList())) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            throw new IOException("not found: " + chemin);
        }
    }

    @Override
    public boolean ajouterDependance(String module, String coordonnees) {
        if (dependances == null || editeurModele == null
                || modele == null) {
            throw new jo.joide.agent.espace.EspaceNonSupporte(
                    "add dependency");
        }
        Module cible = null;
        for (Module candidat : modele.allModules().values()) {
            if (candidat.path().equals(module)) {
                cible = candidat;
                break;
            }
        }
        if (cible == null) {
            throw new IllegalArgumentException(
                    "unknown module: " + module);
        }
        for (DependencyRef existante : cible.dependencies()) {
            if (!existante.isModule()
                    && coordonnees.equals(existante.coordinates())) {
                return false;
            }
        }
        if (!dependances.resoudre(coordonnees)) {
            throw new IllegalArgumentException(
                    "resolution failed for " + coordonnees);
        }
        List<DependencyRef> nouvelles = new ArrayList<>(
                cible.dependencies());
        nouvelles.add(DependencyRef.external(coordonnees,
                DependencyScope.IMPLEMENTATION, Set.of()));
        Module remplace = new Module(cible.path(), cible.name(),
                cible.type(), cible.sourceSets(), nouvelles,
                cible.facets());
        WorkspaceEdit edition = WorkspaceEdit.withModule(modele,
                remplace);
        for (Map.Entry<String, String> fichier
                : edition.files().entrySet()) {
            try {
                ecrireFichier(fichier.getKey(), fichier.getValue());
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "cannot write model: " + e.getMessage());
            }
        }
        // l'hôte applique l'édition et rend l'espace RECHARGÉ : le
        // port le garde courant — la demande suivante voit la
        // dépendance déjà là
        modele = editeurModele.apply(edition);
        return true;
    }

    // ------------------------------------------------------------------
    // Sémantique
    // ------------------------------------------------------------------

    @Override
    public Optional<PositionEspace> definition(String fichier, int ligne,
                                               int colonne) {
        if (moteurs == null) {
            throw new jo.joide.agent.espace.EspaceNonSupporte(
                    "go to definition");
        }
        try {
            Path cible = confine(fichier);
            String texte = lireFichier(fichier);
            int decalage = decalageDe(texte, ligne, colonne);
            List<CibleNavigation> cibles = moteurs.definition(
                    cible.toString(), texte, decalage);
            if (cibles.isEmpty()) {
                return Optional.empty();
            }
            CibleNavigation cibleNav = cibles.get(0);
            if (cibleNav.uri().startsWith("jdt://")) {
                return Optional.of(new PositionEspace(
                        cibleNav.uri(), 0, 0, cibleNav.nomSimple()));
            }
            Path cheminCible = java.nio.file.Paths.get(cheminDepuisUri(
                    cibleNav.uri()));
            return Optional.of(new PositionEspace(
                    cheminCible.startsWith(racine)
                            ? racine.relativize(cheminCible).toString()
                                    .replace('\\', '/')
                            : cibleNav.uri(),
                    ligneDe(cheminCible.toString(), cibleNav.debut()),
                    1, cibleNav.nomSimple()));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "cannot read " + fichier + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Exécution, mémoire
    // ------------------------------------------------------------------

    @Override
    public ResultatExecution executer(String cible, long delaiMaxSecondes) {
        if (executeur == null) {
            throw new jo.joide.agent.espace.EspaceNonSupporte(
                    "run task");
        }
        return executeur.executer(cible, delaiMaxSecondes);
    }

    @Override
    public String lireMemoire() throws IOException {
        Path cible = racine.resolve(FICHIER_MEMOIRE);
        if (!Files.isRegularFile(cible)) {
            return "";
        }
        return new String(Files.readAllBytes(cible),
                StandardCharsets.UTF_8);
    }

    @Override
    public void ecrireMemoire(String contenu) throws IOException {
        ecrireFichier(FICHIER_MEMOIRE, contenu);
    }

    // ------------------------------------------------------------------
    // Conversions ligne/décalage (elles appartiennent à l'hôte)
    // ------------------------------------------------------------------

    private int decalageDe(String texte, int ligne, int colonne) {
        int decalage = 0;
        int ligneCourante = 1;
        for (int i = 0; i < texte.length(); i++) {
            if (ligneCourante >= ligne) {
                break;
            }
            if (texte.charAt(i) == '\n') {
                ligneCourante++;
            }
            decalage = i + 1;
        }
        return decalage + Math.max(0, colonne - 1);
    }

    /** Épluche un « file: » d'URI au besoin (les moteurs mélangent les formes). */
    private static String cheminDepuisUri(String uri) {
        if (uri.startsWith("file://")) {
            return uri.substring("file://".length());
        }
        if (uri.startsWith("file:")) {
            return uri.substring("file:".length());
        }
        return uri;
    }

    private int ligneDe(String uri, int decalage) {
        try {
            String texte = new String(
                    Files.readAllBytes(java.nio.file.Paths.get(uri)),
                    StandardCharsets.UTF_8);
            int ligne = 1;
            for (int i = 0; i < decalage && i < texte.length(); i++) {
                if (texte.charAt(i) == '\n') {
                    ligne++;
                }
            }
            return ligne;
        } catch (IOException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Le résolveur P4 assemblé (l'hôte du bac à sable s'en sert)
    // ------------------------------------------------------------------

    /**
     * Un port de dépendances sur le résolveur P4 avec lockfile — le
     * câblage que l'atelier (lot P) refera avec ses propres dépôts.
     *
     * @param depots   les dépôts à essayer, dans l'ordre
     * @param cache    le cache m2 partagé
     * @param lockfile le chemin du joide.lock
     * @return le port qui résout et écrit le lockfile
     */
    public static PortDependances resolveurP4(
            List<jo.joide.deps.repo.Repository> depots,
            DependencyCache cache, Path lockfile) {
        return coordonnees -> {
            String[] parties = coordonnees.split(":");
            if (parties.length != 3) {
                throw new IllegalArgumentException(
                        "expected group:artifact:version, got: "
                                + coordonnees);
            }
            ResolverConfig.Builder config = new ResolverConfig.Builder()
                    .cache(cache)
                    .lockfile(lockfile);
            for (jo.joide.deps.repo.Repository depot : depots) {
                config.repository(depot);
            }
            ResolutionResult resultat = new Resolver(config.build())
                    .resolve(List.of(new DependencyRequest(parties[0],
                            parties[1],
                            VersionRequirement.parse(parties[2]),
                            Exposure.IMPLEMENTATION)));
            boolean sain = resultat.problems.stream()
                    .noneMatch(Problem::isError);
            return sain && !resultat.artifacts.isEmpty();
        };
    }

    /** Le cache m2 local (l'hôte choisit l'emplacement). */
    public static DependencyCache cacheM2(Path m2) throws IOException {
        return new DependencyCache(m2);
    }
}
