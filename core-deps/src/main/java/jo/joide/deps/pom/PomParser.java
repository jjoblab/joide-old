package jo.joide.deps.pom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import jo.joide.deps.xml.Xml;
import jo.joide.deps.xml.XmlNode;

/**
 * Analyse et résolution d'un POM : parents suivis (via un fournisseur),
 * propriétés interpolées (héritées puis propres), gestion de
 * dépendances fusionnée avec importations de nomenclatures
 * (« scope import » sur « type pom »), exclusions conservées.
 *
 * <p>Tolérant : tout point non résolu produit une entrée dans la
 * liste de problèmes et, quand le document reste lisible, le
 * modèle partiel.</p>
 */
public final class PomParser {

    /** Un problème de résolution de POM. */
    public static final class Problem {
        public final String message;

        Problem(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "pom: " + message;
        }
    }

    /** Le résultat : modèle (peut être null) et problèmes. */
    public static final class Result {
        public final PomModel model;
        public final List<Problem> problems;

        Result(PomModel model, List<Problem> problems) {
            this.model = model;
            this.problems = List.copyOf(problems);
        }
    }

    /** Fournit le POM d'un parent ou d'une nomenclature, ou null. */
    public interface Fetcher {
        /**
         * @param groupId    groupe
         * @param artifactId artefact
         * @param version    version
         * @return le texte du POM, ou {@code null} si introuvable
         * @throws IOException si le transport échoue
         */
        String fetch(String groupId, String artifactId, String version)
                throws IOException;
    }

    private static final int MAX_PARENTS = 10;
    private static final int MAX_BOM_DEPTH = 10;

    private final Fetcher fetcher;

