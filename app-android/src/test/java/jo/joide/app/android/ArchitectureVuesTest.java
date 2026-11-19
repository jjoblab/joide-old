package jo.joide.app.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * ADR-085 (chantier « architecture des vues ») — le filet de sécurité du
 * lot A0 : l'état actuel est mesuré et VERROUILLÉ, lot par lot la liste
 * d'exceptions DÉCROÎT, vide au lot A9. Ce test ne lit QUE des fichiers
 * (JVM pur, aucune vue gonflée) :
 *
 * <ul>
 * <li>les ÉCRANS ({@code MainActivity} et tout fichier sous
 * {@code ecrans/}) n'instancient aucune vue du cadre, d'AndroidX ou de
 * Material en code — depuis le lot A7 la liste d'exceptions est
 * VIDE ;</li>
 * <li>depuis le lot A5, les VUES DE ZONE de l'atelier (vues et
 * adaptateurs, hors kit {@code ui/} racine) n'instancient aucune vue
 * non plus — depuis le lot A7 (popups de l'agent) la liste est
 * VIDE ;</li>
 * <li>aucune classe de dialogue interdite (D3 : {@code AlertDialog},
 * {@code DialogFragment}, …) — depuis le lot A7, PLUS AUCUNE,
 * {@code ui/Dialogue.java} est supprimée ;</li>
 * <li>les usages de la classe maison {@code Dialogue.} ne vivent plus
 * NULLE PART — depuis le lot A7 la liste d'excusés est VIDE (le
 * pattern reste : toute régression échoue) ;</li>
 * <li>les imports AndroidX/Material restent dans la liste blanche de
 * l'ADR-085 — {@code androidx.compose} et
 * {@code androidx.fragment.app.DialogFragment} interdits absolus ;</li>
 * <li>chaque layout a un consommateur {@code *Binding} ;</li>
 * <li>aucune couleur ni texte littéraux dans les layouts (ADR-083 §3)
 * — depuis le lot A9 la liste d'exceptions couleurs est VIDE
 * (la pastille du compteur de la feuille porte
 * {@code ?attr/colorOnError}).</li>
 * </ul>
 *
 * <p>Discipline de la liste d'exceptions : une entrée qui ne correspond
 * plus à aucune faute ÉCHOUE le test (« retire-la ») — la liste ne peut
 * que décroître, jamais végéter. Toute nouvelle faute hors liste échoue
 * aussi. La liste VIDE est l'état final (A9) : TOUTES les listes sont
 * vides depuis le lot A9 (consolidation) — le filet reste armé.</p>
 */
class ArchitectureVuesTest {

    /** Les sources principales du module. */
    private static final Path SRC = Path.of("src", "main", "java");

    /** Les layouts du module. */
    private static final Path LAYOUTS = Path.of("src", "main", "res",
            "layout");

    /** Racine du paquet applicatif dans SRC. */
    private static final Path APP = SRC.resolve("jo").resolve("joide")
            .resolve("app").resolve("android");

    /**
     * Les classes de vues du prompt v2 (cadre + AndroidX + Material) :
     * instancier l'une d'elles dans un écran est une faute.
     */
    private static final Pattern VUES_INSTANCIEES = Pattern.compile(
            "new (LinearLayout|FrameLayout|RelativeLayout|ScrollView"
                    + "|HorizontalScrollView|NestedScrollView|TextView"
                    + "|Button|ImageView|ImageButton|EditText|View"
                    + "|GridLayout|ProgressBar|MaterialButton"
                    + "|MaterialCardView|MaterialToolbar|MaterialSwitch"
                    + "|Chip|ChipGroup|TextInputLayout|TextInputEditText"
                    + "|TabLayout|FloatingActionButton"
                    + "|ExtendedFloatingActionButton|ConstraintLayout"
                    + "|CoordinatorLayout|DrawerLayout|RecyclerView"
                    + "|AppCompat[A-Za-z]*)\\(");

