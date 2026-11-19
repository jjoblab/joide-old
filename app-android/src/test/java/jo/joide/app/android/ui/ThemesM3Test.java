package jo.joide.app.android.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jo.joide.app.android.atelier.EtatAtelier;

/**
 * ADR-085 (décision 5, lot A1) — {@code Graphite.java} reste la source
 * de vérité ; les thèmes {@code Sombre}/{@code Clair} descendent de
 * {@code Theme.Material3.*} avec des RÔLES Material 3 branchés sur les
 * jetons existants. Ce test (JVM, lecture de fichiers seule) vérifie :
 * les parents M3 des deux thèmes, chaque rôle requis DÉFINI et résolu
 * vers une couleur EXISTANTE, l'égalité des dimens avec
 * {@code Graphite} (rayons, espace, échelle typographique), les styles
 * {@code TextAppearance.Jo.*}/{@code ShapeAppearance.Jo.*} (parents M3,
 * valeurs par @dimen), les slots de forme et de texte du thème, et la
 * parité du qualificatif {@code w720dp} avec
 * {@code EtatAtelier.SEUIL_GRAND_ECRAN_DP} — jamais de jeton en l'air.
 */
class ThemesM3Test {

    /** Les ressources de valeurs du module. */
    private static final Path RES = Path.of("src", "main", "res");

    /** Les rôles M3 que les deux thèmes DOIVENT définir et résoudre. */
    private static final List<String> ROLES = List.of(
            "colorPrimary", "colorOnPrimary", "colorPrimaryContainer",
            "colorOnPrimaryContainer",
            "colorSecondary", "colorOnSecondary", "colorSecondaryContainer",
            "colorOnSecondaryContainer",
            "colorTertiary", "colorOnTertiary", "colorTertiaryContainer",
            "colorOnTertiaryContainer",
            "colorSurface", "colorOnSurface", "colorSurfaceVariant",
            "colorOnSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow",
            "colorSurfaceContainer", "colorSurfaceContainerHigh",
            "colorSurfaceContainerHighest", "colorSurfaceDim",
            "colorSurfaceBright",
            "colorOnBackground", "colorOutline", "colorOutlineVariant",
            "colorError", "colorOnError", "colorErrorContainer",
            "colorOnErrorContainer",
            "colorSurfaceInverse", "colorOnSurfaceInverse",
            "colorPrimaryInverse", "android:colorBackground");

    /** Les slots typographiques que le thème branche sur Jo.*. */
    private static final List<String> SLOTS_TEXTE = List.of(
            "textAppearanceBodySmall", "textAppearanceBodyMedium",
            "textAppearanceBodyLarge", "textAppearanceTitleMedium",
            "textAppearanceTitleLarge", "textAppearanceHeadlineMedium",
            "textAppearanceLabelSmall", "textAppearanceLabelLarge");

    /** Les slots de forme que le thème branche sur Jo.*. */
    private static final List<String> SLOTS_FORME = List.of(
            "shapeAppearanceSmallComponent", "shapeAppearanceMediumComponent",
            "shapeAppearanceLargeComponent");

