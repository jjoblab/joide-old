package jo.joide.app.android.ecrans.atelier;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.ProprietesConstruction;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jo.joide.app.android.BuildService;
import jo.joide.app.android.FichiersProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.SpecsBuild;
import jo.joide.app.android.StoreAndroid;
import jo.joide.app.android.parametres.PreferencesAndroid;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.atelier.Arborescence;
import jo.joide.app.android.atelier.EcrituresFusionnees;
import jo.joide.app.android.atelier.EtatApercu;
import jo.joide.app.android.AgentService;
import jo.joide.app.android.atelier.EtatAtelier;
import jo.joide.agent.ReductionImages;
import jo.joide.app.android.atelier.agent.ConversationsAgent;
import jo.joide.app.android.atelier.agent.FournisseurCameraAgent;
import jo.joide.app.android.atelier.agent.PieceJointe;
import jo.joide.app.android.atelier.agent.ReducteurImagesAndroid;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.atelier.agent.VueAgent;
import jo.joide.app.android.atelier.NouveauNom;
import jo.joide.app.android.atelier.VueApercu;
import jo.joide.app.android.atelier.VueArbre;
import jo.joide.app.android.console.EtatConsole;
import jo.joide.app.android.console.VueConsole;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.EtatVide;
import jo.joide.app.android.ui.Fab;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.atelier.ModeleOnglets;
import jo.joide.app.android.atelier.EtatFeuille;
import jo.joide.app.android.atelier.EtatJournal;
import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.logcat.FluxLogcat;
import jo.joide.app.android.atelier.EtatProblemes;
import jo.joide.app.android.ui.atelier.BarreHaut;
import jo.joide.app.android.ui.atelier.BarreOnglets;
import jo.joide.app.android.ui.atelier.BarreSymboles;
import jo.joide.app.android.ui.atelier.FilAriane;
import jo.joide.app.android.ui.atelier.FeuilleBasse;
import jo.joide.app.android.ui.atelier.ListeJournal;
import jo.joide.app.android.ui.atelier.ListeLogcat;
import jo.joide.app.android.ui.atelier.ListeProblemes;
import jo.joide.app.android.ui.atelier.TiroirFichiers;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.PopupAncre;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.TiroirPousse;
import jo.joide.app.android.ui.choix.ChoixBouton;
import jo.joide.build.daemon.client.DaemonClient;
import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.repo.HttpTransport;
import jo.joide.deps.repo.Repository;
import jo.joide.deps.resolve.ResolverConfig;
import jo.joide.agent.Permissions;
import jo.joide.agent.espace.EspaceCheckpoint;
import jo.joide.ide.agent.EspaceTravailIde;
import jo.joide.ide.apercu.ApercuLayouts;
import jo.joide.ide.apercu.ConfigurationApercu;
import jo.joide.ide.apercu.ConfigurationsApercu;
import jo.joide.ide.editeur.SceneEditeur;
import jo.joide.ide.langages.EtatIndex;
import jo.joide.ide.langages.MoteursLangage;
import jo.joide.ide.sync.AndroidPlatform;
import jo.joide.ide.sync.ExternalResolution;
import jo.joide.ide.sync.LanguageModelSnapshot;
import jo.joide.ide.sync.LanguageModelSync;
import jo.joide.ide.sync.ModuleDirectories;
import jo.joide.ide.sync.ProjetLspAdapter;
import jo.joide.ide.sync.Resolutions;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceLoad;
import jo.joide.model.WorkspaceLoader;

/**
 * L6 — le contrôleur de l'arbre de fichiers : rechargement, rendu,
 * menu contextuel (dupliquer, renommer, supprimer), création,
 * localisation, pilule d'index. La vérité reste dans le Workspace et
 * le disque ; les corps viennent d'EcranAtelier sans changement de
 * comportement (extraction mécanique, mêmes commentaires).
 */
final class ControleurFichiers {

    private final EcranAtelier atelier;

    ControleurFichiers(EcranAtelier atelier) {
        this.atelier = atelier;
    }

