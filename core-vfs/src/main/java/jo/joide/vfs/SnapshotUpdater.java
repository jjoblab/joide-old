package jo.joide.vfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import jo.joide.platform.CancelToken;
import jo.joide.platform.Problem;
import jo.joide.platform.Severity;
import jo.joide.store.BuildStore;
import jo.joide.store.FileStamp;

/**
 * Mise à jour du snapshot d'une racine : parcours parallèle de statuts,
 * comparaison aux états connus, empreintes recalculées seulement pour
 * ce qui a changé, persistance transactionnelle.
 *
 * <p>L'empreinte de contenu est paresseuse : un fichier dont les
 * signaux (taille, date, clé) n'ont pas bougé garde son empreinte
 * connue ; un fichier nouveau ou changé se retrouve sans empreinte
 * jusqu'à la demande explicite {@link #ensureHashed}.</p>
 */
public final class SnapshotUpdater {

    private final BuildStore store;
    private final ExecutorService pool;

    /**
     * @param store store de persistance du snapshot
     * @param pool  pool de fils injecté pour les parcours
     */
    public SnapshotUpdater(BuildStore store, ExecutorService pool) {
        this.store = Objects.requireNonNull(store, "store");
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    /**
     * Charge le snapshot persisté d'une racine.
     *
     * @param rootId identifiant de la racine
     * @return l'instantané correspondant, vide si aucun
     */
    public VfsSnapshot load(String rootId) {
        List<FileStamp> stamps = store.loadSnapshot(rootId);
        return stamps.isEmpty() ? VfsSnapshot.empty() : VfsSnapshot.of(stamps);
    }

    /**
     * Parcourt la racine et produit un nouvel instantané, avec les
     * fichiers sales depuis l'instantané courant.
     *
     * @param rootId    identifiant de la racine
     * @param current   instantané courant (vide pour un premier parcours)
     * @param rootDir   répertoire réel de la racine
     * @param token     jeton d'annulation coopérative
     * @return le nouvel instantané et les fichiers sales
     * @throws IOException si le parcours échoue
     */
    public UpdateResult refresh(String rootId, VfsSnapshot current,
                                Path rootDir, CancelToken token)
            throws IOException {
        Objects.requireNonNull(current, "current");
        List<FileStamp> scanned = TreeScanner.scan(rootDir, pool, token);
        return integrateScan(rootId, current, scanned);
    }

    /**
     * Intègre un parcours déjà fait : mêmes règles que
     * {@link #refresh}, sans re-parcourir.
     *
     * @param rootId  identifiant de la racine
     * @param current instantané courant
     * @param scanned états parcourus, triés par chemin
     * @return le nouvel instantané et les fichiers sales
     */
    public UpdateResult integrateScan(String rootId, VfsSnapshot current,
                                      List<FileStamp> scanned) {
        TreeMap<String, FileStamp> next = new TreeMap<>();
        for (FileStamp fresh : scanned) {
            FileStamp known = current.entry(fresh.path());
            next.put(fresh.path(),
                    known != null && !VfsSnapshot.signalsChanged(known, fresh)
                            ? known
                            : fresh);
        }
        VfsSnapshot updated = VfsSnapshot.of(new ArrayList<>(next.values()));
        DirtySet dirty = updated.diffAgainst(current);
        persistDelta(rootId, updated, dirty);
        return new UpdateResult(updated, dirty);
    }

    /**
     * Calcule et enregistre l'empreinte de contenu des fichiers qui en
     * sont dépourvus dans l'instantané.
     *
     * @param rootId  identifiant de la racine
     * @param current instantané courant
     * @param rootDir répertoire réel de la racine
     * @param paths   chemins relatifs dont l'empreinte est demandée
     * @param hasher  hacheur stable injecté
     * @return le nouvel instantané et les problèmes rencontrés
     */
    public HashResult ensureHashed(String rootId, VfsSnapshot current,
                                   Path rootDir, List<String> paths,
                                   StableHasher hasher) {
        Objects.requireNonNull(hasher, "hasher");
        Map<String, String> computed = new LinkedHashMap<>();
        List<Problem> problems = new ArrayList<>();
        for (String path : paths) {
            FileStamp stamp = current.entry(path);
            if (stamp == null || stamp.contentHash() != null) {
                continue;
            }
            try {
                computed.put(path,
                        hasher.hashStable(rootDir.resolve(path)).toHex());
            } catch (IOException e) {
                problems.add(Problem.warn("hash-unreadable",
                        "cannot hash " + path + ": " + e.getMessage()));
            }
        }
        if (computed.isEmpty()) {
            return new HashResult(current, problems);
        }
        VfsSnapshot updated = current.withContentHashes(computed);
        store.inTransaction(s -> {
            List<FileStamp> upserts = new ArrayList<>();
            for (Map.Entry<String, String> entry : computed.entrySet()) {
                upserts.add(updated.entry(entry.getKey()));
            }
            s.updateSnapshot(rootId, upserts, List.of());
            return null;
        });
        return new HashResult(updated, problems);
    }

    private void persistDelta(String rootId, VfsSnapshot updated, DirtySet dirty) {
        store.inTransaction(s -> {
            if (dirty.isEmpty()) {
                return null;
            }
            List<FileStamp> upserts = new ArrayList<>();
            for (String path : dirty.added()) {
                upserts.add(updated.entry(path));
            }
            for (String path : dirty.modified()) {
                upserts.add(updated.entry(path));
            }
            List<String> removed = new ArrayList<>(dirty.removed());
            s.updateSnapshot(rootId, upserts, removed);
            return null;
        });
    }

    /**
     * Résultat d'une mise à jour : nouvel instantané et fichiers sales.
     */
    public static final class UpdateResult {
        private final VfsSnapshot snapshot;
        private final DirtySet dirty;

        UpdateResult(VfsSnapshot snapshot, DirtySet dirty) {
            this.snapshot = snapshot;
            this.dirty = dirty;
        }

        /** @return le nouvel instantané */
        public VfsSnapshot snapshot() {
            return snapshot;
        }

        /** @return les fichiers sales depuis l'instantané précédent */
        public DirtySet dirty() {
            return dirty;
        }
    }

    /**
     * Résultat d'un calcul d'empreintes : instantané enrichi et
     * problèmes rencontrés.
     */
    public static final class HashResult {
        private final VfsSnapshot snapshot;
        private final List<Problem> problems;

        HashResult(VfsSnapshot snapshot, List<Problem> problems) {
            this.snapshot = snapshot;
            this.problems = problems;
        }

        /** @return l'instantané enrichi des empreintes */
        public VfsSnapshot snapshot() {
            return snapshot;
        }

        /** @return les problèmes de lecture rencontrés */
        public List<Problem> problems() {
            return problems;
        }

        /** @return vrai si aucune lecture n'a échoué */
        public boolean isClean() {
            return problems.stream().noneMatch(p -> p.severity() == Severity.WARN
                    || p.severity() == Severity.ERROR);
        }
    }
}
