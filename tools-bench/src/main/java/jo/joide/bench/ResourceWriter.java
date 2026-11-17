package jo.joide.bench;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Génération des fichiers Android d'un module synthétique :
 * manifeste, mises en page, valeurs, drawable et assets binaires à motif
 * fixe. Tous les contenus sont déterministes à graine égale.
 */
public final class ResourceWriter {

    private final String module;

    /**
     * @param module nom du module
     */
    public ResourceWriter(String module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** @return le manifeste minimal du module */
    public String manifest() {
        return "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example." + module + "\">\n"
                + "</manifest>\n";
    }

    /**
     * @param index index de la mise en page
     * @param rng   générateur propre au fichier
     * @return un layout linéaire avec deux vues texte
     */
    public String layout(int index, Random rng) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"wrap_content\"\n"
                + "    android:orientation=\"vertical\">\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/title_" + index + "\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"@string/title_" + index + "\" />\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/detail_" + index + "\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:paddingTop=\"" + rng.nextInt(16) + "dp\"\n"
                + "        android:text=\"@string/detail_" + index + "\" />\n"
                + "</LinearLayout>\n";
    }

    /**
     * @param indexes index des mises en page du module (trié)
     * @return le fichier strings.xml correspondant
     */
    public String strings(List<Integer> indexes) {
        StringBuilder out = new StringBuilder(256 + indexes.size() * 96);
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<resources>\n");
        for (int index : indexes) {
            out.append("    <string name=\"title_").append(index)
                    .append("\">Title ").append(index).append("</string>\n");
            out.append("    <string name=\"detail_").append(index)
                    .append("\">Detail ").append(index).append("</string>\n");
        }
        out.append("</resources>\n");
        return out.toString();
    }

    /**
     * @param count nombre de couleurs
     * @param rng   générateur propre au fichier
     * @return le fichier colors.xml correspondant
     */
    public String colors(int count, Random rng) {
        StringBuilder out = new StringBuilder(128 + count * 64);
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<resources>\n");
        for (int i = 0; i < count; i++) {
            out.append(String.format(Locale.ROOT,
                    "    <color name=\"tone_%d\">#%06X</color>\n",
                    i, rng.nextInt(0x100_0000)));
        }
        out.append("</resources>\n");
        return out.toString();
    }

    /** @param index index du drawable */
    public String drawable(int index) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:shape=\"rectangle\">\n"
                + "    <solid android:color=\"#" + String.format(
                        Locale.ROOT, "%06X", (index * 3737) % 0x100_0000)
                + "\" />\n"
                + "</shape>\n";
    }

    /**
     * @param size  taille en octets
     * @param rng   générateur propre au fichier
     * @return des octets pseudo-aléatoires stables (assets binaires)
     */
    public byte[] binaryAsset(int size, Random rng) {
        byte[] data = new byte[size];
        rng.nextBytes(data);
        return data;
    }
}
