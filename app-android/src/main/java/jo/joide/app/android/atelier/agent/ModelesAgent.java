package jo.joide.app.android.atelier.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lot C4 (2026-11-20, demande du terrain : « l'écran choisir modèle
 * (le popup) doit comprendre les options de filtrage, charger la
 * liste des modèles, etc. » — consultation clean-room n° 4, mandat du
 * propriétaire, la manière du sélecteur éprouvé) — LE
 * MODÈLE PUR DU SÉLECTEUR : le filtre (recherche multi-terme,
 * fournisseur, gratuits seulement, tri), les sections (favoris,
 * récents, le reste), le tri NATUREL (« Modèle 2 » avant « Modèle
 * 10 », « GPT-4.1 » avant « GPT-5 » — les chiffres se lisent en
 * nombres). Aucune vue, aucun Android : la vérité est testable JVM,
 * le contrôleur applique.
 *
 * <p>Un modèle ne se montre QU'UNE fois : un favori ne se répète ni
 * dans les récents ni dans le reste ; les favoris gardent l'ordre du
 * tri, les récents l'ordre du plus récent (le premier de la liste
 * persistée), le reste suit le tri demandé.</p>
 */
public final class ModelesAgent {

    /** L'ordre de tri du sélecteur. */
    public enum Tri {
        /** Par nom d'affichage (naturel). */
        NOM,
        /** Par fournisseur d'identifiant ({@code vendor/model}), puis nom. */
        FOURNISSEUR
    }

    /** Le filtre courant du sélecteur. */
    public static final class Filtre {

        /** La recherche (chaque terme séparé par un blanc doit
         *  apparaître — « anthropic sonnet » trouve les Sonnet
         *  d'Anthropic). */
        public final String recherche;

        /** Le fournisseur d'identifiant demandé, null pour tous. */
        public final String fournisseur;

        /** Les seuls modèles gratuits ({@code :free}). */
        public final boolean gratuits;

        /** Le tri demandé. */
        public final Tri tri;

        /**
         * @param recherche   la recherche brute (peut être vide)
         * @param fournisseur le fournisseur d'identifiant, null sinon
         * @param gratuits    les seuls gratuits
         * @param tri         le tri
         */
        public Filtre(String recherche, String fournisseur,
                      boolean gratuits, Tri tri) {
            this.recherche = recherche == null ? "" : recherche;
            this.fournisseur = fournisseur;
            this.gratuits = gratuits;
            this.tri = tri == null ? Tri.NOM : tri;
        }
    }

    /** Les sections du sélecteur (une fois chaque modèle). */
    public static final class Sections {

        /** Les favoris (tri demandé). */
        public final List<String> favoris;

        /** Les récents (le plus récent d'abord, hors favoris). */
        public final List<String> recents;

        /** Le reste (tri demandé). */
        public final List<String> reste;

        /**
         * @param favoris les favoris
         * @param recents les récents
         * @param reste   le reste
         */
        public Sections(List<String> favoris, List<String> recents,
                        List<String> reste) {
            this.favoris = List.copyOf(favoris);
            this.recents = List.copyOf(recents);
            this.reste = List.copyOf(reste);
        }

        /** @return vrai si aucune section ne montre rien */
        public boolean vide() {
            return favoris.isEmpty() && recents.isEmpty()
                    && reste.isEmpty();
        }

        /** @return le nombre total de modèles montrés */
        public int total() {
            return favoris.size() + recents.size() + reste.size();
        }
    }

    /**
     * La moitié fournisseur d'un identifiant OpenRouter
     * ({@code vendor/model}), ou null pour un identifiant nu.
     *
     * @param id l'identifiant du modèle
     * @return le fournisseur, ou null
     */
    public static String fournisseurDe(String id) {
        if (id == null) {
            return null;
        }
        int barre = id.indexOf('/');
        if (barre <= 0) {
            return null;
        }
        String morceau = id.substring(0, barre);
        return morceau.isBlank() ? null : morceau;
    }

    /**
     * Le modèle gratuit OpenRouter (identifiant en
     * {@code :free}, sans égard à la casse).
     *
     * @param id l'identifiant du modèle
     * @return vrai si gratuit
     */
    public static boolean estGratuit(String id) {
        return id != null && id.toLowerCase(Locale.ROOT)
                .endsWith(":free");
    }

    /**
     * Le nom d'affichage nu : le préfixe « Fournisseur : » se retire
     * quand l'identifiant porte un fournisseur (sinon un tri par nom
     * serait un tri par fournisseur).
     *
     * @param id          l'identifiant du modèle
     * @param nomAffiche le nom d'affichage brut
     * @return le nom nu
     */
    public static String nomNu(String id, String nomAffiche) {
        if (nomAffiche == null) {
            return "";
        }
        if (fournisseurDe(id) == null) {
            return nomAffiche;
        }
        int deuxPoints = nomAffiche.indexOf(": ");
        if (deuxPoints > 0 && deuxPoints <= 40) {
            return nomAffiche.substring(deuxPoints + 2);
        }
        return nomAffiche;
    }

