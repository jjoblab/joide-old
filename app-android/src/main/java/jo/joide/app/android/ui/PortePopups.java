package jo.joide.app.android.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jo.joide.app.android.databinding.ItemLigneMenuBinding;
import jo.joide.app.android.databinding.PopupConfirmationBinding;
import jo.joide.app.android.databinding.PopupDestructiveBinding;
import jo.joide.app.android.databinding.PopupInformationBinding;
import jo.joide.app.android.databinding.PopupMenuNoeudBinding;
import jo.joide.app.android.databinding.PopupPageTexteBinding;
import jo.joide.app.android.databinding.PopupSaisieBinding;

/**
 * Le PORTE-POPUPS d'un écran (lot A7, ADR-085 décision 12) : LA voie
 * d'appel uniforme des popups de l'écran — les gabarits
 * {@link PopupAncre} (ancré : menus, sélecteurs) et
 * {@link PopupCentre} (centré avec voile : ce qui exige une réponse)
 * restent la mécanique ; le porte-popups tient le REGISTRE des
 * fenêtres ouvertes et les CORPS COMMUNS gonflés de leurs
 * {@code popup_*.xml} par binding. La classe {@code Dialogue} est
 * supprimée : aucune fenêtre de dialogue ici — des
 * {@code PopupWindow} sur des corps XML.
 *
 * <ul>
 * <li><b>cycle de vie</b> : {@link #fermer()} ferme tout (idempotent)
 * — l'écran l'appelle en {@code onPause}/{@code onDestroy}/
 * {@code onConfigurationChanged} (décision 12 : fermeture explicite,
 * jamais une fuite au détachement) ;</li>
 * <li><b>l'ordre du retour</b> : {@link #ouvert()} alimente l'état pur
 * (le popup se ferme EN PREMIER, avant feuille et tiroirs — testé en
 * JVM) ;</li>
 * <li><b>l'accessibilité et le clavier</b> : posées une seule fois
 * par gabarit, plus jamais dispersées par site d'appel.</li>
 * </ul>
 */
public final class PortePopups {

    /** Une entrée de menu ancré : le libellé et son verbe. */
    public record EntreeMenu(CharSequence libelle, Runnable action) {
    }

    /** La vue-voile du layout racine de l'écran (popups centrés) —
     *  ré-liable (lot A8 : la racine se ré-influe). */
    private View voile;

    /** Les fenêtres ouvertes (au plus une centrée, un menu ancré). */
    private final List<PopupWindow> ouvertes = new ArrayList<>(2);

    /**
     * @param voile la vue-voile du layout racine ({@code ?attr/jo_rideau},
     *              {@code gone} au repos — PopupCentre la montre)
     */
    public PortePopups(View voile) {
        this.voile = voile;
    }

    /**
     * Re-pose la vue-voile (lot A8, ADR-085 décision 10) : après une
     * ré-inflation de la racine, le NOUVEAU layout porte son propre
     * voile ({@code gone} au repos) — le registre, lui, survit (il
     * est vide : les popups se ferment avant toute ré-inflation).
     *
     * @param voile la vue-voile du nouveau layout racine
     */
    public void poserVoile(View voile) {
        this.voile = voile;
    }

    /** Un popup est-il ouvert (menus ancrés ou carte centrée) ? */
    public boolean ouvert() {
        return !ouvertes.isEmpty();
    }

    /**
     * Ferme TOUT popup ouvert (idempotent) — cycle de vie de l'écran :
     * onPause, onDestroy, onConfigurationChanged. Les rappels de
     * fermeture partent (voile caché, registre vidé) ; les états purs
     * se recalent par leurs PROPRES rappels de site (la permission :
     * fermer revient à refuser).
     */
    public void fermer() {
        for (PopupWindow fenetre : List.copyOf(ouvertes)) {
            fenetre.dismiss();
        }
    }

    /**
     * Le popup ANCRÉ de corps libre : {@code popup_*.xml} gonflé par
     * l'appelant, mécanique de {@link PopupAncre} (menus spécifiques,
     * sélecteur de modèles).
     *
     * @param ancre     la vue à laquelle le popup s'accroche
     * @param carte     le corps du popup (fond porté par le layout)
     * @param largeurDp la largeur de la carte en dp
     * @return la fenêtre montrée
     */
    public PopupWindow ancrer(View ancre, View carte, int largeurDp) {
        // le rappel se référençant lui-même : la fenêtre vit dans un
        // holder une case (elle n'existe pas encore au moment du
        // rappel posé à sa construction)
        PopupWindow[] fenetre = new PopupWindow[1];
        fenetre[0] = PopupAncre.montrer(ancre, carte, largeurDp,
                () -> retrait(fenetre[0]));
        ouvertes.add(fenetre[0]);
        return fenetre[0];
    }

    /**
     * Le popup CENTRÉ de corps libre : {@code popup_*.xml} gonflé par
     * l'appelant, mécanique de {@link PopupCentre} (usages,
     * historique, connexions, permission sur diff).
     *
     * @param carte     le corps du popup (fond porté par le layout)
     * @param titrePane le titre du pane (accessibilité)
     * @return la fenêtre montrée
     */
    public PopupWindow centrer(View carte, CharSequence titrePane) {
        return centrer(carte, titrePane, null);
    }

