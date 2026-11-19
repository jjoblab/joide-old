package jo.joide.app.android.ecrans.atelier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.FichiersProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.Arborescence;
import jo.joide.app.android.atelier.EtatAtelier;
import jo.joide.app.android.atelier.EtatJournal;
import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.atelier.EtatProblemes;
import jo.joide.app.android.atelier.ModeleOnglets;
import jo.joide.app.android.logcat.FluxLogcat;
import jo.joide.ide.editeur.SceneEditeur;

/**
 * L6 — le contrôleur de l'édition : fichiers ouverts (ouvrir,
 * montrer, fermer), gestes d'édition (commenter, dupliquer,
 * symboles), diagnostics LSP, verdicts Problèmes/Journal/Logcat,
 * fil d'Ariane, onglets. La vérité reste dans les états purs
 * (EtatProblemes/EtatJournal/EtatLogcat) et l'éditeur ; les corps
 * viennent de l'écran d'hier sans changement de comportement.
 *
 * <p>Lot A6 (ADR-085 décision 3) : le contrôleur dépend de
 * {@link HoteAtelier} — plus un champ d'écran.</p>
 */
final class ControleurEdition {

    private final HoteAtelier hote;

    ControleurEdition(HoteAtelier hote) {
        this.hote = hote;
    }

    void ouvrirFichier(String relatif, Integer ligne) {
        if (hote.racineProjet() == null) {
            return;
        }
        EtatAtelier.Ouverture ouverture =
                hote.etat().ouvrir(relatif);
        if (!ouverture.faite()) {
            hote.popups().information(
                    hote.texte(R.string.atelier_etat_vide_titre),
                    hote.texte(R.string.atelier_onglets_plafond,
                            EtatAtelier.ONGLETS_MAX),
                    hote.texte(R.string.action_ok));
            return;
        }
        if (ouverture.evince() != null) {
            majOnglets();
        }
        montrerFichier(relatif, ligne);
    }

    void montrerFichier(String relatif, Integer ligne) {
        if (hote.racineProjet() == null || hote.scene() == null) {
            return;
        }
        hote.fond().execute(() -> {
            String contenu;
            try {
                contenu = FichiersProjet.lire(hote.racineProjet(),
                        relatif);
            } catch (IOException e) {
                hote.executerSurFilUi(() -> hote.popups().information(
                        hote.texte(R.string.import_echoue_titre),
                        hote.texte(R.string.lecture_echouee, relatif),
                        hote.texte(R.string.action_ok)));
                return;
            }
            hote.executerSurFilUi(() -> {
                hote.poserFichierCourant(relatif);
                hote.dossiersOuverts().addAll(
                        Arborescence.parentsDe(relatif));
                String uri = hote.racineProjet().resolve(relatif)
                        .toString();
                hote.scene().ouvrir(uri,
                        Arborescence.nomAffiche(relatif), contenu);
                hote.majPuits();
                boolean miseEnPage = FichiersProjet.estMiseEnPage(
                        relatif);
                hote.etat().surActifEstMiseEnPage(miseEnPage);
                if (miseEnPage) {
                    hote.poserXmlCourant(contenu);
                    hote.etatApercu().setTheme(
                            hote.configApercuBase().nomTheme());
                    hote.reconfigurerApercu();
                    hote.apercu().rendreSansDelai(contenu);
                }
                majOnglets();
                hote.majVolets();
                hote.majArbre();
                if (ligne != null) {
                    hote.scene().allerALigne(ligne);
                }
            });
        });
    }

