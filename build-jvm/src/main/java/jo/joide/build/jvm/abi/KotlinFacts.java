package jo.joide.build.jvm.abi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jo.joide.build.jvm.classfile.ClassModel;
import jo.joide.platform.Hashing;

import kotlin.metadata.Attributes;
import kotlin.metadata.ClassKind;
import kotlin.metadata.MemberKind;
import kotlin.metadata.Modality;
import kotlin.metadata.Visibility;
import kotlin.metadata.jvm.KotlinClassMetadata;

/**
 * Faits Kotlin d'une classe pour l'ABI : visibilité interne, fonctions
 * inline, propriétés constantes, scellage, genre de classe et noms de
 * paramètres (arguments nommés côté appelant).
 *
 * <p>Deux niveaux, du plus précis au plus sûr : lecture structurelle
 * par la bibliothèque de métadonnées (faits par membre), et à défaut
 * hachage brut des données de métadonnées — sur-invalidation prudente,
 * jamais de sous-invalidation. Pour les classes qui déclarent au moins
 * un membre inline (le corps fait partie de l'ABI), le hachage brut
 * est ajouté : la bibliothèque de métadonnées n'expose pas les corps.</p>
 */
public final class KotlinFacts {

    private final String canonical;
    private final boolean inlineMemberPresent;

    private KotlinFacts(String canonical, boolean inlineMemberPresent) {
        this.canonical = canonical;
        this.inlineMemberPresent = inlineMemberPresent;
    }

    /**
     * @param model classe lue, dont les métadonnées peuvent être
     *              absentes (classe Java)
     * @return les faits, ou {@code null} si la classe n'est pas Kotlin
     */
    public static KotlinFacts of(ClassModel model) {
        ClassModel.KotlinMetadata metadata = model.kotlinMetadata;
        if (metadata == null) {
            return null;
        }
        String rawHash = rawMetadataHash(metadata);
        Structural structural = structuralFacts(metadata);
        if (structural != null && !structural.inlinePresent) {
            return new KotlinFacts(structural.rendering, false);
        }
        if (structural != null) {
            return new KotlinFacts(structural.rendering
                    + "\n  rawmetadata=" + rawHash, true);
        }
        // Lecture structurelle impossible : repli intégral sur le
        // hachage brut (sur-invalidation sûre)
        return new KotlinFacts("rawmetadata=" + rawHash, true);
    }

    /** @return le rendu canonique, jamais {@code null} */
    public String canonical() {
        return canonical;
    }

    /** @return vrai si un membre inline rend les corps sensibles */
    public boolean inlineMemberPresent() {
        return inlineMemberPresent;
    }

