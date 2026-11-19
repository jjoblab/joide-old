package jo.joide.app.android.ecrans.atelier;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.FichiersProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.Arborescence;
import jo.joide.app.android.atelier.NouveauNom;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.PortePopups;
import jo.joide.ide.langages.EtatIndex;

/**
 * L6 — le contrôleur de l'arbre de fichiers : rechargement, rendu,
 * menu contextuel (dupliquer, renommer, supprimer), création,
 * localisation, pilule d'index. La vérité reste dans le Workspace et
 * le disque.
 *
 * <p>Lot A7 (ADR-085, D3) : le menu contextuel devient le MENU ANCRÉ
 * ({@code popup_menu_noeud} gonflé par le porte-popups), les saisies
 * et confirmations deviennent les cartes centrées
 * ({@code popup_saisie}/{@code popup_confirmation} sur voile) — les
 * {@code EditText} d'hier disparaissent du contrôleur (le champ vit
 * dans {@code champ_saisie_param} inclus par le popup), le brouillon
 * de saisie vit dans {@code EtatPopups} (l'état pur). Plus AUCUNE vue
 * du cadre n'est instanciée ici.</p>
 *
 * <p>Lot A6 (ADR-085 décision 3) : le contrôleur dépend de
 * {@link HoteAtelier} — plus un champ d'écran.</p>
 */
final class ControleurFichiers {

    /** La clé du brouillon de la saisie « Nouveau fichier/dossier ». */
    private static final String BROUILLON_NOUVEAU = "fichiers.nouveau";