    /** L'égalité Graphite ↔ dimens attendue (nom ↔ constante). */
    private static final Map<String, String> DIMENS = Map.ofEntries(
            Map.entry("jo_rayon_carte", dp(Graphite.RAYON_CARTE)),
            Map.entry("jo_rayon_composant", dp(Graphite.RAYON_COMPOSANT)),
            Map.entry("jo_rayon_puce", dp(Graphite.RAYON_PUCE)),
            Map.entry("jo_grille", dp(Graphite.GRILLE)),
            Map.entry("jo_marge", dp(Graphite.MARGE)),
            Map.entry("jo_gouttiere", dp(Graphite.GOUTTIERE)),
            Map.entry("jo_tuile", dp(Graphite.TUILE)),
            Map.entry("jo_texte_legende", sp(Graphite.LEGENDE)),
            Map.entry("jo_texte_legende_interligne",
                    spInterligne(Graphite.LEGENDE)),
            Map.entry("jo_texte_note", sp(Graphite.NOTE)),
            Map.entry("jo_texte_note_interligne", spInterligne(Graphite.NOTE)),
            Map.entry("jo_texte_corps", sp(Graphite.CORPS)),
            Map.entry("jo_texte_corps_interligne",
                    spInterligne(Graphite.CORPS)),
            Map.entry("jo_texte_sous_titre", sp(Graphite.SOUS_TITRE)),
            Map.entry("jo_texte_sous_titre_interligne",
                    spInterligne(Graphite.SOUS_TITRE)),
            Map.entry("jo_texte_titre", sp(Graphite.TITRE)),
            Map.entry("jo_texte_titre_interligne",
                    spInterligne(Graphite.TITRE)),
            Map.entry("jo_texte_titre_ecran", sp(Graphite.TITRE_ECRAN)),
            Map.entry("jo_texte_titre_ecran_interligne",
                    spInterligne(Graphite.TITRE_ECRAN)),
            Map.entry("jo_texte_code", sp(Graphite.CODE)),
            Map.entry("jo_texte_code_interligne", spInterligne(Graphite.CODE)),
            Map.entry("jo_texte_code_compact", sp(Graphite.CODE_COMPACT)),
            Map.entry("jo_texte_code_compact_interligne",
                    spInterligne(Graphite.CODE_COMPACT)));

    @Test
    void lesDeuxThemesDescendentDeMaterial3() throws Exception {
        Map<String, Map<String, String>> themes = styles(
                RES.resolve("values").resolve("themes.xml").toFile());
        assertEquals("Theme.Material3.Dark.NoActionBar",
                themes.get("Theme.JoIDE.Sombre").get("_parent"),
                "Sombre doit descendre de Theme.Material3.Dark (D2)");
        assertEquals("Theme.Material3.Light.NoActionBar",
                themes.get("Theme.JoIDE.Clair").get("_parent"),
                "Clair doit descendre de Theme.Material3.Light (D2)");
    }

    @Test
    void chaqueRoleM3EstDefiniEtResoutUnJetonExistant() throws Exception {
        Map<String, Map<String, String>> themes = styles(
                RES.resolve("values").resolve("themes.xml").toFile());
        Map<String, String> couleurs = enfants(
                document(RES.resolve("values").resolve("colors.xml").toFile()),
                "color");
        for (String nomTheme : List.of("Theme.JoIDE.Sombre",
                "Theme.JoIDE.Clair")) {
            Map<String, String> items = themes.get(nomTheme);
            assertNotNull(items, nomTheme + " doit exister");
            for (String role : ROLES) {
                String valeur = items.get(role);
                assertTrue(valeur != null && valeur.startsWith("@color/"),
                        nomTheme + " doit poser le rôle " + role
                                + " sur un jeton @color/");
                assertTrue(couleurs.containsKey(
                                valeur.substring("@color/".length())),
                        nomTheme + " : " + role + " référence une couleur "
                                + "absente : " + valeur);
            }
            for (String slot : SLOTS_TEXTE) {
                assertTrue(items.containsKey(slot),
                        nomTheme + " doit brancher " + slot);
            }
            for (String slot : SLOTS_FORME) {
                assertTrue(items.containsKey(slot),
                        nomTheme + " doit brancher " + slot);
            }
        }
    }

    @Test
    void lesDimensEgalentGraphite() throws Exception {
        Map<String, String> xml = enfants(
                document(RES.resolve("values").resolve("dimens.xml")
                        .toFile()), "dimen");
        for (Map.Entry<String, String> attendu : DIMENS.entrySet()) {
            assertEquals(attendu.getValue(), xml.get(attendu.getKey()),
                    "le dimen " + attendu.getKey()
                            + " doit égaler Graphite (source de vérité)");
        }
    }

