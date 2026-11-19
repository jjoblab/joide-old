package jo.joide.app.android.ecrans.atelier;

import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.FichiersProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.Arborescence;
import jo.joide.app.android.atelier.NouveauNom;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.PopupAncre;
import jo.joide.ide.langages.EtatIndex;

/**
 * L6 — le contrôleur de l'arbre de fichiers : rechargement, rendu,
 * menu contextuel (dupliquer, renommer, supprimer), création,
 * localisation, pilule d'index. La vérité reste dans le Workspace et
 * le disque ; les corps viennent de l'écran d'hier sans changement de
 * comportement (extraction mécanique, mêmes commentaires).
 *
 * <p>Lot A6 (ADR-085 décision 3) : le contrôleur dépend de
 * {@link HoteAtelier} — plus un champ d'écran. Les menus du lot L4
 * (popup et dialogue) restent TELS QUELS : le lot A7 les migre en
 * {@code popup_*.xml}.</p>
 */
final class ControleurFichiers {

    private final HoteAtelier hote;

    ControleurFichiers(HoteAtelier hote) {
        this.hote = hote;
    }

    void rechargerArbre() {
        if (hote.racineProjet() == null) {
            return;
        }
        hote.fond().execute(() -> {
            try {
                List<String> fichiers = FichiersProjet.lister(
                        hote.racineProjet());
                boolean sorties = Files.isDirectory(
                        hote.racineProjet().resolve(
                                Arborescence.NOM_SORTIES));
                hote.executerSurFilUi(() -> {
                    hote.poserFichiersArbre(fichiers);
                    hote.poserSortiesPresentes(sorties);
                    majArbre();
                });
            } catch (IOException e) {
                // l'arbre reste l'état précédent : jamais un arbre vide
                // muet — le prochain accès le rechargera
            }
        });
    }

    void majArbre() {
        if (hote.vueArbre() == null || hote.racineProjet() == null) {
            return;
        }
        hote.vueArbre().setSortiesEnCours(hote.buildEnCours());
        List<String> retenus = Arborescence.cheminsFiltres(
                hote.fichiersArbre(), hote.filtreTiroir());
        hote.vueArbre().setArbre(Arborescence.composer(retenus,
                String.valueOf(
                        hote.racineProjet().getFileName()),
                hote.filtreTiroir().isBlank()
                        ? hote.dossiersOuverts()
                        : Arborescence.ancetresDe(retenus),
                hote.etat().actif(), hote.sortiesPresentes()),
                String.valueOf(
                        hote.racineProjet().getFileName()));
    }

