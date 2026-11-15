package jo.joide.build.jvm.classfile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lecture immuable d'un fichier de classe : tout ce dont le pipeline
 * JVM a besoin pour l'ABI, le graphe classe-classe et l'association
 * classe vers source.
 *
 * <p>Le modèle ne conserve ni corps de méthode ni table locale : la
 * vue est celle d'un consommateur de code compilé (compilateur aval),
 * pas celle d'un débogueur. Les noms de classes sont en forme interne
 * à barres obliques (« com/example/Foo »), comme dans le pool de
 * constantes.</p>
 */
public final class ClassModel {

    /** Drapeau d'accès « public » du fichier de classe. */
    public static final int ACC_PUBLIC = 0x0001;
    /** Drapeau d'accès « private » du fichier de classe. */
    public static final int ACC_PRIVATE = 0x0002;
    /** Drapeau « static ». */
    public static final int ACC_STATIC = 0x0008;
    /** Drapeau « final ». */
    public static final int ACC_FINAL = 0x0010;
    /** Drapeau « synthetic ». */
    public static final int ACC_SYNTHETIC = 0x1000;

    /** Attribut d'annotations visibles à l'exécution. */
    public static final String RUNTIME_VISIBLE = "RuntimeVisibleAnnotations";
    /** Attribut d'annotations invisibles à l'exécution (rétention classe). */
    public static final String RUNTIME_INVISIBLE = "RuntimeInvisibleAnnotations";
    /** Attribut d'annotations de paramètres visibles à l'exécution. */
    public static final String RUNTIME_VISIBLE_PARAMS =
            "RuntimeVisibleParameterAnnotations";
    /** Attribut d'annotations de paramètres invisibles à l'exécution. */
    public static final String RUNTIME_INVISIBLE_PARAMS =
            "RuntimeInvisibleParameterAnnotations";
    /** Attribut d'annotations de types visibles à l'exécution. */
    public static final String RUNTIME_VISIBLE_TYPES =
            "RuntimeVisibleTypeAnnotations";
    /** Attribut d'annotations de types invisibles à l'exécution. */
    public static final String RUNTIME_INVISIBLE_TYPES =
            "RuntimeInvisibleTypeAnnotations";

    /** Nom binaire à barres obliques, jamais {@code null}. */
    public final String binaryName;
    /** Super-classe à barres obliques, {@code null} pour Object seul. */
    public final String superName;
    /** Interfaces directes à barres obliques, ordonnées, non modifiable. */
    public final List<String> interfaces;
    /** Drapeaux d'accès de la classe. */
    public final int accessFlags;
    /** Signature générique, ou {@code null}. */
    public final String genericSignature;
    /** Nom du fichier source (attribut SourceFile), ou {@code null}. */
    public final String sourceFile;
    /** Champs, dans l'ordre du fichier, non modifiable. */
    public final List<Member> fields;
    /** Méthodes, dans l'ordre du fichier, non modifiable. */
    public final List<Member> methods;
    /** Attributs d'annotations de la classe (bruts), non modifiable. */
    public final List<RawAttribute> annotationAttributes;
    /** Toutes les classes référencées par ce fichier (sur-ensemble
     *  sûr : pool, descripteurs, signatures, annotations). */
    public final java.util.Set<String> referencedClasses;
    /** Valeurs de l'annotation {@code @kotlin.Metadata} de la classe,
     *  ou {@code null} si absente (classe Java par exemple). */
    public final KotlinMetadata kotlinMetadata;

    private ClassModel(String binaryName, String superName,
                       List<String> interfaces, int accessFlags,
                       String genericSignature, String sourceFile,
                       List<Member> fields, List<Member> methods,
                       List<RawAttribute> annotationAttributes,
                       java.util.Set<String> referencedClasses,
                       KotlinMetadata kotlinMetadata) {
        this.binaryName = binaryName;
        this.superName = superName;
        this.interfaces = interfaces;
        this.accessFlags = accessFlags;
        this.genericSignature = genericSignature;
        this.sourceFile = sourceFile;
        this.fields = fields;
        this.methods = methods;
        this.annotationAttributes = annotationAttributes;
        this.referencedClasses = referencedClasses;
        this.kotlinMetadata = kotlinMetadata;
    }

    /** @return le nom binaire en forme pointée (« com.example.Foo ») */
    public String dottedName() {
        return binaryName.replace('/', '.');
    }

    /** @return vrai si la classe est marquée {@code synthetic} */
    public boolean isSynthetic() {
        return (accessFlags & ACC_SYNTHETIC) != 0;
    }

