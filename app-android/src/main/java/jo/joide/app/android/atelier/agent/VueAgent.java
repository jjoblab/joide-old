package jo.joide.app.android.atelier.agent;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import jo.joide.agent.Permissions;
import jo.joide.agent.UsageLlm;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ComposeurAgentBinding;
import jo.joide.app.android.databinding.ItemBorneAgentBinding;
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
import jo.joide.app.android.atelier.agent.EtatAgentChat.ActionErreur;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Carte;
import jo.joide.app.android.atelier.agent.EtatAgentChat.EtatOutil;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Genre;
import jo.joide.app.android.atelier.agent.EtatAgentChat.TourEtat;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Tranche;

/**
 * Le tiroir de l'agent (lot P, ADR-É11) : en-tête à puces (modèle,
 * permission, fermer), transcript de cartes (messages, raisonnement
 * replié, outils pliables, questions, usage, erreurs actionnables) et
 * composeur (envoyer / arrêter). La vue ne décide RIEN : l'état pur
 * {@link EtatAgentChat} arrive posé, elle le traduit ; les actions
 * repartent par l'{@link Auditeur} — jamais de logique ici.
 *
 * <p>Lot C4 (2026-11-20, demande du terrain : « la section thinking
 * identique à CodeAssist de tyron12233, toutes fonctionnalités
 * incluses » — consultation clean-room n° 4, mandat du propriétaire) :
 * le travail d'un tour (outils + raisonnements intermédiaires)
 * se replie en UN BLOC à en-tête pliable (point d'état, « N outils ·
 * M fichiers », dernier appel, rail vertical déplié — le WorkBlock
 * de CodeAssist) ; la carte de réflexion vit à l'identique du
 * ThinkingStep (ouverte tant que le tour la remplit, repliée en une
 * ligne « Pensée » rouvrable au tap, point accent PULSANT) ; la bande
 * d'avertissement d'usage (≥ 80 % de la fenêtre qui porte le modèle)
 * s'affiche au-dessus du composeur et ouvre l'écran Usages.</p>
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
         * porte les pièces, la vue n’affiche que les noms posés.
         */
        void ouvrirAjouts();

        /**
         * La bande d'avertissement d'usage est TAPPÉE (lot C4, la
         * manière CodeAssist du bandeau de limite) : l'écran Usages
         * s'ouvre — la vue ne décide RIEN.
         */
        void ouvrirUsages();

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
    private final com.google.android.material.button.MaterialButton envoyer;
    private final com.google.android.material.button.MaterialButton arreter;
    private final Puce puceModele;
    private final Puce puceMode;
    private final Icone vueMenu;
    private final Icone vueAjouter;
    private final LinearLayout ligneActivite;
    private final TextView texteActivite;
    private final TextView tempsActivite;
    private final LinearLayout bandeAvertissement;
    private final TextView libelleAvertissement;
    private final TextView relanceAvertissement;
    private final View remplissageAvertissement;
    private final View resteAvertissement;
    private final android.os.Handler horloge = new android.os.Handler(
            android.os.Looper.getMainLooper());
    /** La pulsation du point vivant (lot C4 — l'étoile qui clignote du
     *  ThinkingStep de CodeAssist) : UN animateur, la vue du point
     *  change à chaque rafraîchissement, l'animateur se réinstalle. */
    private android.animation.ObjectAnimator pulsation;
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
        int fond = palette.fond;

        // ---- la coque du XML (lot L5) : en-tête, transcript,
        // ligne d'activité, composeur — les auditeurs et glyphes
        // restent posés ici (le contrat des vues du kit ne change pas)
        coque = TiroirAgentBinding.inflate(
                LayoutInflater.from(contexte), this, true);
        composeur = coque.composeur;
        colonneCartes = coque.colonneTranscript;
        defilement = coque.defilementAgent;
        saisie = composeur.saisieAgent;
        rangeePieces = composeur.rangeePiecesAgent;
        envoyer = composeur.btnEnvoyerAgent;
        arreter = composeur.btnArreterAgent;
        puceModele = coque.puceModeleAgent;
        puceMode = coque.puceModeAgent;
        vueMenu = coque.btnMenuAgent;
        vueAjouter = composeur.btnAjouterPiece;
        ligneActivite = coque.ligneActiviteAgent;
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

        // lot A9 : les couleurs de la saisie et du bouton vivent en XML
        // (?attr/jo_texte, ?attr/jo_texte3, Widget.Jo.Button.Tonal) ; la
        // police Graphite (400) reste posée ici (ADR-085 décision 7 :
        // graisse non exprimable en textAppearance).
        saisie.setTypeface(GraphiteVue.police(contexte, 400, false));
        envoyer.setOnClickListener(vue -> envoyer());
        arreter.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.arreter();
            }
        });
        puceModele.setOnClickListener(vue -> auditeurModele());
        puceMode.setOnClickListener(vue -> auditeurMode());
        vueMenu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirMenuAgent();
            }
        });
        // lot A9 : glyphe, couleur et description des icônes (menu,
        // fermer, ajouter) vivent en XML (app:jo_icone_* sur
        // tiroir_agent/composeur_agent) — une seule voie de thémage
        Icone fermer = coque.btnFermerAgent;
        fermer.setOnClickListener(vue -> demanderFermeture());
        vueAjouter.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirAjouts();
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

    /** L'ancre de la puce modèle (le sélecteur ancré, lot T). */
    public View ancreModele() {
        return puceModele;
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

    /** Redessine depuis l'état courant. Lot C4 : les TRANCHES DE
     *  TRAVAIL d'abord (tout ce qui précède la dernière carte d'outil
     *  d'un tour se replie en un bloc — la décision vit dans
     *  {@link EtatAgentChat#tranches()}, la vue applique), puis les
     *  cartes restantes au premier plan. */
    public void rafraichir() {
        Context contexte = getContext();
        arreterPulsation();
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
            // l'état vide du transcript (lot A7 : gonflé de
            // item_vide_agent par binding — plus aucune vue du cadre
            // instanciée ici)
            ItemVideAgentBinding vide = ItemVideAgentBinding.inflate(
                    LayoutInflater.from(contexte), colonneCartes, true);
            // lot A9 : les couleurs (glyphe et consigne) vivent déjà en
            // XML (?attr/jo_texte3, item_vide_agent) — injections
            // redondantes supprimées
        }
        if (etat.plafondAnnonce()) {
            ItemBorneAgentBinding borne = ItemBorneAgentBinding.inflate(
                    LayoutInflater.from(contexte), colonneCartes, false);
            borne.texteBorneAgent.setText(getContext().getString(
                    R.string.agent_plafond, etat.evictions()));
            colonneCartes.addView(borne.getRoot(), 0);
        }
        boolean enCours = etat.etat() == TourEtat.EN_COURS;
        envoyer.setEnabled(!enCours);
        envoyer.setVisibility(enCours ? GONE : VISIBLE);
        arreter.setVisibility(enCours ? VISIBLE : GONE);
        majLigneActivite(enCours);
        if (etat.preRemplissage() != null) {
            saisie.setText(etat.preRemplissage());
        }
        puceModele.setText(etat.modele().isEmpty()
                ? getContext().getString(R.string.agent_modele_vide)
                : etat.modele());
        puceMode.setText(texteMode(etat.mode()));
        defilement.post(() -> defilement.fullScroll(
                View.FOCUS_DOWN));
    }

    /** La vue d'une carte AU PREMIER PLAN (hors tranche de travail) :
     *  le même barème d'hier, extrait du rafraîchissement (lot C4). */
    private View vueCarte(Carte carte) {
        Context contexte = getContext();
        return switch (carte.genre) {
            case MESSAGE_UTILISATEUR -> carteMessage(carte);
            case REPONSE -> carteReponse(carte);
            case RAISONNEMENT -> carteRaisonnement(carte);
            case OUTIL -> carteOutil(carte);
            case FICHIERS -> ligneTonale(carte.detail);
            case ATTENTE -> ligneTonale(contexte.getString(
                    R.string.agent_attente, carte.secondes));
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

    private View carteMessage(Carte carte) {
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
        return item.getRoot();
    }

    private View carteReponse(Carte carte) {
        ItemMsgAgentBinding item = ItemMsgAgentBinding.inflate(
                LayoutInflater.from(getContext()),
                colonneCartes, false);
        TextView vue = item.texteReponse;
        GraphiteVue.typographie(vue, Graphite.CORPS);
        vue.setText(carte.reponse == null ? "" : carte.reponse);
        return item.getRoot();
    }

    /** La carte de réflexion (lot C4 — l'IDENTIQUE du ThinkingStep de
     *  CodeAssist) : l'en-tête [point accent PULSANT (tant que le tour
     *  la remplit) · « Réflexion… » | « Pensée » · chevron], OUVERTE
     *  tant que le tour vit (il se passe quelque chose à regarder),
     *  repliée à UNE LIGNE quand il est fini — le tap rouvre ; plus de
     *  compte de lignes dans l'en-tête (CodeAssist n'en montre pas).
     */
    private View carteRaisonnement(Carte carte) {
        Context contexte = getContext();
        ItemRaisonnementBinding item = ItemRaisonnementBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout bloc = item.getRoot();
        boolean vivant = etat.etat() == TourEtat.EN_COURS
                && estDernierRaisonnement(carte);
        boolean ouverte = carte.deplie || vivant;
        if (!ouverte) {
            bloc.setBackgroundResource(0);
            int fin = GraphiteVue.dp(contexte, 2);
            bloc.setPadding(fin, fin, fin, fin);
        }
        // le point vivant PULSE (la marque qui respire de CodeAssist)
        item.pointRaisonnement.setVisibility(
                vivant ? View.VISIBLE : View.GONE);
        if (vivant) {
            item.pointRaisonnement.setBackground(
                    cercle(contexte, palette.accent));
            demarrerPulsation(item.pointRaisonnement);
        }
        TextView plis = item.plisRaisonnement;
        GraphiteVue.typographie(plis, Graphite.LEGENDE);
        plis.setTextColor(palette.texteSecondaire);
        plis.setText(vivant
                ? R.string.agent_raisonnement_vivant
                : R.string.agent_raisonnement_plie);
        plis.setSingleLine(true);
        plis.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // le chevron suit l'état (la rotation du précédent CodeAssist)
        LinearLayout entete = item.enteteRaisonnement;
        Icone chevron = new Icone(contexte);
        chevron.setGlyphe(Icone.Glyphe.CHEVRON);
        chevron.setCouleur(palette.texteEstompe);
        chevron.setTraitDp(1.6f);
        chevron.setRotation(ouverte ? 180f : 0f);
        entete.setOnClickListener(vue -> {
            carte.deplie = !ouverte;
            rafraichir();
        });
        entete.addView(chevron, new LinearLayout.LayoutParams(
                GraphiteVue.dp(contexte, 16),
                GraphiteVue.dp(contexte, 16)));
        if (ouverte && carte.raisonnement.length() > 0) {
            TextView texte = item.contenuRaisonnement;
            GraphiteVue.typographie(texte, Graphite.LEGENDE);
            texte.setText(carte.raisonnement.toString());
            texte.setVisibility(View.VISIBLE);
        }
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.setMargins(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        bloc.setLayoutParams(params);
        return bloc;
    }

    /**
     * LE BLOC DE TRAVAIL d'un tour (lot C4 — l'IDENTIQUE du WorkBlock
     * de CodeAssist) : tout ce qui précède la dernière carte d'outil
     * se replie en UNE rangée [point d'état agrégé · « N outils · M
     * fichiers » · le dernier appel · chevron] ; déplié, le RAIL
     * VERTICAL porte les étapes dans l'ordre (la réflexion ouverte
     * tant qu'elle vit, chaque appel d'outil pliable, les lignes
     * tonales). Le pli vit dans l'état pur (clé = id d'appel du
     * dernier outil — survit aux évictions) ; l'état agrégé suit
     * en cours > refus > fini.
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
        int couleur = agregat == EtatOutil.EN_COURS ? palette.accent
                : agregat == EtatOutil.REFUSE ? palette.erreur
                : palette.reussite;
        item.pointTravail.setBackground(cercle(contexte, couleur));
        if (agregat == EtatOutil.EN_COURS) {
            // la marque respire tant qu'un appel tourne (CodeAssist)
            demarrerPulsation(item.pointTravail);
        }

        // le résumé « N outils · M fichiers » (M omis sans fichiers)
        int fichiers = EtatAgentChat.fichiersTouches(cartes, tranche);
        StringBuilder resume = new StringBuilder(
                contexte.getString(R.string.agent_travail_outils, outils));
        if (fichiers > 0) {
            resume.append(" · ").append(contexte.getString(
                    R.string.agent_travail_fichiers, fichiers));
        }
        item.resumeTravail.setText(resume);

        // replié, l'en-tête dit le DERNIER appel (CodeAssist : folded,
        // the header still says what the run did last)
        String cle = EtatAgentChat.cleDeTranche(cartes, tranche);
        boolean deplie = etat.travailDeplie(cle);
        String titreDernier = dernierOutil == null ? ""
                : dernierOutil.outil
                + (dernierOutil.cible == null
                        || dernierOutil.cible.isEmpty() ? ""
                        : " " + dernierOutil.cible);
        item.dernierTravail.setText(deplie ? "" : titreDernier);
        item.chevronTravail.setRotation(deplie ? 180f : 0f);
        item.enteteTravail.setOnClickListener(vue -> {
            etat.poserTravailDeplie(cle, !deplie);
            rafraichir();
        });

        if (deplie) {
            // le rail vertical porte les étapes DANS L'ORDRE (le
            // drawBehind du rail de CodeAssist, ici une vue 1 dp)
            item.etapesTravail.setVisibility(View.VISIBLE);
            LinearLayout colonne = item.colonneEtapesTravail;
            for (int i = tranche.debut(); i <= tranche.fin(); i++) {
                Carte carte = cartes.get(i);
                View vue = switch (carte.genre) {
                    case RAISONNEMENT -> carteRaisonnement(carte);
                    case OUTIL -> carteOutil(carte);
                    case FICHIERS -> ligneTonale(carte.detail);
                    case ATTENTE -> ligneTonale(contexte.getString(
                            R.string.agent_attente, carte.secondes));
                    case COMPACTION -> ligneTonale(contexte.getString(
                            R.string.agent_compaction, carte.avant,
                            carte.apres));
                    default -> null;
                };
                if (vue != null) {
                    colonne.addView(vue);
                }
            }
        }
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.setMargins(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        bloc.setLayoutParams(params);
        return bloc;
    }

    private View carteOutil(Carte carte) {
        Context contexte = getContext();
        ItemOutilBinding item = ItemOutilBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout bloc = item.getRoot();
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 12));
        int couleur = carte.etatOutil == EtatOutil.EN_COURS
                ? palette.accent
                : carte.etatOutil == EtatOutil.REFUSE
                        ? palette.erreur : palette.reussite;
        fond.setStroke(GraphiteVue.dp(contexte, 1),
                carte.etatOutil == EtatOutil.REFUSE
                        ? couleur : palette.bordFin);
        bloc.setBackground(fond);
        item.pointOutil.setBackground(cercle(contexte, couleur));
        TextView nom = item.nomOutil;
        nom.setTypeface(GraphiteVue.police(contexte, 500, true));
        nom.setTextColor(carte.etatOutil == EtatOutil.REFUSE
                ? palette.erreur : palette.texte);
        nom.setText(carte.outil);
        TextView cible = item.cibleOutil;
        GraphiteVue.typographie(cible, Graphite.LEGENDE);
        cible.setText(carte.cible);
        if (carte.etatOutil != EtatOutil.EN_COURS
                && carte.resultat != null) {
            TextView resume = item.libelleOutil;
            GraphiteVue.typographie(resume, Graphite.LEGENDE);
            resume.setText(carte.etatOutil == EtatOutil.REFUSE
                    ? contexte.getString(R.string.agent_refuse)
                    : contexte.getString(R.string.agent_lignes,
                            carte.lignesResultat));
            item.rangeeOutil.setOnClickListener(vue -> {
                carte.deplie = !carte.deplie;
                rafraichir();
            });
        }
        if (carte.deplie && carte.resultat != null) {
            TextView texte = item.resultatOutil;
            texte.setTypeface(GraphiteVue.police(contexte, 400, true));
            texte.setText(carte.resultat);
            texte.setVisibility(View.VISIBLE);
        }
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.setMargins(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        bloc.setLayoutParams(params);
        return bloc;
    }

    private View ligneTonale(String texte) {
        Context contexte = getContext();
        // lot A7 : gonflé d'item_ligne_tonale_agent par binding — la
        // mono compact estompée d'hier vit dans le layout
        ItemLigneTonaleAgentBinding ligne =
                ItemLigneTonaleAgentBinding.inflate(
                        LayoutInflater.from(contexte), colonneCartes,
                        false);
        ligne.texteLigneTonale.setText(texte);
        return ligne.getRoot();
    }

    private View carteUsage(Carte carte) {
        Context contexte = getContext();
        // la légende discrète de fin de tour (la manière CodeAssist) :
        // les jetons compacts (« 940 », « 8.4k », « 1.2M »), la ligne
        // qui monte tendance sans prendre la place — lot A7 : gonflée
        // d'item_usage_agent par binding
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
        // lot A7 : titre, détail et ACTION vivent dans le layout
        // d'item — plus aucune vue du cadre instanciée ici
        ItemErreurAgentBinding item = ItemErreurAgentBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes, false);
        LinearLayout bloc = item.getRoot();
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(0x12E06C75);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 12));
        fond.setStroke(GraphiteVue.dp(contexte, 1), 0x66E06C75);
        bloc.setBackground(fond);
        item.titreErreur.setText(texteErreur(carte));
        item.detailErreur.setText(carte.detail);
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
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.setMargins(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        bloc.setLayoutParams(params);
        return bloc;
    }

    private View carteQuestions(Carte carte) {
        Context contexte = getContext();
        LinearLayout bloc = ItemQuestionBinding.inflate(
                LayoutInflater.from(contexte), colonneCartes,
                false).getRoot();
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 12));
        fond.setStroke(GraphiteVue.dp(contexte, 1), 0x5982AAFF);
        bloc.setBackground(fond);
        for (int i = 0; i < carte.questions.size(); i++) {
            EtatAgentChat.Question question = carte.questions.get(i);
            // lot A7 : la demande et sa rangée d'options vivent dans
            // item_question_ligne gonflé par binding (les Puce du kit
            // restent posées par code : elles portent l'état pointé)
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
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.setMargins(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        bloc.setLayoutParams(params);
        return bloc;
    }

    private void envoyer() {
        String message = saisie.getText().toString().trim();
        if (message.isEmpty() || auditeur == null) {
            return;
        }
        saisie.setText("");
        auditeur.envoyer(message);
    }

    /**
     * Le sélecteur de fournisseur et de modèle (puce d'en-tête, lot
     * S) : l'atelier ouvre le dialogue in-situ — plus de détour par
     * les réglages.
     */
    private void auditeurModele() {
        if (auditeur != null) {
            auditeur.ouvrirModele();
        }
    }

    /** La puce de mode de permission (ADR-É11 point 3) : l'atelier
     *  compose le menu ANCRÉ des modes (lot A7 — la vue ne décide
     *  RIEN du contenu). */
    private void auditeurMode() {
        if (auditeur != null) {
            auditeur.ouvrirMode();
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

    private String texteErreur(Carte carte) {
        return getContext().getString(R.string.agent_erreur_generique);
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

    private android.graphics.drawable.GradientDrawable cercle(
            Context contexte, int couleur) {
        android.graphics.drawable.GradientDrawable rond =
                new android.graphics.drawable.GradientDrawable();
        rond.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        rond.setColor(couleur);
        return rond;
    }

    /**
     * La marque vivante RESPIRE (lot C4, la pulsation de la marque de
     * CodeAssist) : UN animateur pour UN point vivant à la fois (le
     * point de réflexion vivant OU le point du bloc dont un outil
     * tourne) — le rafraîchissement l'arrête avant d'en poser un
     * neuf, jamais d'accumulateur.
     *
     * @param point la vue du point vivant
     */
    private void demarrerPulsation(View point) {
        pulsation = android.animation.ObjectAnimator.ofFloat(point,
                "alpha", 1f, 0.25f);
        pulsation.setDuration(700);
        pulsation.setRepeatCount(
                android.animation.ValueAnimator.INFINITE);
        pulsation.setRepeatMode(
                android.animation.ValueAnimator.REVERSE);
        pulsation.start();
    }

    /** Arrête la pulsation courante (un rafraîchissement arrive). */
    private void arreterPulsation() {
        if (pulsation != null) {
            pulsation.cancel();
            pulsation = null;
        }
    }

    /**
     * La bande d'avertissement d'usage au-dessus du composeur (lot C4,
     * l'IDENTIQUE du bandeau de limite de CodeAssist) : visible
     * seulement quand la fenêtre qui porte le modèle courant est
     * chargée (≥ 80 %), la mini-bande suit la fraction, la teinte dit
     * la bande (alerte au seuil, erreur au plafond) — le tap ouvre
     * l'écran Usages. Les données viennent du service (le contrôleur
     * pose) : la vue ne lit RIEN.
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
        // la teinte du bandeau : alerte au seuil, erreur au plafond
        int teinte = epuise || fraction >= 1 ? palette.erreur
                : palette.alerte;
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setCornerRadius(GraphiteVue.dp(contexte, 10));
        fond.setColor(Graphite.melange(teinte, 0.12f, palette.fond));
        bandeAvertissement.setBackground(fond);
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
        remplissageAvertissement.getBackground().mutate().setTint(teinte);
        bandeAvertissement.setVisibility(VISIBLE);
    }

    /** Le seuil d'avertissement (CodeAssist : 0,8 — la bande ne
     *  supplie JAMAIS en dessous). */
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

    /**
     * La ligne d'activité (lot T, la manière CodeAssist) : le tour qui
     * tourne annonce ce qu'il fait — réflexion, outil, rédaction — et
     * depuis combien de temps, si un pas long ne lit jamais un blocage.
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
        texteActivite.setText(labelActivite());
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
                return getContext().getString(R.string.agent_attente,
                        c.secondes);
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