    void rechargerArbre() {
        if (atelier.racineProjet == null) {
            return;
        }
        atelier.fond.execute(() -> {
            try {
                List<String> fichiers = FichiersProjet.lister(atelier.racineProjet);
                boolean sorties = Files.isDirectory(
                        atelier.racineProjet.resolve(Arborescence.NOM_SORTIES));
                atelier.runOnUiThread(() -> {
                    atelier.fichiersArbre = fichiers;
                    atelier.sortiesPresentes = sorties;
                    majArbre();
                });
            } catch (IOException e) {
                // l'arbre reste l'état précédent : jamais un arbre vide
                // muet — le prochain accès le rechargera
            }
        });
    }

    void majArbre() {
        if (atelier.vueArbre == null || atelier.racineProjet == null) {
            return;
        }
        atelier.vueArbre.setSortiesEnCours(atelier.buildEnCours);
        List<String> retenus = Arborescence.cheminsFiltres(atelier.fichiersArbre,
                atelier.filtreTiroir);
        atelier.vueArbre.setArbre(Arborescence.composer(retenus,
                String.valueOf(atelier.racineProjet.getFileName()),
                atelier.filtreTiroir.isBlank() ? atelier.dossiersOuverts
                        : Arborescence.ancetresDe(retenus),
                atelier.etat.actif(), atelier.sortiesPresentes),
                String.valueOf(atelier.racineProjet.getFileName()));
    }