    @Override
    public String toString() {
        return "ClassModel[" + binaryName + ", super="
                + superName + ", members=" + (fields.size() + methods.size())
                + ", refs=" + referencedClasses.size() + "]";
    }

    /**
     * Membre (champ ou méthode) : nom, descripteur, drapeaux,
     * signature générique, valeur constante, clause throws, noms de
     * paramètres et attributs d'annotations bruts.
     */
    public static final class Member {

        /** Nom simple du membre, jamais {@code null}. */
        public final String name;
        /** Descripteur JVMS, jamais {@code null}. */
        public final String descriptor;
        /** Drapeaux d'accès du membre. */
        public final int accessFlags;
        /** Signature générique, ou {@code null}. */
        public final String genericSignature;
        /** Valeur constante (champ {@code static final} uniquement),
         *  sinon {@code null}. */
        public final Object constantValue;
        /** Classes de la clause {@code throws}, à barres obliques. */
        public final List<String> exceptions;
        /** Noms de paramètres (attribut MethodParameters), autant
         *  d'éléments que de paramètres connus, éventuellement vide. */
        public final List<String> parameterNames;
        /** Attributs d'annotations du membre, bruts. */
        public final List<RawAttribute> annotationAttributes;

        Member(String name, String descriptor, int accessFlags,
               String genericSignature, Object constantValue,
               List<String> exceptions, List<String> parameterNames,
               List<RawAttribute> annotationAttributes) {
            this.name = name;
            this.descriptor = descriptor;
            this.accessFlags = accessFlags;
            this.genericSignature = genericSignature;
            this.constantValue = constantValue;
            this.exceptions = exceptions;
            this.parameterNames = parameterNames;
            this.annotationAttributes = annotationAttributes;
        }

        /** @return vrai si le membre est privé (exclu de l'ABI) */
        public boolean isPrivate() {
            return (accessFlags & ACC_PRIVATE) != 0;
        }

        @Override
        public String toString() {
            return name + descriptor;
        }
    }

    /**
     * Attribut brut : nom et octets exacts. Le contenu brut sert de
     * base de comparaison d'ABI (deux attributs égaux octet pour octet
     * donnent la même lecture par un compilateur aval).
     */
    public static final class RawAttribute {

        /** Nom de l'attribut, jamais {@code null}. */
        public final String name;
        /** Octets bruts, jamais {@code null}, jamais modifiés. */
        public final byte[] bytes;

        RawAttribute(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RawAttribute)) {
                return false;
            }
            RawAttribute that = (RawAttribute) other;
            return name.equals(that.name) && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + Arrays.hashCode(bytes);
        }
    }

    /**
     * Valeurs de l'annotation {@code @kotlin.Metadata} : ce qui suffit
     * à relire la structure Kotlin (bibliothèque de métadonnées) ou à
     * hacher l'intégralité des données en repli sûr.
     */
    public static final class KotlinMetadata {

        /** Genre de métadonnées (1 = classe, 2 = façade de fichier…). */
        public final int kind;
        /** Version du format de métadonnées. */
        public final int[] metadataVersion;
        /** Version de bytecode attendue par la métadonnée. */
        public final int[] bytecodeVersion;
        /** Données sérialisées (premier lot, protobuf compacté). */
        public final String[] data1;
        /** Données sérialisées (second lot, chaînes). */
        public final String[] data2;

        KotlinMetadata(int kind, int[] metadataVersion, int[] bytecodeVersion,
                       String[] data1, String[] data2) {
            this.kind = kind;
            this.metadataVersion = metadataVersion;
            this.bytecodeVersion = bytecodeVersion;
            this.data1 = data1;
            this.data2 = data2;
        }
    }

    /** Assemble le modèle immuable (réservé au lecteur). */
    static ClassModel of(String binaryName, String superName,
                         List<String> interfaces, int accessFlags,
                         String genericSignature, String sourceFile,
                         List<Member> fields, List<Member> methods,
                         List<RawAttribute> annotationAttributes,
                         java.util.Set<String> referencedClasses,
                         KotlinMetadata kotlinMetadata) {
        return new ClassModel(binaryName, superName,
                Collections.unmodifiableList(interfaces), accessFlags,
                genericSignature, sourceFile,
                Collections.unmodifiableList(fields),
                Collections.unmodifiableList(methods),
                Collections.unmodifiableList(annotationAttributes),
                Collections.unmodifiableSet(referencedClasses),
                kotlinMetadata);
    }
}
