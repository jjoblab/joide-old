package jo.joide.build.jvm.classfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jo.joide.platform.Hashing;

/**
 * Lecteur de fichiers de classe, sans dépendance : pool de constantes
 * complet, membres, attributs utiles à l'ABI et à la navigation
 * (Signature, SourceFile, ConstantValue, Exceptions, MethodParameters,
 * annotations des quatre rétentions).
 *
 * <p>Tolérance de lecture : un fichier non conforme lève
 * {@link ClassFileException} — l'appelant (pipeline) transforme
 * l'échec en diagnostic explicite, jamais en état silencieux. Les
 * références extraites forment un sur-ensemble sûr : tout le pool
 * (donc aussi les corps de méthode : appels, champs, instances,
 * exceptions attrapées), descripteurs, signatures génériques et arbres
 * d'annotations.</p>
 */
public final class ClassFileReader {

    private static final int MAGIC = 0xCAFEBABE;

    private static final int CONSTANT_UTF8 = 1;
    private static final int CONSTANT_INTEGER = 3;
    private static final int CONSTANT_FLOAT = 4;
    private static final int CONSTANT_LONG = 5;
    private static final int CONSTANT_DOUBLE = 6;
    private static final int CONSTANT_CLASS = 7;
    private static final int CONSTANT_STRING = 8;
    private static final int CONSTANT_FIELDREF = 9;
    private static final int CONSTANT_METHODREF = 10;
    private static final int CONSTANT_INTERFACE_METHODREF = 11;
    private static final int CONSTANT_NAME_AND_TYPE = 12;
    private static final int CONSTANT_METHOD_HANDLE = 15;
    private static final int CONSTANT_METHOD_TYPE = 16;
    private static final int CONSTANT_DYNAMIC = 17;
    private static final int CONSTANT_INVOKE_DYNAMIC = 18;
    private static final int CONSTANT_MODULE = 19;
    private static final int CONSTANT_PACKAGE = 20;

    private final DataInputStream in;
    private final Object[] pool;
    private final int[] poolTags;
    private final Set<String> refs = new LinkedHashSet<>();
    private String fileDigest = "";

    private ClassFileReader(DataInputStream in, int count) {
        this.in = in;
        this.pool = new Object[count];
        this.poolTags = new int[count];
    }

