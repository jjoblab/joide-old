package jo.joide.deps.resolve;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import jo.joide.deps.Checksums;
import jo.joide.deps.Coordinate;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Version;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.aar.AarContents;
import jo.joide.deps.aar.AarExtractor;
import jo.joide.deps.gmm.GmmModule;
import jo.joide.deps.gmm.GmmModule.GmmDependency;
import jo.joide.deps.gmm.GmmModule.GmmFile;
import jo.joide.deps.gmm.GmmModule.GmmVariant;
import jo.joide.deps.gmm.GmmParser;
import jo.joide.deps.lock.EdgeKind;
import jo.joide.deps.lock.Lockfile;
import jo.joide.deps.lock.LockfileEdge;
import jo.joide.deps.lock.LockfileEntry;
import jo.joide.deps.pom.PomModel;
import jo.joide.deps.pom.PomParser;
import jo.joide.deps.repo.Layout;
import jo.joide.deps.repo.MavenMetadata;
import jo.joide.deps.repo.MavenMetadataParser;
import jo.joide.deps.repo.Repository;
import jo.joide.deps.repo.TransportResponse;
import jo.joide.platform.AtomicFiles;
import jo.joide.platform.Problem;
import jo.joide.platform.WorkerPools;

/**
 * Le résolveur de dépendances (P4). Deux phases :
 *
 * <p><b>A — lockfile</b> : si un joide.lock lisible couvre toutes les
 * demandes et que chaque artefact est vérifiable au cache (empreinte
 * lue et confirmée, taille concordante), la résolution se fait
 * entièrement hors réseau — zéro requête, y compris pour les
 * instantanés et sélecteurs dynamiques, figés à leur valeur concrète.
 * Un instantané littéral (« 1.0-SNAPSHOT ») accepte sa forme concrète
 * (« 1.0-horodatage-numéro ») : c'est le gel.</p>
 *
 * <p><b>B — complète</b> : choix de versions par point fixe (l'arbitrage
 * des conflits est « la plus récente gagne », une exigence stricte
 * s'impose), fermeture avec exclusions par sous-arbre (une exclusion
 * ampute le sous-graphe de la dépendance qui la porte, pas les autres
 * chemins — la fermeture est l'union des chemins non exclus),
 * téléchargements parallèles plafonnés, vérification des checksums
 * publiés (GMM incorporées d'abord, puis fichier .sha256, puis .sha1 ;
 * la première source disponible fait foi, toute divergence est un
 * échec explicite), extraction des AAR protégée zip-slip, puis
 * écriture déterministe du lockfile, inchangé octet pour octet si la
 * résolution redonne le même contenu.</p>
 */
public final class Resolver {

    private static final int MAX_FIXED_POINT_ROUNDS = 64;
    private static final int MAX_AVAILABLE_AT_HOPS = 3;

    private final ResolverConfig config;
    private final List<Problem> problems = new ArrayList<>();

    private final Map<String, Metadata> metadataMemo = new HashMap<>();
    private final Map<String, RawEdges> edgeMemo = new HashMap<>();
    private final Map<String, Choice> choices = new HashMap<>();
    private final Map<String, MavenMetadata> artifactMetadataMemo =
            new HashMap<>();
    private final Map<String, String> snapshotMemo = new HashMap<>();
    private final Set<String> pomFetchMemo = new HashSet<>();

    private int requests;
    private int downloadCount;
    private long downloadBytes;
    private boolean lockfileRewritten;

    public Resolver(ResolverConfig config) {
        this.config = config;
    }

    /**
     * @param requests demandes de dépendances
     * @return le résultat de la résolution
     */
    public ResolutionResult resolve(List<DependencyRequest> requests) {
        List<DependencyRequest> roots = new ArrayList<>(requests);
        if (roots.isEmpty()) {
            return new ResolutionResult(List.of(), List.of(), false, false,
                    stats());
        }
        // Phase A : couverture par lockfile, sans réseau.
        Lockfile lock = loadLockfile();
        if (lock != null) {
            List<ResolvedArtifact> covered = coverFromLockfile(lock, roots);
            if (covered != null) {
                return new ResolutionResult(covered, List.copyOf(problems),
                        true, false, stats());
            }
        }
        // Hors-ligne strict : jamais de réseau, échec explicite.
        if (config.offlineStrict) {
            StringBuilder missing = new StringBuilder();
            for (DependencyRequest r : roots) {
                missing.append(missing.length() > 0 ? ", " : "")
                        .append(r.ga()).append(":").append(r.requirement());
            }
            problems.add(Problem.error("deps.offline",
                    "mode hors-ligne strict : résolution réseau interdite ;"
                            + " manquant au cache/lockfile : " + missing));
            return failure();
        }
        fullResolve(roots);
        if (success()) {
            maybeWriteLockfile();
        }
        return new ResolutionResult(List.copyOf(artifacts),
                List.copyOf(problems), false, lockfileRewritten, stats());
    }

    private ResolutionResult.Stats stats() {
        return new ResolutionResult.Stats(requests, downloadCount,
                downloadBytes);
    }

    // ------------------------------------------------------------------
    // Phase A : couverture par lockfile
    // ------------------------------------------------------------------

