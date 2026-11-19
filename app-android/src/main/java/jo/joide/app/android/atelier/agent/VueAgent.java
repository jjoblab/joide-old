package jo.joide.app.android.atelier.agent;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import jo.joide.agent.Permissions;
import jo.joide.agent.UsageLlm;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ComposeurAgentBinding;
import jo.joide.app.android.databinding.ItemAttenteAgentBinding;
import jo.joide.app.android.databinding.ItemBorneAgentBinding;
import jo.joide.app.android.databinding.ItemBlocCitationMarkdownBinding;
import jo.joide.app.android.databinding.ItemBlocCodeMarkdownBinding;
import jo.joide.app.android.databinding.ItemBlocSeparateurMarkdownBinding;
import jo.joide.app.android.databinding.ItemBlocTexteMarkdownBinding;
import jo.joide.app.android.databinding.ItemErreurAgentBinding;
import jo.joide.app.android.databinding.ItemLigneTonaleAgentBinding;
import jo.joide.app.android.databinding.ItemMsgAgentBinding;
import jo.joide.app.android.databinding.ItemMsgUtilisateurBinding;
import jo.joide.app.android.databinding.ItemOutilBinding;
import jo.joide.app.android.databinding.ItemQuestionBinding;
import jo.joide.app.android.databinding.ItemQuestionLigneBinding;
import jo.joide.app.android.databinding.ItemRaisonnementBinding;
import jo.joide.app.android.databinding.ItemTravailAgentBinding;
import jo.joide.app.android.databinding.ItemUsageAgentBinding;
import jo.joide.app.android.databinding.ItemVideAgentBinding;
import jo.joide.app.android.databinding.TiroirAgentBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.RenduMarkdown;
import jo.joide.app.android.atelier.agent.EtatAgentChat.ActionErreur;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Carte;
import jo.joide.app.android.atelier.agent.EtatAgentChat.EtatOutil;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Genre;
import jo.joide.app.android.atelier.agent.EtatAgentChat.TourEtat;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Tranche;

/**
 * Le tiroir de l'agent — le transcript, la ligne d'activité et le
 * composeur REFAITS AU DESSIN DE RÉFÉRENCE (mandat du propriétaire) :
 * l'en-tête porte la marque (étincelle en pastille), le titre IA, le
 * picker de modèle dessous, la puce de mode qui se teinte par
 * permission, le menu ⋯ ; les messages de l'utilisateur vivent en
 * BULLE sur le conteneur secondaire (coins 20/20/6/20, ≤ 320 dp) avec
 * leur bouton copier ; la réponse vit pleine largeur avec le CARET
 * qui clignote pendant l'écriture et le pied copier + usage ; le
 * RAISONNEMENT vit à l'identique du ThinkingStep (étincelle qui pulse
 * tant que le tour la remplit, « Réflexion » ouverte / « Pensée »
 * repliée rouvrable au tap, conteneur tonal haut ouverte) ; le
 * travail d'un tour se replie en UN BLOC (icône d'état agrégé, « N
 * outils · M fichiers », le dernier appel, le rail vertical déplié) ;
 * la ligne d'activité dit ce que le tour fait et depuis combien de
 * temps ; le composeur porte le bouton d'envoi ROND 48 dp qui devient
 * le bouton d'arrêt. La vue ne décide RIEN : l'état pur
 * {@link EtatAgentChat} arrive posé, elle le traduit ; les actions
 * repartent par l'{@link Auditeur}.
 */
public final class VueAgent extends LinearLayout {

    /** Les actions de l'utilisateur vers l'atelier. */
    public interface Auditeur {
        /** @param message l'utilisateur envoie */
        void envoyer(String message);

        /** L'utilisateur arrête le tour. */
        void arreter();

        /**
         * @param accordee la réponse à la permission en attente
         */
        void repondrePermission(boolean accordee);

        /** @param index la question, @param choix l'option (-1 passer) */
        void repondreQuestion(int index, int choix);

        /** @param mode le mode de permission posé */
        void poserMode(Permissions.Mode mode);

        /**
         * Le sélecteur de fournisseur et de modèle IN-SITU (lot S,
         * ADR-064 — ferme l'écart du lot P « la puce ouvre les
         * réglages ») : l'atelier compose le dialogue, la vue ne
         * décide rien.
         */
        void ouvrirModele();

        /**
         * Le menu de MODE de permission (lot A7 : la puce d'en-tête
         * ouvre le menu ANCRÉ — l'atelier compose, la vue ne décide
         * rien du contenu).
         */
        void ouvrirMode();

        /**
         * Le menu de l'agent (lot S, ADR-064) : nouvelle
         * conversation, historique, fournisseur, connexions, usages
         * — la vue ne décide RIEN du contenu du menu, l'atelier le
         * compose et le service garde la vérité.
         */
        void ouvrirMenuAgent();

        /**
         * Les options d'ajout du composeur (lot S) : image, photo,
         * fichier courant, projet — l'atelier compose le menu et
         * porte les pièces, la vue n'affiche que les noms posés.
         */
        void ouvrirAjouts();

        /**
         * La bande d'avertissement d'usage est TAPPÉE (lot C4) :
         * l'écran Usages s'ouvre — la vue ne décide RIEN.
         */
        void ouvrirUsages();

        /**
         * Le bouton RÉESSAYER de la carte d'erreur (rapporté le
         * 2026-11-21 : « aucun remède à côté de la raison ») : le
         * dernier message utilisateur REPART tel quel — l'atelier le
         * retrouve dans le transcript, la vue ne retient rien.
         */
        void reessayer();

        /**
         * @param index la pièce retirée du composeur
         */
        void retirerPiece(int index);
    }

    private final Graphite.Palette palette;
    /** La coque du tiroir gonflée du XML (lot L5). */
    private final TiroirAgentBinding coque;
    /** Le composeur inclus (binding imbriqué du <include>). */
    private final ComposeurAgentBinding composeur;
    private final LinearLayout colonneCartes;
    private final ScrollView defilement;
    private final EditText saisie;
    private final LinearLayout rangeePieces;
    private final FrameLayout boutonEnvoi;
    private final Icone glypheEnvoi;
    private final LinearLayout pickerModele;
    private final TextView nomModele;
    private final Puce puceMode;
    private final Icone vueMenu;
    private final Icone vueAjouter;
    private final LinearLayout ligneActivite;
    private final Icone etincelleActivite;
    private final TextView texteActivite;
    private final TextView tempsActivite;
    private final LinearLayout bandeAvertissement;
    private final TextView libelleAvertissement;
    private final TextView relanceAvertissement;
    private final View remplissageAvertissement;
    private final View resteAvertissement;
    private final android.os.Handler horloge = new android.os.Handler(
            android.os.Looper.getMainLooper());
    /** Les pulsations vivantes (l'étincelle du tour, celle de la
     *  réflexion, celle du bloc au travail) : le rafraîchissement les
     *  arrête TOUTES avant d'en poser de neuves. */
    private final List<android.animation.ValueAnimator> pulsations =
            new ArrayList<>(3);
    private Long debutEnCours;
    private Auditeur auditeur;
    private EtatAgentChat etat = new EtatAgentChat();
    private List<String> pieces = List.of();

    public VueAgent(Context contexte) {
        this(contexte, null);
    }

