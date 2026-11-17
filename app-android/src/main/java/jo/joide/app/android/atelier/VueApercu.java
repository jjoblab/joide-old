package jo.joide.app.android.atelier;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.EtatApercu.Banniere;
import jo.joide.app.android.atelier.EtatApercu.Genre;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.Segment;

/**
 * Le volet d'aperçu de mise en page (lot L, ADR-É7) : barre de volet
 * (« Aperçu — nom du fichier », rafraîchir sans délai, plein
 * écran/côte à côte, fermer), contrôles RÉELS du
 * {@code ConfigurationApercu} (densité segmentée, mode nuit,
 * dimensions cibles), surface de rendu sur damier — le résultat
 * LayoutLib tel quel, jamais retouché —, stats MESURÉES de
 * l'écouteur et bannières de franchise. La vue ne décide rien :
 * {@link EtatApercu} arrive posé.
 */
public final class VueApercu extends LinearLayout {

    /** L'auditeur des actions du volet. */
    public interface AuditeurApercu {
        /** Rafraîchir sans délai (l'action manuelle explicite). */
        void surRafraichir();

        /** Basculer plein écran / côte à côte. */
        void surPleinEcran();

        /** Fermer le volet. */
        void surFermer();

        /** La densité change (1, 2 ou 3). */
        void surDensite(int densite);

        /** Le mode nuit change. */
        void surNuit(boolean nuit);

        /** Les dimensions cibles changent (dp). */
        void surDimensions(int largeurDp, int hauteurDp);
    }

    private final Graphite.Palette palette;
    private final TextView fichierCourant;
    private final Segment segmentDensite;
    private final Puce puceNuit;
    private final Puce puceDimensions;
    private final TextView stats;
    private final LinearLayout bannieres;
    private final FrameLayout surface;
    private final LinearLayout colonneRendu;
    private AuditeurApercu auditeur;

    public VueApercu(Context contexte) {
        this(contexte, null);
    }

