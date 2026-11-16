package jo.joide.app.android.atelier.agent;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
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
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Champ;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.atelier.agent.EtatAgentChat.ActionErreur;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Carte;
import jo.joide.app.android.atelier.agent.EtatAgentChat.EtatOutil;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Genre;
import jo.joide.app.android.atelier.agent.EtatAgentChat.TourEtat;

/**
 * Le tiroir de l'agent (lot P, ADR-É11) : en-tête à puces (modèle,
 * permission, fermer), transcript de cartes (messages, raisonnement
 * replié, outils pliables, questions, usage, erreurs actionnables) et
 * composeur (envoyer / arrêter). La vue ne décide RIEN : l'état pur
 * {@link EtatAgentChat} arrive posé, elle le traduit ; les actions
 * repartent par l'{@link Auditeur} — jamais de logique ici.
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
         * @param index la pièce retirée du composeur
         */
        void retirerPiece(int index);
    }

    private final Graphite.Palette palette;
    private final LinearLayout colonneCartes;
    private final ScrollView defilement;
    private final EditText saisie;
    private final LinearLayout rangeePieces;
    private final Bouton envoyer;
    private final Bouton arreter;
    private final Puce puceModele;
    private final Puce puceMode;
    private final Icone vueMenu;
    private final Icone vueAjouter;
    private final LinearLayout ligneActivite;
    private final TextView texteActivite;
    private final TextView tempsActivite;
    private final android.os.Handler horloge = new android.os.Handler(
            android.os.Looper.getMainLooper());
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

        // ---- en-tête : titre, puces, fermer (compact, lot S) ----
        LinearLayout tete = new LinearLayout(contexte);
        tete.setOrientation(LinearLayout.HORIZONTAL);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        tete.setBackgroundColor(palette.surface);
        int marge = GraphiteVue.dp(contexte, 8);
        tete.setPadding(marge, GraphiteVue.dp(contexte, 5),
                GraphiteVue.dp(contexte, 2),
                GraphiteVue.dp(contexte, 5));
        TextView titre = new TextView(contexte);
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.agent_titre);
        tete.addView(titre, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        puceModele = new Puce(contexte);
        puceModele.setOnClickListener(
                vue -> auditeurModele());
        tete.addView(puceModele, margeParams(contexte));
        puceMode = new Puce(contexte);
        puceMode.setOnClickListener(vue -> auditeurMode());
        tete.addView(puceMode, margeParams(contexte));
        vueMenu = new Icone(contexte);
        Icone menu = vueMenu;
        menu.setGlyphe(Icone.Glyphe.POINTS);
        menu.setCouleur(palette.texte);
        menu.setClickable(true);
        menu.setContentDescription(
                getContext().getString(R.string.agent_menu));
        menu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirMenuAgent();
            }
        });
        tete.addView(menu, new LinearLayout.LayoutParams(
                GraphiteVue.dp(contexte, 38),
                GraphiteVue.dp(contexte, 38)));
        Icone fermer = new Icone(contexte);
        fermer.setGlyphe(Icone.Glyphe.CROIX);
        fermer.setCouleur(palette.texte);
        fermer.setClickable(true);
        fermer.setContentDescription(
                getContext().getString(R.string.agent_fermer));
        fermer.setOnClickListener(vue -> demanderFermeture());
        tete.addView(fermer, new LinearLayout.LayoutParams(
                GraphiteVue.dp(contexte, 38),
                GraphiteVue.dp(contexte, 38)));
        addView(tete, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- transcript (les cartes respirent de 8 dp, lot S) ----
        colonneCartes = new LinearLayout(contexte);
        colonneCartes.setOrientation(LinearLayout.VERTICAL);
        colonneCartes.setBackgroundColor(fond);
        int margeCarte = GraphiteVue.dp(contexte, 8);
        colonneCartes.setPadding(margeCarte, margeCarte, margeCarte,
                margeCarte);
        defilement = new ScrollView(contexte);
        defilement.setBackgroundColor(fond);
        defilement.addView(colonneCartes, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        addView(defilement, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- composeur ----
        LinearLayout composeur = new LinearLayout(contexte);
        composeur.setOrientation(LinearLayout.VERTICAL);
        composeur.setBackgroundColor(palette.surface);
        int margeBas = GraphiteVue.dp(contexte, 10);
        composeur.setPadding(marge, marge, marge, margeBas);
        rangeePieces = new LinearLayout(contexte);
        rangeePieces.setOrientation(LinearLayout.HORIZONTAL);
        rangeePieces.setVisibility(GONE);
        composeur.addView(rangeePieces, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout rangee = new LinearLayout(contexte);
        rangee.setOrientation(LinearLayout.HORIZONTAL);
        rangee.setGravity(Gravity.BOTTOM);
        vueAjouter = new Icone(contexte);
        Icone ajouter = vueAjouter;
        ajouter.setGlyphe(Icone.Glyphe.PLUS);
        ajouter.setCouleur(palette.texteSecondaire);
        ajouter.setClickable(true);
        ajouter.setContentDescription(
                getContext().getString(R.string.agent_ajouter));
        ajouter.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.ouvrirAjouts();
            }
        });
        LinearLayout.LayoutParams paramsAjouter = new LinearLayout.LayoutParams(
                GraphiteVue.dp(contexte, 36),
                GraphiteVue.dp(contexte, 36));
        paramsAjouter.gravity = Gravity.CENTER_VERTICAL;
        rangee.addView(ajouter, paramsAjouter);
        saisie = new EditText(contexte);
        saisie.setHint(R.string.agent_ecrire_indice);
        saisie.setTextColor(palette.texte);
        saisie.setHintTextColor(palette.texteEstompe);
        saisie.setTypeface(GraphiteVue.police(contexte, 400, false));
        saisie.setTextSize(14f);
        saisie.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        saisie.setMaxLines(4);
        // la saisie vit dans une pilule discrète (finesse du lot S)
        android.graphics.drawable.GradientDrawable pilule =
                new android.graphics.drawable.GradientDrawable();
        pilule.setColor(palette.fond);
        pilule.setCornerRadius(GraphiteVue.dp(contexte, 18));
        pilule.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        saisie.setBackground(pilule);
        saisie.setPadding(GraphiteVue.dp(contexte, 12),
                GraphiteVue.dp(contexte, 8),
                GraphiteVue.dp(contexte, 12),
                GraphiteVue.dp(contexte, 8));
        rangee.addView(saisie, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        envoyer = new Bouton(contexte);
        envoyer.setTexte(getContext()
                .getString(R.string.agent_envoyer));
        envoyer.setOnClickListener(vue -> envoyer());
        rangee.addView(envoyer, margeParams(contexte));
        arreter = new Bouton(contexte);
        arreter.setTexte(getContext()
                .getString(R.string.agent_arreter_tour));
        arreter.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.arreter();
            }
        });
        arreter.setVisibility(GONE);
        composeur.addView(rangee, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout rangeeArret = new LinearLayout(contexte);
        rangeeArret.setOrientation(LinearLayout.HORIZONTAL);
        rangeeArret.addView(arreter, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        composeur.addView(rangeeArret, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addView(composeur, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- ligne d'activité (lot T, la manière CodeAssist) : le
        // tour qui tourne dit ce qu'il fait et depuis combien de temps
        ligneActivite = new LinearLayout(contexte);
        ligneActivite.setOrientation(LinearLayout.HORIZONTAL);
        ligneActivite.setGravity(Gravity.CENTER_VERTICAL);
        ligneActivite.setPadding(marge, GraphiteVue.dp(contexte, 6),
                marge, 0);
        ligneActivite.setVisibility(GONE);
        View pointVivant = new View(contexte);
        pointVivant.setBackground(cercle(contexte, palette.accent));
        LinearLayout.LayoutParams paramsPoint = new LinearLayout
                .LayoutParams(GraphiteVue.dp(contexte, 7),
                GraphiteVue.dp(contexte, 7));
        paramsPoint.rightMargin = GraphiteVue.dp(contexte, 8);
        ligneActivite.addView(pointVivant, paramsPoint);
        texteActivite = new TextView(contexte);
        GraphiteVue.typographie(texteActivite, Graphite.LEGENDE);
        texteActivite.setTextColor(palette.texte);
        texteActivite.setSingleLine(true);
        texteActivite.setEllipsize(android.text.TextUtils
                .TruncateAt.END);
        ligneActivite.addView(texteActivite, new LinearLayout
                .LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        tempsActivite = new TextView(contexte);
        GraphiteVue.typographie(tempsActivite, Graphite.LEGENDE);
        tempsActivite.setTextColor(palette.texteEstompe);
        ligneActivite.addView(tempsActivite, new LinearLayout
                .LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        addView(ligneActivite, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
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

    /** Redessine depuis l'état courant. */
    public void rafraichir() {
        Context contexte = getContext();
        colonneCartes.removeAllViews();
        for (Carte carte : etat.cartes()) {
            View vue = switch (carte.genre) {
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
            colonneCartes.addView(vue);
        }
        if (etat.cartes().isEmpty()) {
            LinearLayout vide = new LinearLayout(contexte);
            vide.setOrientation(LinearLayout.VERTICAL);
            vide.setGravity(Gravity.CENTER_HORIZONTAL);
            vide.setPadding(0, GraphiteVue.dp(contexte, 28), 0, 0);
            Icone bulle = new Icone(contexte);
            bulle.setGlyphe(Icone.Glyphe.BULLE);
            bulle.setCouleur(palette.texteEstompe);
            vide.addView(bulle, new LinearLayout.LayoutParams(
                    GraphiteVue.dp(contexte, 34),
                    GraphiteVue.dp(contexte, 34)));
            TextView consigne = new TextView(contexte);
            GraphiteVue.typographie(consigne, Graphite.CORPS);
            consigne.setTextColor(palette.texteEstompe);
            consigne.setGravity(Gravity.CENTER);
            consigne.setText(R.string.agent_vide);
            consigne.setPadding(0, GraphiteVue.dp(contexte, 8), 0, 0);
            vide.addView(consigne);
            colonneCartes.addView(vide);
        }
        if (etat.plafondAnnonce()) {
            TextView borne = new TextView(contexte);
            GraphiteVue.typographie(borne, Graphite.LEGENDE);
            borne.setTextColor(palette.texteEstompe);
            borne.setText(getContext().getString(
                    R.string.agent_plafond, etat.evictions()));
            colonneCartes.addView(borne, 0);
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

    // ------------------------------------------------------------------
    // Les cartes
    // ------------------------------------------------------------------

    private View carteMessage(Carte carte) {
        Context contexte = getContext();
        LinearLayout bloc = new LinearLayout(contexte);
        bloc.setOrientation(LinearLayout.VERTICAL);
        bloc.setGravity(Gravity.END);
        TextView vue = new TextView(contexte);
        GraphiteVue.typographie(vue, Graphite.CORPS);
        vue.setTextColor(palette.texte);
        vue.setText(carte.texte);
        vue.setPadding(GraphiteVue.dp(contexte, 12),
                GraphiteVue.dp(contexte, 8),
                GraphiteVue.dp(contexte, 12),
                GraphiteVue.dp(contexte, 8));
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surfaceHaute);
        // le coin bas-droit se resserre : la bulle parle DU côté de
        // son auteur (finesse du lot S)
        int grand = GraphiteVue.dp(contexte, 16);
        int petit = GraphiteVue.dp(contexte, 6);
        fond.setCornerRadii(new float[]{grand, grand, grand, grand,
                petit, petit, grand, grand});
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        vue.setBackground(fond);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        params.setMargins(0, GraphiteVue.dp(contexte, 2), 0,
                GraphiteVue.dp(contexte, 2));
        vue.setLayoutParams(params);
        vue.setMaxWidth(GraphiteVue.dp(contexte, 300));
        bloc.addView(vue);
        if (carte.pieces != null && !carte.pieces.isEmpty()) {
            LinearLayout puces = new LinearLayout(contexte);
            puces.setOrientation(LinearLayout.HORIZONTAL);
            puces.setGravity(Gravity.END);
            for (String nom : carte.pieces) {
                Puce puce = new Puce(contexte);
                puce.setText(nom);
                puces.addView(puce, margeParams(contexte));
            }
            bloc.addView(puces, new LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
        }
        return bloc;
    }

    private View carteReponse(Carte carte) {
        Context contexte = getContext();
        TextView vue = new TextView(contexte);
        GraphiteVue.typographie(vue, Graphite.CORPS);
        vue.setTextColor(palette.texte);
        vue.setText(carte.reponse == null ? "" : carte.reponse);
        vue.setPadding(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        return vue;
    }

    private View carteRaisonnement(Carte carte) {
        Context contexte = getContext();
        // la manière CodeAssist (lot T) : la réflexion OUVERTE tant
        // que le tour vit (il se passe quelque chose à regarder),
        // repliée à une ligne quand il est fini — le tap rouvre.
        boolean vivant = etat.etat() == TourEtat.EN_COURS
                && estDernierRaisonnement(carte);
        boolean ouverte = carte.deplie || vivant;
        LinearLayout bloc = new LinearLayout(contexte);
        bloc.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        if (ouverte) {
            fond.setColor(palette.surface);
            fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        } else {
            fond.setColor(android.graphics.Color.TRANSPARENT);
        }
        fond.setCornerRadius(GraphiteVue.dp(contexte, 12));
        bloc.setBackground(fond);
        int horizontal = GraphiteVue.dp(contexte, ouverte ? 11 : 2);
        bloc.setPadding(horizontal, GraphiteVue.dp(contexte, ouverte ? 7 : 2),
                horizontal, GraphiteVue.dp(contexte, ouverte ? 8 : 2));
        LinearLayout entete = new LinearLayout(contexte);
        entete.setOrientation(LinearLayout.HORIZONTAL);
        entete.setGravity(Gravity.CENTER_VERTICAL);
        if (vivant) {
            // le point qui dit « ça réfléchit » (l'accent du tiroir)
            View point = new View(contexte);
            point.setBackground(cercle(contexte, palette.accent));
            LinearLayout.LayoutParams paramsPoint = new LinearLayout
                    .LayoutParams(GraphiteVue.dp(contexte, 6),
                    GraphiteVue.dp(contexte, 6));
            paramsPoint.rightMargin = GraphiteVue.dp(contexte, 7);
            entete.addView(point, paramsPoint);
        }
        TextView plis = new TextView(contexte);
        GraphiteVue.typographie(plis, Graphite.LEGENDE);
        plis.setTextColor(vivant ? palette.accent
                : palette.texteEstompe);
        plis.setText(getContext().getString(vivant
                ? R.string.agent_raisonnement_vivant
                : R.string.agent_raisonnement,
                carte.lignesRaisonnement));
        plis.setSingleLine(true);
        plis.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // le chevron suit l'état (la rotation du précédent CodeAssist)
        Icone chevron = new Icone(contexte);
        chevron.setGlyphe(Icone.Glyphe.CHEVRON);
        chevron.setCouleur(palette.texteEstompe);
        chevron.setTraitDp(1.6f);
        chevron.setRotation(ouverte ? 180f : 0f);
        entete.setOnClickListener(vue -> {
            carte.deplie = !ouverte;
            rafraichir();
        });
        entete.addView(plis, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        entete.addView(chevron, new LinearLayout.LayoutParams(
                GraphiteVue.dp(contexte, 16),
                GraphiteVue.dp(contexte, 16)));
        bloc.addView(entete);
        if (ouverte && carte.raisonnement.length() > 0) {
            TextView texte = new TextView(contexte);
            GraphiteVue.typographie(texte, Graphite.LEGENDE);
            texte.setTextColor(palette.texteSecondaire);
            texte.setText(carte.raisonnement.toString());
            texte.setPadding(0, GraphiteVue.dp(contexte, 6), 0, 0);
            bloc.addView(texte);
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
        LinearLayout bloc = new LinearLayout(contexte);
        bloc.setOrientation(LinearLayout.VERTICAL);
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
        LinearLayout plis = new LinearLayout(contexte);
        plis.setOrientation(LinearLayout.HORIZONTAL);
        plis.setGravity(Gravity.CENTER_VERTICAL);
        plis.setPadding(GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 8),
                GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 8));
        View point = new View(contexte);
        point.setBackground(
                cercle(contexte, couleur));
        plis.addView(point, new LinearLayout.LayoutParams(
                GraphiteVue.dp(contexte, 8),
                GraphiteVue.dp(contexte, 8)));
        TextView nom = new TextView(contexte);
        nom.setTypeface(GraphiteVue.police(contexte, 500, true));
        nom.setTextSize(12f);
        nom.setTextColor(carte.etatOutil == EtatOutil.REFUSE
                ? palette.erreur : palette.texte);
        nom.setText(carte.outil);
        LinearLayout.LayoutParams paramsNom =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsNom.leftMargin = GraphiteVue.dp(contexte, 9);
        plis.addView(nom, paramsNom);
        TextView cible = new TextView(contexte);
        GraphiteVue.typographie(cible, Graphite.LEGENDE);
        cible.setTextColor(palette.texteEstompe);
        cible.setText(carte.cible);
        cible.setSingleLine(true);
        LinearLayout.LayoutParams paramsCible =
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        paramsCible.leftMargin = GraphiteVue.dp(contexte, 8);
        plis.addView(cible, paramsCible);
        if (carte.etatOutil != EtatOutil.EN_COURS
                && carte.resultat != null) {
            TextView resume = new TextView(contexte);
            GraphiteVue.typographie(resume, Graphite.LEGENDE);
            resume.setTextColor(palette.texteEstompe);
            resume.setText(carte.etatOutil == EtatOutil.REFUSE
                    ? contexte.getString(R.string.agent_refuse)
                    : contexte.getString(R.string.agent_lignes,
                            carte.lignesResultat));
            plis.addView(resume);
            plis.setOnClickListener(vue -> {
                carte.deplie = !carte.deplie;
                rafraichir();
            });
        }
        bloc.addView(plis);
        if (carte.deplie && carte.resultat != null) {
            TextView texte = new TextView(contexte);
            texte.setTypeface(GraphiteVue.police(contexte, 400, true));
            texte.setTextSize(11f);
            texte.setTextColor(palette.texteSecondaire);
            texte.setText(carte.resultat);
            texte.setPadding(GraphiteVue.dp(contexte, 11), 0,
                    GraphiteVue.dp(contexte, 11),
                    GraphiteVue.dp(contexte, 9));
            bloc.addView(texte);
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
        TextView vue = new TextView(contexte);
        vue.setTypeface(GraphiteVue.police(contexte, 400, true));
        vue.setTextSize(11f);
        vue.setTextColor(palette.texteEstompe);
        vue.setText(texte);
        vue.setPadding(GraphiteVue.dp(contexte, 2),
                GraphiteVue.dp(contexte, 2),
                GraphiteVue.dp(contexte, 2),
                GraphiteVue.dp(contexte, 2));
        return vue;
    }

    private View carteUsage(Carte carte) {
        Context contexte = getContext();
        // la légende discrète de fin de tour (la manière CodeAssist) :
        // les jetons compacts (« 940 », « 8.4k », « 1.2M »), la ligne
        // qui monte tendance sans prendre la place
        TextView vue = new TextView(contexte);
        GraphiteVue.typographie(vue, Graphite.LEGENDE);
        vue.setTextColor(palette.texteEstompe);
        vue.setMaxLines(1);
        vue.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        vue.setText(ligne);
        vue.setPadding(0, GraphiteVue.dp(contexte, 2), 0,
                GraphiteVue.dp(contexte, 2));
        return vue;
    }

    /** Les jetons compacts (« 940 », « 8.4k », « 1.2M ») du pied. */
    private static String jetonsCompacts(long n) {
        return EtatAgentChat.jetonsCompacts(n);
    }

    private View carteErreur(Carte carte) {
        Context contexte = getContext();
        LinearLayout bloc = new LinearLayout(contexte);
        bloc.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(0x12E06C75);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 12));
        fond.setStroke(GraphiteVue.dp(contexte, 1), 0x66E06C75);
        bloc.setBackground(fond);
        bloc.setPadding(GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 10),
                GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 10));
        TextView titre = new TextView(contexte);
        GraphiteVue.typographie(titre, Graphite.CORPS);
        titre.setTextColor(palette.erreur);
        titre.setText(texteErreur(carte));
        bloc.addView(titre);
        TextView detail = new TextView(contexte);
        GraphiteVue.typographie(detail, Graphite.LEGENDE);
        detail.setTextColor(palette.texteSecondaire);
        detail.setText(carte.detail);
        detail.setPadding(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        bloc.addView(detail);
        if (carte.action == ActionErreur.OUVRIR_REGLAGES
                || carte.action == ActionErreur.CHANGER_MODELE) {
            TextView action = new TextView(contexte);
            GraphiteVue.typographie(action, Graphite.CORPS);
            action.setTextColor(palette.accent);
            action.setText(carte.action == ActionErreur
                    .OUVRIR_REGLAGES
                    ? R.string.agent_ouvrir_reglages
                    : R.string.agent_changer_modele);
            action.setPadding(0, GraphiteVue.dp(contexte, 6), 0, 0);
            action.setOnClickListener(vue ->
                    ouvrirReglagesIa());
            bloc.addView(action);
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
        LinearLayout bloc = new LinearLayout(contexte);
        bloc.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 12));
        fond.setStroke(GraphiteVue.dp(contexte, 1), 0x5982AAFF);
        bloc.setBackground(fond);
        bloc.setPadding(GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 10),
                GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 10));
        for (int i = 0; i < carte.questions.size(); i++) {
            EtatAgentChat.Question question = carte.questions.get(i);
            TextView demande = new TextView(contexte);
            GraphiteVue.typographie(demande, Graphite.CORPS);
            demande.setTextColor(palette.texte);
            demande.setText(contexte.getString(
                    R.string.agent_question_numero, i + 1,
                            carte.questions.size())
                    + " " + question.question);
            if (i > 0) {
                demande.setPadding(0, GraphiteVue.dp(contexte, 10), 0,
                        0);
            }
            bloc.addView(demande);
            LinearLayout options = new LinearLayout(contexte);
            options.setOrientation(LinearLayout.HORIZONTAL);
            options.setPadding(0, GraphiteVue.dp(contexte, 7), 0,
                    GraphiteVue.dp(contexte, 2));
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
            bloc.addView(options);
        }
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.setMargins(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        bloc.setLayoutParams(params);
        return bloc;
    }

    // ------------------------------------------------------------------
    // Actions locales
    // ------------------------------------------------------------------

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

    /** La puce de mode de permission (ADR-É11 point 3). */
    private void auditeurMode() {
        jo.joide.app.android.ui.Dialogue.menu(getContext(), palette,
                getContext().getString(R.string.agent_permission_titre),
                new CharSequence[]{
                        getContext().getString(
                                R.string.agent_mode_demande),
                        getContext().getString(
                                R.string.agent_mode_auto),
                        getContext().getString(
                                R.string.agent_mode_plan)},
                (dialog, lequel) -> {
                    if (auditeur != null) {
                        auditeur.poserMode(switch (lequel) {
                            case 0 -> Permissions.Mode.DEMANDE_CHAQUE;
                            case 1 -> Permissions.Mode.ACCEPTATION_AUTO;
                            default -> Permissions.Mode.PLAN_SEULEMENT;
                        });
                    }
                });
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
