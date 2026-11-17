package jo.joide.build.android.dex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Répartition en buckets multidex : déterminisme, respect du plafond,
 * regroupement des petits dex et isolement des gros.
 */
class DexBucketsTest {

    private static DexBuckets.Item item(String label, int methods) {
        return new DexBuckets.Item(label, label + ".dex",
                DexInfo.ofCounts(methods, methods / 2, methods * 2, methods,
                methods * 10L));
    }

    @Test
    void smallScopesGroupIntoOneBucket() {
        DexBuckets buckets = DexBuckets.standard();
        List<DexBuckets.Bucket> plan = buckets.plan(List.of(
                item("app", 1000), item("lib-a", 500), item("lib-b", 700)));
        assertEquals(1, plan.size());
        assertEquals(2200, plan.get(0).methodTotal);
        assertEquals("classes.dex", plan.get(0).dexName());
        assertTrue(DexBuckets.withinLimits(plan));
    }

    @Test
    void oversizedScopeOpensItsOwnBucket() {
        DexBuckets buckets = new DexBuckets(1000);
        List<DexBuckets.Bucket> plan = buckets.plan(List.of(
                item("app", 900), item("huge", 4000), item("small", 100)));
        // le petit dex complète le bucket principal à hauteur exacte,
        // le gros part seul dans un bucket suivant
        assertEquals(2, plan.size());
        assertEquals(List.of("app", "small"), names(plan.get(0)));
        assertEquals(List.of("huge"), names(plan.get(1)));
        assertEquals("classes.dex", plan.get(0).dexName());
        assertEquals("classes2.dex", plan.get(1).dexName());
        assertTrue(DexBuckets.withinLimits(plan));
    }

    @Test
    void bucketOverRealLimitIsReported() {
        List<DexBuckets.Bucket> plan = DexBuckets.standard().plan(List.of(
                item("monster", 70_000)));
        assertEquals(1, plan.size());
        assertFalse(DexBuckets.withinLimits(plan),
                "a dex above 65 536 methods exceeds the format limit");
    }

    @Test
    void planningIsDeterministic() {
        DexBuckets buckets = DexBuckets.standard();
        List<DexBuckets.Item> items = List.of(item("a", 40000),
                item("b", 40000), item("c", 10));
        assertEquals(buckets.plan(items).toString(),
                buckets.plan(items).toString());
    }

    @Test
    void emptyPlanStillYieldsMainBucket() {
        List<DexBuckets.Bucket> plan = DexBuckets.standard()
                .plan(List.of());
        assertEquals(1, plan.size());
        assertTrue(plan.get(0).items.isEmpty());
    }

    @Test
    void headerParseReadsCounts() throws IOException {
        byte[] dex = new byte[112];
        dex[0] = 'd';
        dex[1] = 'e';
        dex[2] = 'x';
        dex[3] = '\n';
        putInt(dex, 56, 42);
        putInt(dex, 64, 7);
        putInt(dex, 80, 11);
        putInt(dex, 88, 13);
        DexInfo info = DexInfo.parse(dex);
        assertEquals(42, info.stringIds);
        assertEquals(7, info.typeIds);
        assertEquals(11, info.fieldIds);
        assertEquals(13, info.methodIds);
        assertEquals(112, info.size);
    }

    @Test
    void headerParseRejectsBadMagic() {
        byte[] dex = new byte[112];
        dex[0] = 'x';
        assertThrows(IOException.class, () -> DexInfo.parse(dex));
    }

    private static List<String> names(DexBuckets.Bucket bucket) {
        return bucket.items.stream().map(i -> i.label).toList();
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >> 8);
        bytes[offset + 2] = (byte) (value >> 16);
        bytes[offset + 3] = (byte) (value >> 24);
    }
}
