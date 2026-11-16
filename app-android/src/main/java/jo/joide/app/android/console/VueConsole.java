package jo.joide.app.android.console;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.app.android.R;
import jo.joide.app.android.console.EtatConsole.Etat;
import jo.joide.app.android.console.EtatConsole.Filtre;
import jo.joide.app.android.console.EtatConsole.PhaseEtat;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.choix.ChoixBouton;

/**
 * Le panneau console de build (lot K, ADR-É6) : en-tête « Console de
 * build » + projet + pilule d'état + actions (arrêter, vider), bande
 * de phases cliquables (tap = filtre), filtres « Tout / Erreurs (n) /
 * Système », transcript en grille deux colonnes colorée par nature —
 * chaque ligne reste du texte sélectionnable/copiable —, lignes de
 * problème cliquables, rangée d'action « Ouvrir le fichier à la
 * ligne » et plafond annoncé. La vue ne décide rien : l'état pur
 * {@link EtatConsole} arrive posé, elle le traduit.
 */
public final class VueConsole extends LinearLayout {

    /** L'auditeur des actions de la console. */
    public interface AuditeurConsole {
        /** Arrêter le build en cours. */
        void surArreter();

        /** Vider le transcript (confirmation portée par l'écran). */
        void surVider();

        /** Le filtre change. */
        void surFiltre(Filtre filtre);

        /** Le filtre par phase change (null = toutes les phases). */
        void surPhase(ConsoleBuild.Phase phase);

        /** Ouvrir la cible d'un problème (« fichier:ligne »). */
        void surOuvrir(String cible);
    }

    private final Graphite.Palette palette;
    private final TextView titre;
    private final TextView projet;
    private final Pilule piluleEtat;
    private final Icone boutonArreter;
    private final Bouton boutonVider;
    private final LinearLayout bandePhases;
    private final Puce filtreTout;
    private final Puce filtreErreurs;
    private final Puce filtreSysteme;
    private final LinearLayout transcript;
    private final TextView legendPlafond;
    private final LinearLayout zoneProbleme;
    private AuditeurConsole auditeur;

    public VueConsole(Context contexte) {
        this(contexte, null);
    }