    @Test
    void lesTextAppearanceJoPortentLEchelleEtUnParentM3() throws Exception {
        Map<String, Map<String, String>> styles = styles(
                RES.resolve("values").resolve("styles.xml").toFile());
        Map<String, String> dimens = enfants(
                document(RES.resolve("values").resolve("dimens.xml").toFile()),
                "dimen");
        List<String> attendus = List.of(
                "TextAppearance.Jo.Legende", "TextAppearance.Jo.Note",
                "TextAppearance.Jo.Corps", "TextAppearance.Jo.SousTitre",
                "TextAppearance.Jo.Titre", "TextAppearance.Jo.TitreEcran",
                "TextAppearance.Jo.Code", "TextAppearance.Jo.CodeCompact");
        for (String nom : attendus) {
            Map<String, String> style = styles.get(nom);
            assertNotNull(style, nom + " doit exister dans styles.xml");
            assertTrue(style.get("_parent") != null
                            && style.get("_parent")
                            .startsWith("TextAppearance.Material3."),
                    nom + " doit hériter d'un TextAppearance.Material3.*");
            String taille = style.get("android:textSize");
            String interligne = style.get("android:lineHeight");
            assertTrue(taille != null && taille.startsWith("@dimen/")
                            && interligne != null
                            && interligne.startsWith("@dimen/"),
                    nom + " : taille et interligne par @dimen, jamais "
                            + "de littéral");
            assertTrue(dimens.containsKey(taille.substring("@dimen/"
                    .length())), nom + " : dimen inconnue " + taille);
            assertTrue(dimens.containsKey(
                            interligne.substring("@dimen/".length())),
                    nom + " : dimen inconnue " + interligne);
            String familleAttendue;
            if (nom.endsWith("Code") || nom.endsWith("CodeCompact")) {
                familleAttendue = "monospace";
            } else if (nom.endsWith("SousTitre") || nom.endsWith("Titre")) {
                // la graisse 600 de Graphite se rapproche par
                // sans-serif-medium (500) — nuance consignée dans ADR-085
                familleAttendue = "sans-serif-medium";
            } else {
                familleAttendue = "sans-serif";
            }
            assertEquals(familleAttendue, style.get("android:fontFamily"),
                    nom + " : la famille doit suivre Graphite");
        }
    }

    @Test
    void lesShapeAppearanceJoPortentLesRayons() throws Exception {
        Map<String, Map<String, String>> styles = styles(
                RES.resolve("values").resolve("styles.xml").toFile());
        Map<String, String> dimens = enfants(
                document(RES.resolve("values").resolve("dimens.xml").toFile()),
                "dimen");
        assertEquals(dp(Graphite.RAYON_COMPOSANT),
                dimens.get(styleCorner(styles, "ShapeAppearance.Jo.Composant")
                        .substring("@dimen/".length())));
        assertEquals(dp(Graphite.RAYON_CARTE),
                dimens.get(styleCorner(styles, "ShapeAppearance.Jo.Carte")
                        .substring("@dimen/".length())));
        assertEquals(dp(Graphite.RAYON_PUCE),
                dimens.get(styleCorner(styles, "ShapeAppearance.Jo.Pilule")
                        .substring("@dimen/".length())));
    }

    @Test
    void leSeuilGrandEcranEstCoherentAvecLEtat() throws Exception {
        assertEquals("false", enfants(document(
                        RES.resolve("values").resolve("bools.xml").toFile()),
                "bool").get("est_grand_ecran"),
                "l'étroit écran est le cas général");
        String attendu = "values-w" + EtatAtelier.SEUIL_GRAND_ECRAN_DP + "dp";
        Path dossier = RES.resolve(attendu);
        assertTrue(Files.isDirectory(dossier),
                "le qualificatif de largeur doit égaler "
                        + "EtatAtelier.SEUIL_GRAND_ECRAN_DP : " + attendu
                        + " (w, PAS sw — suit rotation et multi-fenêtre)");
        assertEquals("true", enfants(document(
                        dossier.resolve("bools.xml").toFile()), "bool")
                        .get("est_grand_ecran"),
                "la largeur disponible atteinte signifie grand écran");
        // aucun autre qualificatif de largeur ne doit exister : une seule
        // source de vérité pour le seuil
        try (var parcours = Files.list(RES)) {
            List<Path> variantes = parcours.filter(d -> {
                String nom = d.getFileName().toString();
                return nom.startsWith("values-w") && nom.endsWith("dp");
            }).toList();
            assertEquals(List.of(dossier), variantes,
                    "un seul qualificatif de largeur — celui du seuil");
        }
    }

