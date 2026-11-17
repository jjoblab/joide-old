package jo.joide.build.jvm.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * Scan d'en-têtes, codec d'état incrémental et analyse d'impact :
 * chaque règle du §6.4 est testée isolément, avec un point fixe à
 * deux itérations.
 */
class GraphTest {

    private static SourceHeaderScanner.Header scanJava(String text) {
        return SourceHeaderScanner.scan(Path.of("A.java"),
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static SourceHeaderScanner.Header scanKotlin(String text) {
        return SourceHeaderScanner.scan(Path.of("A.kt"),
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void scannerReadsJavaPackageAndImports() {
        SourceHeaderScanner.Header header = scanJava(
                "/* comment package ignored */\n"
                + "package com.example.app;\n"
                + "import java.util.List; // real import\n"
                + "import com.example.lib.*;\n"
                + "import static java.lang.Math.max;\n"
                + "class A { String s = \"package fake.import\"; }\n");
        assertEquals("com.example.app", header.packageName);
        assertTrue(header.singleImports.contains("java.util.List"),
                "singles: " + header.singleImports);
        assertTrue(header.singleImports.contains("java.lang.Math.max"));
        assertTrue(header.starImports.contains("com.example.lib"),
                "stars: " + header.starImports);
    }

    @Test
    void scannerReadsKotlinHeaderAndIgnoresStrings() {
        SourceHeaderScanner.Header header = scanKotlin(
                "// fichier genere\n"
                + "package com.example.kt\n"
                + "import com.example.lib.Model\n"
                + "import com.example.other.*\n"
                + "const val TEXT = \"\"\"package not.a.package\n"
                + "import not.an.import\"\"\"\n");
        assertEquals("com.example.kt", header.packageName);
        assertTrue(header.singleImports.contains("com.example.lib.Model"));
        assertTrue(header.starImports.contains("com.example.other"));
    }

    @Test
    void scannerSurvivesGarbage() {
        SourceHeaderScanner.Header header = scanJava(
                "class { \"unterminated }} ;; package ???");
        assertEquals("", header.packageName);
        assertTrue(header.singleImports.isEmpty());
        assertTrue(header.starImports.isEmpty());
    }

    @Test
    void stateCodecRoundTrips() throws Exception {
        CompileState state = new CompileState();
        state.spec = "ecj|17|utf8|cp=a.jar,b.jar";
        state.consumedManifests.put("a.jar", "a".repeat(32));
        state.consumedManifests.put("b.jar", "b".repeat(32));
        CompileState.SourceDeps deps = new CompileState.SourceDeps(
                "src/main/java/com/example/A.java", "com.example");
        deps.singleImports.add("com.example.lib.B");
        deps.starImports.add("com.example.other");
        deps.produced.put("com/example/A",
                List.of("java/lang/Object", "com/example/Iface"));
        deps.references.add("com/example/lib/B");
        deps.references.add("java/lang/String");
        state.sources.put(deps.relativePath, deps);
        state.classAbis.put("com/example/A", "c".repeat(32));

        byte[] encoded = state.encode();
        CompileState decoded = CompileState.decode(encoded);
        assertEquals(state.spec, decoded.spec);
        assertEquals(state.consumedManifests, decoded.consumedManifests);
        assertEquals(1, decoded.sources.size());
        CompileState.SourceDeps back = decoded.sources.get(
                "src/main/java/com/example/A.java");
        assertEquals("com.example", back.packageName);
        assertEquals(List.of("com/example/Iface", "java/lang/Object"),
                back.produced.get("com/example/A"));
        assertEquals(state.classAbis, decoded.classAbis);

        // encodage déterministe : deux passes, mêmes octets
        assertEquals(java.util.Arrays.hashCode(encoded),
                java.util.Arrays.hashCode(state.encode()));
    }

    @Test
    void corruptedStateIsRejected() {
        byte[] encoded = new CompileState().encode();
        encoded[encoded.length - 1] ^= 0x1;
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> CompileState.decode(encoded));
    }

    private static CompileState stateWithSources(int count) {
        CompileState state = new CompileState();
        for (int i = 0; i < count; i++) {
            CompileState.SourceDeps deps = new CompileState.SourceDeps(
                    "src/F" + i + ".java", "com.example");
            if (i > 0) {
                deps.references.add("com/example/Hub");
            }
            deps.produced.put("com/example/F" + i, List.of("java/lang/Object"));
            state.sources.put(deps.relativePath, deps);
            state.classAbis.put("com/example/F" + i, String.valueOf(i));
        }
        CompileState.SourceDeps hub = new CompileState.SourceDeps(
                "src/Hub.java", "com.example");
        hub.produced.put("com/example/Hub", List.of("java/lang/Object"));
        state.sources.put("src/Hub.java", hub);
        state.classAbis.put("com/example/Hub", "hub");
        return state;
    }

    @Test
    void bodyChangeInHubAffectsReferencersOnly() {
        CompileState state = stateWithSources(20);
        // seuil relâché : la sémantique d'impact est testée seule
        ImpactAnalysis analysis = ImpactAnalysis.of(state,
                new ImpactAnalysis.Policy(1.0, false), state.sources.size());
        Set<String> batch = analysis.nextBatch(Set.of("src/Hub.java"),
                Map.of("com/example/Hub", ImpactAnalysis.ChangeKind.CHANGED));
        // les 19 sources qui référencent Hub sont recompilées ; F0 ne
        // le référence pas et reste épargnée (corps inchangé)
        assertEquals(19, batch.size(), "batch: " + batch);
        org.junit.jupiter.api.Assertions.assertFalse(
                batch.contains("src/F0.java"), "F0 must be spared");
    }

    @Test
    void signatureChangePropagatesToFixedPoint() {
        CompileState state = stateWithSources(20);
        ImpactAnalysis analysis = ImpactAnalysis.of(state,
                new ImpactAnalysis.Policy(1.0, false), state.sources.size());
        Set<String> first = analysis.nextBatch(Set.of("src/Hub.java"),
                Map.of("com/example/Hub", ImpactAnalysis.ChangeKind.CHANGED));
        // les sources recompilées changent d'ABI a leur tour (simule)
        Map<String, ImpactAnalysis.ChangeKind> secondWave = new TreeMap<>();
        secondWave.put("com/example/F0", ImpactAnalysis.ChangeKind.CHANGED);
        Set<String> second = analysis.nextBatch(first, secondWave);
        // F0 est referencee par personne d'autre : point fixe
        assertTrue(second.isEmpty(), "second: " + second);
    }

    @Test
    void thresholdTriggersFullFallback() {
        CompileState state = stateWithSources(10);
        ImpactAnalysis analysis = ImpactAnalysis.of(state,
                new ImpactAnalysis.Policy(0.30, false),
                state.sources.size());
        // 10 sources sur 21 depassent 30 % : repli complet
        Set<String> batch = analysis.initialBatch(Set.of(), Set.of(),
                Map.of("com/example/Hub", ImpactAnalysis.ChangeKind.CHANGED));
        assertNull(batch, "20 of 21 sources must exceed the 30% threshold");
        // en dessous du seuil : dix sources, deux référenceurs d'une
        // bibliothèque du module — lot de deux, pas de repli
        CompileState mixed = new CompileState();
        for (int i = 0; i < 10; i++) {
            CompileState.SourceDeps deps = new CompileState.SourceDeps(
                    "src/G" + i + ".java", "p");
            deps.produced.put("p/G" + i, List.of("java/lang/Object"));
            if (i < 2) {
                deps.references.add("p/Lib");
            }
            mixed.sources.put(deps.relativePath, deps);
        }
        CompileState.SourceDeps lib = new CompileState.SourceDeps(
                "src/Lib.java", "p");
        lib.produced.put("p/Lib", List.of("java/lang/Object"));
        mixed.sources.put("src/Lib.java", lib);
        ImpactAnalysis small = ImpactAnalysis.of(mixed,
                new ImpactAnalysis.Policy(0.30, false), 11);
        Set<String> batch2 = small.initialBatch(Set.of(), Set.of(),
                Map.of("p/Lib", ImpactAnalysis.ChangeKind.CHANGED));
        org.junit.jupiter.api.Assertions.assertNotNull(batch2);
        assertEquals(2, batch2.size(), "batch: " + batch2);
    }

    @Test
    void addedClassAffectsSamePackageAndStarImporters() {
        CompileState state = new CompileState();
        CompileState.SourceDeps samePackage = new CompileState.SourceDeps(
                "src/Same.java", "com.example");
        state.sources.put("src/Same.java", samePackage);
        CompileState.SourceDeps starImporter = new CompileState.SourceDeps(
                "src/Star.java", "other");
        starImporter.starImports.add("com.example");
        state.sources.put("src/Star.java", starImporter);
        CompileState.SourceDeps elsewhere = new CompileState.SourceDeps(
                "src/Elsewhere.java", "third");
        state.sources.put("src/Elsewhere.java", elsewhere);

        ImpactAnalysis analysis = ImpactAnalysis.of(state,
                new ImpactAnalysis.Policy(1.0, false), 3);
        Set<String> affected = analysis.initialBatch(Set.of(), Set.of(),
                Map.of("com/example/New", ImpactAnalysis.ChangeKind.ADDED));
        assertTrue(affected.contains("src/Same.java"), "same package");
        assertTrue(affected.contains("src/Star.java"), "star importer");
        org.junit.jupiter.api.Assertions.assertFalse(
                affected.contains("src/Elsewhere.java"), "unrelated");
    }

    @Test
    void aggregatingProcessorForcesFull() {
        CompileState state = stateWithSources(5);
        ImpactAnalysis analysis = ImpactAnalysis.of(state,
                new ImpactAnalysis.Policy(0.30, true),
                state.sources.size());
        assertNull(analysis.initialBatch(Set.of(), Set.of("src/Hub.java"),
                Map.of()));
    }

    @Test
    void subclassRuleFiresOnHierarchyChange() {
        CompileState state = new CompileState();
        CompileState.SourceDeps base = new CompileState.SourceDeps(
                "src/Base.java", "p");
        base.produced.put("p/Base", List.of("java/lang/Object"));
        state.sources.put("src/Base.java", base);
        CompileState.SourceDeps child = new CompileState.SourceDeps(
                "src/Child.java", "p");
        child.produced.put("p/Child", List.of("p/Base"));
        state.sources.put("src/Child.java", child);
        CompileState.SourceDeps caller = new CompileState.SourceDeps(
                "src/Caller.java", "q");
        caller.references.add("p/Child");
        state.sources.put("src/Caller.java", caller);

        ImpactAnalysis analysis = ImpactAnalysis.of(state,
                new ImpactAnalysis.Policy(1.0, false), 3);
        Set<String> affected = analysis.initialBatch(Set.of(), Set.of(),
                Map.of("p/Base", ImpactAnalysis.ChangeKind.CHANGED));
        // Caller référence Child, sous-classe de Base : touché même
        // sans référencer Base directement
        assertTrue(affected.contains("src/Caller.java"),
                "affected: " + affected);
    }
}
