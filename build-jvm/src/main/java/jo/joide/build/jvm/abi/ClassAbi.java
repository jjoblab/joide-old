package jo.joide.build.jvm.abi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jo.joide.build.jvm.classfile.ClassModel;
import jo.joide.build.jvm.classfile.ClassModel.Member;
import jo.joide.build.jvm.classfile.ClassModel.RawAttribute;
import jo.joide.platform.Hashing;

/**
 * ABI d'une classe : rendu canonique de sa surface visible par un
 * compilateur aval, haché en 128 bits.
 *
 * <p>Sont retenus : drapeaux, super-type, interfaces, signature
 * générique, attributs d'annotations (toutes rétentions visibles du
 * format de classe), membres non privés (descripteur, signature,
 * valeur constante inlinable, clause throws, noms de paramètres) et
 * faits Kotlin (visibilité interne, inline, const, scellage, noms de
 * paramètres, corps des membres inline par hachage des métadonnées).
 * Sont exclus : corps de méthode et membres privés.</p>
 */
public final class ClassAbi {

    /** Version du rendu : tout changement de format l'incrémente et
     *  invalide les manifestes existants. */
    static final String FORMAT = "v1";

    private final String canonical;
    private final String hash;

    private ClassAbi(String canonical, String hash) {
        this.canonical = canonical;
        this.hash = hash;
    }

    /**
     * @param model classe lue, jamais {@code null}
     * @return l'ABI canonique et son hachage 128 bits hexadécimal
     */
    public static ClassAbi of(ClassModel model) {
        String canonical = render(model);
        return new ClassAbi(canonical,
                Hashing.toHex(Hashing.sha256(
                        canonical.getBytes(StandardCharsets.UTF_8)))
                        .substring(0, 32));
    }

    /** @return le rendu canonique, jamais {@code null} */
    public String canonical() {
        return canonical;
    }

    /** @return le hachage 128 bits en hexadécimal (32 caractères) */
    public String hash() {
        return hash;
    }

    private static String render(ClassModel model) {
        StringBuilder out = new StringBuilder(256);
        out.append(FORMAT).append(" class ").append(model.binaryName)
                .append('\n');
        out.append("flags=0x").append(
                Integer.toHexString(model.accessFlags)).append('\n');
        out.append("super=").append(model.superName).append('\n');
        out.append("ifaces=").append(String.join(",", model.interfaces))
                .append('\n');
        out.append("sig=").append(nullable(model.genericSignature))
                .append('\n');
        out.append("ann=").append(annotationDigest(model.annotationAttributes))
                .append('\n');
        KotlinFacts facts = KotlinFacts.of(model);
        for (Member field : model.fields) {
            if (field.isPrivate()) {
                continue;
            }
            out.append("f 0x").append(Integer.toHexString(field.accessFlags))
                    .append('|').append(field.name)
                    .append('|').append(field.descriptor)
                    .append('|').append(nullable(field.genericSignature))
                    .append('|').append(constantValue(field))
                    .append("|ex[").append(String.join(",", field.exceptions))
                    .append(']')
                    .append("|ann[")
                    .append(annotationDigest(field.annotationAttributes))
                    .append("]\n");
        }
        for (Member method : model.methods) {
            if (method.isPrivate()) {
                continue;
            }
            out.append("m 0x").append(
                    Integer.toHexString(method.accessFlags))
                    .append('|').append(method.name)
                    .append('|').append(method.descriptor)
                    .append('|').append(nullable(method.genericSignature))
                    .append('|').append(constantValue(method))
                    .append("|ex[").append(String.join(",", method.exceptions))
                    .append(']')
                    .append("|par[")
                    .append(String.join(",", paramNames(method)))
                    .append(']')
                    .append("|ann[")
                    .append(annotationDigest(method.annotationAttributes))
                    .append("]\n");
        }
        out.append("kotlin=").append(facts == null ? "-" : facts.canonical())
                .append('\n');
        return out.toString();
    }

    private static List<String> paramNames(Member member) {
        List<String> names = new ArrayList<>();
        for (String name : member.parameterNames) {
            names.add(name == null ? "?" : name);
        }
        return names;
    }

    private static String constantValue(Member member) {
        Object value = member.constantValue;
        if (value == null) {
            return "-";
        }
        if (value instanceof String) {
            // les chaînes peuvent contenir les séparateurs du rendu :
            // encodage hexadécimal pour rester canonique
            byte[] bytes = ((String) value).getBytes(StandardCharsets.UTF_8);
            return "S:" + Hashing.toHex(bytes);
        }
        if (value instanceof Float) {
            return "F:" + Float.floatToRawIntBits((Float) value);
        }
        if (value instanceof Double) {
            return "D:" + Double.doubleToRawLongBits((Double) value);
        }
        if (value instanceof Long) {
            return "J:" + value;
        }
        return value.getClass().getSimpleName().charAt(0) + ":" + value;
    }

    /**
     * Condensé des attributs d'annotations : ordre par nom puis
     * hachage de chaque attribut — le contenu brut seul compte, jamais
     * l'ordre d'écriture dans le fichier.
     */
    private static String annotationDigest(List<RawAttribute> attributes) {
        if (attributes.isEmpty()) {
            return "-";
        }
        List<RawAttribute> sorted = new ArrayList<>(attributes);
        sorted.sort(Comparator.comparing(a -> a.name));
        StringBuilder out = new StringBuilder();
        for (RawAttribute attribute : sorted) {
            if (out.length() > 0) {
                out.append(',');
            }
            byte[] bytes = new byte[attribute.name.length()
                    + attribute.bytes.length + 1];
            byte[] name = attribute.name.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(name, 0, bytes, 0, name.length);
            bytes[name.length] = ':';
            System.arraycopy(attribute.bytes, 0, bytes, name.length + 1,
                    attribute.bytes.length);
            out.append(Hashing.toHex(Hashing.sha256(bytes)).substring(0, 16));
        }
        return out.toString();
    }

    private static String nullable(String value) {
        return value == null ? "-" : value;
    }
}