    @Test
    void lesStylesDuLotParametresOntUnParentM3EtDesJetonsExistants()
            throws Exception {
        // ADR-085 §2 : tout nouveau jeton reçoit sa parité — lot A3
        // (maquette parametres.html, 1 px = 1 dp et 1 px = 1 sp)
        Map<String, Map<String, String>> styles = styles(
                RES.resolve("values").resolve("styles.xml").toFile());
        Map<String, String> dimens = enfants(
                document(RES.resolve("values").resolve("dimens.xml").toFile()),
                "dimen");

        // Titre de la sous-barre : 20 gras — .titre de la maquette ;
        // interligne à la cadence Graphite (+6, comme SOUS_TITRE 16/22
        // et TITRE_ECRAN 28/34 — l'interligne hérité 22 du corps de la
        // maquette n'est pas un couple Graphite)
        Map<String, String> barre = styles.get("TextAppearance.Jo.TitreBarre");
        assertNotNull(barre, "TextAppearance.Jo.TitreBarre doit exister");
        assertEquals("TextAppearance.Material3.TitleLarge",
                barre.get("_parent"),
                "le titre de barre hérite d'un TextAppearance.Material3.*");
        assertEquals("@dimen/jo_texte_titre_barre",
                barre.get("android:textSize"));
        assertEquals("@dimen/jo_texte_titre_barre_interligne",
                barre.get("android:lineHeight"));
        assertEquals("20sp", dimens.get("jo_texte_titre_barre"),
                "le .titre de la maquette : 20 px = 20 sp");
        assertEquals("26sp", dimens.get("jo_texte_titre_barre_interligne"));

        // Nom de section : l'échelle LEGENDE (12/16 par héritage) en
        // graisse 700 espacée de 0,07 — le .nom-sect de la maquette
        Map<String, String> nom = styles.get("TextAppearance.Jo.NomSection");
        assertNotNull(nom, "TextAppearance.Jo.NomSection doit exister");
        assertEquals("TextAppearance.Jo.Legende", nom.get("_parent"),
                "l'échelle du nom de section est la légende Graphite");
        assertEquals("bold", nom.get("android:textStyle"));
        assertEquals("0.07", nom.get("android:letterSpacing"));

        // Bascule (Segment remplacé, D2) : case pilule teintée par
        // ColorStateList, paddings 6/13 et écart 3 de la maquette
        Map<String, String> bascule = styles.get("Widget.Jo.Bascule");
        assertNotNull(bascule, "Widget.Jo.Bascule doit exister");
        assertEquals("Widget.Material3.Button.OutlinedButton",
                bascule.get("_parent"),
                "la bascule remplace le kit Segment par un MaterialButton");
        assertEquals("@color/bascule_texte", bascule.get("android:textColor"));
        assertEquals("@color/bascule_fond",
                bascule.get("android:backgroundTint"));
        assertEquals("@dimen/jo_pad_bascule_cote",
                bascule.get("android:paddingLeft"));
        assertEquals("@dimen/jo_pad_bascule_cote",
                bascule.get("android:paddingRight"));
        assertEquals("@dimen/jo_pad_bascule_haut",
                bascule.get("android:paddingTop"));
        assertEquals("13dp", dimens.get("jo_pad_bascule_cote"),
                ".segment span : padding 6px 13px de la maquette");
        assertEquals("6dp", dimens.get("jo_pad_bascule_haut"));
        assertEquals("3dp", dimens.get("jo_pad_bascule"),
                ".segment : padding 3px du conteneur");
        assertEquals("3dp", dimens.get("jo_ecart_bascule"),
                ".segment : gap 3px de la maquette");

        // Commutateur (Interrupteur remplacé, D2) : MaterialSwitch teinté
        // par les trois ColorStateList du kit d'hier
        Map<String, String> interrupteur = styles.get(
                "Widget.Jo.Interrupteur");
        assertNotNull(interrupteur, "Widget.Jo.Interrupteur doit exister");
        assertEquals("Widget.Material3.CompoundButton.MaterialSwitch",
                interrupteur.get("_parent"),
                "le commutateur remplace le kit Interrupteur");
        assertEquals("@color/interrupteur_pouce",
                interrupteur.get("thumbTint"));
        assertEquals("@color/interrupteur_piste",
                interrupteur.get("trackTint"));
        assertEquals("@color/interrupteur_lisere",
                interrupteur.get("trackDecorationTint"));
    }

