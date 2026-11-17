package jo.joide.deps.gmm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jo.joide.deps.gmm.GmmModule.GmmDependency;
import jo.joide.deps.gmm.GmmModule.GmmFile;
import jo.joide.deps.gmm.GmmModule.GmmVariant;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonValue;

/**
 * Analyse d'un fichier « .module » (JSON des métadonnées de module
 * Gradle) en {@link GmmModule}. Tolérant : les entrées illisibles
 * produisent des problèmes et sont ignorées une par une, jamais
 * d'échec global tant qu'un variant reste lisible.
 */
public final class GmmParser {

    /** Un problème d'analyse. */
    public static final class Problem {
        public final String message;

        Problem(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "gmm: " + message;
        }
    }

    /** Le résultat : module (peut être null) et problèmes. */
    public static final class Result {
        public final GmmModule module;
        public final List<Problem> problems;

        Result(GmmModule module, List<Problem> problems) {
            this.module = module;
            this.problems = List.copyOf(problems);
        }
    }

    private GmmParser() {
    }

    /**
     * @param text contenu du fichier .module
     * @return le module et les problèmes
     */
    public static Result parse(String text) {
        List<Problem> problems = new ArrayList<>();
        Json.Document doc = Json.parse(text);
        for (Json.Problem jp : doc.problems) {
            problems.add(new Problem(jp.toString()));
        }
        if (doc.root == null || doc.root.type() != JsonValue.Type.OBJECT) {
            problems.add(new Problem("root is not an object"));
            return new Result(null, problems);
        }
        JsonValue root = doc.root;
        String formatVersion = root.member("formatVersion").asString();
        if (formatVersion == null) {
            problems.add(new Problem("missing formatVersion"));
        }
        List<GmmVariant> variants = new ArrayList<>();
        for (JsonValue v : root.member("variants").items()) {
            GmmVariant variant = variant(v, problems);
            if (variant != null) {
                variants.add(variant);
            }
        }
        return new Result(new GmmModule(formatVersion, variants), problems);
    }

    private static GmmVariant variant(JsonValue v, List<Problem> problems) {
        if (v.type() != JsonValue.Type.OBJECT) {
            problems.add(new Problem("variant is not an object"));
            return null;
        }
        String name = v.member("name").asString();
        if (name == null) {
            problems.add(new Problem("variant without name"));
            return null;
        }
        Map<String, String> attributes = new HashMap<>();
        JsonValue attrs = v.member("attributes");
        if (attrs.type() == JsonValue.Type.OBJECT) {
            for (String key : attrs.keys()) {
                String value = attrs.member(key).asString();
                if (value == null) {
                    value = attrs.member(key).toString();
                }
                attributes.put(key, value);
            }
        }
        List<GmmFile> files = new ArrayList<>();
        for (JsonValue f : v.member("files").items()) {
            GmmFile file = file(f, problems);
            if (file != null) {
                files.add(file);
            }
        }
        List<GmmDependency> dependencies = new ArrayList<>();
        for (JsonValue d : v.member("dependencies").items()) {
            GmmDependency dep = dependency(d, problems);
            if (dep != null) {
                dependencies.add(dep);
            }
        }
        String availableAtUrl = null;
        Map<String, String> availableAtAttributes = new HashMap<>();
        JsonValue availableAt = v.member("available-at");
        if (availableAt.type() == JsonValue.Type.OBJECT) {
            availableAtUrl = availableAt.member("url").asString();
            JsonValue aa = availableAt.member("attributes");
            if (aa.type() == JsonValue.Type.OBJECT) {
                for (String key : aa.keys()) {
                    String value = aa.member(key).asString();
                    if (value != null) {
                        availableAtAttributes.put(key, value);
                    }
                }
            }
            if (availableAtUrl == null) {
                problems.add(new Problem("available-at without url in "
                        + name));
            }
        }
        return new GmmVariant(name, attributes, files, dependencies,
                availableAtUrl, availableAtAttributes);
    }

    private static GmmFile file(JsonValue f, List<Problem> problems) {
        if (f.type() != JsonValue.Type.OBJECT) {
            problems.add(new Problem("file is not an object"));
            return null;
        }
        String name = f.member("name").asString();
        if (name == null) {
            problems.add(new Problem("file without name"));
            return null;
        }
        String url = f.member("url").asString();
        long size = f.member("size").asLong();
        String sha1 = null;
        String sha256 = null;
        JsonValue checksums = f.member("checksums");
        if (checksums.type() == JsonValue.Type.OBJECT) {
            sha1 = checksums.member("sha1").asString();
            sha256 = checksums.member("sha256").asString();
        }
        return new GmmFile(name, url, size, sha1, sha256);
    }

    private static GmmDependency dependency(JsonValue d,
                                            List<Problem> problems) {
        if (d.type() != JsonValue.Type.OBJECT) {
            problems.add(new Problem("dependency is not an object"));
            return null;
        }
        String group = d.member("group").asString();
        String module = d.member("module").asString();
        if (group == null || module == null) {
            problems.add(new Problem("dependency without group or module"));
            return null;
        }
        JsonValue version = d.member("version");
        String versionText = version.asString();
        boolean strictly = false;
        if (versionText == null && version.type() == JsonValue.Type.OBJECT) {
            // Forme riche : strictly > requires > prefers.
            String s = version.member("strictly").asString();
            String req = version.member("requires").asString();
            String pref = version.member("prefers").asString();
            if (s != null) {
                versionText = s;
                strictly = true;
            } else if (req != null) {
                versionText = req;
            } else {
                versionText = pref;
            }
        }
        Boolean optional = d.member("optional").asBoolean();
        List<String> excludes = new ArrayList<>();
        for (JsonValue e : d.member("excludes").items()) {
            String eg = e.member("group").asString();
            String em = e.member("module").asString();
            if (eg != null && em != null) {
                excludes.add((eg + ":" + em).toLowerCase(Locale.ROOT));
            }
        }
        return new GmmDependency(group, module, versionText, strictly,
                optional != null && optional, excludes);
    }
}