    /**
     * Le popup CENTRÉ de corps libre, avec rappel de fermeture (la
     * permission : fermer revient à REFUSER — le défaut sûr).
     *
     * @param carte         le corps du popup
     * @param titrePane     le titre du pane (accessibilité)
     * @param aLaFermeture  le rappel (toute fermeture l'appelle)
     * @return la fenêtre montrée
     */
    public PopupWindow centrer(View carte, CharSequence titrePane,
                               Runnable aLaFermeture) {
        PopupWindow[] fenetre = new PopupWindow[1];
        fenetre[0] = PopupCentre.montrer(voile, carte, titrePane,
                false, () -> {
                    retrait(fenetre[0]);
                    if (aLaFermeture != null) {
                        aLaFermeture.run();
                    }
                });
        ouvertes.add(fenetre[0]);
        return fenetre[0];
    }

    /**
     * Le popup CENTRÉ À SAISIE de corps libre : clavier requis,
     * fenêtre redimensionnée, focus au premier champ (décision 12).
     *
     * @param carte     le corps du popup (porte une saisie)
     * @param titrePane le titre du pane (accessibilité)
     * @return la fenêtre montrée
     */
    public PopupWindow centrerSaisie(View carte, CharSequence titrePane) {
        PopupWindow[] fenetre = new PopupWindow[1];
        fenetre[0] = PopupCentre.montrer(voile, carte, titrePane,
                true, () -> retrait(fenetre[0]));
        ouvertes.add(fenetre[0]);
        return fenetre[0];
    }

    // ------------------------------------------------------------------
    // Les corps communs (popup_*.xml gonflés par binding) : le menu
    // ancré, l'information, la confirmation, la destructive, la saisie,
    // l'écran texte — une écriture par cas, les règles posées une fois.
    // ------------------------------------------------------------------

    /**
     * Le MENU ANCRÉ ({@code popup_menu_noeud}, gabarit des menus de
     * l'application) : titre optionnel, une ligne par entrée
     * ({@code item_ligne_menu}), le tap FERME le menu PUIS agit
     * (jamais une action morte derrière un menu resté ouvert).
     *
     * @param ancre     la vue à laquelle le menu s'accroche (⋮, puce,
     *                  ligne de l'arbre)
     * @param titre     le titre du menu (nul : pas de titre)
     * @param largeurDp la largeur de la carte en dp
     * @param entrees   les entrées (libellé et verbe)
     */
    public void menu(View ancre, CharSequence titre, int largeurDp,
                     List<EntreeMenu> entrees) {
        Context contexte = voile.getContext();
        PopupMenuNoeudBinding corps = PopupMenuNoeudBinding.inflate(
                LayoutInflater.from(contexte));
        if (titre != null && titre.length() > 0) {
            corps.titreMenu.setText(titre);
            corps.titreMenu.setVisibility(View.VISIBLE);
        }
        PopupWindow[] fenetre = new PopupWindow[1];
        for (EntreeMenu entree : entrees) {
            ItemLigneMenuBinding ligne = ItemLigneMenuBinding.inflate(
                    LayoutInflater.from(contexte), corps.lignesMenu,
                    true);
            ligne.texteLigneMenu.setText(entree.libelle());
            ligne.getRoot().setOnClickListener(vue -> {
                if (fenetre[0] != null) {
                    fenetre[0].dismiss();
                }
                entree.action().run();
            });
        }
        fenetre[0] = ancrer(ancre, corps.getRoot(), largeurDp);
    }

    /**
     * L'INFORMATION centrée ({@code popup_information}) : titre,
     * message portant un détail nommé, une action fermante.
     *
     * @param titre   le titre (nommé : version, cause d'échec)
     * @param message le message (jamais un texte générique)
     * @param ok      le libellé de l'action fermante
     */
    public void information(CharSequence titre, CharSequence message,
                            CharSequence ok) {
        Context contexte = voile.getContext();
        PopupInformationBinding corps = PopupInformationBinding.inflate(
                LayoutInflater.from(contexte));
        corps.titreInformation.setText(titre);
        corps.messageInformation.setText(message);
        corps.okInformation.setText(ok);
        corps.okInformation.setOnClickListener(vue -> fermer());
        centrer(corps.getRoot(), titre);
    }