    @Test
    void lesStylesDuLotCreationOntUnParentM3EtDesJetonsExistants()
            throws Exception {
        // ADR-085 §2 : tout nouveau jeton reçoit sa parité — lot A4
        // (maquette creation-projet.html, 1 px = 1 dp et 1 px = 1 sp ;
        // les valeurs du kit/code d'hier font l'identité)
        Map<String, Map<String, String>> styles = styles(
                RES.resolve("values").resolve("styles.xml").toFile());
        Map<String, String> dimens = enfants(
                document(RES.resolve("values").resolve("dimens.xml").toFile()),
                "dimen");

        // Étiquette de champ (.etiq : la légende 12/16 en graisse 600
        // rapprochée par sans-serif-medium — nuance documentée)
        Map<String, String> etiquette = styles.get(
                "TextAppearance.Jo.Etiquette");
        assertNotNull(etiquette,
                "TextAppearance.Jo.Etiquette doit exister");
        assertEquals("TextAppearance.Jo.Legende",
                etiquette.get("_parent"),
                "l'étiquette hérite de la légende Graphite (12/16)");
        assertEquals("sans-serif-medium", etiquette.get("android:fontFamily"));

        // Numéro d'étape (.etape .num : 11/16 gras dans le rond 20 dp)
        Map<String, String> numero = styles.get(
                "TextAppearance.Jo.NumEtape");
        assertNotNull(numero, "TextAppearance.Jo.NumEtape doit exister");
        assertEquals("TextAppearance.Material3.LabelSmall",
                numero.get("_parent"),
                "le numéro d'étape hérite d'un TextAppearance.Material3.*");
        assertEquals("bold", numero.get("android:textStyle"));
        assertEquals("@dimen/jo_texte_num_etape",
                numero.get("android:textSize"));
        assertEquals("@dimen/jo_texte_num_etape_interligne",
                numero.get("android:lineHeight"));
        assertEquals("11sp", dimens.get("jo_texte_num_etape"),
                "le .num de la maquette : 11 px = 11 sp");
        assertEquals("16sp",
                dimens.get("jo_texte_num_etape_interligne"),
                "l'interligne du .num suit la cadence de la légende");

        // Puce Chip (Puce du kit remplacée, D2) : pilule note 13/18,
        // paddings 14/7 du kit, teintes de ChoixPuce en res/color
        Map<String, String> puce = styles.get("Widget.Jo.Puce");
        assertNotNull(puce, "Widget.Jo.Puce doit exister");
        assertEquals("Widget.Material3.Chip.Filter", puce.get("_parent"),
                "la puce remplace le kit Puce par un Chip Material");
        assertEquals("@color/puce_texte", puce.get("android:textColor"));
        assertEquals("@color/puce_fond", puce.get("chipBackgroundColor"));
        assertEquals("@color/puce_bord", puce.get("chipStrokeColor"));
        assertEquals("@dimen/jo_pad_puce_cote", puce.get("android:paddingLeft"));
        assertEquals("@dimen/jo_pad_puce_cote",
                puce.get("android:paddingRight"));
        assertEquals("@dimen/jo_pad_puce_haut", puce.get("android:paddingTop"));
        assertEquals("@dimen/jo_pad_puce_haut",
                puce.get("android:paddingBottom"));
        assertEquals("false", puce.get("checkedIconVisible"),
                "la maquette ne montre pas de coche : la couleur porte "
                        + "la sélection");
        assertEquals("14dp", dimens.get("jo_pad_puce_cote"),
                "le padding horizontal du kit Puce");
        assertEquals("7dp", dimens.get("jo_pad_puce_haut"),
                "le padding vertical du kit Puce");
        assertEquals("6dp", dimens.get("jo_ecart_puce"),
                "l'écart des puces du kit (marginRight d'hier)");
        assertEquals("14dp", dimens.get("jo_rayon_arbo"),
                "le .arbo de la maquette : rayon 14 px = 14 dp");

        // Élévation des cartes Material (convention posée au pilote A2,
        // factorisée au lot A4 : la carte de modèle en est le TROISIÈME
        // consommateur — contrainte §6, dimension répétée ≥ 3 fois en
        // @dimen, les cartes projet/reprise de l'accueil suivent)
        assertEquals("3dp", dimens.get("jo_elevation_carte"),
                "l'élévation 3 dp des cartes Material (A2/A4)");
    }