    public VueAgent(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setOrientation(VERTICAL);
        palette = GraphiteVue.palette(contexte);

        // ---- la coque du XML : en-tête (marque, titre, picker, mode,
        // menu, fermer), transcript, bande d'avertissement, ligne
        // d'activité, composeur — les auditeurs restent posés ici
        coque = TiroirAgentBinding.inflate(
                LayoutInflater.from(contexte), this, true);
        composeur = coque.composeur;
        colonneCartes = coque.colonneTranscript;
        defilement = coque.defilementAgent;
        saisie = composeur.saisieAgent;
        rangeePieces = composeur.rangeePiecesAgent;
        boutonEnvoi = composeur.btnEnvoyerAgent;
        glypheEnvoi = composeur.glypheEnvoiAgent;
        pickerModele = coque.pickerModeleAgent;
        nomModele = coque.nomPickerAgent;
        puceMode = coque.puceModeAgent;
        vueMenu = coque.btnMenuAgent;
        vueAjouter = composeur.btnAjouterPiece;
        ligneActivite = coque.ligneActiviteAgent;
        etincelleActivite = coque.etincelleActiviteAgent;
        texteActivite = coque.libelleActiviteAgent;
        tempsActivite = coque.ecouleActiviteAgent;
        bandeAvertissement = coque.avertissementUsageAgent;
        libelleAvertissement = coque.libelleAvertissementAgent;
        relanceAvertissement = coque.relanceAvertissementAgent;
        remplissageAvertissement = coque.remplissageAvertissementAgent;
        resteAvertissement = coque.resteAvertissementAgent;
        bandeAvertissement.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirUsages();
            }
        });

        // la police Graphite du champ reste posée ici (ADR-085
        // décision 7 : la graisse n'est pas exprimable en
        // textAppearance)
        saisie.setTypeface(GraphiteVue.police(contexte, 400, false));
        boutonEnvoi.setOnClickListener(vue -> {
            if (auditeur == null) {
                return;
            }
            if (etat.etat() == TourEtat.EN_COURS) {
                auditeur.arreter();
            } else {
                envoyer();
            }
        });
        pickerModele.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirModele();
            }
        });
        puceMode.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirMode();
            }
        });
        vueMenu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirMenuAgent();
            }
        });
        Icone fermer = coque.btnFermerAgent;
        fermer.setOnClickListener(vue -> demanderFermeture());
        vueAjouter.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirAjouts();
            }
        });
        // la saisie suit son contenu : le bouton d'envoi s'active
        // quand il y a quelque chose à envoyer
        saisie.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start,
                    int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                    int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                majBoutonEnvoi();
            }
        });
    }

    /**
     * Pose l'auditeur des actions.
     *
     * @param auditeur le récepteur (l'atelier)
     */
    public void poserAuditeur(Auditeur auditeur) {
        this.auditeur = auditeur;
    }

    /** L'ancre du menu ⋯ (les popups ancrés, lot T). */
    public View ancreMenu() {
        return vueMenu;
    }

    /** L'ancre du bouton + du composeur (les popups ancrés, lot T). */
    public View ancreAjouter() {
        return vueAjouter;
    }

    /** L'ancre du picker de modèle (le sélecteur ancré, lot T). */
    public View ancreModele() {
        return pickerModele;
    }

    /** L'ancre de la puce de MODE de permission (le menu ancré des
     *  modes, lot A7 — la puce d'en-tête ne décide RIEN du contenu). */
    public View ancreMode() {
        return puceMode;
    }

    /**
     * Pose l'état et redessine le transcript (la vue ne garde rien).
     *
     * @param etat l'état pur du tiroir
     */
    public void poserEtat(EtatAgentChat etat) {
        this.etat = etat;
        rafraichir();
    }

    /**
     * Pose les pièces en attente du composeur (lot S) : des puces
     * nommées au-dessus de la saisie, chacune retirable.
     *
     * @param noms les noms d'affichage des pièces
     */
    public void poserPieces(List<String> noms) {
        pieces = noms == null ? List.of() : List.copyOf(noms);
        Context contexte = getContext();
        rangeePieces.removeAllViews();
        rangeePieces.setVisibility(pieces.isEmpty() ? GONE : VISIBLE);
        for (int i = 0; i < pieces.size(); i++) {
            final int index = i;
            Puce puce = new Puce(contexte);
            puce.setText(pieces.get(i));
            puce.setOnClickListener(vue -> {
                if (auditeur != null) {
                    auditeur.retirerPiece(index);
                }
            });
            rangeePieces.addView(puce, margeParams(contexte));
        }
    }

    /** Redessine depuis l'état courant : les TRANCHES DE TRAVAIL
     *  d'abord (tout ce qui précède la dernière carte d'outil d'un
     *  tour se replie en un bloc — la décision vit dans
     *  {@link EtatAgentChat#tranches()}, la vue applique), puis les
     *  cartes restantes au premier plan, espacées de 12 dp. */
    public void rafraichir() {
        Context contexte = getContext();
        arreterPulsations();
        colonneCartes.removeAllViews();
        List<Carte> cartes = etat.cartes();
        List<Tranche> tranches = etat.tranches();
        int prochaine = 0;
        int i = 0;
        while (i < cartes.size()) {
            if (prochaine < tranches.size()
                    && tranches.get(prochaine).debut() == i) {
                // la tranche couvre [debut..fin] : UN bloc de travail
                Tranche tranche = tranches.get(prochaine);
                colonneCartes.addView(
                        carteTravail(cartes, tranche));
                i = tranche.fin() + 1;
                prochaine++;
                continue;
            }
            colonneCartes.addView(vueCarte(cartes.get(i)));
            i++;
        }
        if (cartes.isEmpty()) {
            // l'état vide du transcript (gonflé de item_vide_agent)
            ItemVideAgentBinding.inflate(
                    LayoutInflater.from(contexte), colonneCartes, true);
        }
        if (etat.plafondAnnonce()) {
            ItemBorneAgentBinding borne = ItemBorneAgentBinding.inflate(
                    LayoutInflater.from(contexte), colonneCartes, false);
            borne.texteBorneAgent.setText(getContext().getString(
                    R.string.agent_plafond, etat.evictions()));
            colonneCartes.addView(borne.getRoot(), 0);
        }
        boolean enCours = etat.etat() == TourEtat.EN_COURS;
        majBoutonEnvoi();
        majLigneActivite(enCours);
        if (etat.preRemplissage() != null) {
            saisie.setText(etat.preRemplissage());
        }
        nomModele.setText(etat.modele().isEmpty()
                ? getContext().getString(R.string.agent_modele_vide)
                : etat.modele());
        poserTeinteMode();
        defilement.post(() -> defilement.fullScroll(
                View.FOCUS_DOWN));
    }

    /** Le bouton d'envoi : la flèche quand le champ porte un message
     *  à envoyer, le carré d'arrêt quand un tour vit (il reste
     *  activé : arrêter est TOUJOURS possible). */
    private void majBoutonEnvoi() {
        boolean enCours = etat.etat() == TourEtat.EN_COURS;
        boolean peutEnvoyer = !enCours
                && saisie.getText().toString().trim().length() > 0;
        glypheEnvoi.setGlyphe(enCours ? Icone.Glyphe.STOP
                : Icone.Glyphe.FLECHE);
        boutonEnvoi.setEnabled(enCours || peutEnvoyer);
        boutonEnvoi.setAlpha(enCours || peutEnvoyer ? 1f : 0.38f);
        boutonEnvoi.setContentDescription(getContext().getString(
                enCours ? R.string.agent_arreter
                        : R.string.agent_envoyer));
    }

    /** La puce de MODE se teinte par ce qu'elle laisse faire l'agent :
     *  l'acceptation auto voit le conteneur réussite, le plan seul le
     *  conteneur secondaire, la demande le conteneur discret. */
    private void poserTeinteMode() {
        puceMode.setText(texteMode(etat.mode()));
        switch (etat.mode()) {
            case ACCEPTATION_AUTO -> puceMode.poserTeinte(
                    Graphite.melange(palette.reussite, 0.14f,
                            palette.surface),
                    palette.reussite);
            case PLAN_SEULEMENT -> puceMode.poserTeinte(
                    Graphite.melange(palette.accent, 0.16f,
                            palette.surface),
                    palette.texte);
            default -> puceMode.poserTeinte(palette.surfaceHaute,
                    palette.texteSecondaire);
        }
    }

    /** La vue d'une carte AU PREMIER PLAN (hors tranche de travail). */
    private View vueCarte(Carte carte) {
        Context contexte = getContext();
        return switch (carte.genre) {
            case MESSAGE_UTILISATEUR -> carteMessage(carte);
            case REPONSE -> carteReponse(carte);
            case RAISONNEMENT -> carteRaisonnement(carte);
            case OUTIL -> carteOutil(carte);
            case FICHIERS -> ligneTonale(carte.detail);
            case ATTENTE -> carteAttente(carte);
            case COMPACTION -> ligneTonale(contexte.getString(
                    R.string.agent_compaction, carte.avant,
                    carte.apres));
            case USAGE -> carteUsage(carte);
            case ERREUR -> carteErreur(carte);
            case QUESTIONS -> carteQuestions(carte);
        };
    }

    // ------------------------------------------------------------------
    // Les cartes
    // ------------------------------------------------------------------

    /** Le message UTILISATEUR : la bulle sur le conteneur secondaire
     *  (≤ 320 dp, le petit coin côté queue), les pièces dessous, le
     *  bouton COPIER qui revient en coche 1,5 s. */
    private View carteMessage(Carte carte) {
        Context contexte = getContext();
        ItemMsgUtilisateurBinding item = ItemMsgUtilisateurBinding
                .inflate(LayoutInflater.from(getContext()),
                        colonneCartes, false);
        TextView vue = item.texteMessage;
        GraphiteVue.typographie(vue, Graphite.CORPS);
        vue.setText(carte.texte);
        if (carte.pieces != null && !carte.pieces.isEmpty()) {
            for (String nom : carte.pieces) {
                Puce puce = new Puce(getContext());
                puce.setText(nom);
                item.piecesMessage.addView(puce,
                        margeParams(getContext()));
            }
        }
        brancherCopie(item.copierMessage, carte.texte);
        return item.getRoot();
    }

    /** La carte de RÉPONSE au rendu MODERNE (terrain du 2026-11-22 :
     *  « plus moderne, plus intelligent, rendu propre ») : le texte
     *  de l'agent vit en BLOCS Markdown — paragraphes aux spans
     *  (gras, italique, code en ligne, liens), titres à l'échelle,
     *  listes à puces, citations à barre accent, blocs de code
     *  monospace sur conteneur haut avec bouton copier — le geste
     *  de l'IDE de référence (AssistantMarkdown + CodeSample). Le
     *  CARET clignote pendant que le tour écrit ; le pied porte le
     *  bouton COPIER et la légende des jetons quand le tour est fini. */
    private View carteReponse(Carte carte) {
        Context contexte = getContext();
        ItemMsgAgentBinding item = ItemMsgAgentBinding.inflate(
                LayoutInflater.from(getContext()),
                colonneCartes, false);
        remplirMarkdown(item.blocsReponse,
                carte.reponse == null ? "" : carte.reponse, false);
        boolean enCours = etat.etat() == TourEtat.EN_COURS;
        boolean ecriture = enCours && estDerniereReponse(carte)
                && !unOutilTourne() && !etat.contientAttente();
        if (ecriture) {
            // le caret qui clignote : 7×14 dp au rayon 2, la couleur
            // pulse comme la ligne d'activité
            item.caretReponse.setVisibility(View.VISIBLE);
            demarrerPulsationFond(item.caretReponse);
        }
        if (!enCours && carte.reponse != null
                && !carte.reponse.isEmpty()) {
            item.piedReponse.setVisibility(View.VISIBLE);
            brancherCopie(item.copierReponse, carte.reponse);
        }
        return item.getRoot();
    }

    /** La carte de RÉFLEXION au dessin de référence : l'en-tête
     *  [étincelle 12 dp · « Réflexion » | « Pensée » · chevron],
     *  OUVERTE tant que le tour la remplit (il se passe quelque chose
     *  à regarder), repliée à UNE LIGNE quand il est fini — le tap
     *  rouvre ; ouverte, la carte prend le conteneur tonal haut au
     *  rayon 14 et le rembourrage 12/10 ; repliée, le fond se retire
     *  et le rembourrage tombe à 2/4. */
    private View carteRaisonnement(Carte carte) {
        Context contexte = getContext();
        ItemRaisonnementBinding item = ItemRaisonnementBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout bloc = item.getRoot();
        boolean vivant = etat.etat() == TourEtat.EN_COURS
                && estDernierRaisonnement(carte);
        boolean ouverte = carte.deplie || vivant;
        boolean pliable = !vivant && carte.raisonnement.length() > 0;

        // l'étincelle : elle pulse tant que le tour remplit la carte,
        // elle pose l'accent quand elle est finie
        Icone etincelle = item.etincelleRaisonnement;
        if (vivant) {
            demarrerPulsationTrait(etincelle);
        } else {
            etincelle.setCouleur(palette.accent);
        }

        TextView plis = item.plisRaisonnement;
        plis.setText(contexte.getString(vivant
                ? R.string.agent_raisonnement_vivant
                : R.string.agent_raisonnement_plie));

        // le chevron ne vit que sur une carte finie repliable
        Icone chevron = item.chevronRaisonnement;
        if (pliable) {
            chevron.setVisibility(View.VISIBLE);
            chevron.setRotation(ouverte ? 90f : 0f);
        }

        // la carte : conteneur tonal haut rayon 14 ouverte, transparente
        // repliée ; le rembourrage suit (12/10 ouverte, 2/4 repliée)
        if (ouverte) {
            bloc.setBackgroundResource(R.drawable.carte_reflexion_ia);
            bloc.setPadding(GraphiteVue.dp(contexte, 12),
                    GraphiteVue.dp(contexte, 10),
                    GraphiteVue.dp(contexte, 12),
                    GraphiteVue.dp(contexte, 10));
        } else {
            bloc.setBackgroundResource(0);
            bloc.setPadding(GraphiteVue.dp(contexte, 2),
                    GraphiteVue.dp(contexte, 4),
                    GraphiteVue.dp(contexte, 2),
                    GraphiteVue.dp(contexte, 4));
        }

        bloc.setOnClickListener(vue -> {
            if (pliable) {
                carte.deplie = !ouverte;
                rafraichir();
            }
        });
        if (ouverte && carte.raisonnement.length() > 0) {
            // terrain du 2026-11-22 : le raisonnement vit AUSSI en
            // blocs Markdown (note 13 sp secondaire — les blocs de code
            // du raisonnement restent monospace sur conteneur haut)
            remplirMarkdown(item.conteneurRaisonnement,
                    carte.raisonnement.toString(), true);
            item.conteneurRaisonnement.setVisibility(View.VISIBLE);
        }
        return bloc;
    }

    /**
     * LE BLOC DE TRAVAIL d'un tour : tout ce qui précède la dernière
     * carte d'outil se replie en UNE rangée [icône d'état agrégé · « N
     * outils · M fichiers » · le dernier appel · chevron] ; déplié,
     * le RAIL VERTICAL (1 dp) porte les étapes dans l'ordre (la
     * réflexion ouverte tant qu'elle vit, chaque appel d'outil
     * pliable, les lignes tonales). Le pli vit dans l'état pur (clé =
     * id d'appel du dernier outil — survit aux évictions) ; l'état
     * agrégé suit en cours > erreur > refus > fini.
     */
    private View carteTravail(List<Carte> cartes, Tranche tranche) {
        Context contexte = getContext();
        ItemTravailAgentBinding item = ItemTravailAgentBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout bloc = item.getRoot();

        // l'état agrégé (en cours > refus > fini) et le dernier appel
        EtatOutil agregat = EtatOutil.FINI;
        int outils = 0;
        Carte dernierOutil = null;
        for (int i = tranche.debut(); i <= tranche.fin(); i++) {
            Carte carte = cartes.get(i);
            if (carte.genre == Genre.OUTIL) {
                outils++;
                dernierOutil = carte;
                if (carte.etatOutil == EtatOutil.EN_COURS) {
                    agregat = EtatOutil.EN_COURS;
                } else if (carte.etatOutil == EtatOutil.REFUSE
                        && agregat != EtatOutil.EN_COURS) {
                    agregat = EtatOutil.REFUSE;
                }
            }
        }
        poserIconeEtat(item.statutTravail, agregat, true);

        // le résumé « N outils · M fichiers » (M omis sans fichiers)
        int fichiers = EtatAgentChat.fichiersTouches(cartes, tranche);
        StringBuilder resume = new StringBuilder(
                contexte.getString(R.string.agent_travail_outils, outils));
        if (fichiers > 0) {
            resume.append(" · ").append(contexte.getString(
                    R.string.agent_travail_fichiers, fichiers));
        }
        item.resumeTravail.setTypeface(GraphiteVue.police(contexte,
                500, false));
        item.resumeTravail.setText(resume);

        // replié, l'en-tête dit le DERNIER appel
        String cle = EtatAgentChat.cleDeTranche(cartes, tranche);
        boolean deplie = etat.travailDeplie(cle);
        String titreDernier = dernierOutil == null ? ""
                : dernierOutil.outil
                + (dernierOutil.cible == null
                        || dernierOutil.cible.isEmpty() ? ""
                        : " " + dernierOutil.cible);
        item.dernierTravail.setText(deplie ? "" : titreDernier);
        item.chevronTravail.setRotation(deplie ? 90f : 0f);
        item.enteteTravail.setOnClickListener(vue -> {
            etat.poserTravailDeplie(cle, !deplie);
            rafraichir();
        });

        if (deplie) {
            // le rail vertical porte les étapes DANS L'ORDRE (le
            // contenu vit à 19 dp du bord — le rail à 8, l'écart de 10)
            item.etapesTravail.setVisibility(View.VISIBLE);
            LinearLayout colonne = item.colonneEtapesTravail;
            for (int i = tranche.debut(); i <= tranche.fin(); i++) {
                Carte carte = cartes.get(i);
                View vue = switch (carte.genre) {
                    case RAISONNEMENT -> carteRaisonnement(carte);
                    case OUTIL -> carteOutil(carte);
                    case FICHIERS -> ligneTonale(carte.detail);
                    case ATTENTE -> carteAttente(carte);
                    case COMPACTION -> ligneTonale(contexte.getString(
                            R.string.agent_compaction, carte.avant,
                            carte.apres));
                    default -> null;
                };
                if (vue != null) {
                    LinearLayout.LayoutParams params = new
                            LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT);
                    params.bottomMargin = GraphiteVue.dp(contexte, 4);
                    vue.setLayoutParams(params);
                    colonne.addView(vue);
                }
            }
        }
        return bloc;
    }

    /** La ligne d'un APPEL D'OUTIL : l'icône d'état, le TITRE où le
     *  VERBE vit plus bas que sa cible, le détail replié (première
     *  ligne du résultat), le chevron ; déplié, la section
     *  « Résultat » : le bloc monospace défilant borné 320 dp. */
    private View carteOutil(Carte carte) {
        Context contexte = getContext();
        ItemOutilBinding item = ItemOutilBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout bloc = item.getRoot();

        poserIconeEtat(item.statutOutil, carte.etatOutil, false);

        // le titre : le verbe (premier mot) vit plus bas que la cible
        String titre = carte.outil
                + (carte.cible == null || carte.cible.isEmpty() ? ""
                        : " " + carte.cible);
        TextView vueTitre = item.titreOutil;
        vueTitre.setTypeface(GraphiteVue.police(contexte, 400, false));
        vueTitre.setText(titreVerbe(titre));
        vueTitre.setTextColor(palette.texte);

        // le détail replié : la première ligne du résultat
        TextView detail = item.detailOutil;
        boolean ouvrable = carte.etatOutil != EtatOutil.EN_COURS
                && carte.resultat != null;
        if (ouvrable && !carte.deplie) {
            detail.setVisibility(View.VISIBLE);
            detail.setText(premiereLigne(carte.resultat));
        }
        Icone chevron = item.chevronOutil;
        if (ouvrable) {
            chevron.setVisibility(View.VISIBLE);
            chevron.setRotation(carte.deplie ? 90f : 0f);
            item.rangeeOutil.setOnClickListener(vue -> {
                carte.deplie = !carte.deplie;
                rafraichir();
            });
        }
        if (carte.deplie && carte.resultat != null) {
            item.blocResultatOutil.setVisibility(View.VISIBLE);
            TextView resultat = item.resultatOutil;
            resultat.setTypeface(GraphiteVue.police(contexte, 400,
                    true));
            resultat.setText(carte.resultat);
            resultat.setMovementMethod(
                    new android.text.method.ScrollingMovementMethod());
        }
        return bloc;
    }

    /** Le titre d'un appel : le premier mot (le verbe de l'outil) en
     *  graisse moyenne et couleur secondaire, le reste au premier
     *  plan. */
    private CharSequence titreVerbe(String titre) {
        int coupe = titre.indexOf(' ');
        if (coupe <= 0) {
            return titre;
        }
        SpannableString span = new SpannableString(titre);
        span.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                0, coupe, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(palette.texteSecondaire),
                0, coupe, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    /** La première ligne d'un résultat pluri-lignes (le détail
     *  replié d'un appel). */
    private static String premiereLigne(String resultat) {
        if (resultat == null) {
            return "";
        }
        int fin = resultat.indexOf('\n');
        return fin < 0 ? resultat : resultat.substring(0, fin);
    }

    /** L'icône d'état d'un appel ou du groupe : la rotation qui
     *  tourne (14 dp, trait 1,5) tant qu'il vit, la coche réussite,
     *  la croix erreur, la croix alerte du refus. */
    private void poserIconeEtat(FrameLayout conteneur,
                                EtatOutil etatOutil, boolean groupe) {
        Context contexte = getContext();
        conteneur.removeAllViews();
        if (etatOutil == EtatOutil.EN_COURS) {
            // la rotation qui tourne : une progression indéterminée
            // teintée accent dans la case 14 dp
            android.widget.ProgressBar rotation =
                    new android.widget.ProgressBar(contexte, null,
                            android.R.attr.progressBarStyleSmall);
            rotation.setIndeterminate(true);
            rotation.getIndeterminateDrawable().mutate()
                    .setTint(palette.accent);
            FrameLayout.LayoutParams params = new FrameLayout
                    .LayoutParams(GraphiteVue.dp(contexte, 14),
                    GraphiteVue.dp(contexte, 14),
                    android.view.Gravity.CENTER);
            conteneur.addView(rotation, params);
            return;
        }
        Icone icone = new Icone(contexte);
        icone.setGlyphe(etatOutil == EtatOutil.REFUSE
                ? Icone.Glyphe.CROIX : Icone.Glyphe.COCHE);
        icone.setCouleur(etatOutil == EtatOutil.REFUSE
                ? (groupe ? palette.erreur : palette.erreur)
                : palette.reussite);
        icone.setTraitDp(1.5f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                GraphiteVue.dp(contexte, 14),
                GraphiteVue.dp(contexte, 14),
                android.view.Gravity.CENTER);
        conteneur.addView(icone, params);
    }

    // ------------------------------------------------------------------
    // Terrain du 2026-11-22 — la pastille d'attente et le rendu Markdown
    // ------------------------------------------------------------------

    /**
     * La PASTILLE d'attente (le dessin de la maquette .attente) :
     * pilule alignée à gauche, fond alerte 8 %, bord alerte 30 %,
     * rayon 999, point 8 dp qui PULSE tant que le tour vit — la pause
     * de limite de débit s'énonce UNE fois par attente (la carte par
     * seconde d'hier spammait le transcript, « peu professionnel »).
     */
    private View carteAttente(Carte carte) {
        Context contexte = getContext();
        ItemAttenteAgentBinding item = ItemAttenteAgentBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout pastille = item.getRoot();
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(Graphite.melange(palette.alerte, 0.08f,
                palette.surface));
        fond.setCornerRadius(GraphiteVue.dp(contexte, 999));
        fond.setStroke(GraphiteVue.dp(contexte, 1),
                Graphite.melange(palette.alerte, 0.30f, palette.surface));
        pastille.setBackground(fond);
        item.texteAttente.setTypeface(GraphiteVue.police(contexte, 400,
                true));
        item.texteAttente.setText(getContext().getString(
                R.string.agent_attente, carte.secondes));
        // le point qui pulse : la couleur glisse alerte → texte et
        // retour tant que la pause coule
        if (etat.etat() == TourEtat.EN_COURS) {
            demarrerPulsationFond(item.pointAttente);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pastille.setLayoutParams(params);
        return pastille;
    }

    /**
     * Remplit un conteneur avec les BLOCS Markdown d'un texte — le
     * geste de l'IDE de référence : paragraphes aux spans, titres à
     * l'échelle, listes, citations, blocs de code monospace avec
     * bouton copier. Le mode NOTE (la carte de réflexion) met le
     * texte en 13 sp secondaire. Chaque rafraîchissement repart de
     * zéro : le conteneur se vide d'abord.
     *
     * @param conteneur le conteneur vertical à remplir
     * @param texte     le texte markdown de l'agent
     * @param note      vrai pour la typographie note (réflexion)
     */
    private void remplirMarkdown(LinearLayout conteneur, String texte,
                                 boolean note) {
        Context contexte = getContext();
        conteneur.removeAllViews();
        List<RenduMarkdown.Bloc> blocs = RenduMarkdown.analyser(texte);
        boolean premier = true;
        for (RenduMarkdown.Bloc bloc : blocs) {
            for (View vue : vuesBloc(bloc, note)) {
                LinearLayout.LayoutParams params = new LinearLayout
                        .LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT);
                if (!premier) {
                    params.topMargin = GraphiteVue.dp(contexte,
                            note ? 5 : 8);
                }
                premier = false;
                conteneur.addView(vue, params);
            }
        }
    }

    /**
     * Les vues d'un bloc markdown — les LISTES s'aplatissent (un
     * gonflé par élément, l'indentation porte le niveau), tout le
     * reste est UNE vue ; la hiérarchie vit en XML (ADR-085 :
     * item_bloc_*_markdown.xml, gonflés par binding), la typographie
     * seule se pose par code.
     */
    private List<View> vuesBloc(RenduMarkdown.Bloc bloc, boolean note) {
        Context contexte = getContext();
        LayoutInflater gonfleur = LayoutInflater.from(contexte);
        switch (bloc.genre) {
            case SEPARATEUR -> {
                return List.of(ItemBlocSeparateurMarkdownBinding
                        .inflate(gonfleur, colonneCartes, false)
                        .getRoot());
            }
            case CODE -> {
                return List.of(vueBlocCode(bloc.texte, bloc.langue));
            }
            case TITRE -> {
                ItemBlocTexteMarkdownBinding item =
                        ItemBlocTexteMarkdownBinding.inflate(gonfleur,
                                colonneCartes, false);
                TextView vue = item.texteBlocTexte;
                int sp = switch (Math.min(6, Math.max(1, bloc.niveau))) {
                    case 1 -> 20;
                    case 2 -> 18;
                    case 3 -> 17;
                    default -> 15;
                };
                vue.setTextSize(note ? Math.max(13, sp - 2) : sp);
                vue.setTypeface(GraphiteVue.police(contexte, 600, false));
                vue.setTextColor(palette.texte);
                RenduMarkdown.Ligne ligneTitre = RenduMarkdown.enLigne(
                        bloc.texte);
                vue.setText(ligneTitre.texte());
                poserSpans(vue, ligneTitre, 0);
                return List.of(item.getRoot());
            }
            case LISTE -> {
                List<View> elements = new ArrayList<>();
                for (RenduMarkdown.ElementListe element : bloc.elements) {
                    ItemBlocTexteMarkdownBinding item =
                            ItemBlocTexteMarkdownBinding.inflate(
                                    gonfleur, colonneCartes, false);
                    TextView vue = item.texteBlocTexte;
                    vue.setTextSize(note ? 13 : 15);
                    vue.setTypeface(GraphiteVue.police(contexte, 400,
                            false));
                    vue.setTextColor(note ? palette.texteSecondaire
                            : palette.texte);
                    vue.setLineSpacing(GraphiteVue.dp(contexte, 4), 1f);
                    RenduMarkdown.Ligne ligne = RenduMarkdown.enLigne(
                            element.texte());
                    String marqueur = element.ordonne()
                            ? element.numero() + ". "
                            : "\u2022  ";
                    vue.setText(marqueur + ligne.texte());
                    poserSpans(vue, ligne, marqueur.length());
                    LinearLayout.LayoutParams params = new LinearLayout
                            .LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT);
                    params.leftMargin = GraphiteVue.dp(contexte,
                            12 * element.niveau());
                    vue.setLayoutParams(params);
                    elements.add(item.getRoot());
                }
                return elements;
            }
            case CITATION -> {
                ItemBlocCitationMarkdownBinding item =
                        ItemBlocCitationMarkdownBinding.inflate(gonfleur,
                                colonneCartes, false);
                LinearLayout interne = item.conteneurBlocCitation;
                boolean premier = true;
                for (RenduMarkdown.Bloc sous : bloc.interne) {
                    for (View vue : vuesBloc(sous, true)) {
                        LinearLayout.LayoutParams params = new LinearLayout
                                .LayoutParams(LayoutParams.MATCH_PARENT,
                                LayoutParams.WRAP_CONTENT);
                        if (!premier) {
                            params.topMargin = GraphiteVue.dp(contexte, 4);
                        }
                        premier = false;
                        interne.addView(vue, params);
                    }
                }
                return List.of(item.getRoot());
            }
            default -> {
                ItemBlocTexteMarkdownBinding item =
                        ItemBlocTexteMarkdownBinding.inflate(gonfleur,
                                colonneCartes, false);
                TextView vue = item.texteBlocTexte;
                vue.setTextSize(note ? 13 : 15);
                vue.setTypeface(GraphiteVue.police(contexte, 400, false));
                vue.setTextColor(note ? palette.texteSecondaire
                        : palette.texte);
                vue.setLineSpacing(GraphiteVue.dp(contexte, 5), 1f);
                RenduMarkdown.Ligne ligne = RenduMarkdown.enLigne(
                        bloc.texte);
                vue.setText(ligne.texte());
                poserSpans(vue, ligne, 0);
                return List.of(item.getRoot());
            }
        }
    }

    /** Le bloc de CODE : conteneur haut rayon 10 (fond par code),
     *  étiquette de langue, texte monospace défilant borné 320 dp,
     *  pied copier. */
    private View vueBlocCode(String code, String langue) {
        Context contexte = getContext();
        ItemBlocCodeMarkdownBinding item = ItemBlocCodeMarkdownBinding
                .inflate(LayoutInflater.from(contexte), colonneCartes,
                        false);
        LinearLayout bloc = item.getRoot();
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surfaceHaute);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 10));
        bloc.setBackground(fond);
        if (langue != null && !langue.isBlank()) {
            item.langueBlocCode.setVisibility(VISIBLE);
            item.langueBlocCode.setTypeface(GraphiteVue.police(contexte,
                    500, true));
            item.langueBlocCode.setText(langue.trim().toLowerCase(
                    java.util.Locale.ROOT));
        } else {
            item.langueBlocCode.setVisibility(GONE);
        }
        TextView vue = item.codeBlocCode;
        vue.setTypeface(GraphiteVue.police(contexte, 400, true));
        vue.setTextColor(palette.texte);
        vue.setText(code.stripTrailing());
        vue.setMovementMethod(
                new android.text.method.ScrollingMovementMethod());
        vue.setMaxHeight(GraphiteVue.dp(contexte, 320));
        brancherCopie(item.copierBlocCode, code.stripTrailing());
        return bloc;
    }

    /** Pose les marquages d'une ligne sur le TextView (décalage du
     *  marqueur de liste inclus). */
    private void poserSpans(TextView vue, RenduMarkdown.Ligne ligne,
                            int decalage) {
        if (ligne.marquages().isEmpty()) {
            return;
        }
        CharSequence texte = vue.getText();
        SpannableString spans = texte instanceof SpannableString deja
                ? deja : new SpannableString(texte);
        for (RenduMarkdown.Marquage marquage : ligne.marquages()) {
            int debut = Math.min(marquage.debut() + decalage,
                    spans.length());
            int fin = Math.min(marquage.fin() + decalage,
                    spans.length());
            if (debut >= fin) {
                continue;
            }
            switch (marquage.marque()) {
                case GRAS -> spans.setSpan(
                        new StyleSpan(android.graphics.Typeface.BOLD),
                        debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                case ITALIQUE -> spans.setSpan(
                        new StyleSpan(android.graphics.Typeface.ITALIC),
                        debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                case CODE -> {
                    spans.setSpan(new TypefaceSpan("monospace"),
                            debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spans.setSpan(new ForegroundColorSpan(
                                    palette.texte),
                            debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spans.setSpan(new BackgroundColorSpan(
                                    palette.surfaceHaute),
                            debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spans.setSpan(new RelativeSizeSpan(0.92f),
                            debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                case LIEN -> {
                    spans.setSpan(new URLSpan(marquage.cible()),
                            debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spans.setSpan(new ForegroundColorSpan(palette.accent),
                            debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spans.setSpan(new UnderlineSpan(),
                            debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        vue.setText(spans);
        vue.setMovementMethod(
                android.text.method.LinkMovementMethod.getInstance());
    }

    private View ligneTonale(String texte) {
        Context contexte = getContext();
        ItemLigneTonaleAgentBinding ligne =
                ItemLigneTonaleAgentBinding.inflate(
                        LayoutInflater.from(contexte), colonneCartes,
                        false);
        ligne.texteLigneTonale.setText(texte);
        return ligne.getRoot();
    }

    private View carteUsage(Carte carte) {
        Context contexte = getContext();
        // la légende discrète de fin de tour : les jetons compacts
        // (« 940 », « 8.4k », « 1.2M »), la ligne qui monte tendance
        // sans prendre la place
        ItemUsageAgentBinding vue = ItemUsageAgentBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        UsageLlm u = carte.usage;
        StringBuilder ligne = new StringBuilder();
        if (u.entrees > 0) {
            ligne.append(jetonsCompacts(u.entrees)).append(' ')
                    .append(contexte.getString(R.string.agent_usage_entrees));
        }
        if (u.sorties > 0) {
            if (ligne.length() > 0) {
                ligne.append(" · ");
            }
            ligne.append(jetonsCompacts(u.sorties)).append(' ')
                    .append(contexte.getString(R.string.agent_usage_sorties));
        }
        if (u.lecturesCache > 0) {
            if (ligne.length() > 0) {
                ligne.append(" · ");
            }
            ligne.append(jetonsCompacts(u.lecturesCache)).append(' ')
                    .append(contexte.getString(R.string.agent_usage_cache));
        }
        if (ligne.length() == 0) {
            ligne.append(contexte.getString(R.string.agent_usage_requete,
                    carte.requetes));
        } else if (carte.requetes > 0) {
            ligne.append(" · ")
                    .append(contexte.getString(R.string.agent_usage_requete,
                            carte.requetes));
        }
        vue.texteUsageAgent.setText(ligne);
        return vue.getRoot();
    }

    /** Les jetons compacts (« 940 », « 8.4k », « 1.2M ») du pied. */
    private static String jetonsCompacts(long n) {
        return EtatAgentChat.jetonsCompacts(n);
    }

    private View carteErreur(Carte carte) {
        Context contexte = getContext();
        ItemErreurAgentBinding item = ItemErreurAgentBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout bloc = item.getRoot();
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(Graphite.melange(palette.erreur, 0.14f,
                palette.surface));
        fond.setCornerRadius(GraphiteVue.dp(contexte, 14));
        fond.setStroke(GraphiteVue.dp(contexte, 1),
                Graphite.melange(palette.erreur, 0.45f, palette.surface));
        bloc.setBackground(fond);
        item.titreErreur.setText(texteErreur(carte));
        // rapporté le 2026-11-21 : le détail muet (« null ») quittait
        // la carte au lieu de l'habiter — la ligne se CACHE quand le
        // fournisseur n'a rien dit. Terrain du 2026-11-22 : le détail
        // devient le DIAGNOSTIC TECHNIQUE — monospace discret sous la
        // raison française, jamais une seconde phrase de même taille
        // (« le cadre erreur est affiché 2 fois »)
        if (carte.detail == null || carte.detail.isBlank()) {
            item.detailErreur.setVisibility(View.GONE);
        } else {
            item.detailErreur.setVisibility(View.VISIBLE);
            item.detailErreur.setTypeface(GraphiteVue.police(contexte,
                    400, true));
            item.detailErreur.setText(carte.detail);
        }
        if (carte.action == ActionErreur.OUVRIR_REGLAGES
                || carte.action == ActionErreur.CHANGER_MODELE) {
            item.actionErreur.setVisibility(VISIBLE);
            item.actionErreur.setText(carte.action == ActionErreur
                    .OUVRIR_REGLAGES
                    ? R.string.agent_ouvrir_reglages
                    : R.string.agent_changer_modele);
            item.actionErreur.setOnClickListener(vue ->
                    ouvrirReglagesIa());
        }
        // la rangée de secours : RÉESSAYER (le dernier message repart)
        // et COPIER (le diagnostic complet part au presse-papiers)
        item.reessayerErreur.setText(R.string.agent_reessayer);
        item.reessayerErreur.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.reessayer();
            }
        });
        item.copierErreur.setText(R.string.agent_copier);
        item.copierErreur.setOnClickListener(vue ->
                copierDiagnostic(item.copierErreur,
                        texteErreur(carte), carte.detail));
        return bloc;
    }

    /** Le diagnostic complet part au presse-papiers ; le petit
     * bouton dit « Copié » 1,5 s — la raison sans le détail se
     * copie aussi (le fournisseur ne dit pas toujours plus). */
    private void copierDiagnostic(TextView bouton,
                                  String titre, String detail) {
        String texte = detail == null || detail.isBlank()
                ? titre : titre + "\n" + detail;
        try {
            android.content.ClipboardManager pressePapiers =
                    (android.content.ClipboardManager)
                            getContext().getSystemService(
                                    Context.CLIPBOARD_SERVICE);
            if (pressePapiers != null) {
                pressePapiers.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                                "diagnostic", texte));
            }
        } catch (RuntimeException e) {
            // le presse-papiers reste un détail : rien ne crash
        }
        bouton.setText(R.string.agent_copie);
        bouton.setTextColor(palette.reussite);
        horloge.postDelayed(() -> {
            bouton.setText(R.string.agent_copier);
            bouton.setTextColor(palette.texteSecondaire);
        }, 1500);
    }

    private View carteQuestions(Carte carte) {
        Context contexte = getContext();
        LinearLayout bloc = ItemQuestionBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes,
                false).getRoot();
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 14));
        fond.setStroke(GraphiteVue.dp(contexte, 1),
                Graphite.melange(palette.accent, 0.35f, palette.surface));
        bloc.setBackground(fond);
        for (int i = 0; i < carte.questions.size(); i++) {
            EtatAgentChat.Question question = carte.questions.get(i);
            ItemQuestionLigneBinding ligne =
                    ItemQuestionLigneBinding.inflate(
                            LayoutInflater.from(contexte), bloc, true);
            if (i > 0) {
                ligne.getRoot().setPadding(0,
                        GraphiteVue.dp(contexte, 10), 0, 0);
            }
            ligne.demandeQuestion.setText(contexte.getString(
                    R.string.agent_question_numero, i + 1,
                            carte.questions.size())
                    + " " + question.question);
            LinearLayout options = ligne.optionsQuestion;
            for (int choix = 0; choix < question.options.size()
                    + 2; choix++) {
                Puce puce = new Puce(contexte);
                final boolean passer = choix
                        == question.options.size() + 1;
                String texte = passer
                        ? contexte.getString(R.string.agent_passer)
                        : choix == question.options.size()
                                ? contexte.getString(R.string.agent_autre)
                                : question.options.get(choix);
                boolean choisi = question.choix == choix
                        || (passer && question.choix == -1);
                puce.setText(texte);
                puce.setPointee(choisi);
                final int indexQuestion = i;
                final int indexOption = choix;
                puce.setOnClickListener(vue -> {
                    if (auditeur != null) {
                        auditeur.repondreQuestion(indexQuestion,
                                passer ? -1 : indexOption);
                    }
                });
                options.addView(puce, margeParams(contexte));
            }
        }
        return bloc;
    }

    // ------------------------------------------------------------------
    // Le bouton copier (la coche revient 1,5 s)
    // ------------------------------------------------------------------

    /** Branche le bouton copier : le texte part au presse-papiers, le
     *  glyphe revient en coche réussie 1,5 s. */
    private void brancherCopie(Icone bouton, String texte) {
        bouton.setGlyphe(Icone.Glyphe.COPIER);
        bouton.setCouleur(palette.texteSecondaire);
        bouton.setTraitDp(1.5f);
        bouton.setOnClickListener(vue -> {
            try {
                android.content.ClipboardManager pressePapiers =
                        (android.content.ClipboardManager)
                                getContext().getSystemService(
                                        Context.CLIPBOARD_SERVICE);
                if (pressePapiers != null) {
                    pressePapiers.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                    "message", texte));
                }
            } catch (RuntimeException e) {
                // le presse-papiers reste un détail : rien ne crash
            }
            bouton.setGlyphe(Icone.Glyphe.COCHE);
            bouton.setCouleur(palette.reussite);
            horloge.postDelayed(() -> {
                bouton.setGlyphe(Icone.Glyphe.COPIER);
                bouton.setCouleur(palette.texteSecondaire);
            }, 1500);
        });
    }

    // ------------------------------------------------------------------
    // La ligne d'activité et la bande d'avertissement
    // ------------------------------------------------------------------

    private void envoyer() {
        String message = saisie.getText().toString().trim();
        if (message.isEmpty() || auditeur == null) {
            return;
        }
        saisie.setText("");
        auditeur.envoyer(message);
    }

    /** Le picker de modèle de l'en-tête : l'atelier ouvre le
     *  dialogue in-situ. */
    private void auditeurModele() {
        if (auditeur != null) {
            auditeur.ouvrirModele();
        }
    }

    private String texteMode(Permissions.Mode mode) {
        return switch (mode) {
            case DEMANDE_CHAQUE -> getContext().getString(
                    R.string.agent_mode_demande);
            case ACCEPTATION_AUTO -> getContext().getString(
                    R.string.agent_mode_auto);
            case PLAN_SEULEMENT -> getContext().getString(
                    R.string.agent_mode_plan);
        };
    }

    /** Le titre d'une carte d'erreur : la RAISON par genre (rapporté
     *  le 2026-11-21 — « La requête a échoué » ne disait NI pourquoi
     *  NI quoi faire ; chaque genre porte sa phrase qui guérit). */
    private String texteErreur(Carte carte) {
        if (carte.genreErreur == null) {
            return getContext().getString(R.string.agent_erreur_generique);
        }
        int ressource = switch (carte.genreErreur) {
            case RESEAU -> R.string.agent_erreur_reseau;
            case DELAI -> R.string.agent_erreur_delai;
            case LIMITE_DEBIT -> R.string.agent_erreur_limite_debit;
            case LIMITE_JOUR -> R.string.agent_erreur_limite_jour;
            case QUOTA_EPUISE -> R.string.agent_erreur_quota;
            case CONTEXTE_TROP_LONG -> R.string.agent_erreur_contexte;
            case MODELE_INCONNU -> R.string.agent_erreur_modele;
            case CLE_MANQUANTE -> R.string.agent_erreur_cle_manquante;
            case CLE_INVALIDE -> R.string.agent_erreur_cle_invalide;
            case CLE_SANS_DROIT -> R.string.agent_erreur_cle_droit;
            case CONTENU_REFUSE -> R.string.agent_erreur_contenu;
            case ERREUR_SERVEUR -> R.string.agent_erreur_serveur;
            case REQUETE_REFUSEE -> R.string.agent_erreur_requete;
            case REPONSE_ILLISIBLE -> R.string.agent_erreur_illisible;
            case FLUX_INTERROMPU -> R.string.agent_erreur_flux;
        };
        return getContext().getString(ressource);
    }

    private void ouvrirReglagesIa() {
        try {
            android.content.Intent intention =
                    new android.content.Intent(getContext(),
                            jo.joide.app.android.ecrans.parametres
                                    .EcranParametres.class);
            getContext().startActivity(intention);
        } catch (RuntimeException e) {
            // l'écran de réglages reste atteignable par le menu
        }
    }

    private void demanderFermeture() {
        // la fermeture du tiroir appartient à l'atelier (retour
        // système, bouton) — la vue ne ferme pas l'écran elle-même
        if (auditeur instanceof AuditeurTiroir tiroir) {
            tiroir.fermer();
        }
    }

    /** L'extension locale : fermer le tiroir. */
    public interface AuditeurTiroir extends Auditeur {
        /** Ferme le tiroir (l'atelier décide). */
        void fermer();
    }

    // ------------------------------------------------------------------
    // Les pulsations (la marque qui respire)
    // ------------------------------------------------------------------

    /**
     * La pulsation du TRAIT d'une étincelle : la couleur glisse de la
     * couleur secondaire vers l'accent et retour (900 ms, elle va et
     * revient) — deux couleurs OPACQUES, jamais un alpha qui rend
     * le glyphe translucide.
     *
     * @param icone l'étincelle vivante
     */
    private void demarrerPulsationTrait(Icone icone) {
        android.animation.ValueAnimator pulsation =
                android.animation.ValueAnimator.ofObject(
                        new android.animation.ArgbEvaluator(),
                        palette.texteSecondaire, palette.accent);
        pulsation.setDuration(900);
        pulsation.setRepeatCount(
                android.animation.ValueAnimator.INFINITE);
        pulsation.setRepeatMode(
                android.animation.ValueAnimator.REVERSE);
        pulsation.addUpdateListener(animation ->
                icone.setCouleur((Integer) animation.getAnimatedValue()));
        pulsation.start();
        pulsations.add(pulsation);
    }

    /**
     * La pulsation du FOND du caret : la couleur du bloc glisse de
     * la couleur secondaire vers l'accent et retour.
     *
     * @param vue le caret vivant
     */
    private void demarrerPulsationFond(View vue) {
        android.animation.ValueAnimator pulsation =
                android.animation.ValueAnimator.ofObject(
                        new android.animation.ArgbEvaluator(),
                        palette.texteSecondaire, palette.accent);
        pulsation.setDuration(900);
        pulsation.setRepeatCount(
                android.animation.ValueAnimator.INFINITE);
        pulsation.setRepeatMode(
                android.animation.ValueAnimator.REVERSE);
        pulsation.addUpdateListener(animation ->
                vue.getBackground().mutate().setTint(
                        (Integer) animation.getAnimatedValue()));
        pulsation.start();
        pulsations.add(pulsation);
    }

    /** Arrête TOUTES les pulsations (un rafraîchissement arrive). */
    private void arreterPulsations() {
        for (android.animation.ValueAnimator pulsation : pulsations) {
            pulsation.cancel();
        }
        pulsations.clear();
    }

    /**
     * La bande d'avertissement d'usage au-dessus du composeur :
     * visible seulement quand la fenêtre qui porte le modèle courant
     * est chargée (≥ 80 %), la mini-bande suit la fraction, la teinte
     * dit le seuil (alerte au seuil, erreur au plafond) — le tap
     * ouvre l'écran Usages. Les données viennent du service (le
     * contrôleur pose) : la vue ne lit RIEN.
     *
     * @param fraction    l'occupation de la fenêtre qui porte (0..1)
     * @param requetes    vrai si la fenêtre porteuse compte des
     *                    requêtes (le libellé suit le genre)
     * @param restant     ce qui reste dans la fenêtre
     * @param relanceTexte le texte de relance (« 12 s »), null sans
     */
    public void poserAvertissementUsage(double fraction, boolean requetes,
                                         long restant, String relanceTexte) {
        if (fraction < SEUIL_AVERTISSEMENT) {
            bandeAvertissement.setVisibility(GONE);
            return;
        }
        boolean epuise = restant <= 0;
        Context contexte = getContext();
        libelleAvertissement.setText(epuise
                ? contexte.getString(R.string.agent_avertissement_epuise)
                : contexte.getString(requetes
                        ? R.string.agent_avertissement_requetes
                        : R.string.agent_avertissement_jetons,
                        (int) Math.max(0, restant)));
        relanceAvertissement.setText(relanceTexte == null ? ""
                : contexte.getString(
                        R.string.agent_avertissement_relance,
                        relanceTexte));
        // le bandeau : le conteneur tonal haut au seuil, le tonal
        // erreur au plafond ; l'icône suit
        boolean critique = epuise || fraction >= 1;
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setCornerRadius(GraphiteVue.dp(contexte, 14));
        fond.setColor(critique
                ? Graphite.melange(palette.erreur, 0.14f, palette.surface)
                : palette.surfaceHaute);
        bandeAvertissement.setBackground(fond);
        coque.iconeAvertissementAgent.setGlyphe(critique
                ? Icone.Glyphe.TRIANGLE : Icone.Glyphe.INFO);
        coque.iconeAvertissementAgent.setCouleur(critique
                ? palette.erreur : palette.texte);
        // la mini-bande suit la fraction (le poids du remplissage)
        float rempli = (float) Math.max(0.02, Math.min(1, fraction));
        android.widget.LinearLayout.LayoutParams paramsRemplissage =
                (android.widget.LinearLayout.LayoutParams)
                        remplissageAvertissement.getLayoutParams();
        paramsRemplissage.weight = rempli;
        remplissageAvertissement.setLayoutParams(paramsRemplissage);
        android.widget.LinearLayout.LayoutParams paramsReste =
                (android.widget.LinearLayout.LayoutParams)
                        resteAvertissement.getLayoutParams();
        paramsReste.weight = 1f - rempli;
        resteAvertissement.setLayoutParams(paramsReste);
        remplissageAvertissement.getBackground().mutate().setTint(
                critique ? palette.erreur : palette.alerte);
        bandeAvertissement.setVisibility(VISIBLE);
    }

    /** Le seuil d'avertissement (0,8 — la bande ne supplie JAMAIS en
     *  dessous). */
    private static final double SEUIL_AVERTISSEMENT = 0.8;

    /** Vrai si cette carte est le dernier raisonnement du transcript. */
    private boolean estDernierRaisonnement(Carte carte) {
        List<Carte> cartes = etat.cartes();
        for (int i = cartes.size() - 1; i >= 0; i--) {
            Carte c = cartes.get(i);
            if (c.genre == Genre.RAISONNEMENT) {
                return c == carte;
            }
        }
        return false;
    }

    /** Vrai si cette carte est la dernière réponse du transcript. */
    private boolean estDerniereReponse(Carte carte) {
        List<Carte> cartes = etat.cartes();
        for (int i = cartes.size() - 1; i >= 0; i--) {
            Carte c = cartes.get(i);
            if (c.genre == Genre.REPONSE) {
                return c == carte;
            }
        }
        return false;
    }

    /** Vrai si un appel d'outil tourne (le caret se tait : la ligne
     *  d'activité dit « Exécution… »). */
    private boolean unOutilTourne() {
        for (Carte c : etat.cartes()) {
            if (c.genre == Genre.OUTIL
                    && c.etatOutil == EtatOutil.EN_COURS) {
                return true;
            }
        }
        return false;
    }

    /**
     * La ligne d'activité : l'étincelle qui pulse, ce que le tour fait
     * (réflexion, exécution d'un outil, rédaction) et depuis combien
     * de temps — un pas long ne lit jamais un blocage.
     */
    private void majLigneActivite(boolean enCours) {
        if (!enCours) {
            horloge.removeCallbacks(tic);
            debutEnCours = null;
            ligneActivite.setVisibility(GONE);
            return;
        }
        if (debutEnCours == null) {
            debutEnCours = android.os.SystemClock
                    .elapsedRealtime();
        }
        etincelleActivite.setCouleur(palette.accent);
        demarrerPulsationTrait(etincelleActivite);
        texteActivite.setText(labelActivite());
        tempsActivite.setText(texteEcart());
        ligneActivite.setVisibility(VISIBLE);
        horloge.removeCallbacks(tic);
        horloge.postDelayed(tic, 1000);
    }

    /** Le pas d'une seconde : l'écart du tour se met à jour tout seul. */
    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (debutEnCours == null) {
                return;
            }
            tempsActivite.setText(texteEcart());
            horloge.postDelayed(tic, 1000);
        }
    };

    /** L'écart lisible : « 12 s », puis « 1 min 05 ». */
    private String texteEcart() {
        long secondes = (android.os.SystemClock.elapsedRealtime()
                - debutEnCours) / 1000;
        if (secondes < 60) {
            return getContext().getString(R.string.agent_ecart_secondes,
                    (int) secondes);
        }
        return getContext().getString(R.string.agent_ecart_minutes,
                (int) (secondes / 60), (int) (secondes % 60));
    }

    /**
     * Ce que le tour fait, lu du transcript (la vue ne déduit rien :
     * elle lit les cartes posées par le service) — le dernier pas
     * parle : outil en cours, rédaction, sinon réflexion.
     */
    private String labelActivite() {
        List<Carte> cartes = etat.cartes();
        for (int i = cartes.size() - 1; i >= 0; i--) {
            Carte c = cartes.get(i);
            if (c.genre == Genre.OUTIL
                    && c.etatOutil == EtatOutil.EN_COURS) {
                return getContext().getString(
                        R.string.agent_activite_outil, c.outil);
            }
            if (c.genre == Genre.ATTENTE) {
                return getContext().getString(
                        R.string.agent_activite_attente);
            }
            if (c.genre == Genre.REPONSE) {
                return getContext()
                        .getString(R.string.agent_activite_redaction);
            }
            if (c.genre == Genre.RAISONNEMENT) {
                return getContext()
                        .getString(R.string.agent_activite_reflexion);
            }
        }
        return getContext().getString(R.string.agent_activite_reflexion);
    }

    private static LinearLayout.LayoutParams margeParams(
            Context contexte) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = GraphiteVue.dp(contexte, 6);
        return params;
    }
}
