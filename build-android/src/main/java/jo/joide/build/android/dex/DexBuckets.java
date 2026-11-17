package jo.joide.build.android.dex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Répartition de dex en buckets multidex : chaque fichier dex peut
 * référencer au plus 65 536 méthodes ; le bucket principal porte le
 * code de l'application, les buckets suivants portent les
 * dépendances. La répartition est déterministe : mêmes entrées dans le
 * même ordre, mêmes buckets.
 */
public final class DexBuckets {

    /** Plafond de méthodes référencées par fichier dex. */
    public static final int MAX_METHOD_IDS = 65_536;

    /** Marge sur le plafond (méthodes de liaisons générées). */
    public static final int HEADROOM = 512;

    /** Un dex placé dans un bucket. */
    public static final class Item {
        /** Libellé d'origine (module ou artefact). */
        public final String label;
        /** Fichier dex produit par le scope. */
        public final String dexFile;
        /** Comptages du dex. */
        public final DexInfo info;

        /** @param label libellé d'origine
         *  @param dexFile fichier dex
         *  @param info comptages du dex */
        public Item(String label, String dexFile, DexInfo info) {
            this.label = label;
            this.dexFile = dexFile;
            this.info = info;
        }

        @Override
        public String toString() {
            return label + "(" + info.methodIds + " méthodes)";
        }
    }

    /** Un bucket : un fichier classes.dex final. */
    public static final class Bucket {
        /** Index du bucket (0 = principal). */
        public final int index;
        /** Éléments affectés au bucket. */
        public final List<Item> items = new ArrayList<>();
        /** Somme des méthodes référencées. */
        public int methodTotal;

        Bucket(int index) {
            this.index = index;
        }

        /** @return le nom de fichier dex du bucket */
        public String dexName() {
            return index == 0 ? "classes.dex"
                    : "classes" + (index + 1) + ".dex";
        }

        @Override
        public String toString() {
            return "Bucket[" + index + " " + items + " total="
                    + methodTotal + "]";
        }
    }

    private final int capacity;

    /** @param capacity plafond de méthodes par bucket */
    public DexBuckets(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /** @return un répartiteur au plafond standard moins la marge. */
    public static DexBuckets standard() {
        return new DexBuckets(MAX_METHOD_IDS - HEADROOM);
    }

    /**
     * Répartit les dex de scopes en buckets. Le premier élément va
     * toujours au bucket principal (code de l'application) ; chaque
     * élément suivant est placé dans le premier bucket qui le contient
     * encore, sinon un nouveau bucket est ouvert — les petits dex de
     * dépendances se regroupent, les gros partent seuls.
     *
     * @param items dex de scopes, ordre stable
     * @return les buckets remplis (au moins un)
     */
    public List<Bucket> plan(List<Item> items) {
        Objects.requireNonNull(items, "items");
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(new Bucket(0));
        for (Item item : items) {
            Bucket target = null;
            for (Bucket bucket : buckets) {
                if (bucket.methodTotal + item.info.methodIds <= capacity) {
                    target = bucket;
                    break;
                }
            }
            if (target == null) {
                Bucket last = buckets.get(buckets.size() - 1);
                if (last.items.isEmpty()) {
                    // bucket encore vide : l'item y entre même s'il
                    // dépasse seul la capacité — le dépassement du
                    // format est signalé par la vérification de
                    // plafond, jamais caché dans un bucket fantôme
                    target = last;
                } else {
                    target = new Bucket(buckets.size());
                    buckets.add(target);
                }
            }
            target.items.add(item);
            target.methodTotal += item.info.methodIds;
        }
        return buckets;
    }

    /**
     * @param buckets buckets planifiés
     * @return vrai si aucun bucket ne dépasse le plafond réel
     */
    public static boolean withinLimits(List<Bucket> buckets) {
        for (Bucket bucket : buckets) {
            if (bucket.methodTotal > MAX_METHOD_IDS) {
                return false;
            }
        }
        return true;
    }
}