    @Test
    void lesJetonsDuLotA5OntLeursDimensEtLeurSelecteur()
            throws Exception {
        // ADR-085 §2 : tout nouveau jeton reçoit sa parité — lot A5
        // (vues d'atelier : console, aperçu, arbre, listes de la
        // feuille) ; les valeurs du CODE d'hier (lots K/L/L3/L4) font
        // l'identité, la maquette comble ses trous (le perchoir 24 px =
        // 24 dp d'apercu.html)
        Map<String, String> dimens = enfants(
                document(RES.resolve("values").resolve("dimens.xml")
                        .toFile()),
                "dimen");

        // Console (lot K) : retrait 14, aération 7 (= marge/2), point
        // de la pilule 6 (taille ET écart), ligne de problème (rayon
        // 8, padding 2) ; reprise d'audit — pilule d'état (retrait
        // vertical 3), icône d'arrêt (34, écart 6), écart de phase 8,
        // colonne nature (64, écart 10), bouton Ouvrir (écart 10)
        assertEquals("14dp", dimens.get("jo_pad_console"),
                "le retrait latéral du lot K");
        assertEquals("7dp", dimens.get("jo_ecart_console"),
                "l'aération du lot K (marge/2)");
        assertEquals("6dp", dimens.get("jo_cote_point_etat"),
                "le point de la pilule d'état (taille et écart)");
        assertEquals("8dp", dimens.get("jo_rayon_ligne_probleme"),
                "le rayon de la ligne de problème du lot K");
        assertEquals("2dp", dimens.get("jo_pad_ligne_probleme"),
                "le padding de la ligne de problème du lot K");
        assertEquals("3dp", dimens.get("jo_pad_pilule_etat"),
                "reprise d'audit : le retrait vertical de la pilule "
                        + "d'état (littéral factorisé, §6)");
        assertEquals("34dp", dimens.get("jo_cote_icone_console"),
                "reprise d'audit : l'icône d'arrêt du lot K");
        assertEquals("6dp", dimens.get("jo_ecart_icone_console"),
                "reprise d'audit : l'écart avant l'icône d'arrêt");
        assertEquals("8dp", dimens.get("jo_ecart_phase"),
                "reprise d'audit : l'écart des pilules de phase");
        assertEquals("64dp", dimens.get("jo_largeur_nature_console"),
                "reprise d'audit : la colonne nature du transcript");
        assertEquals("10dp", dimens.get("jo_ecart_nature_console"),
                "reprise d'audit : l'écart après la nature");
        assertEquals("10dp", dimens.get("jo_ecart_bouton_probleme"),
                "reprise d'audit : l'écart au-dessus du bouton Ouvrir");

        // Aperçu (lot L) : retrait 12, aération 6, icônes 32 écartées
        // de 4, perchoir 24 (maquette .perchoir), bannière (padding
        // 10, rayon 9), damier 8 ; reprise d'audit — écart des puces
        // 8, marge du perchoir 10, écart des bannières 6
        assertEquals("12dp", dimens.get("jo_pad_volet_apercu"));
        assertEquals("6dp", dimens.get("jo_ecart_volet_apercu"));
        assertEquals("32dp", dimens.get("jo_cote_icone_apercu"));
        assertEquals("4dp", dimens.get("jo_ecart_icones_apercu"));
        assertEquals("24dp", dimens.get("jo_rayon_perchoir"),
                "le .perchoir de la maquette ADR-É7 : 24 px = 24 dp");
        assertEquals("10dp", dimens.get("jo_pad_banniere_apercu"),
                "le padding de la bannière du lot L");
        assertEquals("9dp", dimens.get("jo_rayon_banniere"),
                "le rayon de la bannière du lot L");
        assertEquals("8dp", dimens.get("jo_cote_case_damier"),
                "la case du damier (lot L : cases 8 dp, deux gris)");
        assertEquals("8dp", dimens.get("jo_ecart_puce_apercu"),
                "reprise d'audit : l'écart des puces nuit/dimensions");
        assertEquals("10dp", dimens.get("jo_ecart_perchoir"),
                "reprise d'audit : la marge du perchoir du lot L");
        assertEquals("6dp", dimens.get("jo_ecart_banniere_apercu"),
                "reprise d'audit : l'écart des bannières du lot L");

        // Arbre (lot L4) : en-tête 16, notes 20, pastille 6, ligne
        // active 10 ; reprise d'audit — dessous de la note de sorties
        // 10, dessus de la note de troncature 8, écarts d'en-tête de
        // groupe (au-dessus 8, dessous 2)
        assertEquals("16dp", dimens.get("jo_pad_arbre"),
                "le retrait d'en-tête du lot L4");
        assertEquals("20dp", dimens.get("jo_retrait_note_arbre"),
                "le retrait des notes du lot L4");
        assertEquals("6dp", dimens.get("jo_rayon_pastille_noeud"),
                "le rayon de la pastille de type (§3.8)");
        assertEquals("10dp", dimens.get("jo_rayon_ligne_active"),
                "le rayon de la ligne active (§3.8)");
        assertEquals("10dp", dimens.get("jo_ecart_note_sorties"),
                "reprise d'audit : le dessous de la note de sorties");
        assertEquals("8dp", dimens.get("jo_ecart_note_tronquee"),
                "reprise d'audit : le dessus de la note de troncature");
        assertEquals("8dp", dimens.get("jo_ecart_entete_groupe"),
                "reprise d'audit : l'écart au-dessus d'un en-tête de "
                        + "groupe non premier (lot L3)");
        assertEquals("2dp", dimens.get("jo_ecart_entete_groupe_bas"),
                "reprise d'audit : le dessous de l'en-tête de groupe "
                        + "(lot L3)");

        // Le sélecteur de la ligne de problème du transcript : erreur
        // à 10 % par ?attr (le mélange du lot K porté par l'alpha de
        // l'item — le puits transparaît derrière)
        assertTrue(Files.isRegularFile(RES.resolve("color")
                        .resolve("fond_ligne_probleme.xml")),
                "fond_ligne_probleme.xml doit exister (res/color)");
        NodeList items = document(RES.resolve("color")
                        .resolve("fond_ligne_probleme.xml").toFile())
                .getElementsByTagName("item");
        assertEquals(1, items.getLength());
        assertEquals("?attr/jo_erreur",
                ((Element) items.item(0)).getAttribute("android:color"));
        assertEquals("0.10",
                ((Element) items.item(0)).getAttribute("android:alpha"),
                "le mélange erreur 10 % du lot K, l'alpha porte l'item");
    }