    /**
     * La CONFIRMATION centrée ({@code popup_confirmation}) : Annuler
     * tonal / Valider plein — le valideur part APRÈS la fermeture.
     *
     * @param titre    le titre de la confirmation
     * @param message  le message (le compte de fichiers modifiés,
     *                 jamais un texte générique qui cache)
     * @param annuler  le libellé d'annulation
     * @param valider  le libellé de validation
     * @param valideur le verbe validé (peut être nul : fermeture seule)
     */
    public void confirmation(CharSequence titre, CharSequence message,
                              CharSequence annuler, CharSequence valider,
                              Runnable valideur) {
        Context contexte = voile.getContext();
        PopupConfirmationBinding corps = PopupConfirmationBinding.inflate(
                LayoutInflater.from(contexte));
        corps.titreConfirmation.setText(titre);
        corps.messageConfirmation.setText(message);
        corps.annulerConfirmation.setText(annuler);
        corps.annulerConfirmation.setOnClickListener(vue -> fermer());
        corps.validerConfirmation.setText(valider);
        corps.validerConfirmation.setOnClickListener(vue -> {
            fermer();
            if (valideur != null) {
                valideur.run();
            }
        });
        centrer(corps.getRoot(), titre);
    }

    /**
     * La CONFIRMATION DESTRUCTIVE centrée ({@code popup_destructive},
     * lot H / ADR-É3) : retirer de la liste (les fichiers restent) et
     * effacer du stockage (les fichiers disparaissent) sont DEUX
     * actions ; l'annulation reste toujours disponible.
     *
     * @param titre      le titre de la confirmation
     * @param message    le message (ce que chaque action fait)
     * @param annuler    le libellé d'annulation
     * @param retirer    le libellé de retrait de la liste
     * @param effacer    le libellé d'effacement du stockage
     * @param surRetirer le verbe de retrait
     * @param surEffacer le verbe d'effacement
     */
    public void destructive(CharSequence titre, CharSequence message,
                            CharSequence annuler, CharSequence retirer,
                            CharSequence effacer, Runnable surRetirer,
                            Runnable surEffacer) {
        Context contexte = voile.getContext();
        PopupDestructiveBinding corps = PopupDestructiveBinding.inflate(
                LayoutInflater.from(contexte));
        corps.titreDestructive.setText(titre);
        corps.messageDestructive.setText(message);
        corps.annulerDestructive.setText(annuler);
        corps.annulerDestructive.setOnClickListener(vue -> fermer());
        corps.retirerDestructive.setText(retirer);
        corps.retirerDestructive.setOnClickListener(vue -> {
            fermer();
            surRetirer.run();
        });
        corps.effacerDestructive.setText(effacer);
        corps.effacerDestructive.setOnClickListener(vue -> {
            fermer();
            surEffacer.run();
        });
        centrer(corps.getRoot(), titre);
    }

    /**
     * La SAISIE centrée ({@code popup_saisie}, clavier requis) :
     * titre, étiquette optionnelle, champ d'{@code champ_saisie_param},
     * Annuler / Valider — le valideur reçoit le champ posé.
     *
     * @param titre     le titre (le nom du projet, le dossier visé)
     * @param etiquette l'étiquette du champ (nulle : pas d'étiquette)
     * @param annuler  le libellé d'annulation
     * @param valider  le libellé de validation
     * @param valideur le verbe validé (reçoit le champ ; le texte se
     *                 lit à l'instant du choix)
     * @return le champ de saisie posé (préremplir, poser le hint et
     *         la police — le cas tranche : clé, modèle, URL, nom)
     */
    public EditText saisie(CharSequence titre, CharSequence etiquette,
                           CharSequence annuler, CharSequence valider,
                           Consumer<EditText> valideur) {
        Context contexte = voile.getContext();
        PopupSaisieBinding corps = PopupSaisieBinding.inflate(
                LayoutInflater.from(contexte));
        corps.titreSaisie.setText(titre);
        if (etiquette != null && etiquette.length() > 0) {
            corps.etiquetteSaisie.setText(etiquette);
            corps.etiquetteSaisie.setVisibility(View.VISIBLE);
        }
        corps.annulerSaisie.setText(annuler);
        corps.annulerSaisie.setOnClickListener(vue -> fermer());
        corps.validerSaisie.setText(valider);
        EditText champ = corps.champSaisiePopup.editionSaisie;
        corps.validerSaisie.setOnClickListener(vue -> {
            fermer();
            valideur.accept(champ);
        });
        centrerSaisie(corps.getRoot(), titre);
        return champ;
    }

    /**
     * L'ÉCRAN TEXTE centré ({@code popup_page_texte}) : licences et
     * NOTICE, journal de diagnostic — le texte long reste lisible,
     * jamais coupé (ADR-É8).
     *
     * @param titre   le titre (licences, journal)
     * @param texte   le texte intégral (mono compact)
     * @param fermer  le libellé de l'action fermante
     */
    public void page(CharSequence titre, CharSequence texte,
                     CharSequence fermer) {
        Context contexte = voile.getContext();
        PopupPageTexteBinding corps = PopupPageTexteBinding.inflate(
                LayoutInflater.from(contexte));
        corps.titrePage.setText(titre);
        corps.textePage.setText(texte);
        corps.fermerPage.setText(fermer);
        corps.fermerPage.setOnClickListener(vue -> fermer());
        centrer(corps.getRoot(), titre);
    }

    /** Retire la fenêtre fermée du registre (l'état pur se recale par
     *  son propre rappel de site, s'il en pose un). */
    private void retrait(PopupWindow fenetre) {
        ouvertes.remove(fenetre);
    }
}