    public VueApercu(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(VERTICAL);
        setBackgroundColor(palette.surface);

        // --- barre de volet ---
        LinearLayout barre = new LinearLayout(contexte);
        barre.setOrientation(HORIZONTAL);
        barre.setGravity(Gravity.CENTER_VERTICAL);
        int marge = GraphiteVue.dp(contexte, 12);
        barre.setPadding(marge, marge / 2, marge, marge / 2);

        TextView titre = new TextView(contexte);
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.apercu_titre);
        barre.addView(titre, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        fichierCourant = new TextView(contexte);
        GraphiteVue.typographie(fichierCourant, Graphite.NOTE);
        fichierCourant.setTextColor(palette.texteEstompe);
        fichierCourant.setSingleLine(true);
        barre.addView(fichierCourant, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Icone rafraichir = icone(Icone.Glyphe.RAFRAICHIR,
                palette.texteSecondaire);
        rafraichir.setContentDescription(
                getContext().getString(R.string.apercu_desc_rafraichir));
        rafraichir.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surRafraichir();
            }
        });
        barre.addView(rafraichir);
        Icone pleinEcran = icone(Icone.Glyphe.AGRANDIR,
                palette.texteSecondaire);
        pleinEcran.setContentDescription(
                getContext().getString(R.string.apercu_desc_plein_ecran));
        pleinEcran.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surPleinEcran();
            }
        });
        barre.addView(pleinEcran);
        Icone fermer = icone(Icone.Glyphe.CROIX,
                palette.texteSecondaire);
        fermer.setContentDescription(
                getContext().getString(R.string.apercu_desc_fermer));
        fermer.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surFermer();
            }
        });
        barre.addView(fermer);
        addView(barre, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // --- contrôles ---
        LinearLayout controles = new LinearLayout(contexte);
        controles.setOrientation(HORIZONTAL);
        controles.setGravity(Gravity.CENTER_VERTICAL);
        controles.setPadding(marge, 0, marge, marge / 2);

        segmentDensite = new Segment(contexte);
        segmentDensite.setOptions(java.util.List.of("1×", "2×", "3×"), 1);
        segmentDensite.setAuditeur(new Segment.AuditeurSegment() {
            @Override
            public void surChoix(Segment segment, int index) {
                if (auditeur != null) {
                    auditeur.surDensite(index + 1);
                }
            }
        });
        controles.addView(segmentDensite, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        puceNuit = new Puce(contexte);
        puceNuit.setCategorie(true);
        puceNuit.setText(R.string.apercu_nuit);
        puceNuit.setAuditeur(new Puce.AuditeurPuce() {
            @Override
            public void surBascule(Puce basculee, boolean pointee) {
                if (auditeur != null) {
                    auditeur.surNuit(pointee);
                }
            }
        });
        LinearLayout.LayoutParams nuitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nuitParams.leftMargin = GraphiteVue.dp(contexte, 8);
        controles.addView(puceNuit, nuitParams);

        puceDimensions = new Puce(contexte);
        puceDimensions.setCategorie(true);
        puceDimensions.setOnClickListener(vue -> choisirDimensions());
        LinearLayout.LayoutParams dimsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dimsParams.leftMargin = GraphiteVue.dp(contexte, 8);
        controles.addView(puceDimensions, dimsParams);
        addView(controles, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // --- surface sur damier ---
        surface = new Damier(contexte, palette);
        colonneRendu = new LinearLayout(contexte);
        colonneRendu.setOrientation(VERTICAL);
        colonneRendu.setGravity(Gravity.CENTER);
        surface.addView(colonneRendu, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, 0,
                1f));

        // --- stats + bannières ---
        stats = new TextView(contexte);
        GraphiteVue.typographie(stats, Graphite.LEGENDE);
        stats.setTextColor(palette.texteEstompe);
        stats.setGravity(Gravity.CENTER_HORIZONTAL);
        stats.setPadding(marge, marge / 2, marge, 0);
        addView(stats, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        bannieres = new LinearLayout(contexte);
        bannieres.setOrientation(VERTICAL);
        bannieres.setPadding(marge, marge / 2, marge, marge / 2);
        addView(bannieres, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    private Icone icone(Icone.Glyphe glyphe, int couleur) {
        Icone vue = new Icone(getContext());
        vue.setGlyphe(glyphe);
        vue.setCouleur(couleur);
        vue.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                GraphiteVue.dp(getContext(), 32),
                GraphiteVue.dp(getContext(), 32));
        params.leftMargin = GraphiteVue.dp(getContext(), 4);
        vue.setLayoutParams(params);
        return vue;
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurApercu ecouteur) {
        this.auditeur = ecouteur;
    }

    /**
     * Traduit l'état : fichier lié, contrôles, stats mesurées,
     * bannières de franchise. Le rendu lui-même est posé par l'écran
     * ({@link #conteneurRendu()}) — jamais retouché ici.
     */
    public void maj(EtatApercu etat, String nomFichier) {
        Context contexte = getContext();
        fichierCourant.setText(nomFichier == null ? "" : " · " + nomFichier);
        segmentDensite.setSelection(etat.densite() - 1);
        puceNuit.setPointee(etat.nuit());
        puceDimensions.setText(etat.largeurDp() + " × "
                + etat.hauteurDp());
        if (etat.stats() == null) {
            stats.setText(contexte.getString(
                    R.string.apercu_stats_attente));
        } else {
            stats.setText(contexte.getString(R.string.apercu_stats,
                    etat.stats().vues(), etat.stats().dureeMs(),
                    etat.stats().largeur(), etat.stats().hauteur(),
                    etat.densite()));
        }
        if (etat.nomTheme() == null) {
            // le thème n'est jamais deviné (lot E) : le repli se dit
            banniereTheme = contexte.getString(
                    R.string.apercu_theme_hote);
        } else {
            banniereTheme = null;
        }
        majBannieres(etat.bannieres());
    }

    private String banniereTheme;

    private void majBannieres(List<Banniere> nouvelles) {
        Context contexte = getContext();
        bannieres.removeAllViews();
        // le thème n'est jamais deviné (lot E) : le repli hôte se dit
        if (banniereTheme != null) {
            bannieres.addView(banniere(contexte, palette.accent,
                    banniereTheme));
        }
        for (Banniere banniere : nouvelles) {
            int couleur = switch (banniere.gravite()) {
                case ERREUR -> palette.erreur;
                case ALERTE -> palette.alerte;
                case INFO -> palette.accent;
            };
            bannieres.addView(banniere(contexte, couleur,
                    texteBanniere(banniere)));
        }
    }

    private View banniere(Context contexte, int couleur, String texte) {
        TextView vue = new TextView(contexte);
        GraphiteVue.typographie(vue, Graphite.LEGENDE);
        vue.setTextColor(couleur);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(Graphite.melange(couleur, 0.10f,
                palette.surface));
        fond.setCornerRadius(GraphiteVue.dp(contexte, 9));
        fond.setStroke(GraphiteVue.dp(contexte, 1),
                Graphite.melange(couleur, 0.30f, palette.surface));
        vue.setBackground(fond);
        int pad = GraphiteVue.dp(contexte, 10);
        vue.setPadding(pad, pad, pad, pad);
        vue.setText(texte);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        params.topMargin = GraphiteVue.dp(contexte, 6);
        return vue;
    }

    private String texteBanniere(Banniere banniere) {
        Context contexte = getContext();
        return switch (banniere.genre()) {
            case FRAPPE -> contexte.getString(
                    R.string.apercu_banniere_frappe,
                    banniere.detail() == null ? "?"
                            : banniere.detail());
            case RESSOURCES -> contexte.getString(
                    R.string.apercu_banniere_ressources,
                    banniere.detail());
            case MOCKVIEW -> contexte.getString(
                    R.string.apercu_banniere_mockview,
                    banniere.detail());
            case ERREUR -> contexte.getString(
                    R.string.apercu_banniere_erreur,
                    banniere.detail());
            case CONFIG -> contexte.getString(
                    R.string.apercu_banniere_config,
                    banniere.detail());
        };
    }

    private void choisirDimensions() {
        Context contexte = getContext();
        String[] libelles = {
                contexte.getString(R.string.apercu_dims_telephone),
                contexte.getString(R.string.apercu_dims_grand),
                contexte.getString(R.string.apercu_dims_tablette),
        };
        int[] largeurs = {360, 412, 600};
        int[] hauteurs = {640, 915, 1024};
        Dialogue.menu(contexte, palette,
                contexte.getString(R.string.apercu_dims_titre),
                libelles, (dialog, lequel) -> {
                    if (auditeur != null) {
                        auditeur.surDimensions(largeurs[lequel],
                                hauteurs[lequel]);
                    }
                });
    }

    /**
     * Le conteneur du rendu : l'écran y pose la vue LayoutLib telle
     * quelle — jamais retouchée, jamais embellie.
     */
    public FrameLayout conteneurRendu() {
        FrameLayout perchoir = new FrameLayout(getContext());
        GradientDrawable cadre = new GradientDrawable();
        cadre.setColor(palette.puits);
        cadre.setCornerRadius(GraphiteVue.dp(getContext(), 24));
        perchoir.setBackground(cadre);
        perchoir.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.topMargin = params.bottomMargin =
                GraphiteVue.dp(getContext(), 10);
        colonneRendu.addView(perchoir, params);
        return perchoir;
    }

    /** Le damier neutre derrière le rendu (cases 8 dp, deux gris). */
    private static final class Damier extends FrameLayout {
        private final Graphite.Palette palette;
        private final Paint pinceau = new Paint();

        Damier(Context contexte, Graphite.Palette palette) {
            super(contexte);
            this.palette = palette;
            setBackgroundColor(palette.fond);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cote = GraphiteVue.dp(getContext(), 8);
            pinceau.setColor(palette.bordFin);
            for (float y = 0; y < getHeight(); y += cote) {
                for (float x = 0; x < getWidth(); x += cote) {
                    if (((x / cote) + (y / cote)) % 2 == 0) {
                        canvas.drawRect(x, y, x + cote, y + cote,
                                pinceau);
                    }
                }
            }
        }
    }
}