    void fermerOnglet(String relatif) {
        if (!hote.etat().fermer(relatif)) {
            return;
        }
        hote.ecritures().drainer();
        hote.fond().execute(hote::ecrireEnAttente);
        String nouveau = hote.etat().actif();
        if (nouveau != null) {
            montrerFichier(nouveau, null);
        } else {
            hote.poserFichierCourant(null);
            hote.poserXmlCourant(null);
            hote.etat().fermerVoletApercu();
            majOnglets();
            hote.majVolets();
            hote.majArbre();
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
        if (hote.scene() == null) {
            return null;
        }
        android.view.View editeur = hote.scene().vue();
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

    /** Lot L3 (ADR-083) — le journal de l'IDE reçoit l'événement et
     *  la zone vivante suit (source accent, MCP alerte, erreur rouge). */
    void journaliser(EtatJournal.Source source,
                     EtatJournal.Niveau niveau,
                     String message) {
        hote.etatJournal().ajouter(source, niveau, message);
        if (hote.feuille().listeJournal() != null) {
            hote.feuille().listeJournal().maj(hote.etatJournal());
        }
        EtatJournal.Evenement dernier = hote.etatJournal().dernier();
        if (hote.feuille() != null && dernier != null) {
            int couleur = dernier.niveau() == EtatJournal.Niveau.ERREUR
                    ? hote.palette().erreur : hote.palette().accent;
            hote.feuille().poserVerdictJournal("● " + dernier.source()
                    + " · " + message.toLowerCase(java.util.Locale
                    .FRENCH), dernier.heure() + " · "
                    + hote.etatJournal().taille() + " événements");
        }
    }

    /** La ligne courante du curseur (le saut ↓ part d'ici) — 1 si
     *  la session n'est pas lisible. */
    int ligneCourante() {
        if (hote.scene() == null) {
            return 1;
        }
        android.view.View editeur = hote.scene().vue();
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
        List<EtatProblemes.Probleme> nouveaux =
                new ArrayList<>();
        for (SceneEditeur.ProblemeEcran brut : bruts) {
            nouveaux.add(new EtatProblemes.Probleme(chemin,
                    Arborescence.nomAffiche(chemin),
                    brut.ligne(), brut.message(), brut.erreur()));
        }
        hote.etatProblemes().poserFichier(chemin,
                Arborescence.nomAffiche(chemin), nouveaux);
        if (hote.feuille().listeProblemes() != null) {
            hote.feuille().listeProblemes().maj(
                    hote.etatProblemes());
        }
        hote.feuille().poserBadgeProblemes(
                hote.etatProblemes().erreurs());
        majVerdictProblemes();
    }

    /** La zone vivante des problèmes (§3.6 : ✕ n ▲ n ou « Aucun
     *  problème » / première erreur du fichier actif). */
    private void majVerdictProblemes() {
        if (hote.feuille() == null) {
            return;
        }
        int erreurs = hote.etatProblemes().erreurs();
        int avertissements = hote.etatProblemes().avertissements();
        String verdict;
        int couleur;
        if (erreurs == 0 && avertissements == 0) {
            verdict = hote.texte(R.string.atelier_aucun_probleme);
            couleur = hote.palette().reussite;
        } else {
            verdict = "✕ " + erreurs + "  ▲ " + avertissements;
            couleur = erreurs > 0 ? hote.palette().erreur
                    : hote.palette().alerte;
        }
        EtatProblemes.Probleme premier =
                hote.etatProblemes().premierDuFichier(
                        hote.fichierCourant() == null ? ""
                                : hote.fichierCourant());
        String detail = premier == null
                ? hote.texte(R.string.atelier_aucun_probleme)
                : Arborescence.nomAffiche(premier.chemin()) + ":"
                + premier.ligne() + " · " + premier.message();
        hote.feuille().poserVerdictProblemes(verdict, detail);
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
                    hote.executerSurFilUi(ControleurEdition.this
                            ::lireFluxRelais);
                }
            };

    /** Lit le tampon du relais et alimente l'état de la section. */
    void lireFluxRelais() {
        for (FluxLogcat.LigneRelais ligne
                : FluxLogcat.tampon().lire()) {
            hote.etatLogcat().recevoir(ligne.niveau(), ligne.tag(),
                    ligne.message(), System.currentTimeMillis());
        }
        if (hote.feuille().listeLogcat() != null) {
            hote.feuille().listeLogcat().maj(hote.etatLogcat());
        }
        majVerdictLogcat();
    }

    /** Au branchement (ouverture de la section) : ce qui est déjà en
     *  tampon entre dans l'état, puis le verdict suit. */
    void viderFluxDansEtatLogcat() {
        hote.executerSurFilUi(this::lireFluxRelais);
    }

    /** La zone vivante du logcat (§3.6) : flux vivant ou état vide
     *  honnête, dernière ligne colorée par niveau, histogramme. */
    private void majVerdictLogcat() {
        if (hote.feuille() == null) {
            return;
        }
        EtatLogcat.Ligne derniere =
                hote.etatLogcat().derniere();
        if (derniere == null) {
            hote.feuille().poserVerdictLogcat(
                    hote.texte(R.string.atelier_logcat_inactif),
                    hote.palette().texteSecondaire,
                    hote.texte(R.string.atelier_logcat_relais),
                    hote.palette().texteEstompe,
                    hote.etatLogcat().histogramme(
                            System.currentTimeMillis()));
            return;
        }
        int couleur = switch (derniere.niveau()) {
            case E -> hote.palette().erreur;
            case W -> hote.palette().alerte;
            case D -> hote.palette().reussite;
            default -> hote.palette().texte;
        };
        hote.feuille().poserVerdictLogcat(
                hote.texte(R.string.atelier_logcat_vivant,
                        derniere.tag()),
                couleur,
                derniere.message(), couleur,
                hote.etatLogcat().histogramme(
                        System.currentTimeMillis()));
    }

    void majFilAriane() {
        if (hote.filAriane() == null
                || hote.fichierCourant() == null) {
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
        hote.filAriane().poser("", "", "", "");
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
        hote.filAriane().poser(module.toString(), paquet.toString(),
                classe.toString(), null);
    }

    void majOnglets() {
        if (hote.barreOnglets() == null) {
            return;
        }
        // lot L2 (ADR-083) : le modèle PUR porte l'état des onglets
        // (pastilles de type, points erreur/modifié), la vue liée le
        // dessine — plus de widget propriétaire ; lot A6 : la bande
        // recycle (DiffUtil), l'identité ne change pas
        ModeleOnglets modele = new ModeleOnglets();
        for (String chemin : hote.etat().onglets()) {
            modele.ouvrir(chemin, Arborescence.nomAffiche(chemin),
                    hote.etat().estModifie(chemin), false);
        }
        modele.activer(hote.etat().actif());
        hote.barreOnglets().poser(modele);
        // le bouton d'aperçu : onglet actif = mise en page (ADR-É7)
        if (hote.boutonApercuOnglets() != null
                && !hote.etat().grandEcran()) {
            hote.boutonApercuOnglets().setVisibility(
                    FichiersProjet.estMiseEnPage(hote.fichierCourant())
                            ? android.view.View.VISIBLE
                            : android.view.View.GONE);
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
            ligne = Integer.parseInt(
                    cible.substring(separation + 1));
        } catch (NumberFormatException e) {
            ligne = 1;
        }
        String prefixe = hote.racineProjet().toString();
        if (chemin.startsWith(prefixe)) {
            chemin = chemin.substring(prefixe.length());
            while (chemin.startsWith("/")) {
                chemin = chemin.substring(1);
            }
        }
        if (!FichiersProjet.ouvrable(Arborescence.nomAffiche(chemin))
                || !java.util.Arrays.asList(
                        hote.fichiersArbre().toArray())
                        .contains(chemin)) {
            hote.popups().information(
                    hote.texte(R.string.console_probleme_ouvrir),
                    hote.texte(R.string.console_cible_introuvable, chemin),
                    hote.texte(R.string.action_ok));
            return;
        }
        ouvrirFichier(chemin, ligne);
    }
}