    private Lockfile loadLockfile() {
        Path path = config.lockfilePath;
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add(Problem.warn("deps.lockfile.read",
                    "joide.lock illisible : " + e.getMessage()));
            return null;
        }
        Lockfile.ParseResult parsed = Lockfile.parse(text);
        for (Lockfile.Problem lp : parsed.problems) {
            problems.add(Problem.warn("deps.lockfile.line", lp.toString()));
        }
        if (parsed.lockfile.entries().isEmpty()) {
            return null;
        }
        return parsed.lockfile;
    }

    private List<ResolvedArtifact> coverFromLockfile(Lockfile lock,
                                                     List<DependencyRequest> roots) {
        Map<String, LockfileEntry> byKey = new HashMap<>();
        for (LockfileEntry e : lock.entries()) {
            byKey.put(e.coordinate.toString(), e);
        }
        LinkedHashMap<String, LockfileEntry> taken = new LinkedHashMap<>();
        List<String> queue = new ArrayList<>();
        for (DependencyRequest r : roots) {
            LockfileEntry root = rootEntry(lock, r);
            if (root == null) {
                return null;
            }
            if (taken.putIfAbsent(root.coordinate.toString(), root) == null) {
                queue.add(root.coordinate.toString());
            }
        }
        while (!queue.isEmpty()) {
            String key = queue.remove(0);
            LockfileEntry entry = byKey.get(key);
            if (entry == null || !verifyInCache(entry)) {
                return null;
            }
            for (LockfileEdge edge : entry.edges) {
                String targetKey = edge.target.toString();
                if (!byKey.containsKey(targetKey)) {
                    return null; // arête pointant hors lockfile
                }
                if (taken.putIfAbsent(targetKey, byKey.get(targetKey)) == null
                        && edge.kind != EdgeKind.COMPILE) {
                    queue.add(targetKey);
                }
            }
        }
        List<ResolvedArtifact> out = new ArrayList<>();
        for (LockfileEntry entry : taken.values()) {
            ResolvedArtifact artifact = materialize(entry);
            if (artifact == null) {
                return null;
            }
            out.add(artifact);
        }
        out.sort(Comparator.comparing(a -> a.coordinate));
        return out;
    }

    private LockfileEntry rootEntry(Lockfile lock, DependencyRequest r) {
        for (LockfileEntry e : lock.entries()) {
            if (!e.ga().equals(r.ga())) {
                continue;
            }
            if (e.coordinate.classifier() != null) {
                continue; // entrée de classifieur : pas l'artefact principal
            }
            if (!frozenAccepts(r.requirement(), e.coordinate.version())) {
                continue;
            }
            return e;
        }
        return null;
    }

    /**
     * L'acceptation « gelée » : une exigence exacte d'instantané
     * littéral accepte sa forme concrète de même base.
     */
    private boolean frozenAccepts(VersionRequirement requirement,
                                  Version candidate) {
        if (requirement.accepts(candidate)) {
            return true;
        }
        if (requirement.kind() != VersionRequirement.Kind.EXACT) {
            return false;
        }
        Version wanted = requirement.exact();
        String wantedBase = Layout.snapshotBase(wanted.raw());
        String candidateBase = Layout.concreteBase(candidate.raw());
        return wantedBase != null && candidateBase != null
                && wantedBase.equals(candidateBase)
                && requirement.exact().isSnapshot();
    }

    private boolean verifyInCache(LockfileEntry entry) {
        byte[] content = null;
        try {
            content = config.cache.read(entry.sha256);
        } catch (IOException e) {
            // lu comme absent
        }
        if (content == null) {
            return false;
        }
        if (entry.size >= 0 && content.length != entry.size) {
            return false; // lockfile et cache en désaccord : re-résolution
        }
        return true;
    }

    private ResolvedArtifact materialize(LockfileEntry entry) {
        boolean aar = "aar".equals(entry.coordinate.extension());
        AarContents contents = null;
        if (aar) {
            contents = extractAar(config.cache.fileOf(entry.sha256),
                    entry.sha256);
            if (contents == null) {
                return null;
            }
        }
        return new ResolvedArtifact(entry.coordinate, entry.sha256,
                entry.size, entry.repositoryId,
                config.cache.fileOf(entry.sha256), aar, contents,
                entry.edges);
    }

    // ------------------------------------------------------------------
    // Phase B : résolution complète
    // ------------------------------------------------------------------

    private void fullResolve(List<DependencyRequest> roots) {
        boolean changed = true;
        int rounds = 0;
        while (changed && rounds++ < MAX_FIXED_POINT_ROUNDS) {
            changed = false;
            edgeMemo.clear();
            Set<String> visited = new HashSet<>();
            for (DependencyRequest r : roots) {
                changed |= chooseForRoot(r);
                changed |= expand(r.ga(), visited);
            }
        }
        if (rounds >= MAX_FIXED_POINT_ROUNDS) {
            problems.add(Problem.error("deps.fixedpoint",
                    "pas de point fixe des versions en "
                            + MAX_FIXED_POINT_ROUNDS + " tours"));
        }
        collectClosure(roots);
        fetchClosureFiles();
        buildArtifacts();
    }

    private boolean chooseForRoot(DependencyRequest r) {
        Choice existing = choices.get(r.ga());
        Choice picked = pick(r.ga(), r.requirement(), null, existing);
        return picked != null && register(r.ga(), picked);
    }

    private boolean expand(String ga, Set<String> visited) {
        if (!visited.add(ga)) {
            return false;
        }
        Choice choice = choices.get(ga);
        if (choice == null) {
            return false;
        }
        RawEdges edges = edgeMemo.computeIfAbsent(ga,
                g -> buildEdges(g, choices.get(g).version.raw()));
        boolean changed = false;
        for (RawEdge edge : edges.list) {
            changed |= expandWithRequirement(edge);
            changed |= expand(edge.ga, visited);
        }
        return changed;
    }

    private boolean expandWithRequirement(RawEdge edge) {
        Choice existing = choices.get(edge.ga);
        Choice picked = pick(edge.ga, edge.requirement(), edge, existing);
        return picked != null && register(edge.ga, picked);
    }

    /** Enregistre un choix ; vrai si la valeur stockée a changé. */
    private boolean register(String ga, Choice picked) {
        Choice existing = choices.get(ga);
        if (existing == null) {
            choices.put(ga, picked);
            return true;
        }
        if (existing.version.compareTo(picked.version) == 0
                && existing.strict == picked.strict) {
            return false;
        }
        Choice winner;
        if (picked.strict && !existing.strict) {
            winner = picked;
        } else if (existing.strict && !picked.strict) {
            winner = existing;
        } else {
            winner = existing.version.compareTo(picked.version) >= 0
                    ? existing : picked;
        }
        choices.put(ga, winner);
        return existing.version.compareTo(winner.version) != 0
                || existing.strict != winner.strict;
    }

    /**
     * Choisit une version : l'existant compte, s'il satisfait
     * l'exigence il est repris (évite les métadonnées).
     */
    private Choice pick(String ga, VersionRequirement requirement,
                        RawEdge edge, Choice existing) {
        if (existing != null && !existing.strict
                && frozenAccepts(requirement, existing.version)) {
            return existing;
        }
        if (existing != null && existing.strict) {
            return existing; // une exigence stricte déjà posée s'impose
        }
        switch (requirement.kind()) {
            case EXACT:
                return new Choice(requirement.exact(),
                        edge != null && edge.strictly);
            default:
                break;
        }
        MavenMetadata metadata = artifactMetadata(ga);
        if (metadata == null) {
            problems.add(Problem.error("deps.metadata",
                    "aucune métadonnée pour " + ga
                            + " : impossible de choisir parmi "
                            + requirement));
            return null;
        }
        List<Version> candidates = new ArrayList<>();
        for (String raw : metadata.versions) {
            try {
                candidates.add(Version.parse(raw));
            } catch (IllegalArgumentException e) {
                // version illisible dans les métadonnées : ignorée
            }
        }
        switch (requirement.kind()) {
            case RANGE:
                Version best = null;
                for (Version c : candidates) {
                    if (requirement.range().contains(c)
                            && (best == null || c.compareTo(best) > 0)) {
                        best = c;
                    }
                }
                if (best != null) {
                    return new Choice(best, false);
                }
                problems.add(Problem.error("deps.range",
                        "aucune version de " + ga + " dans "
                                + requirement.range()));
                return null;
            case LATEST_RELEASE:
                Version release = null;
                for (Version c : candidates) {
                    if (!c.isSnapshot()
                            && (release == null || c.compareTo(release) > 0)) {
                        release = c;
                    }
                }
                if (release != null) {
                    return new Choice(release, false);
                }
                problems.add(Problem.error("deps.latest",
                        "aucune version publiée (non instantané) de " + ga));
                return null;
            case LATEST_INTEGRATION:
                Version any = null;
                for (Version c : candidates) {
                    if (any == null || c.compareTo(any) > 0) {
                        any = c;
                    }
                }
                if (any != null) {
                    return new Choice(any, false);
                }
                problems.add(Problem.error("deps.latest",
                        "aucune version de " + ga));
                return null;
            default:
                problems.add(Problem.error("deps.choice",
                        "exigence non traitée : " + requirement));
                return null;
        }
    }

    private MavenMetadata artifactMetadata(String ga) {
        if (artifactMetadataMemo.containsKey(ga)) {
            return artifactMetadataMemo.get(ga);
        }
        String[] parts = ga.split(":");
        for (Repository repo : config.repositories) {
            TransportResponse response = fetch(repo,
                    Layout.artifactMetadataPath(parts[0], parts[1]));
            if (response.isHit()) {
                MavenMetadataParser.Result parsed = MavenMetadataParser.parse(
                        new String(response.bytes, StandardCharsets.UTF_8));
                if (parsed.metadata != null) {
                    artifactMetadataMemo.put(ga, parsed.metadata);
                    return parsed.metadata;
                }
            }
        }
        artifactMetadataMemo.put(ga, null);
        return null;
    }

    // ------------------------------------------------------------------
    // Arêtes brutes d'un artefact
    // ------------------------------------------------------------------

    private RawEdges buildEdges(String ga, String v) {
        Metadata metadata = metadata(ga, v);
        RawEdges out = new RawEdges();
        if (metadata == null) {
            return out;
        }
        if (metadata.gmm != null) {
            GmmVariant api = selectVariant(metadata.gmm, "java-api");
            GmmVariant runtime = selectVariant(metadata.gmm, "java-runtime");
            if (api == null && runtime == null) {
                api = selectVariant(metadata.gmm, null);
            }
            for (GmmDependency d : dependenciesOf(api)) {
                addGmmEdge(out, ga, d, EdgeKind.API);
            }
            for (GmmDependency d : dependenciesOf(runtime)) {
                if (out.has(d.ga())) {
                    continue;
                }
                addGmmEdge(out, ga, d, EdgeKind.RUNTIME);
            }
        }
        if (metadata.pom != null) {
            for (PomModel.PomDependency d : metadata.pom.dependencies) {
                if (out.has(d.ga())) {
                    continue; // déjà couvert par le GMM
                }
                switch (d.scope) {
                    case TEST:
                    case IMPORT:
                        continue;
                    default:
                        break;
                }
                if (d.optional) {
                    continue;
                }
                String version = d.version != null ? d.version
                        : managedVersion(metadata.pom, d.ga());
                if (version == null) {
                    problems.add(Problem.error("deps.version.missing",
                            d.ga() + " (dépendance de " + ga + ":" + v
                                    + ") : version manquante"));
                    continue;
                }
                EdgeKind kind;
                switch (d.scope) {
                    case PROVIDED:
                        kind = EdgeKind.COMPILE;
                        break;
                    case RUNTIME:
                        kind = EdgeKind.RUNTIME;
                        break;
                    default:
                        kind = EdgeKind.API;
                        break;
                }
                out.add(d.ga(), version, false, kind, d.exclusions);
            }
        }
        return out;
    }

    private void addGmmEdge(RawEdges out, String from, GmmDependency d,
                            EdgeKind kind) {
        if (d.optional) {
            return; // une optionnelle n'entre jamais dans la fermeture
        }
        if (d.version == null) {
            problems.add(Problem.error("deps.version.missing",
                    d.ga() + " (dépendance GMM de " + from
                            + ") : version manquante"));
            return;
        }
        out.add(d.ga(), d.version, d.versionStrictly, kind,
                new HashSet<>(d.excludes));
    }

    private List<GmmDependency> dependenciesOf(GmmVariant variant) {
        return variant != null ? variant.dependencies : List.of();
    }

    private String managedVersion(PomModel pom, String ga) {
        PomModel.PomDependency managed = pom.managed(ga);
        return managed != null ? managed.version : null;
    }

    // ------------------------------------------------------------------
    // Métadonnées d'une version
    // ------------------------------------------------------------------

    private Metadata metadata(String ga, String v) {
        String key = ga + ":" + v;
        Metadata memo = metadataMemo.get(key);
        if (memo != null) {
            return memo;
        }
        String[] parts = ga.split(":");
        String g = parts[0];
        String a = parts[1];
        String concrete = snapshotConcrete(g, a, v);
        String literal = concrete != null ? concrete : v;
        for (Repository repo : config.repositories) {
            GmmModuleHolder gmm = null;
            PomModel pom = null;
            TransportResponse moduleResponse = fetch(repo,
                    Layout.modulePath(g, a, literal));
            if (moduleResponse.isHit()) {
                GmmParser.Result parsed = GmmParser.parse(
                        new String(moduleResponse.bytes,
                                StandardCharsets.UTF_8));
                if (parsed.module != null) {
                    GmmModule resolved = resolveGmmAvailableAt(parsed.module,
                            Layout.versionDir(g, a, literal));
                    gmm = new GmmModuleHolder(resolved);
                    for (GmmParser.Problem gp : parsed.problems) {
                        problems.add(Problem.warn("deps.gmm",
                                repo.id() + " " + key + " : " + gp));
                    }
                }
            }
            TransportResponse pomResponse = fetch(repo,
                    Layout.pomPath(g, a, literal));
            if (pomResponse.isHit()) {
                PomParser.Result parsed = new PomParser(
                        this::pomText).parse(new String(pomResponse.bytes,
                        StandardCharsets.UTF_8));
                if (parsed.model != null) {
                    pom = parsed.model;
                    for (PomParser.Problem pp : parsed.problems) {
                        problems.add(Problem.warn("deps.pom",
                                repo.id() + " " + key + " : " + pp));
                    }
                }
            }
            if (gmm != null || pom != null) {
                Metadata result = new Metadata(pom,
                        gmm != null ? gmm.module : null, repo.id(), concrete);
                metadataMemo.put(key, result);
                return result;
            }
        }
        problems.add(Problem.error("deps.artifact.missing",
                "introuvable dans tous les dépôts : " + key));
        return null;
    }

    private String pomText(String g, String a, String v) {
        String key = g + ":" + a + ":" + v;
        if (pomFetchMemo.contains(key)) {
            return null; // cycle : le parseur le rapportera
        }
        pomFetchMemo.add(key);
        try {
            for (Repository repo : config.repositories) {
                TransportResponse response = fetch(repo, Layout.pomPath(g, a, v));
                if (response.isHit()) {
                    return new String(response.bytes, StandardCharsets.UTF_8);
                }
            }
            return null;
        } finally {
            pomFetchMemo.remove(key);
        }
    }

    private String snapshotConcrete(String g, String a, String v) {
        if (Layout.snapshotBase(v) == null) {
            return null;
        }
        String memoKey = g + ":" + a + ":" + v;
        if (snapshotMemo.containsKey(memoKey)) {
            return snapshotMemo.get(memoKey);
        }
        for (Repository repo : config.repositories) {
            TransportResponse response = fetch(repo,
                    Layout.versionMetadataPath(g, a, v));
            if (!response.isHit()) {
                continue;
            }
            MavenMetadataParser.Result parsed = MavenMetadataParser.parse(
                    new String(response.bytes, StandardCharsets.UTF_8));
            MavenMetadata.Snapshot snapshot = parsed.metadata != null
                    ? parsed.metadata.snapshot : null;
            if (snapshot != null) {
                String concrete = Layout.concreteSnapshot(v, snapshot.timestamp,
                        snapshot.buildNumber);
                if (concrete != null) {
                    snapshotMemo.put(memoKey, concrete);
                    return concrete;
                }
            }
        }
        snapshotMemo.put(memoKey, null);
        return null;
    }

    // ------------------------------------------------------------------
    // Sélection de variante GMM
    // ------------------------------------------------------------------

    private GmmVariant selectVariant(jo.joide.deps.gmm.GmmModule gmm,
                                     String usage) {
        GmmVariant best = null;
        int bestScore = -1;
        for (GmmVariant v : gmm.variants) {
            int score = variantScore(v, usage);
            if (score > bestScore || (score == bestScore && best != null
                    && v.name.compareTo(best.name) < 0)) {
                best = v;
                bestScore = score;
            }
        }
        if (best == null || bestScore <= 0) {
            return null;
        }
        return best;
    }

    private int variantScore(GmmVariant v, String usage) {
        if (usage != null && !usage.equals(v.attr("org.gradle.usage"))) {
            return -1;
        }
        int score = 1;
        String platform = v.attr("org.jetbrains.kotlin.platform.type");
        if ("androidjvm".equals(platform)) {
            score += 3;
        } else if ("jvm".equals(platform)) {
            score += 2;
        } else if (platform == null) {
            score += 1;
        } else {
            return -1; // js, native : hors cible
        }
        String elements = v.attr("org.gradle.libraryelements");
        if ("jar".equals(elements) || "bundle".equals(elements)) {
            score += 2;
        } else if (elements == null) {
            score += 1;
        }
        String category = v.attr("org.gradle.category");
        if ("library".equals(category) || category == null) {
            score += 1;
        } else {
            return -1; // documentation, sources : hors cible
        }
        return score;
    }

    /**
     * Suit les renvois « available-at » des variantes : chaque renvoi
     * est relatif au répertoire de version ; la variante citée du
     * module référencé fournit fichiers et dépendances, les
     * attributs sont fusionnés (le renvoi gagne).
     */
    private GmmModule resolveGmmAvailableAt(GmmModule module,
                                            String versionDir) {
        List<GmmVariant> resolved = new ArrayList<>();
        for (GmmVariant v : module.variants) {
            GmmVariant current = v;
            for (int hop = 0; hop < MAX_AVAILABLE_AT_HOPS
                    && current.availableAtUrl != null; hop++) {
                GmmVariant target = followAvailableAt(current, versionDir);
                if (target == null) {
                    break;
                }
                current = mergeVariant(current, target);
            }
            resolved.add(current.availableAtUrl != null
                    ? stripAvailableAt(current) : current);
        }
        return new GmmModule(module.formatVersion, resolved);
    }

    private GmmVariant followAvailableAt(GmmVariant variant,
                                        String versionDir) {
        String url = versionDir + "/" + variant.availableAtUrl;
        for (Repository repo : config.repositories) {
            TransportResponse response = fetch(repo, url);
            if (response.isHit()) {
                GmmParser.Result parsed = GmmParser.parse(
                        new String(response.bytes, StandardCharsets.UTF_8));
                if (parsed.module != null) {
                    GmmVariant target = parsed.module.variant(variant.name);
                    if (target != null) {
                        return target;
                    }
                }
            }
        }
        problems.add(Problem.warn("deps.gmm.available-at",
                "renvoi introuvable : " + url));
        return null;
    }

    private GmmVariant stripAvailableAt(GmmVariant v) {
        return new GmmVariant(v.name, v.attributes, v.files, v.dependencies,
                null, Map.of());
    }

    private GmmVariant mergeVariant(GmmVariant from, GmmVariant to) {
        Map<String, String> attrs = new HashMap<>(to.attributes);
        attrs.putAll(from.availableAtAttributes);
        attrs.putAll(from.attributes);
        return new GmmVariant(to.name, attrs, to.files, to.dependencies,
                to.availableAtUrl, to.availableAtAttributes);
    }

    // ------------------------------------------------------------------
    // Fermeture avec exclusions par sous-arbre
    // ------------------------------------------------------------------

    private final LinkedHashMap<String, ClosureMember> closure =
            new LinkedHashMap<>();

    private void collectClosure(List<DependencyRequest> roots) {
        closure.clear();
        pathExclusionsOf.clear();
        List<String> queue = new ArrayList<>();
        Set<String> enqueued = new HashSet<>();
        for (DependencyRequest r : roots) {
            Choice c = choices.get(r.ga());
            if (c == null) {
                continue;
            }
            memberOf(r.ga(), c.version.raw());
            if (enqueued.add(r.ga())) {
                pathExclusionsOf.put(r.ga(), new HashSet<>());
                queue.add(r.ga());
            }
        }
        while (!queue.isEmpty()) {
            String ga = queue.remove(0);
            Choice choice = choices.get(ga);
            if (choice == null) {
                continue;
            }
            ClosureMember consumer = memberOf(ga, choice.version.raw());
            Set<String> excluded = pathExclusionsOf.getOrDefault(
                    ga, new HashSet<>());
            RawEdges edges = edgeMemo.computeIfAbsent(ga,
                    g -> buildEdges(g, choices.get(g).version.raw()));
            for (RawEdge edge : edges.list) {
                if (excluded.contains(edge.ga)) {
                    continue;
                }
                Choice targetChoice = choices.get(edge.ga);
                if (targetChoice == null) {
                    continue;
                }
                ClosureMember target = memberOf(edge.ga,
                        targetChoice.version.raw());
                consumer.rawEdges.add(new LockfileEdge(
                        placeholderCoordinate(edge.ga, targetChoice.version),
                        edge.kind));
                Set<String> nextExcluded = new HashSet<>(excluded);
                nextExcluded.addAll(edge.excludes);
                if (edge.kind == EdgeKind.COMPILE) {
                    continue; // présent, jamais étendu
                }
                Set<String> known = pathExclusionsOf.get(edge.ga);
                if (!enqueued.contains(edge.ga)
                        || (known != null
                        && nextExcluded.size() < known.size())) {
                    pathExclusionsOf.put(edge.ga, nextExcluded);
                    if (enqueued.add(edge.ga)) {
                        queue.add(edge.ga);
                    } else if (!queue.contains(edge.ga)) {
                        queue.add(edge.ga);
                    }
                }
            }
        }
    }

    private final Map<String, Set<String>> pathExclusionsOf = new HashMap<>();

    private Coordinate placeholderCoordinate(String ga, Version v) {
        String[] parts = ga.split(":");
        return Coordinate.of(parts[0], parts[1], v, null, "jar");
    }

    private ClosureMember memberOf(String ga, String v) {
        return closure.computeIfAbsent(ga, k -> new ClosureMember(k, v));
    }

    // ------------------------------------------------------------------
    // Fichiers : identité, téléchargement parallèle, extraction
    // ------------------------------------------------------------------

    private void fetchClosureFiles() {
        // Passe 1 : identité de chaque fichier depuis les métadonnées.
        List<PendingDownload> pending = new ArrayList<>();
        for (ClosureMember member : closure.values()) {
            identify(member, pending);
        }
        // Passe 2 : téléchargements en parallèle, plafonnés.
        if (!pending.isEmpty()) {
            ExecutorService pool = WorkerPools.newFixed(
                    "joide-deps-downloads", config.maxParallelDownloads);
            try {
                List<Future<DownloadOutcome>> futures = new ArrayList<>();
                for (PendingDownload p : pending) {
                    futures.add(pool.submit(() -> downloadVerified(p)));
                }
                for (int i = 0; i < futures.size(); i++) {
                    PendingDownload p = pending.get(i);
                    try {
                        DownloadOutcome outcome = futures.get(i).get();
                        if (outcome != null) {
                            applyOutcome(p.member, outcome);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        problems.add(Problem.error("deps.interrupted",
                                "téléchargement interrompu"));
                        return;
                    } catch (ExecutionException e) {
                        problems.add(Problem.error("deps.download",
                                "échec : " + e.getCause()));
                    }
                }
            } finally {
                pool.shutdown();
            }
        }
        // Passe 3 : extraction des AAR (local).
        for (ClosureMember member : closure.values()) {
            if (member.aar && member.sha256 != null) {
                member.aarContents = extractAar(
                        config.cache.fileOf(member.sha256), member.sha256);
            }
        }
    }

    private void identify(ClosureMember member, List<PendingDownload> pending) {
        Metadata metadata = metadata(member.ga, member.version);
        String[] parts = member.ga.split(":");
        String literal = metadata != null && metadata.concrete != null
                ? metadata.concrete
                : snapshotConcrete(parts[0], parts[1], member.version);
        if (literal == null) {
            literal = member.version;
        }
        GmmVariant api = metadata != null && metadata.gmm != null
                ? selectVariant(metadata.gmm, "java-api") : null;
        if (api == null && metadata != null && metadata.gmm != null) {
            api = selectVariant(metadata.gmm, null);
        }
        String ext;
        String fileName = null;
        String embeddedSha256 = null;
        String embeddedSha1 = null;
        long size = -1;
        if (api != null && !api.files.isEmpty()) {
            GmmFile file = api.files.get(0);
            fileName = file.name;
            ext = file.extension().isEmpty() ? "jar" : file.extension();
            embeddedSha256 = file.sha256;
            embeddedSha1 = file.sha1;
            size = file.size;
        } else if (metadata != null && metadata.pom != null) {
            String packaging = metadata.pom.packaging;
            if ("pom".equals(packaging)) {
                problems.add(Problem.error("deps.packaging.pom",
                        member.ga + ":" + member.version
                                + " : une nomenclature ne peut pas être une"
                                + " dépendance résolue"));
                return;
            }
            ext = "aar".equals(packaging) ? "aar" : "jar";
            fileName = Layout.fileName(parts[0], parts[1], literal, null, ext);
        } else {
            return;
        }
        member.aar = "aar".equals(ext);
        member.coordinate = Coordinate.of(parts[0], parts[1],
                Version.parse(literal), null, ext);
        member.size = size;
        // Déjà au cache par empreinte GMM ?
        if (embeddedSha256 != null && config.cache.has(embeddedSha256)) {
            member.sha256 = embeddedSha256;
            member.repositoryId = metadata.repositoryId;
            if (member.size < 0) {
                member.size = fileSizeOf(embeddedSha256);
            }
            return;
        }
        String relPath = Layout.artifactPath(parts[0], parts[1], literal,
                null, ext);
        pending.add(new PendingDownload(member, relPath, embeddedSha256,
                embeddedSha1));
    }

    private void applyOutcome(ClosureMember member, DownloadOutcome outcome) {
        member.sha256 = outcome.sha256;
        if (member.size < 0) {
            member.size = outcome.bytes;
        }
        member.repositoryId = outcome.repositoryId;
    }

    private long fileSizeOf(String sha256) {
        try {
            byte[] content = config.cache.read(sha256);
            return content != null ? content.length : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    private DownloadOutcome downloadVerified(PendingDownload p) {
        for (Repository repo : config.repositories) {
            TransportResponse response = fetch(repo, p.relPath);
            if (!response.isHit()) {
                if (response.kind == TransportResponse.Kind.ERROR) {
                    problems.add(Problem.error("deps.transport",
                            repo.id() + " : " + p.relPath + " : "
                                    + response.message));
                }
                continue;
            }
            byte[] body = response.bytes;
            String computed = Checksums.sha256Hex(body);
            String published = null;
            String algorithm = null;
            if (p.embeddedSha256 != null) {
                published = p.embeddedSha256;
                algorithm = "sha256";
            } else {
                String sidecar = sidecarChecksum(repo, p.relPath + ".sha256",
                        64);
                if (sidecar != null) {
                    published = sidecar;
                    algorithm = "sha256";
                } else if (p.embeddedSha1 != null) {
                    published = p.embeddedSha1;
                    algorithm = "sha1";
                } else {
                    sidecar = sidecarChecksum(repo, p.relPath + ".sha1", 40);
                    if (sidecar != null) {
                        published = sidecar;
                        algorithm = "sha1";
                    }
                }
            }
            if (published != null) {
                String actual = "sha1".equals(algorithm)
                        ? Checksums.sha1Hex(body) : computed;
                if (!Checksums.matches(published, actual)) {
                    problems.add(Problem.error("deps.checksum",
                            repo.id() + " : " + p.relPath + " : " + algorithm
                                    + " publié " + shorten(published)
                                    + " ≠ calculé " + shorten(actual)));
                    continue; // ce dépôt n'est pas fiable pour ce fichier
                }
            }
            try {
                config.cache.write(body);
            } catch (IOException e) {
                problems.add(Problem.error("deps.cache.write",
                        "écriture impossible : " + e.getMessage()));
                return null;
            }
            downloadCount++;
            downloadBytes += body.length;
            return new DownloadOutcome(computed, body.length, repo.id());
        }
        problems.add(Problem.error("deps.artifact.unavailable",
                "introuvable ou non vérifiable : " + p.relPath));
        return null;
    }

    private String sidecarChecksum(Repository repo, String relPath, int len) {
        TransportResponse response = fetch(repo, relPath);
        if (!response.isHit()) {
            return null;
        }
        String text = new String(response.bytes, StandardCharsets.UTF_8).trim();
        for (String token : text.split("\\s+")) {
            String t = token.trim().toLowerCase(Locale.ROOT);
            if (t.length() == len && t.matches("[0-9a-f]{" + len + "}")) {
                return t;
            }
        }
        return null;
    }

    private static String shorten(String hex) {
        return hex != null && hex.length() > 12
                ? hex.substring(0, 12) + "…" : hex;
    }

    private AarContents extractAar(Path aarFile, String sha256) {
        try {
            return new AarExtractor().extract(aarFile, sha256,
                    config.cache.aarDir(sha256));
        } catch (AarExtractor.Rejected e) {
            problems.add(Problem.error("deps.aar.rejected",
                    "extraction refusée : " + e.getMessage()));
        } catch (IOException e) {
            problems.add(Problem.error("deps.aar.io",
                    "extraction impossible : " + e.getMessage()));
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Artefacts résultat et lockfile
    // ------------------------------------------------------------------

    private final List<ResolvedArtifact> artifacts = new ArrayList<>();

    private void buildArtifacts() {
        artifacts.clear();
        for (ClosureMember member : closure.values()) {
            if (member.coordinate == null || member.sha256 == null) {
                continue;
            }
            if (member.aar && member.aarContents == null) {
                problems.add(Problem.error("deps.aar.missing",
                        member.coordinate + " : extraction impossible"));
                continue;
            }
            // Retargeter les arêtes du consommateur vers les
            // coordonnées finales de chaque cible.
            List<LockfileEdge> recorded = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (LockfileEdge raw : member.rawEdges) {
                String targetGa = raw.target.groupId() + ":"
                        + raw.target.artifactId();
                ClosureMember target = closure.get(targetGa);
                if (target == null || target.coordinate == null) {
                    continue;
                }
                if (!seen.add(targetGa + ":" + raw.kind)) {
                    continue;
                }
                recorded.add(new LockfileEdge(target.coordinate, raw.kind));
            }
            recorded.sort(LockfileEdge.ORDER);
            member.recordedEdges = recorded;
            artifacts.add(new ResolvedArtifact(member.coordinate,
                    member.sha256, member.size, member.repositoryId,
                    config.cache.fileOf(member.sha256), member.aar,
                    member.aarContents, recorded));
        }
        artifacts.sort(Comparator.comparing(a -> a.coordinate));
    }

    private void maybeWriteLockfile() {
        List<LockfileEntry> entries = new ArrayList<>();
        for (ResolvedArtifact artifact : artifacts) {
            entries.add(new LockfileEntry(artifact.coordinate,
                    artifact.sha256, artifact.size, artifact.repositoryId,
                    artifact.edges));
        }
        Lockfile fresh;
        try {
            fresh = Lockfile.of(entries);
        } catch (IllegalArgumentException e) {
            problems.add(Problem.error("deps.lockfile.build",
                    "entrées en double : " + e.getMessage()));
            return;
        }
        String text = fresh.serialize();
        Path path = config.lockfilePath;
        if (path == null) {
            return;
        }
        try {
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }
            if (Files.isRegularFile(path)) {
                String existing = Files.readString(path,
                        StandardCharsets.UTF_8);
                if (existing.equals(text)) {
                    return; // inchangé : ne pas toucher au fichier
                }
            }
            AtomicFiles.writeString(path, text);
            lockfileRewritten = true;
        } catch (IOException e) {
            problems.add(Problem.error("deps.lockfile.write",
                    "écriture impossible : " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private TransportResponse fetch(Repository repo, String relPath) {
        if (repo.remote()) {
            requests++;
        }
        return repo.fetch(relPath, config.token);
    }

    private boolean success() {
        for (Problem p : problems) {
            if (p.isError()) {
                return false;
            }
        }
        return true;
    }

    private ResolutionResult failure() {
        return new ResolutionResult(List.of(), List.copyOf(problems), false,
                false, stats());
    }

    // ------------------------------------------------------------------
    // Types internes
    // ------------------------------------------------------------------

    private static final class GmmModuleHolder {
        final jo.joide.deps.gmm.GmmModule module;

        GmmModuleHolder(jo.joide.deps.gmm.GmmModule module) {
            this.module = module;
        }
    }

    private static final class Metadata {
        final PomModel pom;
        final jo.joide.deps.gmm.GmmModule gmm;
        final String repositoryId;
        final String concrete;

        Metadata(PomModel pom, jo.joide.deps.gmm.GmmModule gmm,
                 String repositoryId, String concrete) {
            this.pom = pom;
            this.gmm = gmm;
            this.repositoryId = repositoryId;
            this.concrete = concrete;
        }
    }

    private static final class Choice {
        final Version version;
        final boolean strict;

        Choice(Version version, boolean strict) {
            this.version = version;
            this.strict = strict;
        }
    }

    private static final class RawEdge {
        final String ga;
        final String versionText;
        final boolean strictly;
        final EdgeKind kind;
        final Set<String> excludes;

        RawEdge(String ga, String versionText, boolean strictly, EdgeKind kind,
                Set<String> excludes) {
            this.ga = ga;
            this.versionText = versionText;
            this.strictly = strictly;
            this.kind = kind;
            this.excludes = Set.copyOf(excludes);
        }

        VersionRequirement requirement() {
            if (versionText == null) {
                return VersionRequirement.parse("latest.release");
            }
            VersionRequirement parsed = VersionRequirement.parse(versionText);
            if (strictly && parsed.kind() == VersionRequirement.Kind.EXACT) {
                return VersionRequirement.strict(parsed.exact());
            }
            return parsed;
        }
    }

    private static final class RawEdges {
        final List<RawEdge> list = new ArrayList<>();

        void add(String ga, String versionText, boolean strictly, EdgeKind kind,
                 Set<String> excludes) {
            if (ga == null) {
                return;
            }
            for (RawEdge existing : list) {
                if (existing.ga.equals(ga)) {
                    return; // un seul bord par g:a (le premier vu gagne)
                }
            }
            list.add(new RawEdge(ga, versionText, strictly, kind, excludes));
        }

        boolean has(String ga) {
            for (RawEdge e : list) {
                if (e.ga.equals(ga)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class PendingDownload {
        final ClosureMember member;
        final String relPath;
        final String embeddedSha256;
        final String embeddedSha1;

        PendingDownload(ClosureMember member, String relPath,
                        String embeddedSha256, String embeddedSha1) {
            this.member = member;
            this.relPath = relPath;
            this.embeddedSha256 = embeddedSha256;
            this.embeddedSha1 = embeddedSha1;
        }
    }

    private static final class DownloadOutcome {
        final String sha256;
        final long bytes;
        final String repositoryId;

        DownloadOutcome(String sha256, long bytes, String repositoryId) {
            this.sha256 = sha256;
            this.bytes = bytes;
            this.repositoryId = repositoryId;
        }
    }

    private static final class ClosureMember {
        final String ga;
        final String version;
        final List<LockfileEdge> rawEdges = new ArrayList<>();
        Coordinate coordinate;
        String sha256;
        long size = -1;
        String repositoryId;
        boolean aar;
        AarContents aarContents;
        List<LockfileEdge> recordedEdges = List.of();

        ClosureMember(String ga, String version) {
            this.ga = ga;
            this.version = version;
        }
    }
}