    /**
     * Lit un fichier de classe complet.
     *
     * @param bytes octets du fichier {@code .class}, jamais {@code null}
     * @return le modèle immuable
     * @throws ClassFileException si les octets ne sont pas un fichier
     *                            de classe valide
     */
    public static ClassModel read(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(
                    new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC) {
                throw new ClassFileException("not a class file (bad magic)");
            }
            in.readUnsignedShort(); // version mineure
            in.readUnsignedShort(); // version majeure
            ClassFileReader reader = new ClassFileReader(
                    in, in.readUnsignedShort());
            // condensé du fichier entier : sert d'ABI aux classes à
            // membres inline, dont les corps copiés chez les appelants
            // ne figurent ni dans les métadonnées ni dans le rendu
            reader.fileDigest = Hashing.toHex(Hashing.sha256(bytes))
                    .substring(0, 32);
            reader.readPool();
            return reader.readBody();
        } catch (ClassFileException e) {
            throw e;
        } catch (IOException e) {
            throw new ClassFileException("truncated class file", e);
        }
    }

    private void readPool() throws IOException {
        int i = 1;
        while (i < pool.length) {
            int tag = in.readUnsignedByte();
            poolTags[i] = tag;
            switch (tag) {
                case CONSTANT_UTF8:
                    int len = in.readUnsignedShort();
                    byte[] data = new byte[len];
                    in.readFully(data);
                    pool[i] = decodeModifiedUtf8(data);
                    break;
                case CONSTANT_INTEGER:
                    pool[i] = Integer.valueOf(in.readInt());
                    break;
                case CONSTANT_FLOAT:
                    pool[i] = Float.valueOf(in.readFloat());
                    break;
                case CONSTANT_LONG:
                    pool[i] = Long.valueOf(in.readLong());
                    i++; // occupe deux emplacements
                    break;
                case CONSTANT_DOUBLE:
                    pool[i] = Double.valueOf(in.readDouble());
                    i++; // occupe deux emplacements
                    break;
                case CONSTANT_CLASS:
                case CONSTANT_STRING:
                case CONSTANT_METHOD_TYPE:
                case CONSTANT_MODULE:
                case CONSTANT_PACKAGE:
                    pool[i] = PoolRef.of(in.readUnsignedShort());
                    break;
                case CONSTANT_FIELDREF:
                case CONSTANT_METHODREF:
                case CONSTANT_INTERFACE_METHODREF:
                case CONSTANT_NAME_AND_TYPE:
                case CONSTANT_DYNAMIC:
                case CONSTANT_INVOKE_DYNAMIC:
                    pool[i] = PoolRef.of(in.readUnsignedShort(),
                            in.readUnsignedShort());
                    break;
                case CONSTANT_METHOD_HANDLE:
                    in.readUnsignedByte();
                    pool[i] = PoolRef.of(in.readUnsignedShort());
                    break;
                default:
                    throw new ClassFileException(
                            "unknown constant pool tag " + tag);
            }
            i++;
        }
        collectPoolReferences();
    }

    /**
     * Références de tout le pool : entrées de classes, propriétaires
     * et descripteurs des références de membres (couvre donc les
     * corps de méthode : appels, accès aux champs, instances,
     * exceptions attrapées) et descripteurs de type de méthode.
     */
    private void collectPoolReferences() {
        for (int i = 1; i < pool.length; i++) {
            int tag = poolTags[i];
            if (tag == 0) {
                continue; // second emplacement d'un long/double
            }
            if (tag == CONSTANT_CLASS) {
                refs.add(utf8(((PoolRef) pool[i]).first));
            } else if (tag == CONSTANT_FIELDREF || tag == CONSTANT_METHODREF
                    || tag == CONSTANT_INTERFACE_METHODREF
                    || tag == CONSTANT_DYNAMIC
                    || tag == CONSTANT_INVOKE_DYNAMIC) {
                PoolRef ref = (PoolRef) pool[i];
                if (poolTags[ref.first] == CONSTANT_CLASS) {
                    refs.add(utf8(((PoolRef) pool[ref.first]).first));
                }
                if (poolTags[ref.second] == CONSTANT_NAME_AND_TYPE) {
                    collectFromDescriptor(nameAndTypeDescriptor(ref.second));
                }
            } else if (tag == CONSTANT_NAME_AND_TYPE) {
                collectFromDescriptor(nameAndTypeDescriptor(i));
            } else if (tag == CONSTANT_METHOD_TYPE) {
                collectFromDescriptor(
                        utf8(((PoolRef) pool[i]).first));
            }
        }
    }

    private String nameAndTypeDescriptor(int index) {
        PoolRef ref = (PoolRef) pool[index];
        return utf8(ref.second);
    }

    private ClassModel readBody() throws IOException {
        int accessFlags = in.readUnsignedShort();
        String thisClass = className(in.readUnsignedShort());
        int superIndex = in.readUnsignedShort();
        String superName = superIndex == 0 ? null : className(superIndex);
        int ifaces = in.readUnsignedShort();
        List<String> interfaces = new ArrayList<>(ifaces);
        for (int i = 0; i < ifaces; i++) {
            interfaces.add(className(in.readUnsignedShort()));
        }
        List<ClassModel.Member> fields = readMembers();
        List<ClassModel.Member> methods = readMembers();
        String genericSignature = null;
        String sourceFile = null;
        List<ClassModel.RawAttribute> classAnnots = new ArrayList<>();
        ClassModel.KotlinMetadata kotlinMetadata = null;
        int attrs = in.readUnsignedShort();
        for (int i = 0; i < attrs; i++) {
            String name = utf8(in.readUnsignedShort());
            int len = in.readInt();
            byte[] data = new byte[len];
            in.readFully(data);
            if ("Signature".equals(name)) {
                genericSignature = utf8(readIndex(data, 0));
                collectFromSignature(genericSignature);
            } else if ("SourceFile".equals(name)) {
                sourceFile = utf8(readIndex(data, 0));
            } else if (isAnnotationAttribute(name)) {
                classAnnots.add(new ClassModel.RawAttribute(name, data));
                collectFromAttribute(name, data);
                if (ClassModel.RUNTIME_VISIBLE.equals(name)) {
                    kotlinMetadata = readKotlinMetadata(data);
                }
            } else if ("InnerClasses".equals(name)) {
                collectFromInnerClasses(data);
            }
        }
        return ClassModel.of(thisClass, superName, interfaces, accessFlags,
                genericSignature, sourceFile, fileDigest, fields, methods,
                classAnnots, refs, kotlinMetadata);
    }

    private List<ClassModel.Member> readMembers() throws IOException {
        int count = in.readUnsignedShort();
        List<ClassModel.Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int flags = in.readUnsignedShort();
            String name = utf8(in.readUnsignedShort());
            String descriptor = utf8(in.readUnsignedShort());
            collectFromDescriptor(descriptor);
            String signature = null;
            Object constantValue = null;
            List<String> exceptions = new ArrayList<>(2);
            List<String> parameterNames = new ArrayList<>(4);
            List<ClassModel.RawAttribute> annots = new ArrayList<>(2);
            int attrs = in.readUnsignedShort();
            for (int a = 0; a < attrs; a++) {
                String attrName = utf8(in.readUnsignedShort());
                int len = in.readInt();
                byte[] data = new byte[len];
                in.readFully(data);
                switch (attrName) {
                    case "Signature":
                        signature = utf8(readIndex(data, 0));
                        collectFromSignature(signature);
                        break;
                    case "ConstantValue":
                        constantValue = poolValue(readIndex(data, 0));
                        break;
                    case "Exceptions":
                        DataInputStream ex = new DataInputStream(
                                new ByteArrayInputStream(data));
                        int exCount = ex.readUnsignedShort();
                        for (int e = 0; e < exCount; e++) {
                            exceptions.add(className(ex.readUnsignedShort()));
                        }
                        break;
                    case "MethodParameters":
                        DataInputStream mp = new DataInputStream(
                                new ByteArrayInputStream(data));
                        int paramCount = mp.readUnsignedByte();
                        for (int p = 0; p < paramCount; p++) {
                            int nameIndex = mp.readUnsignedShort();
                            mp.readUnsignedShort(); // drapeaux, non lus
                            parameterNames.add(nameIndex == 0
                                    ? null : utf8(nameIndex));
                        }
                        break;
                    default:
                        if (isAnnotationAttribute(attrName)) {
                            annots.add(
                                    new ClassModel.RawAttribute(attrName, data));
                            collectFromAttribute(attrName, data);
                        }
                        break;
                }
            }
            members.add(new ClassModel.Member(name, descriptor, flags,
                    signature, constantValue, exceptions, parameterNames,
                    annots));
        }
        return members;
    }

    private static boolean isAnnotationAttribute(String name) {
        return ClassModel.RUNTIME_VISIBLE.equals(name)
                || ClassModel.RUNTIME_INVISIBLE.equals(name)
                || ClassModel.RUNTIME_VISIBLE_PARAMS.equals(name)
                || ClassModel.RUNTIME_INVISIBLE_PARAMS.equals(name)
                || ClassModel.RUNTIME_VISIBLE_TYPES.equals(name)
                || ClassModel.RUNTIME_INVISIBLE_TYPES.equals(name);
    }

    /** InnerClasses : les classes internes citées sont des références. */
    private void collectFromInnerClasses(byte[] data) {
        try {
            DataInputStream inner = new DataInputStream(
                    new ByteArrayInputStream(data));
            int count = inner.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int innerIndex = inner.readUnsignedShort();
                inner.readUnsignedShort(); // externe
                inner.readUnsignedShort(); // nom
                inner.readUnsignedShort(); // drapeaux d'accès
                if (innerIndex != 0) {
                    refs.add(className(innerIndex));
                }
            }
        } catch (IOException e) {
            throw new ClassFileException("bad InnerClasses attribute", e);
        }
    }

    /**
     * Parcourt un attribut d'annotations selon sa grammaire exacte :
     * annotations simples (u2 puis annotations), annotations de
     * paramètres (u1 paramètres, puis par paramètre u2 annotations),
     * annotations de types (u2 annotations, chacune précédée de sa
     * cible et de son chemin de type).
     */
    private void collectFromAttribute(String name, byte[] data) {
        try {
            DataInputStream ann = new DataInputStream(
                    new ByteArrayInputStream(data));
            if (ClassModel.RUNTIME_VISIBLE_PARAMS.equals(name)
                    || ClassModel.RUNTIME_INVISIBLE_PARAMS.equals(name)) {
                int paramCount = ann.readUnsignedByte();
                for (int p = 0; p < paramCount; p++) {
                    int count = ann.readUnsignedShort();
                    for (int i = 0; i < count; i++) {
                        skipAnnotation(ann);
                    }
                }
            } else if (ClassModel.RUNTIME_VISIBLE.equals(name)
                    || ClassModel.RUNTIME_INVISIBLE.equals(name)) {
                int count = ann.readUnsignedShort();
                for (int i = 0; i < count; i++) {
                    skipAnnotation(ann);
                }
            } else {
                int count = ann.readUnsignedShort();
                for (int i = 0; i < count; i++) {
                    skipTypeAnnotationTarget(ann);
                    skipAnnotation(ann);
                }
            }
        } catch (IOException e) {
            throw new ClassFileException(
                    "bad annotation attribute " + name, e);
        }
    }

    /**
     * Consomme la cible et le chemin de type d'une annotation de type
     * (JVMS 4.7.20) : la longueur dépend du type de cible.
     */
    private void skipTypeAnnotationTarget(DataInputStream ann)
            throws IOException {
        int targetType = ann.readUnsignedByte();
        switch (targetType) {
            case 0x00: // paramètre de type de classe
            case 0x01: // paramètre de type de méthode
                ann.readUnsignedByte();
                break;
            case 0x10: // super-type
                ann.readUnsignedShort();
                break;
            case 0x11: // borne de paramètre de type de classe
            case 0x12: // borne de paramètre de type de méthode
                ann.readUnsignedByte();
                ann.readUnsignedByte();
                break;
            case 0x13: // type reçu
            case 0x14: // type du champ
            case 0x15: // type retourné
            case 0x16: // paramètre formel
                ann.readUnsignedByte();
                break;
            case 0x17: // clause throws
                ann.readUnsignedShort();
                break;
            case 0x40: // variable locale
            case 0x41: // variable locale de ressource
                int table = ann.readUnsignedShort();
                for (int i = 0; i < table; i++) {
                    ann.readUnsignedShort();
                    ann.readUnsignedShort();
                    ann.readUnsignedShort();
                }
                break;
            case 0x42: // clause catch
                ann.readUnsignedShort();
                break;
            case 0x43: // instanceof
            case 0x44: // new
            case 0x45: // ::new
            case 0x46: // ::method
                ann.readUnsignedShort();
                break;
            default: // 0x47 à 0x4B : argument de type
                ann.readUnsignedShort();
                ann.readUnsignedByte();
                break;
        }
        int pathLength = ann.readUnsignedByte();
        for (int i = 0; i < pathLength; i++) {
            ann.readUnsignedByte();
            ann.readUnsignedByte();
        }
    }

    /**
     * Consomme une annotation complète (type + paires nom-valeur) et
     * enregistre le type comme référence.
     */
    private void skipAnnotation(DataInputStream ann) throws IOException {
        String type = utf8(ann.readUnsignedShort());
        collectFromDescriptor(type);
        int pairs = ann.readUnsignedShort();
        for (int i = 0; i < pairs; i++) {
            ann.readUnsignedShort(); // nom de l'élément
            skipElementValue(ann);
        }
    }

    /** Consomme une valeur d'élément, en collectant les classes. */
    private void skipElementValue(DataInputStream ann) throws IOException {
        int tag = ann.readUnsignedByte();
        switch (tag) {
            case 'e': // constante d'énumération : classe + nom
                collectFromDescriptor(utf8(ann.readUnsignedShort()));
                ann.readUnsignedShort();
                break;
            case 'c': // valeur de classe : descripteur
                collectFromDescriptor(utf8(ann.readUnsignedShort()));
                break;
            case '@': // annotation imbriquée
                skipAnnotation(ann);
                break;
            case '[': // tableau de valeurs
                int n = ann.readUnsignedShort();
                for (int i = 0; i < n; i++) {
                    skipElementValue(ann);
                }
                break;
            default:
                // 'B' 'C' 'D' 'F' 'I' 'J' 'S' 'Z' 's' : index de constante
                ann.readUnsignedShort();
                break;
        }
    }

    /** Extrait les classes d'un descripteur de champ, méthode ou annotation. */
    private void collectFromDescriptor(String descriptor) {
        if (descriptor == null) {
            return;
        }
        int i = 0;
        int n = descriptor.length();
        while (i < n) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end < 0) {
                    return;
                }
                refs.add(descriptor.substring(i + 1, end));
                i = end + 1;
            } else {
                i++;
            }
        }
    }

    /**
     * Extrait les classes d'une signature générique : les types concrets
     * « Lnom ; » sont collectés (segments internes séparés par « . »
     * collectés chacun), les variables de type « Tnom ; » sont ignorées.
     */
    private void collectFromSignature(String signature) {
        if (signature == null) {
            return;
        }
        int i = 0;
        int n = signature.length();
        while (i < n) {
            char c = signature.charAt(i);
            if (c != 'L') {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < n) {
                char d = signature.charAt(j);
                if (d == ';' || d == '<' || d == '.') {
                    break;
                }
                j++;
            }
            if (j >= n) {
                return;
            }
            refs.add(signature.substring(i + 1, j));
            i = j + 1;
        }
    }

    /**
     * Cherche l'annotation {@code @kotlin/Metadata} dans un attribut
     * d'annotations visibles à l'exécution et en extrait les valeurs
     * (noms de membres abrégés « k », « mv », « d1 », « d2 »).
     */
    private ClassModel.KotlinMetadata readKotlinMetadata(byte[] data) {
        try {
            DataInputStream ann = new DataInputStream(
                    new ByteArrayInputStream(data));
            int count = ann.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                String type = utf8(ann.readUnsignedShort());
                boolean isMetadata = "Lkotlin/Metadata;".equals(type);
                int kind = 0;
                int[] metadataVersion = new int[0];
                int[] bytecodeVersion = new int[0];
                String[] data1 = new String[0];
                String[] data2 = new String[0];
                int pairs = ann.readUnsignedShort();
                for (int p = 0; p < pairs; p++) {
                    String elementName = utf8(ann.readUnsignedShort());
                    Object value = readElementValue(ann);
                    if (!isMetadata || value == null) {
                        continue;
                    }
                    if ("k".equals(elementName)
                            && value instanceof Integer) {
                        kind = (Integer) value;
                    } else if ("mv".equals(elementName)
                            && value instanceof int[]) {
                        metadataVersion = (int[]) value;
                    } else if ("bv".equals(elementName)
                            && value instanceof int[]) {
                        bytecodeVersion = (int[]) value;
                    } else if ("d1".equals(elementName)
                            && value instanceof String[]) {
                        data1 = (String[]) value;
                    } else if ("d2".equals(elementName)
                            && value instanceof String[]) {
                        data2 = (String[]) value;
                    }
                }
                if (isMetadata) {
                    return new ClassModel.KotlinMetadata(kind,
                            metadataVersion, bytecodeVersion, data1, data2);
                }
            }
            return null;
        } catch (IOException e) {
            throw new ClassFileException(
                    "bad RuntimeVisibleAnnotations attribute", e);
        }
    }

    /**
     * Lit une valeur d'élément et la retourne : {@link Integer},
     * {@link Long}, {@link Float}, {@link Double}, {@link String},
     * {@code int[]} ou {@code String[]} selon le tag.
     */
    private Object readElementValue(DataInputStream ann) throws IOException {
        int tag = ann.readUnsignedByte();
        switch (tag) {
            case 'e': // constante d'énumération
                utf8(ann.readUnsignedShort());
                utf8(ann.readUnsignedShort());
                return "enum";
            case 'c': // valeur de classe
                return "class:" + utf8(ann.readUnsignedShort());
            case '@': // annotation imbriquée
                readNestedAnnotation(ann);
                return "annotation";
            case '[': {
                int n = ann.readUnsignedShort();
                Object[] items = new Object[n];
                boolean allStrings = true;
                boolean allInts = true;
                for (int i = 0; i < n; i++) {
                    items[i] = readElementValue(ann);
                    allStrings &= items[i] instanceof String
                            && ((String) items[i]).startsWith("s:");
                    allInts &= items[i] instanceof Integer;
                }
                if (allStrings) {
                    String[] values = new String[n];
                    for (int i = 0; i < n; i++) {
                        values[i] = ((String) items[i]).substring(2);
                    }
                    return values;
                }
                if (allInts) {
                    int[] values = new int[n];
                    for (int i = 0; i < n; i++) {
                        values[i] = (Integer) items[i];
                    }
                    return values;
                }
                return items;
            }
            case 'B':
            case 'C':
            case 'S':
            case 'I':
            case 'Z':
                return Integer.valueOf(
                        ((Integer) poolValue(ann.readUnsignedShort()))
                                .intValue());
            case 'J':
                return (Long) poolValue(ann.readUnsignedShort());
            case 'F':
                return (Float) poolValue(ann.readUnsignedShort());
            case 'D':
                return (Double) poolValue(ann.readUnsignedShort());
            case 's':
                return "s:" + utf8(ann.readUnsignedShort());
            default:
                throw new ClassFileException(
                        "unknown element value tag " + (char) tag);
        }
    }

    /** Consomme une annotation imbriquée sans en garder les valeurs. */
    private void readNestedAnnotation(DataInputStream ann)
            throws IOException {
        utf8(ann.readUnsignedShort());
        int pairs = ann.readUnsignedShort();
        for (int i = 0; i < pairs; i++) {
            utf8(ann.readUnsignedShort());
            readElementValue(ann);
        }
    }

    private String utf8(int index) {
        if (index <= 0 || index >= pool.length || poolTags[index] == 0) {
            throw new ClassFileException(
                    "invalid constant pool index " + index);
        }
        if (poolTags[index] != CONSTANT_UTF8) {
            throw new ClassFileException(
                    "constant " + index + " is not a Utf8 entry");
        }
        return (String) pool[index];
    }

    private String className(int index) {
        Object value = poolEntry(index, CONSTANT_CLASS);
        int nameIndex = ((PoolRef) value).first;
        return utf8(nameIndex);
    }

    private Object poolValue(int index) {
        if (index <= 0 || index >= pool.length || poolTags[index] == 0) {
            throw new ClassFileException(
                    "invalid constant pool index " + index);
        }
        Object value = pool[index];
        if (poolTags[index] == CONSTANT_STRING && value instanceof PoolRef) {
            // valeur constante de type chaîne : résolue, jamais le
            // PoolRef brut (dont l'identité mémoire rendrait l'ABI
            // non déterministe)
            return utf8(((PoolRef) value).first);
        }
        return value;
    }

    private Object poolEntry(int index, int expectedTag) {
        if (index <= 0 || index >= pool.length || poolTags[index] == 0) {
            throw new ClassFileException(
                    "invalid constant pool index " + index);
        }
        if (poolTags[index] != expectedTag) {
            throw new ClassFileException("constant " + index
                    + " has unexpected tag " + poolTags[index]);
        }
        return pool[index];
    }

    private static int readIndex(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /**
     * Décode une entrée Utf8 du pool selon l'UTF-8 modifié du format
     * de classe (JVMS 4.4.7) : l'octet nul s'écrit « C0 80 » et les
     * caractères supplémentaires en paires de substituts — un décodeur
     * UTF-8 standard les remplacerait ou les casserait. Les chaînes
     * de métadonnées Kotlin dépendent de ce décodage exact.
     */
    private static String decodeModifiedUtf8(byte[] data) {
        try {
            byte[] prefixed = new byte[data.length + 2];
            prefixed[0] = (byte) (data.length >>> 8);
            prefixed[1] = (byte) data.length;
            System.arraycopy(data, 0, prefixed, 2, data.length);
            DataInputStream utf = new DataInputStream(
                    new ByteArrayInputStream(prefixed));
            return utf.readUTF();
        } catch (IOException e) {
            // séquence réellement invalide : repli défensif sur le
            // décodage standard (le fichier est déjà douteux)
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /** Référence du pool à un ou deux index. */
    private static final class PoolRef {
        final int first;
        final int second;

        private PoolRef(int first, int second) {
            this.first = first;
            this.second = second;
        }

        static PoolRef of(int first) {
            return new PoolRef(first, 0);
        }

        static PoolRef of(int first, int second) {
            return new PoolRef(first, second);
        }
    }
}