    private static String rawMetadataHash(ClassModel.KotlinMetadata m) {
        StringBuilder text = new StringBuilder();
        for (String part : m.data1) {
            text.append(part.length()).append(':').append(part).append('|');
        }
        for (String part : m.data2) {
            text.append(part.length()).append(':').append(part).append('|');
        }
        return hex16(Hashing.sha256(
                text.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static final class Structural {
        final String rendering;
        final boolean inlinePresent;

        Structural(String rendering, boolean inlinePresent) {
            this.rendering = rendering;
            this.inlinePresent = inlinePresent;
        }
    }

    private static Structural structuralFacts(
            ClassModel.KotlinMetadata metadata) {
        try {
            KotlinClassMetadata parsed = KotlinClassMetadata.readLenient(
                    metadataProxy(metadata));
            if (parsed instanceof KotlinClassMetadata.Class) {
                return classFacts(
                        ((KotlinClassMetadata.Class) parsed).getKmClass());
            }
            if (parsed instanceof KotlinClassMetadata.FileFacade
                    || parsed instanceof KotlinClassMetadata.MultiFileClassPart) {
                return facadeFacts(parsed);
            }
            // Genre non porteur de membres exploitables (façade
            // multi-fichiers, classe synthétique…) : les faits
            // d'octets bruts suffisent
            return new Structural("", false);
        } catch (Throwable t) {
            return null; // métadonnées illisibles : repli brut
        }
    }

    private static Structural classFacts(kotlin.metadata.KmClass kmClass) {
        StringBuilder out = new StringBuilder();
        out.append("kind=").append(safeKind(kmClass));
        out.append(" vis=").append(Attributes.getVisibility(kmClass));
        out.append(" modality=").append(Attributes.getModality(kmClass));
        out.append(" value=").append(Attributes.isValue(kmClass));
        out.append(" funitf=").append(Attributes.isFunInterface(kmClass));
        out.append(" inner=").append(Attributes.isInner(kmClass));
        if (kmClass.getCompanionObject() != null) {
            out.append(" companion=").append(kmClass.getCompanionObject());
        }
        if (!kmClass.getNestedClasses().isEmpty()) {
            out.append(" nested=")
                    .append(String.join(",", kmClass.getNestedClasses()));
        }
        boolean inline = false;
        for (kotlin.metadata.KmFunction function : kmClass.getFunctions()) {
            if (Attributes.getKind(function) != MemberKind.DECLARATION) {
                continue;
            }
            boolean isInline = Attributes.isInline(function);
            inline |= isInline;
            out.append("\n  fun ").append(function.getName())
                    .append('(');
            List<String> paramNames = new ArrayList<>();
            function.getValueParameters()
                    .forEach(p -> paramNames.add(p.getName()));
            out.append(String.join(",", paramNames)).append(')');
            out.append(" vis=").append(Attributes.getVisibility(function));
            out.append(" mod=").append(Attributes.getModality(function));
            out.append(" inline=").append(isInline);
            out.append(" suspend=").append(Attributes.isSuspend(function));
            out.append(" operator=").append(Attributes.isOperator(function));
            out.append(" infix=").append(Attributes.isInfix(function));
        }
        for (kotlin.metadata.KmProperty property : kmClass.getProperties()) {
            boolean isConst = Attributes.isConst(property);
            boolean inlineAccessor = inlineGetter(property);
            inline |= inlineAccessor;
            out.append("\n  prop ").append(property.getName());
            out.append(" vis=").append(Attributes.getVisibility(property));
            out.append(" mod=").append(Attributes.getModality(property));
            out.append(" const=").append(isConst);
            out.append(" inlineaccessor=").append(inlineAccessor);
        }
        return new Structural(out.toString(), inline);
    }

    private static Structural facadeFacts(KotlinClassMetadata parsed) {
        // Façade de fichier : les fonctions de premier niveau sont
        // décrites par la même bibliothèque
        kotlin.metadata.KmPackage pkg = null;
        if (parsed instanceof KotlinClassMetadata.FileFacade) {
            pkg = ((KotlinClassMetadata.FileFacade) parsed).getKmPackage();
        } else if (parsed instanceof KotlinClassMetadata.MultiFileClassPart) {
            pkg = ((KotlinClassMetadata.MultiFileClassPart) parsed)
                    .getKmPackage();
        }
        if (pkg == null) {
            return new Structural("", false);
        }
        StringBuilder out = new StringBuilder("kind=filefacade");
        boolean inline = false;
        for (kotlin.metadata.KmFunction function : pkg.getFunctions()) {
            if (Attributes.getKind(function) != MemberKind.DECLARATION) {
                continue;
            }
            boolean isInline = Attributes.isInline(function);
            inline |= isInline;
            out.append("\n  fun ").append(function.getName())
                    .append('(');
            List<String> paramNames = new ArrayList<>();
            function.getValueParameters()
                    .forEach(p -> paramNames.add(p.getName()));
            out.append(String.join(",", paramNames)).append(')');
            out.append(" vis=").append(Attributes.getVisibility(function));
            out.append(" inline=").append(isInline);
        }
        for (kotlin.metadata.KmProperty property : pkg.getProperties()) {
            boolean isConst = Attributes.isConst(property);
            boolean inlineAccessor = inlineGetter(property);
            inline |= inlineAccessor;
            out.append("\n  prop ").append(property.getName());
            out.append(" const=").append(isConst);
            out.append(" inlineaccessor=").append(inlineAccessor);
        }
        return new Structural(out.toString(), inline);
    }

    private static boolean inlineGetter(kotlin.metadata.KmProperty property) {
        kotlin.metadata.KmPropertyAccessorAttributes getter =
                property.getGetter();
        return getter != null && Attributes.isInline(getter);
    }

    private static String safeKind(kotlin.metadata.KmClass kmClass) {
        ClassKind kind = Attributes.getKind(kmClass);
        return kind == null ? "class" : kind.name();
    }

    /**
     * Construit une instance {@code kotlin.Metadata} (annotation) à
     * partir des valeurs lues : les annotations sont des interfaces,
     * un mandataire dynamique suffit.
     */
    private static kotlin.Metadata metadataProxy(
            ClassModel.KotlinMetadata metadata) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "k":
                        return Integer.valueOf(metadata.kind);
                    case "mv":
                        return metadata.metadataVersion;
                    case "bv":
                        // l'annotation peut omettre bv (valeur par
                        // défaut) : la bibliothèque attend un numéro
                        // valide, jamais un tableau vide
                        return metadata.bytecodeVersion.length == 0
                                ? defaultBytecodeVersion()
                                : metadata.bytecodeVersion;
                    case "d1":
                        return metadata.data1;
                    case "d2":
                        return metadata.data2;
                    case "xs":
                        return "";
                    case "pn":
                        return "";
                    case "xi":
                        return Integer.valueOf(0);
                    case "annotationType":
                        return kotlin.Metadata.class;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return Integer.valueOf(System.identityHashCode(proxy));
                    case "toString":
                        return "kotlin.Metadata(proxy)";
                    default:
                        return null;
                }
            }
        };
        return (kotlin.Metadata) Proxy.newProxyInstance(
                KotlinFacts.class.getClassLoader(),
                new Class<?>[] {kotlin.Metadata.class}, handler);
    }

    /** Numéro de bytecode par défaut déclaré dans l'annotation. */
    private static int[] defaultBytecodeVersion() {
        try {
            Object value = kotlin.Metadata.class.getMethod("bv")
                    .getDefaultValue();
            if (value instanceof int[]) {
                return (int[]) value;
            }
        } catch (ReflectiveOperationException e) {
            // annotation introuvable : repli sur la valeur historique
        }
        return new int[] {1, 0, 3};
    }

    private static String hex16(byte[] digest) {
        return Hashing.toHex(digest).substring(0, 16);
    }
}
