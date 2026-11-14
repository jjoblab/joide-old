package jo.joide.buildlogic;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecture du pool de constantes d'un fichier de classe compilé
 * (ADR-013) : références de classes et de membres, forme utilisée par le
 * contrôle API 28.
 *
 * <p>Ne lit que le pool (aucune traversée des champs/méthodes déclarés) :
 * le contrôle porte sur ce que la classe référence, pas sur ce qu'elle
 * déclare. Un échec de lecture est une erreur, jamais un silence (§4.2).</p>
 */
public final class ClassFileReferences {

    /** Référence extraite : genre, propriétaire, nom, descripteur. */
    public static final class Ref {
        public final char kind;   // 'C' classe, 'M' méthode, 'F' champ
        public final String owner;
        public final String name; // null pour 'C'
        public final String descriptor; // null pour 'C'

        Ref(char kind, String owner, String name, String descriptor) {
            this.kind = kind;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        /** @return la forme « M:owner.name:desc » du fichier de signatures */
        public String signature() {
            if (kind == 'C') {
                return "C:" + owner;
            }
            return kind + ":" + owner + "." + name + ":" + descriptor;
        }
    }

    private ClassFileReferences() {
    }

    /**
     * Extrait les références du pool de constantes.
     *
     * @param bytes le contenu exact du fichier .class
     * @return les références (classes, méthodes, champs)
     * @throws IOException si le fichier n'est pas une classe valide
     */
    public static List<Ref> read(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("not a class file (bad magic)");
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        // Entrée 0 virtuelle ; Long et Double occupent deux emplacements.
        // Les références du pool peuvent pointer EN AVANT (javac émet les
        // Utf8 après leurs utilisateurs) : la passe 1 ne fait que lire
        // les entrées brutes, la résolution suit une fois le pool complet.
        String[] utf8 = new String[count];
        int[] classIndex = new int[count];
        int[] nameAndTypeIndex = new int[count];
        char[] tagOf = new char[count];
        int[] classRefNameSlot = new int[count]; // pour CONSTANT_Class
        int[] ntNameSlot = new int[count];       // NameAndType : nom
        int[] ntDescSlot = new int[count];       // NameAndType : descripteur
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            tagOf[i] = (char) tag;
            switch (tag) {
                case 1: { // Utf8
                    int len = in.readUnsignedShort();
                    byte[] data = new byte[len];
                    in.readFully(data);
                    // UTF-8 « modifié » : pour les noms ASCII (java/javax,
                    // descripteurs) le décodage UTF-8 strict est exact ;
                    // un nom exotique est remplacé, jamais silencieux pour
                    // les préfixes contrôlés.
                    utf8[i] = new String(data, StandardCharsets.UTF_8);
                    break;
                }
                case 3: case 4: // Integer, Float
                    in.readInt();
                    break;
                case 5: case 6: // Long, Double : deux emplacements
                    in.readLong();
                    i++;
                    break;
                case 7: // Class
                    classRefNameSlot[i] = in.readUnsignedShort();
                    break;
                case 8: // String
                    in.readUnsignedShort();
                    break;
                case 9: case 10: case 11: // Fieldref, Methodref, InterfaceMethodref
                    classIndex[i] = in.readUnsignedShort();
                    nameAndTypeIndex[i] = in.readUnsignedShort();
                    break;
                case 12: // NameAndType
                    ntNameSlot[i] = in.readUnsignedShort();
                    ntDescSlot[i] = in.readUnsignedShort();
                    break;
                case 15: // MethodHandle
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                    break;
                case 16: // MethodType
                    in.readUnsignedShort();
                    break;
                case 17: case 18: // Dynamic, InvokeDynamic
                    in.readUnsignedShort();
                    in.readUnsignedShort();
                    break;
                case 19: case 20: // Module, Package
                    in.readUnsignedShort();
                    break;
                default:
                    throw new IOException("unknown constant pool tag " + tag);
            }
        }
        List<Ref> refs = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            char tag = tagOf[i];
            if (tag == 7) {
                refs.add(new Ref('C', utf8At(utf8, classRefNameSlot[i]),
                        null, null));
            } else if (tag == 9 || tag == 10 || tag == 11) {
                // Résolution une fois le pool complet (références en avant).
                String ownerName = utf8At(utf8,
                        classRefNameSlot[classIndex[i]]);
                int nt = nameAndTypeIndex[i];
                String name = utf8At(utf8, ntNameSlot[nt]);
                String desc = utf8At(utf8, ntDescSlot[nt]);
                refs.add(new Ref(tag == 9 ? 'F' : 'M', ownerName, name, desc));
            }
        }
        return refs;
    }

    private static String utf8At(String[] utf8, int i) throws IOException {
        if (i <= 0 || i >= utf8.length || utf8[i] == null) {
            throw new IOException("invalid constant pool index " + i);
        }
        return utf8[i];
    }
}
