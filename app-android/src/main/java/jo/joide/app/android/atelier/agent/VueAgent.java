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
    }

    private final Graphite.Palette palette;
    private final LinearLayout colonneCartes;
    private final ScrollView defilement;
    private final EditText saisie;
    private final Bouton envoyer;
    private final Bouton arreter;
    private final Puce puceModele;
    private final Puce puceMode;
    private Auditeur auditeur;
    private EtatAgentChat etat = new EtatAgentChat();

    public VueAgent(Context contexte) {
        this(contexte, null);
    }

    public VueAgent(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setOrientation(VERTICAL);
        palette = GraphiteVue.palette(contexte);
        int fond = palette.fond;

        // ---- en-tête : titre, puces, fermer ----
        LinearLayout tete = new LinearLayout(contexte);
        tete.setOrientation(LinearLayout.HORIZONTAL);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        tete.setBackgroundColor(palette.surface);
        int marge = GraphiteVue.dp(contexte, 10);
        tete.setPadding(marge, GraphiteVue.dp(contexte, 8), marge,
                GraphiteVue.dp(contexte, 8));
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

        // ---- transcript ----
        colonneCartes = new LinearLayout(contexte);
        colonneCartes.setOrientation(LinearLayout.VERTICAL);
        colonneCartes.setBackgroundColor(fond);
        int margeCarte = GraphiteVue.dp(contexte, 10);
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
        LinearLayout rangee = new LinearLayout(contexte);
        rangee.setOrientation(LinearLayout.HORIZONTAL);
        rangee.setGravity(Gravity.BOTTOM);
        saisie = new EditText(contexte);
        saisie.setHint(R.string.agent_ecrire_indice);
        saisie.setTextColor(palette.texte);
        saisie.setHintTextColor(palette.texteEstompe);
        saisie.setTypeface(GraphiteVue.police(contexte, 400, false));
        saisie.setTextSize(14f);
        saisie.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        saisie.setMaxLines(4);
        saisie.setBackground(null);
        saisie.setPadding(0, GraphiteVue.dp(contexte, 8), 0,
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
    }

    /**
     * Pose l'auditeur des actions.
     *
     * @param auditeur le récepteur (l'atelier)
     */
    public void poserAuditeur(Auditeur auditeur) {
        this.auditeur = auditeur;
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
            TextView vide = new TextView(contexte);
            GraphiteVue.typographie(vide, Graphite.CORPS);
            vide.setTextColor(palette.texteEstompe);
            vide.setText(R.string.agent_vide);
            vide.setPadding(0, GraphiteVue.dp(contexte, 24), 0, 0);
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
        TextView vue = new TextView(contexte);
        GraphiteVue.typographie(vue, Graphite.CORPS);
        vue.setTextColor(palette.texte);
        vue.setText(carte.texte);
        vue.setPadding(GraphiteVue.dp(contexte, 12),
                GraphiteVue.dp(contexte, 9),
                GraphiteVue.dp(contexte, 12),
                GraphiteVue.dp(contexte, 9));
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surfaceHaute);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 13));
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        vue.setBackground(fond);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        params.setMargins(0, GraphiteVue.dp(contexte, 3), 0,
                GraphiteVue.dp(contexte, 3));
        vue.setLayoutParams(params);
        vue.setMaxWidth(Integer.MAX_VALUE);
        return vue;
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
        LinearLayout bloc = new LinearLayout(contexte);
        bloc.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte, 12));
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        bloc.setBackground(fond);
        TextView plis = new TextView(contexte);
        GraphiteVue.typographie(plis, Graphite.LEGENDE);
        plis.setTextColor(palette.texteEstompe);
        plis.setText(getContext().getString(
                R.string.agent_raisonnement, carte.lignesRaisonnement));
        plis.setPadding(GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 8),
                GraphiteVue.dp(contexte, 11),
                GraphiteVue.dp(contexte, 8));
        plis.setOnClickListener(vue -> {
            carte.deplie = !carte.deplie;
            rafraichir();
        });
        bloc.addView(plis);
        if (carte.deplie) {
            TextView texte = new TextView(contexte);
            GraphiteVue.typographie(texte, Graphite.LEGENDE);
            texte.setTextColor(palette.texteSecondaire);
            texte.setText(carte.raisonnement.toString());
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
        TextView vue = new TextView(contexte);
        vue.setTypeface(GraphiteVue.police(contexte, 400, true));
        vue.setTextSize(11f);
        vue.setTextColor(palette.texteSecondaire);
        vue.setText(contexte.getString(R.string.agent_usage,
                carte.usage.entrees, carte.usage.sorties,
                carte.usage.lecturesCache, carte.requetes));
        return vue;
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

    /** Le sélecteur de modèle (puce d'en-tête). */
    private void auditeurModele() {
        // v1 : le modèle se change dans les réglages IA — la puce
        // AFFICHE, elle ne devine pas ; ouvrir les réglages
        ouvrirReglagesIa();
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

    private static LinearLayout.LayoutParams margeParams(
            Context contexte) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = GraphiteVue.dp(contexte, 6);
        return params;
    }
}
