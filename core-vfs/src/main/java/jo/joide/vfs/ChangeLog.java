package jo.joide.vfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import jo.joide.store.FileStamp;

/**
 * Journal de changements fusionné : les événements de l'éditeur, du
 * surveilleur et le rattrapage y convergent en un unique ensemble de
 * fichiers sales.
 *
 * <p>Trois règles. Un : les événements de l'éditeur sont prioritaires
 * et gratuits — c'est la source qui sait le mieux. Deux : un même
 * chemin n'apparaît qu'une fois, avec son changement le plus récent.
 * Trois : en cas de débordement (file bornée pleine ou surveilleur
 * saturé), le journal renonce à fusionner et impose un re-scan au
 * prochain vidage — sur-invalider plutôt que sous-invalider.</p>
 */
public final class ChangeLog {

    private final int capacity;
    private final ConcurrentLinkedQueue<PendingChange> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong queued = new AtomicLong();
    private final AtomicBoolean overflow = new AtomicBoolean(false);
    private final AtomicLong drainedCount = new AtomicLong();

    /**
     * @param capacity nombre maximal d'événements en attente avant
     *                 débordement
     */
    public ChangeLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * Enregistre un changement vu par l'éditeur ou le surveilleur.
     *
     * @param path   chemin relatif du fichier
     * @param change nature du changement
     */
    public void record(String path, FileChange change) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(change, "change");
        if (overflow.get()) {
            return;
        }
        queue.add(new PendingChange(path, change));
        if (queued.incrementAndGet() > capacity) {
            overflow.set(true);
        }
    }

    /**
     * Signale un débordement du surveilleur ou du journal : le
     * prochain vidage exigera un re-scan.
     */
    public void markOverflow() {
        overflow.set(true);
    }

    /**
     * @return vrai si un re-scan est exigé au prochain vidage
     */
    public boolean needsRescan() {
        return overflow.get();
    }

    /**
     * Vide le journal en ensemble de fichiers sales.
     *
     * <p>Sans débordement, la fusion consulte l'instantané courant en
     * mémoire uniquement : aucun accès disque, coût proportionnel au
     * nombre d'événements. Avec débordement, l'appelant doit fournir
     * un re-scan complet — le journal ne peut plus rien garantir sur
     * les événements perdus.</p>
     *
     * @param current instantané courant de la racine
     * @return les fichiers sales, ou une indication de re-scan exigé
     */
    public DrainResult drain(VfsSnapshot current) {
        Objects.requireNonNull(current, "current");
        boolean needsRescan = overflow.getAndSet(false);
        List<PendingChange> changes = new ArrayList<>();
        PendingChange change;
        while ((change = queue.poll()) != null) {
            changes.add(change);
        }
        drainedCount.addAndGet(changes.size());
        if (needsRescan) {
            return DrainResult.rescanRequired(changes.size());
        }
        if (changes.isEmpty()) {
            return DrainResult.of(DirtySet.empty(), 0);
        }
        Map<String, FileChange> latest = new HashMap<>();
        for (PendingChange pending : changes) {
            latest.put(pending.path, pending.change);
        }
        Set<String> added = new LinkedHashSet<>();
        Set<String> modified = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        for (Map.Entry<String, FileChange> entry : latest.entrySet()) {
            String path = entry.getKey();
            FileStamp known = current.entry(path);
            switch (entry.getValue()) {
                case CREATED:
                    (known == null ? added : modified).add(path);
                    break;
                case MODIFIED:
                    (known == null ? added : modified).add(path);
                    break;
                case DELETED:
                    if (known != null) {
                        removed.add(path);
                    } else {
                        // créé puis supprimé dans la même fenêtre : rien
                    }
                    break;
                default:
                    throw new IllegalStateException("unknown change kind");
            }
        }
        return DrainResult.of(DirtySet.of(added, modified, removed),
                changes.size());
    }

    /**
     * @return le nombre total d'événements vidés depuis la création
     */
    public long totalDrained() {
        return drainedCount.get();
    }

    /** Un changement en attente, avant fusion. */
    private static final class PendingChange {
        private final String path;
        private final FileChange change;

        PendingChange(String path, FileChange change) {
            this.path = path;
            this.change = change;
        }
    }

    /**
     * Résultat d'un vidage : soit un ensemble de fichiers sales, soit
     * l'exigence d'un re-scan complet.
     */
    public static final class DrainResult {
        private final DirtySet dirty;
        private final boolean rescanRequired;
        private final int drainedEvents;

        private DrainResult(DirtySet dirty, boolean rescanRequired, int drainedEvents) {
            this.dirty = dirty;
            this.rescanRequired = rescanRequired;
            this.drainedEvents = drainedEvents;
        }

        static DrainResult of(DirtySet dirty, int drainedEvents) {
            return new DrainResult(dirty, false, drainedEvents);
        }

        static DrainResult rescanRequired(int drainedEvents) {
            return new DrainResult(DirtySet.empty(), true, drainedEvents);
        }

        /** @return les fichiers sales, vide si un re-scan est exigé */
        public DirtySet dirty() {
            return dirty;
        }

        /** @return vrai si un re-scan complet est exigé */
        public boolean rescanRequired() {
            return rescanRequired;
        }

        /** @return le nombre d'événements vidés pendant ce vidage */
        public int drainedEvents() {
            return drainedEvents;
        }
    }
}