    @Test
    void lesSelecteursDeCouleurNePortentQueDesAttrsJo()
            throws Exception {
        // lots A3/A4/A5 : les teintes des bascules, du commutateur, des
        // puces Chip, de la barre d'étapes et de la ligne de problème du
        // transcript vivent en res/color — chaque couleur est un
        // ?attr/jo_* DECLARÉ comme couleur de
        // thème (JetonsXmlTest garantit que les thèmes posent chaque
        // attr) ; jamais une couleur littérale, l'alpha est porté
        // par l'item
        Map<String, String> attrs = attrsCouleurDeTheme();
        Path couleurs = RES.resolve("color");
        assertTrue(Files.isDirectory(couleurs),
                "res/color doit exister (teintes des lots A3/A4)");
        List<String> manquants = new ArrayList<>();
        try (var parcours = Files.list(couleurs)) {
            List<Path> selecteurs = parcours.filter(
                            p -> p.toString().endsWith(".xml"))
                    .sorted().toList();
            assertEquals(12, selecteurs.size(),
                    "les douze sélecteurs des lots A3, A4 et A5 (bascule "
                            + "texte/fond, interrupteur piste/liséré/pouce, "
                            + "puce fond/texte/bord, étape "
                            + "numéro/libellé/jalon, ligne de problème du "
                            + "transcript console)");
            for (Path selecteur : selecteurs) {
                NodeList items = document(selecteur.toFile())
                        .getElementsByTagName("item");
                assertTrue(items.getLength() > 0,
                        selecteur + " doit porter des items de couleur");
                for (int i = 0; i < items.getLength(); i++) {
                    Element item = (Element) items.item(i);
                    String couleur = item.getAttribute("android:color");
                    assertTrue(couleur.startsWith("?attr/jo_"),
                            selecteur + " : la couleur doit être un "
                                    + "?attr/jo_* : " + couleur);
                    String attr = couleur.substring("?attr/jo_".length());
                    if (!attrs.containsKey("jo_" + attr)) {
                        manquants.add(selecteur.getFileName()
                                + " → ?attr/jo_" + attr);
                    }
                }
            }
        }
        assertTrue(manquants.isEmpty(),
                () -> "attrs non déclarés dans attrs.xml (l'inflation "
                        + "échouerait) : " + manquants);
    }