    /**
     * Lot L4 (ADR-083) — le menu contextuel de l'arbre (§3.8,
     * appui long) : des VERBES RÉELS — nouveau fichier/dossier ici,
     * renommer (déplacement), dupliquer (copie « (copie) »),
     * supprimer (confirmation, l'onglet ouvert se ferme). Franchise :
     * jamais un item mort.
     */
    void montrerMenuContextuel(String chemin, boolean dossier) {
        List<String> verbes = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        verbes.add(hote.texte(R.string.atelier_nouveau_fichier));
        actions.add(() -> proposerNouveau(true));
        verbes.add(hote.texte(R.string.atelier_nouveau_dossier));
        actions.add(() -> proposerNouveau(false));
        if (!dossier) {
            verbes.add(hote.texte(R.string.atelier_dupliquer));
            actions.add(() -> dupliquerEntree(chemin));
            verbes.add(hote.texte(R.string.atelier_renommer));
            actions.add(() -> renommerEntree(chemin));
        }
        verbes.add(hote.texte(R.string.atelier_supprimer));
        actions.add(() -> supprimerEntree(chemin, dossier));
        // lot A6 (reprise d'audit) : les noms restent NON qualifiés —
        // le filet ArchitectureVuesTest compte CES vues (exception
        // décroissante, le lot A7 les sort en popup_*.xml) ; une forme
        // qualifiée serait une évasion du filet, pas une correction
        LinearLayout contenu = new LinearLayout(hote.contexte());
        contenu.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < verbes.size(); i++) {
            String verbe = verbes.get(i);
            Runnable action = actions.get(i);
            View ligne = PopupAncre.ligneMenu(
                    hote.contexte(), hote.palette(), verbe,
                    () -> {
                        fenetreMenuArbre.dismiss();
                        action.run();
                    });
            contenu.addView(ligne);
        }
        android.view.View vueArbreAncre = hote.fenetre()
                .getDecorView()
                .findViewById(android.R.id.content);
        fenetreMenuArbre = PopupAncre.montrer(
                hote.vueArbre() != null ? hote.vueArbre()
                        : vueArbreAncre,
                hote.palette(), contenu,
                GraphiteVue.dp(hote.contexte(), 220));
    }

    /** Duplique un fichier du projet : la copie vit à côté. */
    private void dupliquerEntree(String chemin) {
        try {
            java.nio.file.Path source = java.nio.file.Path.of(chemin);
            String nom = source.getFileName().toString();
            int point = nom.lastIndexOf('.');
            String copie = point < 0 ? nom + " (copie)"
                    : nom.substring(0, point) + " (copie)"
                    + nom.substring(point);
            java.nio.file.Path cible = source.resolveSibling(copie);
            java.nio.file.Files.copy(source, cible,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            rechargerArbre();
        } catch (java.io.IOException e) {
            Dialogue.information(hote.contexte(), hote.palette(),
                    hote.texte(R.string.atelier_dupliquer),
                    String.valueOf(e.getMessage()), R.string.action_ok);
        }
    }

    /** Renomme un fichier du projet (déplacement au même dossier) :
     *  la saisie suit le même dialogue que la création, la validation
     *  NouveauNom incluse. */
    private void renommerEntree(String chemin) {
        EditText saisie = new EditText(hote.contexte());
        saisie.setHint(R.string.atelier_renommer);
        Dialogue.saisie(hote.contexte(), hote.palette(),
                hote.texte(R.string.atelier_renommer),
                saisie, R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }
                    String nom = saisie.getText().toString().trim();
                    NouveauNom.Raison raison = NouveauNom.valider(nom);
                    if (raison != null) {
                        Dialogue.information(hote.contexte(),
                                hote.palette(),
                                hote.texte(R.string.atelier_renommer),
                                texteRaisonNom(raison),
                                R.string.action_ok);
                        return;
                    }
                    try {
                        java.nio.file.Path source =
                                java.nio.file.Path.of(chemin);
                        java.nio.file.Files.move(source,
                                source.resolveSibling(nom),
                                java.nio.file.StandardCopyOption
                                        .ATOMIC_MOVE);
                        rechargerArbre();
                    } catch (java.io.IOException e) {
                        Dialogue.information(hote.contexte(),
                                hote.palette(),
                                hote.texte(R.string.atelier_renommer),
                                String.valueOf(e.getMessage()),
                                R.string.action_ok);
                    }
                });
    }

    /** Supprime (confirmation ; l'onglet ouvert se ferme, §3.8). */
    private void supprimerEntree(String chemin, boolean dossier) {
        Dialogue.confirmation(hote.contexte(), hote.palette(),
                hote.texte(R.string.atelier_supprimer), chemin,
                R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    try {
                        java.nio.file.Path cible =
                                java.nio.file.Path.of(chemin);
                        if (dossier) {
                            supprimerRecursivement(cible);
                        } else {
                            java.nio.file.Files.deleteIfExists(cible);
                        }
                        if (hote.fichierCourant() != null
                                && hote.fichierCourant()
                                .startsWith(chemin)) {
                            hote.controleurEdition().fermerOnglet(
                                    hote.fichierCourant());
                        }
                        rechargerArbre();
                    } catch (java.io.IOException e) {
                        Dialogue.information(hote.contexte(),
                                hote.palette(),
                                hote.texte(R.string.atelier_supprimer),
                                String.valueOf(e.getMessage()),
                                R.string.action_ok);
                    }
                });
    }

    /**
     * Lot L4 (ADR-083) — « localiser le fichier actif » (§3.8) : les
     * dossiers ancêtres s'ouvrent et l'arbre se redessine avec le
     * filtre levé (on cherche à VOIR le fichier, pas à filtrer).
     */
    void localiserFichierActif() {
        if (hote.fichierCourant() == null) {
            return;
        }
        String relatif = hote.fichierCourant();
        String prefixe = hote.racineProjet() == null ? ""
                : hote.racineProjet().toString();
        if (relatif.startsWith(prefixe)) {
            relatif = relatif.substring(prefixe.length());
            while (relatif.startsWith("/")) {
                relatif = relatif.substring(1);
            }
        }
        hote.poserFiltreTiroir("");
        if (hote.tiroirFichiers() != null) {
            hote.tiroirFichiers().champFiltre().setText("");
        }
        hote.dossiersOuverts().addAll(
                Arborescence.ancetresDe(List.of(relatif)));
        majArbre();
    }

    /** La suppression récursive d'un dossier du projet. */
    private static void supprimerRecursivement(java.nio.file.Path racine)
            throws java.io.IOException {
        try (var chemins = java.nio.file.Files.walk(racine)) {
            chemins.sorted(java.util.Comparator.reverseOrder())
                    .forEach(chemin -> {
                        try {
                            java.nio.file.Files.deleteIfExists(chemin);
                        } catch (java.io.IOException ignore) {
                            // au mieux : le suivant continue
                        }
                    });
        }
    }

    /** La fenêtre du menu contextuel de l'arbre (fermée sur action). */
    private android.widget.PopupWindow fenetreMenuArbre;

    void proposerNouveau(boolean fichier) {
        String dossier = hote.etat().actif() == null
                ? "" : Arborescence.dossierDe(hote.etat().actif());
        String affichage = dossier.isEmpty()
                ? String.valueOf(
                hote.racineProjet().getFileName()) : dossier;
        EditText saisie = new EditText(hote.contexte());
        saisie.setHint(fichier
                ? R.string.atelier_nouveau_fichier
                : R.string.atelier_nouveau_dossier);
        Dialogue.saisie(hote.contexte(), hote.palette(),
                hote.texte(R.string.atelier_nouveau_dans, affichage),
                saisie, R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel == DialogInterface.BUTTON_POSITIVE) {
                        creerEntree(saisie.getText().toString().trim(),
                                fichier, dossier);
                    }
                });
    }

    private void creerEntree(String nom, boolean fichier, String dossier) {
        NouveauNom.Raison raison = NouveauNom.valider(nom);
        if (raison != null) {
            Dialogue.information(hote.contexte(), hote.palette(),
                    hote.texte(R.string.atelier_nouveau),
                    texteRaisonNom(raison), R.string.action_ok);
            return;
        }
        Path cible = hote.racineProjet().resolve(
                dossier.isEmpty() ? nom : dossier + nom);
        if (Files.exists(cible)) {
            Dialogue.information(hote.contexte(), hote.palette(),
                    hote.texte(R.string.atelier_nouveau),
                    hote.texte(R.string.atelier_existe_deja, nom),
                    R.string.action_ok);
            return;
        }
        try {
            if (fichier) {
                Files.createDirectories(cible.getParent());
                Files.write(cible, new byte[0]);
            } else {
                Files.createDirectories(cible);
            }
        } catch (IOException e) {
            Dialogue.information(hote.contexte(), hote.palette(),
                    hote.texte(R.string.atelier_nouveau),
                    hote.texte(R.string.atelier_creation_echouee,
                            String.valueOf(e.getMessage())),
                    R.string.action_ok);
            return;
        }
        rechargerArbre();
        if (fichier) {
            String relatif = hote.racineProjet().relativize(cible)
                    .toString().replace('\\', '/');
            hote.controleurEdition().ouvrirFichier(relatif, null);
        }
    }

    private String texteRaisonNom(NouveauNom.Raison raison) {
        return switch (raison) {
            case VIDE -> hote.texte(R.string.atelier_nom_err_vide);
            case TROP_LONG -> hote.texte(
                    R.string.atelier_nom_err_long);
            case SEPARATEUR -> hote.texte(
                    R.string.atelier_nom_err_separateur);
            case POINT -> hote.texte(
                    R.string.atelier_nom_err_point);
            case CONTROLE -> hote.texte(
                    R.string.atelier_nom_err_controle);
            case POINT_FINAL -> hote.texte(
                    R.string.atelier_nom_err_point_final);
        };
    }

    // ------------------------------------------------------------------
    // Index de noms (pilule honnête)
    // ------------------------------------------------------------------

    void majPiluleIndex() {
        if (hote.piluleIndex() == null || hote.moteurs() == null) {
            return;
        }
        EtatIndex index = hote.moteurs().etatIndex();
        hote.piluleIndex().setEtat(
                hote.texte(R.string.atelier_index, index.phase()),
                Pilule.Role.NEUTRE);
    }

    void montrerDetailIndex() {
        if (hote.moteurs() == null) {
            return;
        }
        EtatIndex index = hote.moteurs().etatIndex();
        majPiluleIndex();
        Dialogue.information(hote.contexte(), hote.palette(),
                hote.texte(R.string.atelier_index, index.phase()),
                hote.texte(R.string.atelier_index_detail, index.phase(),
                        index.total(), index.faits(),
                        index.avertissements().size(),
                        index.erreurs().size()),
                R.string.action_ok);
    }
}