    public VueConsole(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(VERTICAL);
        setBackgroundColor(palette.puits);

        // --- en-tête ---
        LinearLayout entete = new LinearLayout(contexte);
        entete.setOrientation(HORIZONTAL);
        entete.setGravity(Gravity.CENTER_VERTICAL);
        int marge = GraphiteVue.dp(contexte, 14);
        entete.setPadding(marge, marge / 2, marge, marge / 2);

        LinearLayout titres = new LinearLayout(contexte);
        titres.setOrientation(HORIZONTAL);
        titres.setGravity(Gravity.CENTER_VERTICAL);
        titre = new TextView(contexte);
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.console_build);
        titres.addView(titre, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        projet = new TextView(contexte);
        GraphiteVue.typographie(projet, Graphite.NOTE);
        projet.setTextColor(palette.texteEstompe);
        titres.addView(projet, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        entete.addView(titres, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        piluleEtat = new Pilule(contexte);
        entete.addView(piluleEtat, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boutonArreter = icone(Icone.Glyphe.CROIX, palette.erreur);
        boutonArreter.setContentDescription(
                getContext().getString(R.string.atelier_desc_arreter_build));
        boutonArreter.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surArreter();
            }
        });
        entete.addView(boutonArreter);
        boutonVider = new Bouton(contexte);
        boutonVider.setVariante(ChoixBouton.Variante.TEXTE);
        boutonVider.setTexte(R.string.console_vider);
        boutonVider.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surVider();
            }
        });
        entete.addView(boutonVider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addView(entete, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // --- bande de phases ---
        HorizontalScrollView defilePhases =
                new HorizontalScrollView(contexte);
        defilePhases.setHorizontalScrollBarEnabled(false);
        bandePhases = new LinearLayout(contexte);
        bandePhases.setOrientation(HORIZONTAL);
        bandePhases.setPadding(marge, marge / 2, marge, 0);
        defilePhases.addView(bandePhases, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(defilePhases, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // --- filtres ---
        LinearLayout filtres = new LinearLayout(contexte);
        filtres.setOrientation(HORIZONTAL);
        filtres.setPadding(marge, marge / 2, marge, 0);
        filtreTout = puceFiltre(Filtre.TOUT);
        filtreErreurs = puceFiltre(Filtre.ERREURS);
        filtreSysteme = puceFiltre(Filtre.SYSTEME);
        filtres.addView(filtreTout);
        filtres.addView(filtreErreurs);
        filtres.addView(filtreSysteme);
        addView(filtres, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // --- transcript ---
        ScrollView defile = new ScrollView(contexte);
        transcript = new LinearLayout(contexte);
        transcript.setOrientation(VERTICAL);
        transcript.setPadding(marge, marge / 2, marge, marge / 2);
        defile.addView(transcript, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(defile, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // --- plafond annoncé ---
        legendPlafond = new TextView(contexte);
        GraphiteVue.typographie(legendPlafond, Graphite.LEGENDE);
        legendPlafond.setTextColor(palette.texteEstompe);
        legendPlafond.setPadding(marge, 0, marge, marge / 2);
        addView(legendPlafond, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // --- rangée du premier problème localisé ---
        zoneProbleme = new LinearLayout(contexte);
        zoneProbleme.setOrientation(VERTICAL);
        zoneProbleme.setVisibility(GONE);
        zoneProbleme.setPadding(marge, 0, marge, marge);
        addView(zoneProbleme, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    private Icone icone(Icone.Glyphe glyphe, int couleur) {
        Icone vue = new Icone(getContext());
        vue.setGlyphe(glyphe);
        vue.setCouleur(couleur);
        vue.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                GraphiteVue.dp(getContext(), 34),
                GraphiteVue.dp(getContext(), 34));
        params.leftMargin = GraphiteVue.dp(getContext(), 6);
        vue.setLayoutParams(params);
        return vue;
    }

    private Puce puceFiltre(Filtre nature) {
        Puce puce = new Puce(getContext());
        puce.setCategorie(true);
        puce.setText(libelleFiltre(nature, 0));
        puce.setAuditeur(new Puce.AuditeurPuce() {
            @Override
            public void surBascule(Puce basculee, boolean pointee) {
                if (pointee && auditeur != null) {
                    auditeur.surFiltre(nature);
                }
            }
        });
        return puce;
    }

    private String libelleFiltre(Filtre nature, int erreurs) {
        Context contexte = getContext();
        return switch (nature) {
            case TOUT -> contexte.getString(R.string.console_filtre_tout);
            case ERREURS -> contexte.getString(
                    R.string.console_filtre_erreurs, erreurs);
            case SYSTEME -> contexte.getString(
                    R.string.console_filtre_systeme);
        };
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurConsole ecouteur) {
        this.auditeur = ecouteur;
    }

    /**
     * Traduit l'état pur : pilule d'état, phases, filtres, transcript
     * filtré, plafond, rangée du problème localisé.
     */
    public void maj(EtatConsole etat, String nomProjet) {
        Context contexte = getContext();
        projet.setText(nomProjet == null ? "" : " · " + nomProjet);

        switch (etat.etat()) {
            case EN_COURS -> piluleEtat.setEtat(
                    contexte.getString(R.string.console_etat_encours,
                            libellePhase(etat.phaseCourante())),
                    Pilule.Role.ACCENT);
            case REUSSI -> piluleEtat.setEtat(
                    contexte.getString(R.string.console_etat_reussi,
                            EtatConsole.secondes(dureeDernierBuild(etat))),
                    Pilule.Role.REUSSITE);
            case ECHOUE -> piluleEtat.setEtat(
                    contexte.getString(R.string.console_etat_echoue),
                    Pilule.Role.ERREUR);
            case INTERROMPU -> piluleEtat.setEtat(
                    contexte.getString(R.string.console_etat_interrompu),
                    Pilule.Role.ALERTE);
            case VIDE -> piluleEtat.setEtat(
                    contexte.getString(R.string.console_etat_vide),
                    Pilule.Role.NEUTRE);
        }
        boutonArreter.setVisibility(
                etat.etat() == Etat.EN_COURS ? VISIBLE : GONE);

        majPhases(etat);
        filtreTout.setText(libelleFiltre(Filtre.TOUT, 0));
        filtreErreurs.setText(
                libelleFiltre(Filtre.ERREURS, etat.compteErreurs()));
        filtreTout.setPointee(etat.filtre() == Filtre.TOUT);
        filtreErreurs.setPointee(etat.filtre() == Filtre.ERREURS);
        filtreSysteme.setPointee(etat.filtre() == Filtre.SYSTEME);

        majTranscript(etat);

        legendPlafond.setText(etat.plafonne()
                ? contexte.getString(R.string.console_plafond,
                        ConsoleBuild.PLAFOND_LIGNES)
                : (etat.lignes().isEmpty()
                        ? contexte.getString(R.string.console_etat_vide) : ""));
        legendPlafond.setVisibility(
                etat.plafonne() || etat.lignes().isEmpty()
                        ? VISIBLE : GONE);

        ConsoleBuild.Ligne probleme = etat.premierProblemeLocalise();
        zoneProbleme.removeAllViews();
        zoneProbleme.setVisibility(
                probleme == null ? GONE : VISIBLE);
        if (probleme != null) {
            zoneProbleme.addView(carteProbleme(probleme));
        }
    }

    private long dureeDernierBuild(EtatConsole etat) {
        // la durée chiffrée du bilan, celle des phases sinon
        for (int i = etat.lignes().size() - 1; i >= 0; i--) {
            ConsoleBuild.Ligne ligne = etat.lignes().get(i);
            if (ligne.nature() == ConsoleBuild.Nature.SYSTEME
                    && ligne.texte().contains(" · ")
                    && ligne.texte().startsWith("build #")) {
                return dureeDuResume(ligne.texte());
            }
        }
        long somme = 0;
        for (PhaseEtat phase : etat.phases()) {
            somme += phase.dureeMs();
        }
        return somme;
    }

    private static long dureeDuResume(String resume) {
        // « build #4 réussi — … · 6,2 s » : la durée est en queue
        int position = resume.lastIndexOf(" · ");
        if (position < 0 || !resume.endsWith(" s")) {
            return 0;
        }
        try {
            return (long) (Double.parseDouble(resume
                    .substring(position + 3, resume.length() - 2)
                    .replace(',', '.')) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void majPhases(EtatConsole etat) {
        Context contexte = getContext();
        bandePhases.removeAllViews();
        if (etat.phases().isEmpty()) {
            return;
        }
        for (PhaseEtat phase : etat.phases()) {
            Puce pilule = new Puce(contexte);
            pilule.setCategorie(true);
            String duree = phase.terminee()
                    ? EtatConsole.secondes(phase.dureeMs())
                    : (phase.commencee()
                            ? contexte.getString(R.string.console_phase_encours)
                            : "—");
            pilule.setText(libellePhase(phase.phase()) + " · " + duree);
            pilule.setAuditeur(new Puce.AuditeurPuce() {
                @Override
                public void surBascule(Puce basculee, boolean pointee) {
                    if (pointee && auditeur != null) {
                        auditeur.surPhase(phase.phase());
                    }
                }
            });
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = GraphiteVue.dp(contexte, 8);
            bandePhases.addView(pilule, params);
        }
    }

    private String libellePhase(ConsoleBuild.Phase phase) {
        Context contexte = getContext();
        if (phase == null) {
            return contexte.getString(R.string.console_phase_aucune);
        }
        return switch (phase) {
            case PREPARATION -> contexte.getString(
                    R.string.console_phase_preparation);
            case COMPILATION -> contexte.getString(
                    R.string.console_phase_compilation);
            case ASSEMBLAGE -> contexte.getString(
                    R.string.console_phase_assemblage);
        };
    }

    private void majTranscript(EtatConsole etat) {
        Context contexte = getContext();
        transcript.removeAllViews();
        List<ConsoleBuild.Ligne> visibles = etat.lignesFiltrees();
        for (ConsoleBuild.Ligne ligne : visibles) {
            transcript.addView(ligneTranscript(ligne));
        }
        if (!visibles.isEmpty()) {
            // le transcript suit la fin (les événements vivants)
            transcript.post(() -> {
                View parent = (View) transcript.getParent();
                if (parent instanceof ScrollView defile) {
                    defile.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }
    }

    private View ligneTranscript(ConsoleBuild.Ligne ligne) {
        Context contexte = getContext();
        LinearLayout rangee = new LinearLayout(contexte);
        rangee.setOrientation(HORIZONTAL);
        rangee.setGravity(Gravity.TOP);

        TextView nature = new TextView(contexte);
        GraphiteVue.typographie(nature, Graphite.CODE_COMPACT);
        nature.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        nature.setTextColor(couleurNature(ligne.nature()));
        nature.setText(natureCourte(ligne.nature()));
        nature.setMinWidth(GraphiteVue.dp(contexte, 64));
        LinearLayout.LayoutParams natureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        natureParams.rightMargin = GraphiteVue.dp(contexte, 10);
        rangee.addView(nature, natureParams);

        TextView texte = new TextView(contexte);
        GraphiteVue.typographie(texte, Graphite.CODE_COMPACT);
        texte.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        texte.setTextColor(couleurTexte(ligne.nature()));
        texte.setTextIsSelectable(true);
        texte.setText(ligne.texte());
        rangee.addView(texte, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (ligne.cible() != null) {
            // problème ouvrable : la ligne entière l'ouvre
            rangee.setClickable(true);
            GradientDrawable fond = new GradientDrawable();
            fond.setColor(Graphite.melange(palette.erreur, 0.10f,
                    palette.puits));
            fond.setCornerRadius(GraphiteVue.dp(contexte, 8));
            rangee.setBackground(fond);
            String cible = ligne.cible();
            rangee.setOnClickListener(vue -> {
                if (auditeur != null) {
                    auditeur.surOuvrir(cible);
                }
            });
            rangee.setPadding(0, GraphiteVue.dp(contexte, 2), 0,
                    GraphiteVue.dp(contexte, 2));
        }
        return rangee;
    }

    private int couleurNature(ConsoleBuild.Nature nature) {
        return switch (nature) {
            case SYSTEME -> palette.accent;
            case PHASE -> palette.syntaxe.fonction;
            case TACHE -> palette.texteSecondaire;
            case ERREUR -> palette.erreur;
            case AVERTISSEMENT -> palette.alerte;
        };
    }

    private int couleurTexte(ConsoleBuild.Nature nature) {
        return switch (nature) {
            case SYSTEME -> palette.texte;
            case PHASE -> palette.syntaxe.fonction;
            case TACHE -> palette.texte;
            case ERREUR -> palette.erreur;
            case AVERTISSEMENT -> palette.alerte;
        };
    }

    private String natureCourte(ConsoleBuild.Nature nature) {
        Context contexte = getContext();
        return switch (nature) {
            case SYSTEME -> contexte.getString(R.string.console_nature_systeme);
            case PHASE -> contexte.getString(R.string.console_nature_phase);
            case TACHE -> contexte.getString(R.string.console_nature_tache);
            case ERREUR -> contexte.getString(R.string.console_nature_erreur);
            case AVERTISSEMENT -> contexte.getString(
                    R.string.console_nature_avertissement);
        };
    }

    private View carteProbleme(ConsoleBuild.Ligne probleme) {
        Context contexte = getContext();
        LinearLayout carte = new LinearLayout(contexte);
        carte.setOrientation(VERTICAL);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(Graphite.RAYON_COMPOSANT);
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.erreur);
        int pad = GraphiteVue.dp(contexte, 14);
        carte.setPadding(pad, pad, pad, pad);

        TextView titre = new TextView(contexte);
        GraphiteVue.typographie(titre, Graphite.NOTE);
        titre.setTextColor(palette.erreur);
        titre.setTypeface(GraphiteVue.police(contexte, 600, false));
        titre.setText(contexte.getString(R.string.console_probleme_titre,
                probleme.cible()));
        carte.addView(titre, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView detail = new TextView(contexte);
        GraphiteVue.typographie(detail, Graphite.LEGENDE);
        detail.setTextColor(palette.texteSecondaire);
        detail.setText(probleme.texte());
        carte.addView(detail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Bouton ouvrir = new Bouton(contexte);
        ouvrir.setVariante(ChoixBouton.Variante.TONAL);
        ouvrir.setTexte(R.string.console_probleme_ouvrir);
        LinearLayout.LayoutParams boutonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        boutonParams.topMargin = GraphiteVue.dp(contexte, 10);
        carte.addView(ouvrir, boutonParams);
        String cible = probleme.cible();
        ouvrir.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surOuvrir(cible);
            }
        });
        return carte;
    }
}