    /** Les classes de dialogue interdites par D3 (jamais de dialogue). */
    private static final Pattern DIALOGUES_INTERDITS = Pattern.compile(
            "AlertDialog|MaterialAlertDialogBuilder|android\\.app\\.Dialog"
                    + "|new Dialog\\(|DialogFragment|BottomSheetDialog"
                    + "|DatePickerDialog|TimePickerDialog|ProgressDialog");

    /** La classe maison de dialogue (supprimée au lot A7). */
    private static final Pattern DIALOGUE_MAISON = Pattern.compile(
            "Dialogue\\.");

    /** Un hexadécimal de couleur dans un layout : interdit (ADR-083 §3). */
    private static final Pattern COULEUR_LITTERALE = Pattern.compile(
            "#[0-9A-Fa-f]{6,8}");

    /** Un texte littéral (android:text sans référence @string/@null). */
    private static final Pattern TEXTE_LITTERAL = Pattern.compile(
            "android:text=\"[^@]");

    /** La liste EXACTE des changements de configuration que l'atelier
     *  déclare (décision 10 : la GÉOMÉTRIE seule — orientation, taille
     *  d'écran, pliage, multi-fenêtre ; thème, langue, densité et
     *  police restent des recréations, l'état minimal les couvre par
     *  onSaveInstanceState). */
    private static final String CHANGEMENTS_GEOMETRIE =
            "orientation|screenSize|smallestScreenSize|screenLayout";

    /**
     * Liste d'exceptions DÉCROISSANTE — écrans qui instancient encore des
     * vues en code : VIDE depuis le lot A7 (contrôleurs de l'atelier
     * compris — popups XML, sélecteur de modèles, saisies).
     */
    private static final Set<String> ECRANS_EXCUSSES_VUES = Set.of();

    /**
     * Liste d'exceptions DÉCROISSANTE — fichiers portant encore des
     * classes de dialogue interdites : VIDE depuis le lot A7
     * ({@code ui/Dialogue.java} est supprimée, D3).
     */
    private static final Set<String> FICHIERS_EXCUSSES_DIALOGUES =
            Set.of();

    /**
     * Liste d'exceptions DÉCROISSANTE — fichiers consommant encore la
     * classe maison {@code Dialogue.} (58 usages mesurés au lot A0) :
     * VIDE depuis le lot A7 — le pattern reste armé, toute
     * régression échoue.
     */
    private static final Set<String> FICHIERS_EXCUSSES_MAISON = Set.of();

    /**
     * Liste d'exceptions DÉCROISSANTE — layouts portant encore une couleur
     * littérale : VIDE depuis le lot A9 — la pastille du compteur de la
     * feuille porte {@code ?attr/colorOnError} (rôle M3 branché sur
     * Graphite, ADR-085 décision 5) ; le pattern reste armé, toute
     * régression échoue.
     */
    private static final Set<String> LAYOUTS_EXCUSSES_COULEUR = Set.of();

    /**
     * Liste d'exceptions DÉCROISSANTE — vues de zone et adaptateurs
     * (hors écrans, hors kit {@code ui/} racine) qui instancient encore
     * des vues en code : VIDE depuis le lot A7 — le lot A5 a sorti
     * {@code VueConsole}, {@code VueApercu}, {@code VueArbre} et les
     * trois listes de la feuille ; le lot A6 a sorti la feuille basse
     * (histogramme au Canvas) et la bande d'onglets (RecyclerView
     * fermable) ; le lot A7 a sorti l'agent (transcript et popups en
     * {@code item_*}/{@code popup_*} gonflés par binding — seules les
     * PUCES du kit, vues maison déclarables, restent posées par code,
     * hors filet).
     */
    private static final Set<String> ZONES_EXCUSSEES_VUES = Set.of();