    public PomParser(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * @param pomText texte du POM
     * @return le modèle résolu et les problèmes
     */
    public Result parse(String pomText) {
        List<Problem> problems = new ArrayList<>();
        PomModel model = resolve(pomText, problems, new HashSet<>(),
                new HashSet<>(), 0);
        return new Result(model, problems);
    }

    private PomModel resolve(String pomText, List<Problem> problems,
                             Set<String> visitingPoms,
                             Set<String> visitingBoms, int bomDepth) {
        Xml.Document doc = Xml.parse(pomText);
        for (Xml.Problem xp : doc.problems) {
            problems.add(new Problem(xp.toString()));
        }
        if (doc.root == null || !"project".equals(doc.root.name())
                && !"project".equals(localName(doc.root.name()))) {
            problems.add(new Problem("root element is not <project>"));
            return null;
        }
        XmlNode project = doc.root;
        // Coordonnées : héritées du parent si absentes.
        String parentGroupId = null;
        String parentArtifactId = null;
        String parentVersion = null;
        XmlNode parent = project.child("parent");
        PomModel parentModel = null;
        if (parent != null) {
            parentGroupId = text(parent, "groupId");
            parentArtifactId = text(parent, "artifactId");
            parentVersion = text(parent, "version");
            if (parentGroupId == null || parentArtifactId == null
                    || parentVersion == null) {
                problems.add(new Problem("incomplete <parent> coordinates"));
                return null;
            }
            String parentGa = (parentGroupId + ":" + parentArtifactId
                    + ":" + parentVersion).toLowerCase(Locale.ROOT);
            if (!visitingPoms.add(parentGa)) {
                problems.add(new Problem("parent cycle at " + parentGa));
                return null;
            }
            try {
                if (visitingPoms.size() > MAX_PARENTS) {
                    problems.add(new Problem("too many parents"));
                    return null;
                }
                String parentText = fetcher.fetch(parentGroupId,
                        parentArtifactId, parentVersion);
                if (parentText == null) {
                    problems.add(new Problem("parent pom not found: "
                            + parentGa));
                    return null;
                }
                parentModel = resolve(parentText, problems, visitingPoms,
                        visitingBoms, bomDepth);
                if (parentModel == null) {
                    problems.add(new Problem("unresolvable parent: "
                            + parentGa));
                    return null;
                }
            } catch (IOException e) {
                problems.add(new Problem("parent fetch failed: "
                        + parentGa + ": " + e.getMessage()));
                return null;
            } finally {
                visitingPoms.remove(parentGa);
            }
        }
        // Coordonnées d'abord (héritées du parent si absentes), pour
        // pouvoir interpoler les propriétés avec les construits.
        String groupId = text(project, "groupId");
        String artifactId = text(project, "artifactId");
        String version = text(project, "version");
        String packaging = text(project, "packaging");
        if (groupId == null) {
            groupId = parentModel != null ? parentModel.groupId : parentGroupId;
        }
        if (version == null) {
            version = parentModel != null ? parentModel.version : parentVersion;
        }
        if (artifactId == null) {
            problems.add(new Problem("missing <artifactId>"));
            return null;
        }
        if (groupId == null || version == null) {
            problems.add(new Problem("missing <groupId> or <version>"));
            return null;
        }
        if (packaging == null) {
            packaging = parentModel != null ? parentModel.packaging : "jar";
        }
        if (packaging == null) {
            packaging = "jar";
        }
        // Construits du document, disponibles pour l'interpolation.
        Map<String, String> builtins = new HashMap<>();
        builtins.put("project.groupId", groupId);
        builtins.put("project.artifactId", artifactId);
        builtins.put("project.version", version);
        builtins.put("project.packaging", packaging);
        builtins.put("groupId", groupId);
        builtins.put("artifactId", artifactId);
        builtins.put("version", version);
        builtins.put("pom.groupId", groupId);
        builtins.put("pom.artifactId", artifactId);
        builtins.put("pom.version", version);
        // Propriétés : héritées du parent, puis propres — chaque
        // valeur est interpolée à l'insertion (les valeurs qui
        // référencent d'autres propriétés ou des construits se
        // développent donc en une passe).
        Map<String, String> properties = new HashMap<>();
        if (parentModel != null) {
            properties.putAll(parentModel.properties);
        }
        XmlNode props = project.child("properties");
        if (props != null) {
            for (XmlNode p : props.children()) {
                String value = interpolate(p.text(), properties, builtins);
                properties.put(p.name(), value);
            }
        }
        // Gestion de dépendances : parent d'abord, enfant gagne, BOM
        // développées sur place.
        Map<String, PomModel.PomDependency> management =
                new LinkedHashMap<>();
        if (parentModel != null) {
            for (PomModel.PomDependency d : parentModel.dependencyManagement) {
                management.put(d.ga(), d);
            }
        }
        List<PomModel.PomDependency> ownManagement = new ArrayList<>();
        XmlNode depMgmt = project.child("dependencyManagement");
        if (depMgmt != null) {
            XmlNode deps = depMgmt.child("dependencies");
            if (deps != null) {
                for (XmlNode d : deps.children("dependency")) {
                    PomModel.PomDependency parsed = dependency(d, properties,
                            builtins, problems);
                    if (parsed == null) {
                        continue;
                    }
                    if (parsed.scope == PomModel.Scope.IMPORT) {
                        if (parsed.version == null) {
                            problems.add(new Problem(
                                    "BOM import without version"));
                            continue;
                        }
                        if (bomDepth >= MAX_BOM_DEPTH) {
                            problems.add(new Problem("BOM nesting too deep"));
                            continue;
                        }
                        String bomGa = (parsed.groupId + ":" + parsed.artifactId
                                + ":" + parsed.version).toLowerCase(Locale.ROOT);
                        if (!visitingBoms.add(bomGa)) {
                            problems.add(new Problem("BOM cycle at " + bomGa));
                            continue;
                        }
                        try {
                            String bomText = fetcher.fetch(parsed.groupId,
                                    parsed.artifactId, parsed.version);
                            if (bomText == null) {
                                problems.add(new Problem("BOM not found: "
                                        + bomGa));
                            } else {
                                PomModel bom = resolve(bomText, problems,
                                        visitingPoms, visitingBoms,
                                        bomDepth + 1);
                                if (bom != null) {
                                    for (PomModel.PomDependency bd
                                            : bom.dependencyManagement) {
                                        management.put(bd.ga(), bd);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            problems.add(new Problem("BOM fetch failed: "
                                    + bomGa + ": " + e.getMessage()));
                        } finally {
                            visitingBoms.remove(bomGa);
                        }
                    } else {
                        ownManagement.add(parsed);
                    }
                }
            }
        }
        for (PomModel.PomDependency d : ownManagement) {
            management.put(d.ga(), d);
        }
        // Dépendances propres.
        List<PomModel.PomDependency> dependencies = new ArrayList<>();
        XmlNode depsEl = project.child("dependencies");
        if (depsEl != null) {
            for (XmlNode d : depsEl.children("dependency")) {
                PomModel.PomDependency parsed = dependency(d, properties,
                        builtins, problems);
                if (parsed != null) {
                    dependencies.add(parsed);
                }
            }
        }
        return new PomModel(groupId, artifactId, version, packaging,
                properties, dependencies,
                new ArrayList<>(management.values()));
    }

    private PomModel.PomDependency dependency(XmlNode d,
                                              Map<String, String> properties,
                                              Map<String, String> builtins,
                                              List<Problem> problems) {
        String g = interpolate(text(d, "groupId"), properties, builtins);
        String a = interpolate(text(d, "artifactId"), properties, builtins);
        String v = interpolate(text(d, "version"), properties, builtins);
        String c = interpolate(text(d, "classifier"), properties, builtins);
        String scopeText = lowercase(text(d, "scope"));
        String typeText = lowercase(text(d, "type"));
        String optionalText = lowercase(text(d, "optional"));
        if (g == null || a == null) {
            problems.add(new Problem("dependency without group or artifact"));
            return null;
        }
        PomModel.Scope scope = PomModel.Scope.COMPIL;
        if ("import".equals(scopeText)) {
            scope = PomModel.Scope.IMPORT;
        } else if ("provided".equals(scopeText)) {
            scope = PomModel.Scope.PROVIDED;
        } else if ("runtime".equals(scopeText)) {
            scope = PomModel.Scope.RUNTIME;
        } else if ("test".equals(scopeText)) {
            scope = PomModel.Scope.TEST;
        }
        // « type pom » sans scope importé : une dépendance de POM
        // directe — garde le sens par défaut du scope annoncé.
        boolean optional = "true".equals(optionalText);
        Set<String> exclusions = new HashSet<>();
        XmlNode excl = d.child("exclusions");
        if (excl != null) {
            for (XmlNode e : excl.children("exclusion")) {
                String eg = text(e, "groupId");
                String ea = text(e, "artifactId");
                if (eg != null && ea != null) {
                    exclusions.add((eg + ":" + ea).toLowerCase(Locale.ROOT));
                }
            }
        }
        return new PomModel.PomDependency(g, a, v, c, scope, optional,
                exclusions);
    }

    private static String text(XmlNode parent, String childName) {
        XmlNode c = parent.child(childName);
        return c != null ? c.text() : null;
    }

    private static String lowercase(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : null;
    }

    private String interpolate(String raw, Map<String, String> properties,
                               Map<String, String> builtins) {
        if (raw == null) {
            return null;
        }
        if (raw.indexOf("${") < 0) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf("${", i);
            if (open < 0) {
                out.append(raw, i, raw.length());
                break;
            }
            int close = raw.indexOf('}', open + 2);
            if (close < 0) {
                out.append(raw, i, raw.length());
                break;
            }
            out.append(raw, i, open);
            String name = raw.substring(open + 2, close);
            String value = properties.containsKey(name)
                    ? properties.get(name)
                    : builtins.get(name);
            out.append(value != null ? value : "");
            i = close + 1;
        }
        return out.toString();
    }

    private static String localName(String name) {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