    /** La clé du brouillon de la saisie « Renommer ». */
    private static final String BROUILLON_RENOMME = "fichiers.renomme";

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
     * jamais un item mort. Lot A7 : le MENU DE NŒUD ancré, gonflé de
     * {@code popup_menu_noeud} par le porte-popups.
     */
    void montrerMenuContextuel(String chemin, boolean dossier) {
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.atelier_nouveau_fichier),
                () -> proposerNouveau(true)));
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.atelier_nouveau_dossier),
                () -> proposerNouveau(false)));
        if (!dossier) {
            entrees.add(new PortePopups.EntreeMenu(
                    hote.texte(R.string.atelier_dupliquer),
                    () -> dupliquerEntree(chemin)));
            entrees.add(new PortePopups.EntreeMenu(
                    hote.texte(R.string.atelier_renommer),
                    () -> renommerEntree(chemin)));
        }
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.atelier_supprimer),
                () -> supprimerEntree(chemin, dossier)));
        hote.popups().menu(hote.vueArbre(), null, 220, entrees);
    }

    /** Duplique un fichier du projet : la copie vit à côté. */
    private void dupliquerEntree(String chemin) {
        try {
            // Paths.get (API 26) et non Path.of (API 34) : minSdk 28,
            // un Android 13 ne doit pas mourir d'un NoSuchMethodError
            // au premier duplicata (lot C1, la dette NewApi du lint)
            java.nio.file.Path source = java.nio.file.Paths.get(chemin);
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
            hote.popups().information(
                    hote.texte(R.string.atelier_dupliquer),
                    String.valueOf(e.getMessage()),
                    hote.texte(R.string.action_ok));
        }
    }

    /**
     * Renomme un fichier du projet (déplacement au même dossier) :
     * la saisie suit le même popup que la création, la validation
     * NouveauNom incluse — le BROUILLON vit dans l'état pur (le popup
     * n'est jamais restauré, le texte frappé survit).
     */
    private void renommerEntree(String chemin) {
        EditText champ = hote.popups().saisie(
                hote.texte(R.string.atelier_renommer), null,
                hote.texte(R.string.action_annuler),
                hote.texte(R.string.action_ok),
                saisie -> renommer(chemin, saisie.getText()
                        .toString().trim()));
        champ.setHint(R.string.atelier_renommer);
        String brouillon = hote.etatPopups().brouillon(BROUILLON_RENOMME);
        if (brouillon != null) {
            champ.setText(brouillon);
        }
        champ.addTextChangedListener(brouillon(BROUILLON_RENOMME));
    }

    /** Le renommage validé : la règle NouveauNom puis le déplacement. */
    private void renommer(String chemin, String nom) {
        hote.etatPopups().oublierBrouillon(BROUILLON_RENOMME);
        NouveauNom.Raison raison = NouveauNom.valider(nom);
        if (raison != null) {
            hote.popups().information(
                    hote.texte(R.string.atelier_renommer),
                    texteRaisonNom(raison),
                    hote.texte(R.string.action_ok));
            return;
        }
        try {
            // lot C1 : Paths.get (API 26), Path.of exige l'API 34
            java.nio.file.Path source = java.nio.file.Paths.get(chemin);
            java.nio.file.Files.move(source,
                    source.resolveSibling(nom),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            rechargerArbre();
        } catch (java.io.IOException e) {
            hote.popups().information(
                    hote.texte(R.string.atelier_renommer),
                    String.valueOf(e.getMessage()),
                    hote.texte(R.string.action_ok));
        }
    }

    /** Supprime (confirmation ; l'onglet ouvert se ferme, §3.8). */
    private void supprimerEntree(String chemin, boolean dossier) {
        hote.popups().confirmation(
                hote.texte(R.string.atelier_supprimer), chemin,
                hote.texte(R.string.action_annuler),
                hote.texte(R.string.action_ok),
                () -> {
                    try {
                        // lot C1 : Paths.get (API 26), Path.of exige
                        // l'API 34
                        java.nio.file.Path cible =
                                java.nio.file.Paths.get(chemin);
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
                        hote.popups().information(
                                hote.texte(R.string.atelier_supprimer),
                                String.valueOf(e.getMessage()),
                                hote.texte(R.string.action_ok));
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

    /**
     * La création d'un fichier ou d'un dossier : la saisie centrée
     * ({@code popup_saisie}, clavier requis), le BROUILLON vit dans
     * l'état pur.
     */
    void proposerNouveau(boolean fichier) {
        String dossier = hote.etat().actif() == null
                ? "" : Arborescence.dossierDe(hote.etat().actif());
        String affichage = dossier.isEmpty()
                ? String.valueOf(
                hote.racineProjet().getFileName()) : dossier;
        EditText champ = hote.popups().saisie(
                hote.texte(R.string.atelier_nouveau_dans, affichage),
                null,
                hote.texte(R.string.action_annuler),
                hote.texte(R.string.action_ok),
                saisie -> creerEntree(
                        saisie.getText().toString().trim(),
                        fichier, dossier));
        champ.setHint(fichier
                ? R.string.atelier_nouveau_fichier
                : R.string.atelier_nouveau_dossier);
        String brouillon = hote.etatPopups().brouillon(BROUILLON_NOUVEAU);
        if (brouillon != null) {
            champ.setText(brouillon);
        }
        champ.addTextChangedListener(brouillon(BROUILLON_NOUVEAU));
    }

    /** Le relais de brouillon : chaque frappe vit dans l'état pur. */
    private TextWatcher brouillon(String cle) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,
                    int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s,
                    int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                hote.etatPopups().poserBrouillon(cle, s.toString());
            }
        };
    }

    private void creerEntree(String nom, boolean fichier, String dossier) {
        hote.etatPopups().oublierBrouillon(BROUILLON_NOUVEAU);
        NouveauNom.Raison raison = NouveauNom.valider(nom);
        if (raison != null) {
            hote.popups().information(
                    hote.texte(R.string.atelier_nouveau),
                    texteRaisonNom(raison),
                    hote.texte(R.string.action_ok));
            return;
        }
        Path cible = hote.racineProjet().resolve(
                dossier.isEmpty() ? nom : dossier + nom);
        if (Files.exists(cible)) {
            hote.popups().information(
                    hote.texte(R.string.atelier_nouveau),
                    hote.texte(R.string.atelier_existe_deja, nom),
                    hote.texte(R.string.action_ok));
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
            hote.popups().information(
                    hote.texte(R.string.atelier_nouveau),
                    hote.texte(R.string.atelier_creation_echouee,
                            String.valueOf(e.getMessage())),
                    hote.texte(R.string.action_ok));
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
        hote.popups().information(
                hote.texte(R.string.atelier_index, index.phase()),
                hote.texte(R.string.atelier_index_detail, index.phase(),
                        index.total(), index.faits(),
                        index.avertissements().size(),
                        index.erreurs().size()),
                hote.texte(R.string.action_ok));
    }
}
