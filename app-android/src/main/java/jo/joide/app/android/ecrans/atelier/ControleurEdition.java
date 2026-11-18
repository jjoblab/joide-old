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
 * L6 — le contrôleur de l'édition : fichiers ouverts (ouvrir,
 * montrer, fermer), gestes d'édition (commenter, dupliquer,
 * symboles), diagnostics LSP, verdicts Problèmes/Journal/Logcat,
 * fil d'Ariane, onglets. La vérité reste dans les états purs
 * (EtatProblemes/EtatJournal/EtatLogcat) et l'éditeur ; les corps
 * viennent d'EcranAtelier sans changement de comportement.
 */
final class ControleurEdition {

    private final EcranAtelier atelier;

    ControleurEdition(EcranAtelier atelier) {
        this.atelier = atelier;
    }

    void ouvrirFichier(String relatif, Integer ligne) {
        if (atelier.racineProjet == null) {
            return;
        }
        EtatAtelier.Ouverture ouverture = atelier.etat.ouvrir(relatif);
        if (!ouverture.faite()) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.atelier_etat_vide_titre),
                    atelier.getString(R.string.atelier_onglets_plafond,
                            EtatAtelier.ONGLETS_MAX),
                    R.string.action_ok);
            return;
        }
        if (ouverture.evince() != null) {
            majOnglets();
        }
        montrerFichier(relatif, ligne);
    }

    void montrerFichier(String relatif, Integer ligne) {
        if (atelier.racineProjet == null || atelier.scene == null) {
            return;
        }
        atelier.fond.execute(() -> {
            String contenu;
            try {
                contenu = FichiersProjet.lire(atelier.racineProjet, relatif);
            } catch (IOException e) {
                atelier.runOnUiThread(() -> Dialogue.information(
                        atelier, atelier.palette,
                        atelier.getString(R.string.import_echoue_titre),
                        atelier.getString(R.string.lecture_echouee, relatif),
                        R.string.action_ok));
                return;
            }
            atelier.runOnUiThread(() -> {
                atelier.fichierCourant = relatif;
                atelier.dossiersOuverts.addAll(Arborescence.parentsDe(relatif));
                String uri = atelier.racineProjet.resolve(relatif).toString();
                atelier.scene.ouvrir(uri, Arborescence.nomAffiche(relatif),
                        contenu);
                atelier.majPuits();
                boolean miseEnPage = FichiersProjet.estMiseEnPage(relatif);
                atelier.etat.surActifEstMiseEnPage(miseEnPage);
                if (miseEnPage) {
                    atelier.xmlCourant = contenu;
                    atelier.etatApercu.setTheme(atelier.configApercuBase.nomTheme());
                    atelier.reconfigurerApercu();
                    atelier.apercu.rendreSansDelai(contenu);
                }
                majOnglets();
                atelier.majVolets();
                atelier.majArbre();
                if (ligne != null) {
                    atelier.scene.allerALigne(ligne);
                }
            });
        });
    }

    void fermerOnglet(String relatif) {
        if (!atelier.etat.fermer(relatif)) {
            return;
        }
        atelier.ecritures.drainer();
        atelier.fond.execute(atelier::ecrireEnAttente);
        String nouveau = atelier.etat.actif();
        if (nouveau != null) {
            montrerFichier(nouveau, null);
        } else {
            atelier.fichierCourant = null;
            atelier.xmlCourant = null;
            atelier.etat.fermerVoletApercu();
            majOnglets();
            atelier.majVolets();
            atelier.majArbre();
        }
    }

    /** Lot L2 (ADR-083) — commenter la ligne courante : « // » en
     *  tête de ligne, via l'InputConnection réelle de la vue éditeur
     *  (batch unique, la position du curseur reprise après). */
    void commenterLigneCourante() {
        android.view.inputmethod.InputConnection connexion =
                connexionEditeur();
        if (connexion == null) {
            return;
        }
        CharSequence avantBrut = connexion
                .getTextBeforeCursor(8000, 0);
        String avant = avantBrut == null ? "" : avantBrut.toString();
        int debutLigne = avant.lastIndexOf("\n") + 1;
        connexion.beginBatchEdit();
        connexion.setSelection(debutLigne, debutLigne);
        connexion.commitText("// ", 1);
        connexion.endBatchEdit();
    }

    /** Dupliquer la ligne courante sous elle-même. */
    void dupliquerLigneCourante() {
        android.view.inputmethod.InputConnection connexion =
                connexionEditeur();
        if (connexion == null) {
            return;
        }
        CharSequence avantBrut = connexion
                .getTextBeforeCursor(8000, 0);
        CharSequence apresBrut = connexion
                .getTextAfterCursor(8000, 0);
        if (avantBrut == null || apresBrut == null) {
            return;
        }
        String avant = avantBrut.toString();
        String apres = apresBrut.toString();
        int debutLigne = avant.lastIndexOf("\n") + 1;
        String ligne = avant.substring(debutLigne)
                + ligneApres(avant.length() - debutLigne, apres);
        connexion.beginBatchEdit();
        connexion.commitText("\n" + ligne, 1);
        connexion.endBatchEdit();
    }

    /** La suite de la ligne courante (jusqu'au premier retour). */
    private String ligneApres(int dejaLue, CharSequence apres) {
        String suite = apres.toString();
        int finLigne = suite.indexOf("\n");
        return finLigne < 0 ? suite : suite.substring(0, finLigne);
    }

    /** L'InputConnection RÉELLE de la vue éditeur (la fabrique de la
     *  vue est légitime : la connexion est liée à la session). */
    private android.view.inputmethod.InputConnection connexionEditeur() {
        if (atelier.scene == null) {
            return null;
        }
        android.view.View editeur = atelier.scene.vue();
        return editeur.onCreateInputConnection(
                new android.view.inputmethod.EditorInfo());
    }

    /** L'insertion d'une touche symbole (même voie). */
    void insererSymbole(String texte) {
        android.view.inputmethod.InputConnection connexion =
                connexionEditeur();
        if (connexion == null) {
            return;
        }
        connexion.commitText(texte, 1);
    }

    /** Lot L2 (ADR-083) — le fil d'Ariane suit le fichier courant
     *  (§3.3 : chemin + symbole englobant du moteur de langage ; le
     *  symbole arrive au lot L3 avec la source LSP — absent, la
     *  classe termine le fil, franchement). */
    /** Lot L3 (ADR-083) — le journal de l'IDE reçoit l'événement et
     *  la zone vivante suit (source accent, MCP alerte, erreur rouge). */
    void journaliser(EtatJournal.Source source,
                             EtatJournal.Niveau niveau,
                             String message) {
        atelier.etatJournal.ajouter(source, niveau, message);
        if (atelier.listeJournal != null) {
            atelier.listeJournal.maj(atelier.etatJournal);
        }
        EtatJournal.Evenement dernier = atelier.etatJournal.dernier();
        if (atelier.feuille != null && dernier != null) {
            int couleur = dernier.niveau() == EtatJournal.Niveau.ERREUR
                    ? atelier.palette.erreur : atelier.palette.accent;
            atelier.feuille.poserVerdictJournal("● " + dernier.source()
                    + " · " + message.toLowerCase(java.util.Locale
                    .FRENCH), dernier.heure() + " · "
                    + atelier.etatJournal.taille() + " événements");
        }
    }

    /** La ligne courante du curseur (le saut ↓ part d'ici) — 1 si
     *  la session n'est pas lisible. */
    int ligneCourante() {
        if (atelier.scene == null) {
            return 1;
        }
        android.view.View editeur = atelier.scene.vue();
        android.view.inputmethod.EditorInfo infos =
                new android.view.inputmethod.EditorInfo();
        android.view.inputmethod.InputConnection connexion =
                editeur.onCreateInputConnection(infos);
        if (connexion == null) {
            return 1;
        }
        CharSequence avantBrut = connexion
                .getTextBeforeCursor(8000, 0);
        if (avantBrut == null) {
            return 1;
        }
        int lignes = 1;
        for (int i = 0; i < avantBrut.length(); i++) {
            if (avantBrut.charAt(i) == '\n') {
                lignes++;
            }
        }
        return lignes;
    }

    /** L'écran alimente EtatProblemes depuis les diagnostics de la
     *  scène (la source LSP de la section, §3.6) et redessine. */
    void surDiagnosticsLangage(String uri,
            List<SceneEditeur.ProblemeEcran> bruts) {
        String chemin = uri;
        String prefixe = "file://";
        if (chemin.startsWith(prefixe)) {
            chemin = chemin.substring(prefixe.length());
        }
        List<EtatProblemes.Probleme> nouveaux = new ArrayList<>();
        for (SceneEditeur.ProblemeEcran brut : bruts) {
            nouveaux.add(new EtatProblemes.Probleme(chemin,
                    Arborescence.nomAffiche(chemin),
                    brut.ligne(), brut.message(), brut.erreur()));
        }
        atelier.etatProblemes.poserFichier(chemin,
                Arborescence.nomAffiche(chemin), nouveaux);
        if (atelier.listeProblemes != null) {
            atelier.listeProblemes.maj(atelier.etatProblemes);
        }
        atelier.feuille.poserBadgeProblemes(atelier.etatProblemes.erreurs());
        majVerdictProblemes();
    }

    /** La zone vivante des problèmes (§3.6 : ✕ n ▲ n ou « Aucun
     *  problème » / première erreur du fichier actif). */
    private void majVerdictProblemes() {
        if (atelier.feuille == null) {
            return;
        }
        int erreurs = atelier.etatProblemes.erreurs();
        int avertissements = atelier.etatProblemes.avertissements();
        String verdict;
        int couleur;
        if (erreurs == 0 && avertissements == 0) {
            verdict = atelier.getString(R.string.atelier_aucun_probleme);
            couleur = atelier.palette.reussite;
        } else {
            verdict = "✕ " + erreurs + "  ▲ " + avertissements;
            couleur = erreurs > 0 ? atelier.palette.erreur : atelier.palette.alerte;
        }
        EtatProblemes.Probleme premier = atelier.etatProblemes
                .premierDuFichier(atelier.fichierCourant == null ? ""
                        : atelier.fichierCourant);
        String detail = premier == null
                ? atelier.getString(R.string.atelier_aucun_probleme)
                : Arborescence.nomAffiche(premier.chemin()) + ":"
                + premier.ligne() + " · " + premier.message();
        atelier.feuille.poserVerdictProblemes(verdict, detail);
    }

    /**
     * ADR-084 — l'auditeur du flux du relais : les lignes déposées
     * depuis le fil Binder passent sur le fil UI (post) avant toute
     * manipulation de vue.
     */
    final FluxLogcat.Auditeur auditeurFluxLogcat =
            new FluxLogcat.Auditeur() {
                @Override
                public void surLignes() {
                    atelier.runOnUiThread(atelier.controleurEdition::lireFluxRelais);
                }
            };

    /** Lit le tampon du relais et alimente l'état de la section. */
    void lireFluxRelais() {
        for (FluxLogcat.LigneRelais ligne
                : FluxLogcat.tampon().lire()) {
            atelier.etatLogcat.recevoir(ligne.niveau(), ligne.tag(),
                    ligne.message(), System.currentTimeMillis());
        }
        if (atelier.listeLogcat != null) {
            atelier.listeLogcat.maj(atelier.etatLogcat);
        }
        majVerdictLogcat();
    }

    /** Au branchement (ouverture de la section) : ce qui est déjà en
     *  tampon entre dans l'état, puis le verdict suit. */
    void viderFluxDansEtatLogcat() {
        atelier.runOnUiThread(atelier.controleurEdition::lireFluxRelais);
    }

    /** La zone vivante du logcat (§3.6) : flux vivant ou état vide
     *  honnête, dernière ligne colorée par niveau, histogramme. */
    private void majVerdictLogcat() {
        if (atelier.feuille == null) {
            return;
        }
        EtatLogcat.Ligne derniere = atelier.etatLogcat.derniere();
        if (derniere == null) {
            atelier.feuille.poserVerdictLogcat(
                    atelier.getString(R.string.atelier_logcat_inactif),
                    atelier.palette.texteSecondaire,
                    atelier.getString(R.string.atelier_logcat_relais),
                    atelier.palette.texteEstompe,
                    atelier.etatLogcat.histogramme(System.currentTimeMillis()));
            return;
        }
        int couleur = switch (derniere.niveau()) {
            case E -> atelier.palette.erreur;
            case W -> atelier.palette.alerte;
            case D -> atelier.palette.reussite;
            default -> atelier.palette.texte;
        };
        atelier.feuille.poserVerdictLogcat(
                atelier.getString(R.string.atelier_logcat_vivant, derniere.tag()),
                couleur,
                derniere.message(), couleur,
                atelier.etatLogcat.histogramme(System.currentTimeMillis()));
    }

    void majFilAriane() {
        if (atelier.filAriane == null || atelier.fichierCourant == null) {
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
        atelier.filAriane.poser("", "", "", "");
        var segments = jo.joide.app.android.atelier
                .FormateurFilAriane.segmentsDepuisChemin(relatif, null);
        StringBuilder module = new StringBuilder();
        StringBuilder paquet = new StringBuilder();
        StringBuilder classe = new StringBuilder();
        if (!segments.isEmpty()) {
            module.append(segments.get(0).texte());
        }
        for (int i = 1; i < segments.size(); i++) {
            if (i == segments.size() - 1) {
                classe.append(segments.get(i).texte());
            } else {
                if (paquet.length() > 0) {
                    paquet.append('.');
                }
                paquet.append(segments.get(i).texte());
            }
        }
        atelier.filAriane.poser(module.toString(), paquet.toString(),
                classe.toString(), null);
    }

    void majOnglets() {
        if (atelier.barreOnglets == null) {
            return;
        }
        // lot L2 (ADR-083) : le modèle PUR porte l'état des onglets
        // (pastilles de type, points erreur/modifié), la vue liée le
        // dessine — plus de widget propriétaire
        ModeleOnglets modele = new ModeleOnglets();
        for (String chemin : atelier.etat.onglets()) {
            modele.ouvrir(chemin, Arborescence.nomAffiche(chemin),
                    atelier.etat.estModifie(chemin), false);
        }
        modele.activer(atelier.etat.actif());
        atelier.barreOnglets.poser(modele);
        // le bouton d'aperçu : onglet actif = mise en page (ADR-É7)
        if (atelier.boutonApercuOnglets != null && !atelier.etat.grandEcran()) {
            atelier.boutonApercuOnglets.setVisibility(
                    FichiersProjet.estMiseEnPage(atelier.fichierCourant)
                            ? View.VISIBLE : View.GONE);
        }
        majFilAriane();
    }

    void ouvrirCible(String cible) {
        int separation = cible.lastIndexOf(':');
        if (separation <= 0) {
            return;
        }
        String chemin = cible.substring(0, separation);
        int ligne;
        try {
            ligne = Integer.parseInt(cible.substring(separation + 1));
        } catch (NumberFormatException e) {
            ligne = 1;
        }
        String prefixe = atelier.racineProjet.toString();
        if (chemin.startsWith(prefixe)) {
            chemin = chemin.substring(prefixe.length());
            while (chemin.startsWith("/")) {
                chemin = chemin.substring(1);
            }
        }
        if (!FichiersProjet.ouvrable(Arborescence.nomAffiche(chemin))
                || !java.util.Arrays.asList(atelier.fichiersArbre.toArray())
                        .contains(chemin)) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.console_probleme_ouvrir),
                    atelier.getString(R.string.console_cible_introuvable, chemin),
                    R.string.action_ok);
            return;
        }
        ouvrirFichier(chemin, ligne);
    }

    // ------------------------------------------------------------------
    // Arborescence et nouveaux fichiers
    // ------------------------------------------------------------------

}