    // ---- lectures et utilitaires -----------------------------------

    /**
     * Les attrs de thème jo_* de format couleur, enfants DIRECTS de
     * {@code <resources>} (les attrs des styleables — glyphe, tuile —
     * ne sont pas des couleurs de thème).
     */
    private static Map<String, String> attrsCouleurDeTheme()
            throws Exception {
        Map<String, String> attrs = new LinkedHashMap<>();
        NodeList noeuds = document(
                RES.resolve("values").resolve("attrs.xml").toFile())
                .getElementsByTagName("attr");
        for (int i = 0; i < noeuds.getLength(); i++) {
            Element attr = (Element) noeuds.item(i);
            if (!"resources".equals(attr.getParentNode().getNodeName())) {
                continue;
            }
            String format = attr.getAttribute("format");
            if (format.contains("color")) {
                attrs.put(attr.getAttribute("name"), format);
            }
        }
        return attrs;
    }

    /** Le cornerSize @dimen/… d'un style de forme. */
    private static String styleCorner(Map<String, Map<String, String>> styles,
                                       String nom) {
        Map<String, String> style = styles.get(nom);
        assertNotNull(style, nom + " doit exister dans styles.xml");
        String coin = style.get("cornerSize");
        assertTrue(coin != null && coin.startsWith("@dimen/"),
                nom + " doit porter cornerSize @dimen/");
        return coin;
    }

    /** name → (items) de chaque style d'un fichier de styles. */
    private static Map<String, Map<String, String>> styles(File xml)
            throws Exception {
        Map<String, Map<String, String>> styles = new LinkedHashMap<>();
        NodeList noeuds = document(xml).getElementsByTagName("style");
        for (int i = 0; i < noeuds.getLength(); i++) {
            Element style = (Element) noeuds.item(i);
            Map<String, String> items = new LinkedHashMap<>();
            String parent = style.getAttribute("parent");
            if (!parent.isEmpty()) {
                items.put("_parent", parent);
            }
            NodeList enfants = style.getElementsByTagName("item");
            for (int j = 0; j < enfants.getLength(); j++) {
                Element item = (Element) enfants.item(j);
                items.put(item.getAttribute("name"),
                        item.getTextContent().trim());
            }
            styles.put(style.getAttribute("name"), items);
        }
        return styles;
    }

    /** name → valeur des enfants d'une balise donnée. */
    private static Map<String, String> enfants(Document doc, String balise) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        NodeList noeuds = doc.getElementsByTagName(balise);
        for (int i = 0; i < noeuds.getLength(); i++) {
            Element e = (Element) noeuds.item(i);
            valeurs.put(e.getAttribute("name"),
                    e.getTextContent().trim());
        }
        return valeurs;
    }

    private static Document document(File xml) throws Exception {
        DocumentBuilderFactory fabrique =
                DocumentBuilderFactory.newInstance();
        fabrique.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        return fabrique.newDocumentBuilder().parse(xml);
    }

    /** Une dimension Graphite en dp, telle qu'elle vit en XML. */
    private static String dp(int valeur) {
        return valeur + "dp";
    }

    /** Une taille de texte Graphite en sp (« 13.5sp »). */
    private static String sp(Graphite.StyleTexte style) {
        return nombre(style.taille) + "sp";
    }

    /** Un interligne Graphite en sp. */
    private static String spInterligne(Graphite.StyleTexte style) {
        return nombre(style.interligne) + "sp";
    }

    /** Le nombre sans zéro final parasite (12.0 → 12 ; 13.5 → 13.5). */
    private static String nombre(float valeur) {
        return valeur == (long) valeur
                ? String.valueOf((long) valeur)
                : String.valueOf(valeur);
    }
}