    /**
     * Lot A5 (ADR-085, critère du lot) : HORS écrans et hors kit, les
     * vues de l'atelier n'instancient PLUS de vue du cadre, d'AndroidX
     * ou de Material — la hiérarchie vit en XML ; seules les vues
     * DESSINÉES au Canvas ({@code DamierApercu}) et leurs drawables
     * restent du code. La liste d'exceptions ne peut que décroître.
     */
    @Test
    void lesVuesDatelierNInstancientPasDeVuesSaufExceptionsDecroissantes()
            throws IOException {
        List<String> fautes = new ArrayList<>();
        List<String> exceptionsObsoletes = new ArrayList<>();
        Path ui = APP.resolve("ui");
        try (Stream<Path> chemins = Files.walk(APP)) {
            for (Path fichier : chemins.filter(p ->
                    p.toString().endsWith(".java")).sorted().toList()) {
                String nom = relatif(fichier);
                if (nom.equals("MainActivity.java")
                        || nom.startsWith("ecrans/")
                        // le kit ui/ à sa racine : Bouton, Carte, Champ…
                        // remplacés par Material au fil des lots (I9, A9)
                        || (nom.startsWith("ui/")
                        && !nom.startsWith("ui/atelier/")
                        && !nom.startsWith("ui/choix/"))) {
                    continue;
                }
                int faute = compter(VUES_INSTANCIEES,
                        Files.readString(fichier));
                boolean excuse = ZONES_EXCUSSEES_VUES.contains(nom);
                if (faute > 0 && !excuse) {
                    fautes.add(nom + " : " + faute
                            + " vue(s) instanciée(s) en code");
                }
                if (faute == 0 && excuse) {
                    exceptionsObsoletes.add(nom);
                }
            }
        }
        assertFalse(Files.isRegularFile(ui.resolve("Dialogue.java")),
                "ui/Dialogue.java doit rester SUPPRIMÉE (D3, lot A7 : "
                        + "plus aucune classe maison de dialogue)");
        assertTrue(fautes.isEmpty(),
                () -> "vues instanciées en code dans des vues de zone "
                        + "HORS liste d'exceptions (ADR-085 lot A5 : la "
                        + "hiérarchie des vues d'atelier vit en XML, seules "
                        + "les vues dessinées au Canvas restent du code) : "
                        + fautes);
        assertTrue(exceptionsObsoletes.isEmpty(),
                () -> "exceptions obsolètes — la vue n'instancie plus de "
                        + "vues, RETIRE-la de ZONES_EXCUSSEES_VUES (la "
                        + "liste doit décroître) : " + exceptionsObsoletes);
    }

    /** Les préfixes d'imports AndroidX/Material autorisés (ADR-085 §4.2). */
    private static final List<String> IMPORTS_AUTORISES = List.of(
            "androidx.appcompat.",
            "androidx.core.",
            "androidx.activity.",
            "androidx.constraintlayout.",
            "androidx.recyclerview.",
            "androidx.coordinatorlayout.",
            "androidx.drawerlayout.",
            "androidx.fragment.",
            "androidx.lifecycle.",
            "androidx.viewbinding.",
            "com.google.android.material.");

    /** Les écrans : MainActivity et tout fichier sous ecrans/. */
    private static List<Path> lesEcrans() throws IOException {
        List<Path> ecrans = new ArrayList<>();
        Path principal = APP.resolve("MainActivity.java");
        assertTrue(Files.isRegularFile(principal),
                "MainActivity introuvable : " + principal);
        ecrans.add(principal);
        try (Stream<Path> chemins = Files.walk(APP.resolve("ecrans"))) {
            chemins.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .forEach(ecrans::add);
        }
        return ecrans;
    }

