package jo.joide.store;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Processus fille des tests de pannes du store : écrit sans fin des
 * transactions connues, en imprimant une ligne par transaction
 * validée, jusqu'à être tuée par signal.
 *
 * <p>Le parent connaît la formule exacte de chaque transaction ; après
 * le meurtre du processus, il rouvre le store et vérifie que l'état
 * est exactement un préfixe des transactions validées, sans trou ni
 * écriture à moitié appliquée.</p>
 */
public final class StoreCrashChild {

    /** Nombre d'entrées par remplacement de snapshot. */
    public static final int SNAPSHOT_ENTRIES = 64;

    private StoreCrashChild() {
    }

    /**
     * @param args mode (« fingerprints » ou « snapshot ») puis fichier
     *             de base de données
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: StoreCrashChild <fingerprints|snapshot> <dbFile>");
            System.exit(2);
        }
        String mode = args[0];
        Path dbFile = Path.of(args[1]);
        try {
            switch (mode) {
                case "fingerprints":
                    runFingerprints(dbFile);
                    break;
                case "snapshot":
                    runSnapshot(dbFile);
                    break;
                default:
                    System.err.println("unknown mode: " + mode);
                    System.exit(2);
            }
        } catch (RuntimeException e) {
            System.err.println("child failed: " + e);
            System.exit(1);
        }
    }

    /**
     * Clé de la i-ème transaction d'empreintes.
     *
     * @param i numéro de transaction
     * @return la clé stable correspondante
     */
    public static String key(int i) {
        return String.format(Locale.ROOT, "task-%06d", i);
    }

    /**
     * Valeur de la i-ème transaction d'empreintes.
     *
     * @param i numéro de transaction
     * @return la valeur correspondante
     */
    public static String value(int i) {
        return String.format(Locale.ROOT, "val-%06d", i);
    }

    /**
     * État de fichier attendu au chemin j pour une génération i du
     * snapshot.
     *
     * @param generation numéro de remplacement du snapshot
     * @param index      index d'entrée dans le snapshot
     * @return l'état attendu
     */
    public static FileStamp snapshotEntry(int generation, int index) {
        return new FileStamp(
                String.format(Locale.ROOT, "src/main/f%04d.java", index),
                index + generation,
                1_000_000L * generation + index,
                String.format(Locale.ROOT, "inode-%04d", index),
                String.format(Locale.ROOT, "hash-%06d-%06d", generation, index));
    }

    /**
     * État complet attendu du snapshot pour une génération donnée.
     *
     * @param generation numéro de remplacement du snapshot
     * @return la liste ordonnée des états de fichiers
     */
    public static List<FileStamp> snapshotContent(int generation) {
        List<FileStamp> entries = new ArrayList<>(SNAPSHOT_ENTRIES);
        for (int j = 0; j < SNAPSHOT_ENTRIES; j++) {
            entries.add(snapshotEntry(generation, j));
        }
        return entries;
    }

    private static void runFingerprints(Path dbFile) {
        PrintWriter out = new PrintWriter(System.out, false);
        try (BuildStore store = SqliteStores.open(dbFile)) {
            for (int i = 0; ; i++) {
                int transaction = i;
                store.inTransaction(s -> {
                    s.putFingerprint(key(transaction), value(transaction));
                    s.putDuration("bench", key(transaction), transaction);
                    return null;
                });
                out.println("tx " + transaction);
                out.flush();
            }
        }
    }

    private static void runSnapshot(Path dbFile) {
        PrintWriter out = new PrintWriter(System.out, false);
        try (BuildStore store = SqliteStores.open(dbFile)) {
            for (int i = 0; ; i++) {
                int generation = i;
                store.inTransaction(s -> {
                    s.replaceSnapshot("PROJECT", snapshotContent(generation));
                    return null;
                });
                out.println("snap " + generation);
                out.flush();
            }
        }
    }
}
