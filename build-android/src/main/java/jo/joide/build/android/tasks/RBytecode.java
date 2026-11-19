package jo.joide.build.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabrication du jar des classes R en BYTECODE direct — la manière
 * AGP ({@code R.jar}) et la doctrine éprouvée
 * ({@code RBytecodeGenerator}) : un R n'est que des constantes
 * {@code public static final int} et des tableaux {@code int[]}
 * ({@code styleable}) — le compiler n'a RIEN à leur apporter, et le
 * lui donner coûte un carnet de bindings entier et, sur appareil,
 * une chaîne d'erreurs ART (le dex ne transporte pas les resources :
 * le bundle de messages d'ecj disparaît au dexage —
 * « ExceptionInInitializerError: Missing message » ; compiler le
 * R.java n'était donc plus tenable).
 *
 * <p>Le parseur est orienté ligne parce que la sortie aapt2 est
 * rigide et générée par machine : {@code package ...;}, les classes
 * nichées {@code public static [final] class <type> {}}, les champs
 * {@code public static [final] int <nom>=0x...;} et les tableaux
 * {@code public static [final] int[] <nom> = { 0x..., ... };} (les
 * valeurs peuvent s'étendre sur plusieurs lignes). Les lignes et les
 * jetons non reconnus sont SAUTÉS plutôt que fataux : un symbole
 * malformé coûte un champ irrésoluble, une exception coûte tout le
 * build (le contrat éprouvé).</p>
 *
 * <p>L'émission écrit les class files À LA MAIN (aucune dépendance :
 * version 52 = V1_8, pool de constantes internée en DEUX phases —
 * tout référencement d'abord, sérialisation ensuite, le compte
 * d'entrées n'ayant plus le droit de bouger pendant l'écriture) :
 * un champ {@code final} porte un attribut {@code ConstantValue}
 * (inlinable par les compilateurs aval — {@code switch} sur un id) ;
 * un champ non final et TOUS les tableaux {@code int[]} sont posés
 * en {@code <clinit>} (un tableau n'est jamais une constante) ; le
 * {@code static} des classes nichées ne vit QUE dans l'attribut
 * {@code InnerClasses}, jamais dans les access_flags de la classe
 * elle-même.</p>
 */
final class RBytecode {

    private RBytecode() {
        // utilitaire statique
    }

    /** Un champ entier du R : nom, valeur, et finalité de la source. */
    record IntR(String nom, int valeur, boolean constant) {
    }

    /** Un champ tableau ({@code styleable}) : nom, valeurs, finalité. */
    record TableauR(String nom, int[] valeurs, boolean constant) {
    }

    /** Une classe nichée du R ({@code id}, {@code styleable}, …). */
    static final class TypeR {
        final String nom;
        final List<IntR> ints = new ArrayList<>();
        final List<TableauR> tableaux = new ArrayList<>();

        TypeR(String nom) {
            this.nom = nom;
        }
    }

    /** Le R d'un paquet : le paquet et ses classes nichées. */
    static final class ModeleR {
        String paquet = "";
        final List<TypeR> types = new ArrayList<>();
    }

    /**
     * Parse un R.java d'aapt2 en modèle. Le paquet reste vide si le
     * texte n'en déclare pas (fichier non exploitable — l'appelant
     * saute).
     */
    static ModeleR parse(String texte) {
        ModeleR modele = new ModeleR();
        TypeR courant = null;
        String nomTableau = null;
        boolean tableauConstant = false;
        List<Integer> valeurs = new ArrayList<>();
        for (String brut : texte.split("\n", -1)) {
            String ligne = brut.trim();
            if (nomTableau != null) {
                // dans un littéral int[] multi-lignes : accumuler
                // jusqu'à l'accolade fermante
                collecter(valeurs, avant(ligne, '}'));
                if (ligne.indexOf('}') >= 0) {
                    if (courant != null) {
                        courant.tableaux.add(new TableauR(nomTableau,
                                enTableau(valeurs), tableauConstant));
                    }
                    nomTableau = null;
                    valeurs.clear();
                }
                continue;
            }
            if (ligne.startsWith("package ")) {
                String suite = ligne.substring(8);
                int fin = suite.indexOf(';');
                modele.paquet = (fin >= 0 ? suite.substring(0, fin)
                        : suite).trim();
                continue;
            }
            boolean statiquePublic = ligne.startsWith("public static");
            if (statiquePublic && ligne.contains(" class ")) {
                String nom = avant(apres(ligne, " class "), '{').trim();
                courant = new TypeR(nom);
                modele.types.add(courant);
                continue;
            }
            if (statiquePublic && ligne.contains("int[] ")) {
                tableauConstant = avant(ligne, "int[]").contains("final");
                String reste = apres(ligne, "int[] ");
                nomTableau = avant(reste, '=').trim();
                valeurs.clear();
                int ouvrante = reste.indexOf('{');
                if (ouvrante >= 0) {
                    String apres = reste.substring(ouvrante + 1);
                    collecter(valeurs, avant(apres, '}'));
                    if (apres.indexOf('}') >= 0) {
                        if (courant != null) {
                            courant.tableaux.add(new TableauR(nomTableau,
                                    enTableau(valeurs), tableauConstant));
                        }
                        nomTableau = null;
                        valeurs.clear();
                    }
                }
                continue;
            }
            if (statiquePublic && ligne.contains("int ")
                    && courant != null) {
                boolean constant = avant(ligne, "int ").contains("final");
                String reste = apres(ligne, "int ");
                int fin = reste.indexOf(';');
                if (fin >= 0) {
                    reste = reste.substring(0, fin);
                }
                int egale = reste.indexOf('=');
                if (egale < 0) {
                    continue;
                }
                String nom = reste.substring(0, egale).trim();
                Integer valeur = decoder(reste.substring(egale + 1));
                if (!nom.isEmpty() && valeur != null) {
                    courant.ints.add(new IntR(nom, valeur, constant));
                }
                continue;
            }
            // ligne étrangère (en-tête, javadoc, accolade, ctor) : sautée
        }
        return modele;
    }

    /** Le texte avant la première occurrence de {@code jalon} (ou tout). */
    private static String avant(String texte, char jalon) {
        int i = texte.indexOf(jalon);
        return i >= 0 ? texte.substring(0, i) : texte;
    }

    private static String avant(String texte, String jalon) {
        int i = texte.indexOf(jalon);
        return i >= 0 ? texte.substring(0, i) : texte;
    }

    /** Le texte après la première occurrence de {@code jalon} (ou ""). */
    private static String apres(String texte, String jalon) {
        int i = texte.indexOf(jalon);
        return i >= 0 ? texte.substring(i + jalon.length()) : "";
    }

    private static void collecter(List<Integer> sink, String texte) {
        for (String jeton : texte.split(",")) {
            Integer v = decoder(jeton);
            if (v != null) {
                sink.add(v);
            }
        }
    }

    private static int[] enTableau(List<Integer> valeurs) {
        return valeurs.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Les ids sont en hexadécimal non signé (0x7f…), les indices en
     *  décimal ; un jeton qui refuse de décoder est sauté (nul). */
    private static Integer decoder(String jeton) {
        String t = jeton.trim();
        while (!t.isEmpty() && t.charAt(t.length() - 1) == ',') {
            t = t.substring(0, t.length() - 1).trim();
        }
        if (t.isEmpty()) {
            return null;
        }
        try {
            return (int) Long.decode(t).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Émet les class files du modèle sous {@code classesDir} (l'arbre
     * du paquet) — la même sortie que la compilation d'antan, donc le
     * jar déterministe de {@link JarWriter} la prend tel quel.
     *
     * @return le nombre de classes écrites (l'R externe + ses nichées)
     */
    static int emettre(ModeleR modele, Path classesDir) throws IOException {
        if (modele.paquet.isEmpty()) {
            return 0;
        }
        String interne = modele.paquet.replace('.', '/') + "/R";
        ecrire(classesDir, interne, classeR(modele, interne));
        for (TypeR t : modele.types) {
            String nomInterne = interne + "$" + t.nom;
            ecrire(classesDir, nomInterne, classeType(interne, t));
        }
        return 1 + modele.types.size();
    }

    private static void ecrire(Path classesDir, String nomInterne,
                               byte[] octets) throws IOException {
        Path fichier = classesDir.resolve(nomInterne + ".class");
        if (fichier.getParent() != null) {
            Files.createDirectories(fichier.getParent());
        }
        Files.write(fichier, octets);
    }

    /**
     * L'R externe : aucun champ, il ne déclare que ses nichées
     * (l'attribut InnerClasses) et un constructeur par défaut.
     */
    private static byte[] classeR(ModeleR modele, String interne) {
        List<String[]> nichées = new ArrayList<>();
        for (TypeR t : modele.types) {
            nichées.add(new String[] {interne + "$" + t.nom, interne,
                    t.nom});
        }
        return new Classe(nichées, List.of(), List.of(), List.of())
                .bytes(interne);
    }

    /** Une classe nichée : ses champs (ConstantValue pour les final,
     *  <clinit> pour les non final et les tableaux) + InnerClasses. */
    private static byte[] classeType(String interne, TypeR type) {
        List<Champ> champs = new ArrayList<>();
        for (IntR f : type.ints) {
            champs.add(new Champ(f.nom(), "I", f.constant(), f.valeur()));
        }
        for (TableauR t : type.tableaux) {
            champs.add(new Champ(t.nom(), "[I", false, 0));
        }
        List<Champ> nonFinal = new ArrayList<>();
        for (IntR f : type.ints) {
            if (!f.constant()) {
                nonFinal.add(new Champ(f.nom(), "I", false, f.valeur()));
            }
        }
        List<Tableau> tableaux = new ArrayList<>();
        for (TableauR t : type.tableaux) {
            tableaux.add(new Tableau(t.nom(), t.valeurs()));
        }
        List<String[]> nichée = List.<String[]>of(new String[] {
                interne + "$" + type.nom, interne, type.nom});
        return new Classe(nichée, champs, nonFinal, tableaux)
                .bytes(interne + "$" + type.nom);
    }

    private record Champ(String nom, String descripteur, boolean constant,
                         int valeur) {
    }

    private record Tableau(String nom, int[] valeurs) {
    }

    /**
     * L'émetteur de class file : pool de constantes internée en phase
     * de préparation (TOUT doit être résolu avant que le compte
     * d'entrées soit figé), puis sérialisation big-endian sans aucun
     * internement nouveau. Version 52 (V1_8).
     */
    private static final class Classe {

        private static final int ACC_PUBLIC = 0x0001;
        private static final int ACC_STATIC = 0x0008;
        private static final int ACC_FINAL = 0x0010;
        private static final int ACC_SUPER = 0x0020;

        private final List<String[]> nichées;
        private final List<Champ> champs;
        private final List<Champ> nonFinal;
        private final List<Tableau> tableaux;

        private final Map<String, Integer> utf8 = new LinkedHashMap<>();
        private final Map<String, Integer> classes = new LinkedHashMap<>();
        private final Map<Integer, Integer> entiers = new LinkedHashMap<>();
        private final List<byte[]> pool = new ArrayList<>();
        private final Map<String, Integer> nat = new LinkedHashMap<>();
        private final Map<String, Integer> refs = new LinkedHashMap<>();

        Classe(List<String[]> nichées, List<Champ> champs,
               List<Champ> nonFinal, List<Tableau> tableaux) {
            this.nichées = nichées;
            this.champs = champs;
            this.nonFinal = nonFinal;
            this.tableaux = tableaux;
        }

        private int utf8(String s) {
            return utf8.computeIfAbsent(s, k -> {
                byte[] src = k.getBytes(StandardCharsets.UTF_8);
                byte[] b = new byte[src.length + 3];
                b[0] = 1; // CONSTANT_Utf8
                b[1] = (byte) (src.length >> 8);
                b[2] = (byte) src.length;
                System.arraycopy(src, 0, b, 3, src.length);
                pool.add(b);
                return pool.size();
            });
        }

        private int classe(String nomInterne) {
            return classes.computeIfAbsent(nomInterne, k -> {
                int nom = utf8(k);
                byte[] b = new byte[3];
                b[0] = 7; // CONSTANT_Class
                b[1] = (byte) (nom >> 8);
                b[2] = (byte) nom;
                pool.add(b);
                return pool.size();
            });
        }

        private int entier(int v) {
            return entiers.computeIfAbsent(v, k -> {
                byte[] b = new byte[5];
                b[0] = 3; // CONSTANT_Integer
                b[1] = (byte) (v >>> 24);
                b[2] = (byte) (v >>> 16);
                b[3] = (byte) (v >>> 8);
                b[4] = (byte) v;
                pool.add(b);
                return pool.size();
            });
        }

        private int nameAndType(String nom, String descripteur) {
            return nat.computeIfAbsent(nom + "~" + descripteur, k -> {
                int n = utf8(nom);
                int d = utf8(descripteur);
                byte[] b = new byte[5];
                b[0] = 12; // CONSTANT_NameAndType
                b[1] = (byte) (n >> 8);
                b[2] = (byte) n;
                b[3] = (byte) (d >> 8);
                b[4] = (byte) d;
                pool.add(b);
                return pool.size();
            });
        }

        private int methodRef(String classe, String nom,
                              String descripteur) {
            return refs.computeIfAbsent(
                    "m~" + classe + "~" + nom + "~" + descripteur, k -> {
                        int c = classe(classe);
                        int n = nameAndType(nom, descripteur);
                        byte[] b = new byte[5];
                        b[0] = 10; // CONSTANT_Methodref
                        b[1] = (byte) (c >> 8);
                        b[2] = (byte) c;
                        b[3] = (byte) (n >> 8);
                        b[4] = (byte) n;
                        pool.add(b);
                        return pool.size();
                    });
        }

        private int fieldRef(String classe, String nom,
                             String descripteur) {
            return refs.computeIfAbsent(
                    "f~" + classe + "~" + nom + "~" + descripteur, k -> {
                        int c = classe(classe);
                        int n = nameAndType(nom, descripteur);
                        byte[] b = new byte[5];
                        b[0] = 9; // CONSTANT_Fieldref
                        b[1] = (byte) (c >> 8);
                        b[2] = (byte) c;
                        b[3] = (byte) (n >> 8);
                        b[4] = (byte) n;
                        pool.add(b);
                        return pool.size();
                    });
        }

        byte[] bytes(String nomInterne) {
            // PHASE 1 : tout référencer (le compte d'entrées se fige)
            int idxThis = classe(nomInterne);
            int idxSuper = classe("java/lang/Object");
            for (Champ c : champs) {
                utf8(c.nom());
                utf8(c.descripteur());
                if (c.constant()) {
                    entier(c.valeur());
                }
            }
            for (Tableau t : tableaux) {
                // chaque valeur sera poussée en <clinit> : les grosses
                // valeurs demandent une entrée Integer — internées
                // ICI, jamais pendant la sérialisation
                for (int v : t.valeurs()) {
                    entier(v);
                }
                fieldRef(nomInterne, t.nom(), "[I");
            }
            for (Champ f : nonFinal) {
                entier(f.valeur());
                fieldRef(nomInterne, f.nom(), f.descripteur());
            }
            for (String[] inner : nichées) {
                classe(inner[0]);
                classe(inner[1]);
                utf8(inner[2]);
            }
            int ctorRef = methodRef("java/lang/Object", "<init>", "()V");
            utf8("Code");
            utf8("InnerClasses");
            // internés TARDIFS = positions glissantes : tout nom que
            // la sérialisation reference doit exister AVANT la
            // figuration du compte d'entrées (le terrain :
            // « Invalid this class index »)
            utf8("ConstantValue");
            utf8("<clinit>");
            int poolCount = pool.size();

            // PHASE 2 : sérialiser sans rien interné de nouveau
            ByteArrayOutputStream tampon = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(tampon);
            try {
                out.writeInt(0xCAFEBABE);
                out.writeShort(0); // minor
                out.writeShort(52); // major (V1_8)
                // la spec : constant_pool_count = NOMBRE D'ENTRÉES + 1
                // (la place #0 est réservée) — une entrée en moins
                // déclarée glisse tout (le terrain :
                // « Invalid this class index »)
                out.writeShort(poolCount + 1);
                for (byte[] entree : pool) {
                    out.write(entree);
                }
                out.writeShort(ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
                out.writeShort(idxThis);
                out.writeShort(idxSuper);
                out.writeShort(0); // interfaces
                out.writeShort(champs.size());
                for (Champ c : champs) {
                    out.writeShort(ACC_PUBLIC | ACC_STATIC
                            | (c.constant() ? ACC_FINAL : 0));
                    out.writeShort(utf8(c.nom()));
                    out.writeShort(utf8(c.descripteur()));
                    if (c.constant()) {
                        out.writeShort(1); // un attribut
                        out.writeShort(utf8("ConstantValue"));
                        out.writeInt(2);
                        out.writeShort(entier(c.valeur()));
                    } else {
                        out.writeShort(0);
                    }
                }
                out.writeShort(1 + (tableaux.isEmpty() && nonFinal.isEmpty()
                        ? 0 : 1));
                // ctor : ALOAD_0, INVOKESPECIAL Object.<init>, RETURN
                {
                    ByteArrayOutputStream code = new ByteArrayOutputStream();
                    DataOutputStream co = new DataOutputStream(code);
                    co.writeByte(0x2A); // ALOAD_0
                    co.writeByte(0xB7); // INVOKESPECIAL
                    co.writeShort(ctorRef);
                    co.writeByte(0xB1); // RETURN
                    code(out, ACC_PUBLIC, "<init>", "()V",
                            code.toByteArray(), 1, 1);
                }
                if (!tableaux.isEmpty() || !nonFinal.isEmpty()) {
                    ByteArrayOutputStream code = new ByteArrayOutputStream();
                    DataOutputStream co = new DataOutputStream(code);
                    boolean avecTableaux = !tableaux.isEmpty();
                    for (Champ f : nonFinal) {
                        pousser(co, f.valeur());
                        co.writeByte(0xB3); // PUTSTATIC
                        co.writeShort(fieldRef(nomInterne, f.nom(),
                                f.descripteur()));
                    }
                    for (Tableau t : tableaux) {
                        pousser(co, t.valeurs().length);
                        co.writeByte(0xBC); // NEWARRAY (0xBB = new !)
                        co.writeByte(10); // T_INT
                        for (int j = 0; j < t.valeurs().length; j++) {
                            co.writeByte(0x59); // DUP
                            pousser(co, j);
                            pousser(co, t.valeurs()[j]);
                            co.writeByte(0x4F); // IASTORE (javac
                                    // vérifié : 0x4E = astore_3 !)
                        }
                        co.writeByte(0xB3); // PUTSTATIC
                        co.writeShort(fieldRef(nomInterne, t.nom(), "[I"));
                    }
                    co.writeByte(0xB1); // RETURN
                    code(out, ACC_STATIC, "<clinit>", "()V",
                            code.toByteArray(), avecTableaux ? 4 : 1, 0);
                }
                // attributs de classe : InnerClasses
                if (nichées.isEmpty()) {
                    out.writeShort(0);
                } else {
                    out.writeShort(1);
                    out.writeShort(utf8("InnerClasses"));
                    out.writeInt(2 + 8 * nichées.size());
                    out.writeShort(nichées.size());
                    for (String[] inner : nichées) {
                        out.writeShort(classe(inner[0]));
                        out.writeShort(classe(inner[1]));
                        out.writeShort(utf8(inner[2]));
                        out.writeShort(ACC_PUBLIC | ACC_STATIC | ACC_FINAL);
                    }
                }
                out.flush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return tampon.toByteArray();
        }

        /** ICONST/BIPUSH/SIPUSH/LDC_W selon l'amplitude — les entrées
         *  Integer nécessaires ont été internées en phase 1 (pour les
         *  tableaux) ou le sont au champ ConstantValue (les final) ;
         *  un entier isolé non final hors amplitude resterait hors
         *  pool : impossible ici, les ids sont toujours final ou de
         *  valeur indice. */
        private void pousser(DataOutputStream out, int v)
                throws IOException {
            if (v == -1) {
                out.writeByte(0x02); // ICONST_M1
            } else if (v >= 0 && v <= 5) {
                out.writeByte(0x03 + v); // ICONST_0..5
            } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
                out.writeByte(0x10); // BIPUSH
                out.writeByte(v);
            } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
                out.writeByte(0x11); // SIPUSH
                out.writeShort(v);
            } else {
                out.writeByte(0x13); // LDC_W
                out.writeShort(entier(v));
            }
        }

        /** Un attribut Code : max_stack, max_locals, code (sans table
         *  d'exceptions ni attributs nichés). */
        private void code(DataOutputStream out, int access, String nom,
                          String descripteur, byte[] corps, int maxStack,
                          int maxLocals) throws IOException {
            out.writeShort(access);
            out.writeShort(utf8(nom));
            out.writeShort(utf8(descripteur));
            out.writeShort(1); // un attribut
            out.writeShort(utf8("Code"));
            out.writeInt(12 + corps.length);
            out.writeShort(maxStack);
            out.writeShort(maxLocals);
            out.writeInt(corps.length);
            out.write(corps);
            out.writeShort(0); // exception_table_length
            out.writeShort(0); // attributes_count du Code
        }
    }
}