    /** Tout fichier Java du module, chemin relatif à APP. */
    private static List<Map.Entry<String, String>> lesSources()
            throws IOException {
        List<Map.Entry<String, String>> sources = new ArrayList<>();
        try (Stream<Path> chemins = Files.walk(APP)) {
            chemins.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            sources.add(Map.entry(
                                    APP.relativize(p).toString()
                                            .replace('\\', '/'),
                                    Files.readString(p)));
                        } catch (IOException e) {
                            throw new IllegalStateException(
                                    "lecture impossible : " + p, e);
                        }
                    });
        }
        return sources;
    }

    /** Le chemin relatif à APP, séparateurs normalisés. */
    private static String relatif(Path fichier) {
        return APP.relativize(fichier).toString().replace('\\', '/');
    }

    @Test
    void lesEcransNInstancientPasDeVuesSaufExceptionsDecroissantes()
            throws IOException {
        List<String> fautes = new ArrayList<>();
        List<String> exceptionsObsoletes = new ArrayList<>();
        for (Path ecran : lesEcrans()) {
            String nom = relatif(ecran);
            int faute = compter(VUES_INSTANCIEES,
                    Files.readString(ecran));
            boolean excuse = ECRANS_EXCUSSES_VUES.contains(nom);
            if (faute > 0 && !excuse) {
                fautes.add(nom + " : " + faute
                        + " vue(s) instanciée(s) en code");
            }
            if (faute == 0 && excuse) {
                exceptionsObsoletes.add(nom);
            }
        }
        assertTrue(fautes.isEmpty(),
                () -> "vues instanciées en code dans des écrans HORS liste "
                        + "d'exceptions (ADR-085 : la hiérarchie vit en XML, "
                        + "jamais en code) : " + fautes);
        assertTrue(exceptionsObsoletes.isEmpty(),
                () -> "exceptions obsolètes — l'écran n'instancie plus de "
                        + "vues, RETIRE-le de ECRANS_EXCUSSES_VUES (la liste "
                        + "doit décroître) : " + exceptionsObsoletes);
    }

    @Test
    void aucuneClasseDeDialogueInterditeSaufExceptionsDecroissantes()
            throws IOException {
        List<String> fautes = new ArrayList<>();
        List<String> exceptionsObsoletes = new ArrayList<>();
        for (Map.Entry<String, String> source : lesSources()) {
            int faute = compter(DIALOGUES_INTERDITS, source.getValue());
            boolean excuse = FICHIERS_EXCUSSES_DIALOGUES.contains(
                    source.getKey());
            if (faute > 0 && !excuse) {
                fautes.add(source.getKey());
            }
            if (faute == 0 && excuse) {
                exceptionsObsoletes.add(source.getKey());
            }
        }
        assertTrue(fautes.isEmpty(),
                () -> "classes de dialogue interdites par D3 hors fichiers "
                        + "excusés (plus AUCUN dialogue : tout devient popup "
                        + "popup_*.xml) : " + fautes);
        assertTrue(exceptionsObsoletes.isEmpty(),
                () -> "exceptions obsolètes — plus aucune classe de "
                        + "dialogue interdite, RETIRE-le fichier de "
                        + "FICHIERS_EXCUSSES_DIALOGUES : "
                        + exceptionsObsoletes);
    }

    @Test
    void laMaisonDialogueNeVitQueDansLesFichiersExcuses()
            throws IOException {
        List<String> fautes = new ArrayList<>();
        List<String> exceptionsObsoletes = new ArrayList<>();
        for (Map.Entry<String, String> source : lesSources()) {
            int faute = compter(DIALOGUE_MAISON, source.getValue());
            boolean excuse = FICHIERS_EXCUSSES_MAISON.contains(
                    source.getKey());
            if (faute > 0 && !excuse) {
                fautes.add(source.getKey() + " : " + faute + " usage(s)");
            }
            if (faute == 0 && excuse) {
                exceptionsObsoletes.add(source.getKey());
            }
        }
        assertTrue(fautes.isEmpty(),
                () -> "usages de la classe maison Dialogue (D3, lot A7 : "
                        + "Dialogue.java est supprimée, tout usage est une "
                        + "régression à corriger en popup) : " + fautes);
        assertTrue(exceptionsObsoletes.isEmpty(),
                () -> "exceptions obsolètes — le fichier n'utilise plus "
                        + "Dialogue, RETIRE-le de FICHIERS_EXCUSSES_MAISON : "
                        + exceptionsObsoletes);
    }

    @Test
    void lesImportsAndroidXMateriauxRestentDansLaListeBlanche()
            throws IOException {
        List<String> fautes = new ArrayList<>();
        for (Map.Entry<String, String> source : lesSources()) {
            for (String ligne : source.getValue().split("\n")) {
                String nue = ligne.trim();
                if (!nue.startsWith("import androidx.")
                        && !nue.startsWith(
                                "import com.google.android.material.")) {
                    continue;
                }
                if (nue.startsWith("import androidx.compose.")) {
                    fautes.add(source.getKey() + " → " + nue
                            + " (Compose EXCLU : application Java)");
                    continue;
                }
                if (nue.contains("androidx.fragment.app.DialogFragment")) {
                    fautes.add(source.getKey() + " → " + nue
                            + " (DialogFragment interdit par D3)");
                    continue;
                }
                boolean autorise = false;
                for (String prefixe : IMPORTS_AUTORISES) {
                    if (nue.startsWith("import " + prefixe)) {
                        autorise = true;
                        break;
                    }
                }
                if (!autorise) {
                    fautes.add(source.getKey() + " → " + nue
                            + " hors liste blanche de l'ADR-085");
                }
            }
        }
        assertTrue(fautes.isEmpty(),
                () -> "imports AndroidX/Material hors liste blanche "
                        + "ADR-085 (seul Compose est exclu ; les préfixes "
                        + "autorisés sont énumérés dans l'ADR) : " + fautes);
    }

    @Test
    void chaqueLayoutAUnConsommateurBinding() throws IOException {
        List<String> manquants = new ArrayList<>();
        StringBuilder sources = new StringBuilder();
        try (Stream<Path> chemins = Files.walk(APP)) {
            chemins.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            sources.append(Files.readString(p));
                        } catch (IOException e) {
                            throw new IllegalStateException(
                                    "lecture impossible : " + p, e);
                        }
                    });
        }
        try (Stream<Path> chemins = Files.list(LAYOUTS)) {
            List<Path> fichiers = chemins.filter(p -> p.toString()
                    .endsWith(".xml")).sorted().toList();
            assertFalse(fichiers.isEmpty(),
                    "aucun layout trouvé sous " + LAYOUTS);
            for (Path layout : fichiers) {
                String nom = layout.getFileName().toString()
                        .replaceAll("\\.xml$", "");
                String binding = nomDeBinding(nom);
                if (!Pattern.compile("\\b" + binding + "\\b")
                        .matcher(sources).find()) {
                    manquants.add(nom + " → " + binding + " sans "
                            + "consommateur");
                }
            }
        }
        assertTrue(manquants.isEmpty(),
                () -> "layouts sans consommateur *Binding (un layout écrit "
                        + "est un layout vivant) : " + manquants);
    }

    @Test
    void aucuneCouleurLitteraleDansLesLayoutsSaufExceptionsDecroissantes()
            throws IOException {
        List<String> fautes = new ArrayList<>();
        List<String> exceptionsObsoletes = new ArrayList<>();
        try (Stream<Path> chemins = Files.list(LAYOUTS)) {
            for (Path layout : chemins.filter(p -> p.toString()
                    .endsWith(".xml")).sorted().toList()) {
                String nom = layout.getFileName().toString();
                int faute = compter(COULEUR_LITTERALE,
                        Files.readString(layout));
                boolean excuse = LAYOUTS_EXCUSSES_COULEUR.contains(nom);
                if (faute > 0 && !excuse) {
                    fautes.add(nom + " : " + faute + " couleur(s)");
                }
                if (faute == 0 && excuse) {
                    exceptionsObsoletes.add(nom);
                }
            }
        }
        assertTrue(fautes.isEmpty(),
                () -> "couleurs littérales dans les layouts (ADR-083 §3 : "
                        + "les couleurs ne vivent QUE par ?attr/jo_* ou "
                        + "rôles M3) : " + fautes);
        assertTrue(exceptionsObsoletes.isEmpty(),
                () -> "exceptions obsolètes — RETIRE le layout de "
                        + "LAYOUTS_EXCUSSES_COULEUR : "
                        + exceptionsObsoletes);
    }

    @Test
    void aucunTexteLitteralDansLesLayouts() throws IOException {
        List<String> fautes = new ArrayList<>();
        try (Stream<Path> chemins = Files.list(LAYOUTS)) {
            for (Path layout : chemins.filter(p -> p.toString()
                    .endsWith(".xml")).sorted().toList()) {
                int faute = compter(TEXTE_LITTERAL,
                        Files.readString(layout));
                if (faute > 0) {
                    fautes.add(layout.getFileName() + " : " + faute
                            + " texte(s) littéral(aux)");
                }
            }
        }
        assertTrue(fautes.isEmpty(),
                () -> "textes littéraux dans les layouts (parité values/ ↔ "
                        + "values-fr/ : tout texte vit en @string) : "
                        + fautes);
    }

    /**
     * Lot A8 (décision 10) : l'atelier — et LUI SEUL — déclare les
     * changements de configuration de GÉOMÉTRIE. La liste EXACTE du
     * manifest est verrouillée ici (ajouter un changement hors
     * géométrie, densité ou police par exemple, reviendrait à recréer
     * sans l'état : une dérive silencieuse) ; une autre activité qui
     * déclarerait ses changements échoue aussi.
     */
    @Test
    void leManifestePorteLesChangementsDeGeometrieSurLAtelierSeul()
            throws IOException {
        Path manifeste = Path.of("src", "main", "AndroidManifest.xml");
        assertTrue(Files.isRegularFile(manifeste),
                "manifeste introuvable : " + manifeste);
        String texte = Files.readString(manifeste);
        // l'atelier porte l'attribut, avec la liste EXACTE
        int atelier = texte.indexOf(".ecrans.atelier.EcranAtelier");
        assertTrue(atelier >= 0,
                "EcranAtelier est déclarée dans le manifeste");
        int fin = texte.indexOf("</activity>", atelier);
        assertTrue(fin > atelier, "l'activité est fermée");
        assertTrue(texte.substring(atelier, fin).contains(
                "android:configChanges=\"" + CHANGEMENTS_GEOMETRIE
                        + "\""),
                "EcranAtelier déclare les changements de GÉOMÉTRIE seuls "
                        + "(décision 10) : " + CHANGEMENTS_GEOMETRIE);
        // RIEN d'autre ne les déclare — et le compte se garde (un
        // commentaire du manifest ne doit pas porter la literal)
        int total = compter(Pattern.compile("android:configChanges="),
                texte);
        assertEquals(1, total,
                "seul l'atelier déclare ses changements de configuration "
                        + "(les autres écrans recréent, l'état d'UI "
                        + "minimal les couvre)");
    }

    /** Le nombre d'occurrences du motif dans le texte. */
    private static int compter(Pattern motif, String texte) {
        int total = 0;
        java.util.regex.Matcher parcours = motif.matcher(texte);
        while (parcours.find()) {
            total++;
        }
        return total;
    }

    /** atelier_barre_haut → AtelierBarreHautBinding. */
    private static String nomDeBinding(String nomLayout) {
        String[] morceaux = nomLayout.split("_");
        StringBuilder pascal = new StringBuilder();
        for (String morceau : morceaux) {
            if (morceau.isEmpty()) {
                continue;
            }
            pascal.append(Character.toUpperCase(morceau.charAt(0)))
                    .append(morceau.substring(1));
        }
        return pascal.append("Binding").toString();
    }
}