    /**
     * Les sections du sélecteur (lot C4) : la recherche filtre (chaque
     * terme doit apparaître dans l'identifiant ou le nom), le
     * fournisseur et le drapeau gratuit rétrécissent, le tri ordonne ;
     * un modèle se montre UNE fois (favori d'abord, puis récent, le
     * reste ensuite).
     *
     * @param modeles  la liste vivante fusionnée au catalogue
     * @param filtre   le filtre courant
     * @param favoris  les identifiants favoris persistés
     * @param recents  les identifiants récents (récent d'abord)
     * @return les trois sections
     */
    public static Sections sections(List<String> modeles, Filtre filtre,
                                    List<String> favoris,
                                    List<String> recents) {
        List<String> termes = termes(filtre.recherche);
        List<String> filtrage = new ArrayList<>();
        Set<String> ensemble = new LinkedHashSet<>(modeles == null
                ? List.of() : modeles);
        for (String modele : ensemble) {
            if (filtre.fournisseur != null && !filtre.fournisseur.equals(
                    fournisseurDe(modele))) {
                continue;
            }
            if (filtre.gratuits && !estGratuit(modele)) {
                continue;
            }
            if (!correspond(modele, termes)) {
                continue;
            }
            filtrage.add(modele);
        }
        Comparator<String> comparateur = comparateur(filtre.tri);
        Set<String> favorisSet = new LinkedHashSet<>(favoris);
        List<String> sectionFavoris = new ArrayList<>();
        for (String modele : filtrage) {
            if (favorisSet.contains(modele)) {
                sectionFavoris.add(modele);
            }
        }
        sectionFavoris.sort(comparateur);
        List<String> sectionRecents = new ArrayList<>();
        for (String modele : recents == null ? List.<String>of()
                : recents) {
            if (filtrage.contains(modele)
                    && !favorisSet.contains(modele)
                    && !sectionRecents.contains(modele)) {
                sectionRecents.add(modele);
            }
        }
        Set<String> montres = new LinkedHashSet<>(sectionFavoris);
        montres.addAll(sectionRecents);
        List<String> sectionReste = new ArrayList<>();
        for (String modele : filtrage) {
            if (!montres.contains(modele)) {
                sectionReste.add(modele);
            }
        }
        sectionReste.sort(comparateur);
        return new Sections(sectionFavoris, sectionRecents, sectionReste);
    }

    /** Les fournisseurs distincts d'une liste (triés). */
    public static List<String> fournisseurs(List<String> modeles) {
        Set<String> fournisseurs = new LinkedHashSet<>();
        for (String modele : modeles == null ? List.<String>of()
                : modeles) {
            String fournisseur = fournisseurDe(modele);
            if (fournisseur != null) {
                fournisseurs.add(fournisseur);
            }
        }
        List<String> tries = new ArrayList<>(fournisseurs);
        tries.sort(String::compareTo);
        return tries;
    }

    /** Le nombre de modèles d'un fournisseur d'identifiant. */
    public static int nombreDuFournisseur(List<String> modeles,
                                          String fournisseur) {
        int total = 0;
        for (String modele : modeles == null ? List.<String>of()
                : modeles) {
            if (fournisseur.equals(fournisseurDe(modele))) {
                total++;
            }
        }
        return total;
    }

    /** Vrai si la liste montre au moins un modèle gratuit. */
    public static boolean unGratuit(List<String> modeles) {
        for (String modele : modeles == null ? List.<String>of()
                : modeles) {
            if (estGratuit(modele)) {
                return true;
            }
        }
        return false;
    }

    /**
     * L'ORDRE NATUREL insensible à la casse : les suites de chiffres
     * se lisent en nombres (« Modèle 2 » avant « Modèle 10 »,
     * « GPT-4.1 » avant « GPT-5 »), le reste en lettres.
     *
     * @param a le premier texte
     * @param b le second texte
     * @return l'ordre
     */
    public static int comparaisonNaturelle(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null ? 0 : a == null ? -1 : 1;
        }
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int ia = i;
                int ib = j;
                while (i < a.length()
                        && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length()
                        && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(ia, i).replaceFirst("^0+", "");
                String nb = b.substring(ib, j).replaceFirst("^0+", "");
                if (na.length() != nb.length()) {
                    return na.length() - nb.length();
                }
                int c = na.compareTo(nb);
                if (c != 0) {
                    return c;
                }
            } else {
                int c = Character.toLowerCase(ca)
                        - Character.toLowerCase(cb);
                if (c != 0) {
                    return c;
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    /** Le comparateur du tri demandé (nom nu, ou fournisseur puis nom). */
    private static Comparator<String> comparateur(Tri tri) {
        Comparator<String> parNom = ModelesAgent::comparaisonNaturelle;
        if (tri == Tri.FOURNISSEUR) {
            Comparator<String> parFournisseur = Comparator.comparing(
                    (String id) -> fournisseurOuVide(id));
            return parFournisseur.thenComparing(parNom);
        }
        return parNom;
    }

    /** Le fournisseur d'identifiant, ou la chaîne vide (tri stable). */
    private static String fournisseurOuVide(String id) {
        String fournisseur = fournisseurDe(id);
        return fournisseur == null ? "" : fournisseur;
    }

    /** Les termes de la recherche (blancs séparateurs, vides ôtés). */
    private static List<String> termes(String recherche) {
        List<String> termes = new ArrayList<>();
        if (recherche != null) {
            for (String morceau : recherche.toLowerCase(Locale.ROOT)
                    .split("[\\s\\t]+")) {
                if (!morceau.isEmpty()) {
                    termes.add(morceau);
                }
            }
        }
        return termes;
    }

    /** Vrai si chaque terme apparaît dans l'identifiant (nu, en
     *  minuscules). */
    private static boolean correspond(String modele,
                                      List<String> termes) {
        String cible = modele == null ? ""
                : modele.toLowerCase(Locale.ROOT);
        for (String terme : termes) {
            if (!cible.contains(terme)) {
                return false;
            }
        }
        return true;
    }

    /** Instance interdite (le modèle est statique pur). */
    private ModelesAgent() {
    }
}