    /**
     * Lot L4 (ADR-083) — le menu contextuel de l'arbre (§3.8,
     * appui long) : des VERBES RÉELS — nouveau fichier/dossier ici,
     * renommer (déplacement), dupliquer (copie « (copie) »),
     * supprimer (confirmation, l'onglet ouvert se ferme). Franchise :
     * jamais un item mort.
     */
    void montrerMenuContextuel(String chemin, boolean dossier) {
        java.util.List<String> verbes = new ArrayList<>();
        java.util.List<Runnable> actions = new ArrayList<>();
        verbes.add(atelier.getString(R.string.atelier_nouveau_fichier));
        actions.add(() -> proposerNouveau(true));
        verbes.add(atelier.getString(R.string.atelier_nouveau_dossier));
        actions.add(() -> proposerNouveau(false));
        if (!dossier) {
            verbes.add(atelier.getString(R.string.atelier_dupliquer));
            actions.add(() -> dupliquerEntree(chemin));
            verbes.add(atelier.getString(R.string.atelier_renommer));
            actions.add(() -> renommerEntree(chemin));
        }
        verbes.add(atelier.getString(R.string.atelier_supprimer));
        actions.add(() -> supprimerEntree(chemin, dossier));
        LinearLayout contenu = new LinearLayout(atelier);
        contenu.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < verbes.size(); i++) {
            String verbe = verbes.get(i);
            Runnable action = actions.get(i);
            View ligne = PopupAncre.ligneMenu(atelier, atelier.palette, verbe,
                    () -> {
                        fenetreMenuArbre.dismiss();
                        action.run();
                    });
            contenu.addView(ligne);
        }
        View vueArbreAncre = atelier.getWindow().getDecorView()
                .findViewById(android.R.id.content);
        fenetreMenuArbre = PopupAncre.montrer(atelier.vueArbre != null
                ? atelier.vueArbre : vueArbreAncre, atelier.palette, contenu,
                GraphiteVue.dp(atelier, 220));
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
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.atelier_dupliquer),
                    String.valueOf(e.getMessage()), R.string.action_ok);
        }
    }

    /** Renomme un fichier du projet (déplacement au même dossier) :
     *  la saisie suit le même dialogue que la création, la validation
     *  NouveauNom incluse. */
    private void renommerEntree(String chemin) {
        android.widget.EditText saisie = new android.widget.EditText(atelier);
        saisie.setHint(R.string.atelier_renommer);
        Dialogue.saisie(atelier, atelier.palette,
                atelier.getString(R.string.atelier_renommer),
                saisie, R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }
                    String nom = saisie.getText().toString().trim();
                    NouveauNom.Raison raison = NouveauNom.valider(nom);
                    if (raison != null) {
                        Dialogue.information(atelier, atelier.palette,
                                atelier.getString(R.string.atelier_renommer),
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
                        Dialogue.information(atelier, atelier.palette,
                                atelier.getString(R.string.atelier_renommer),
                                String.valueOf(e.getMessage()),
                                R.string.action_ok);
                    }
                });
    }

    /** Supprime (confirmation ; l'onglet ouvert se ferme, §3.8). */
    private void supprimerEntree(String chemin, boolean dossier) {
        Dialogue.confirmation(atelier, atelier.palette,
                atelier.getString(R.string.atelier_supprimer), chemin,
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
                        if (atelier.fichierCourant != null
                                && atelier.fichierCourant.startsWith(chemin)) {
                            atelier.controleurEdition.fermerOnglet(atelier.fichierCourant);
                        }
                        rechargerArbre();
                    } catch (java.io.IOException e) {
                        Dialogue.information(atelier, atelier.palette,
                                atelier.getString(R.string.atelier_supprimer),
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
        if (atelier.fichierCourant == null) {
            return;
        }
        String relatif = atelier.fichierCourant;
        String prefixe = atelier.racineProjet == null ? ""
                : atelier.racineProjet.toString();
        if (relatif.startsWith(prefixe)) {
            relatif = relatif.substring(prefixe.length());
            while (relatif.startsWith("/")) {
                relatif = relatif.substring(1);
            }
        }
        atelier.filtreTiroir = "";
        if (atelier.tiroirFichiers != null) {
            atelier.tiroirFichiers.champFiltre().setText("");
        }
        atelier.dossiersOuverts.addAll(
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
        String dossier = atelier.etat.actif() == null
                ? "" : Arborescence.dossierDe(atelier.etat.actif());
        String affichage = dossier.isEmpty()
                ? String.valueOf(atelier.racineProjet.getFileName()) : dossier;
        android.widget.EditText saisie = new android.widget.EditText(atelier);
        saisie.setHint(fichier
                ? R.string.atelier_nouveau_fichier
                : R.string.atelier_nouveau_dossier);
        Dialogue.saisie(atelier, atelier.palette,
                atelier.getString(R.string.atelier_nouveau_dans, affichage),
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
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.atelier_nouveau),
                    texteRaisonNom(raison), R.string.action_ok);
            return;
        }
        Path cible = atelier.racineProjet.resolve(
                dossier.isEmpty() ? nom : dossier + nom);
        if (Files.exists(cible)) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.atelier_nouveau),
                    atelier.getString(R.string.atelier_existe_deja, nom),
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
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.atelier_nouveau),
                    atelier.getString(R.string.atelier_creation_echouee,
                            String.valueOf(e.getMessage())),
                    R.string.action_ok);
            return;
        }
        rechargerArbre();
        if (fichier) {
            String relatif = atelier.racineProjet.relativize(cible)
                    .toString().replace('\\', '/');
            atelier.controleurEdition.ouvrirFichier(relatif, null);
        }
    }

    private String texteRaisonNom(NouveauNom.Raison raison) {
        return switch (raison) {
            case VIDE -> atelier.getString(R.string.atelier_nom_err_vide);
            case TROP_LONG -> atelier.getString(R.string.atelier_nom_err_long);
            case SEPARATEUR -> atelier.getString(
                    R.string.atelier_nom_err_separateur);
            case POINT -> atelier.getString(R.string.atelier_nom_err_point);
            case CONTROLE -> atelier.getString(R.string.atelier_nom_err_controle);
            case POINT_FINAL -> atelier.getString(
                    R.string.atelier_nom_err_point_final);
        };
    }

    // ------------------------------------------------------------------
    // Index de noms (pilule honnête)
    // ------------------------------------------------------------------

    void majPiluleIndex() {
        if (atelier.piluleIndex == null || atelier.moteurs == null) {
            return;
        }
        EtatIndex index = atelier.moteurs.etatIndex();
        atelier.piluleIndex.setEtat(atelier.getString(R.string.atelier_index,
                        index.phase()), Pilule.Role.NEUTRE);
    }

    void montrerDetailIndex() {
        if (atelier.moteurs == null) {
            return;
        }
        EtatIndex index = atelier.moteurs.etatIndex();
        majPiluleIndex();
        Dialogue.information(atelier, atelier.palette,
                atelier.getString(R.string.atelier_index, index.phase()),
                atelier.getString(R.string.atelier_index_detail, index.phase(),
                        index.total(), index.faits(),
                        index.avertissements().size(),
                        index.erreurs().size()),
                R.string.action_ok);
    }

}
