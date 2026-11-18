package jo.joide.app.android.atelier;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.Arborescence.Noeud;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Le tiroir arborescence de l'atelier (lot J, ADR-É5) : « Nouveau »
 * (fichier/dossier) en tête, l'arbre de {@link Arborescence} avec
 * entrée active surlignée, et les sorties de build estompées avec
 * leur raison — jamais présentées comme sources. La vue ne décide
 * rien : elle reçoit les nœuds composés par l'état pur.
 */
public final class VueArbre extends LinearLayout {

    /**
     * Le plafond d'affichage du tiroir (lignes visibles) — un arbre
     * déplié de fond se coupe avec sa raison, jamais un affichage
     * rendu muet ; la note invite à refermer des dossiers.
     */
    public static final int PLAFOND_NOEUDS = 400;

    /** L'auditeur des actions du tiroir. */
    public interface AuditeurArbre {
        /** L'appui long demande le menu contextuel du nœud (§3.8). */
        void surMenuContextuel(String chemin, boolean dossier);
        /** Un dossier est plié/déplié. */
        void surDossier(String chemin, boolean ouvrir);

        /** Un fichier est ouvert. */
        void surFichier(String chemin);

        /** « Nouveau » : fichier (vrai) ou dossier (faux). */
        void surNouveau(boolean fichier);
    }

    private final Graphite.Palette palette;
    private final LinearLayout colonne;
    private AuditeurArbre auditeur;
    private boolean sortiesEnCours;

    public VueArbre(Context contexte) {
        this(contexte, null);
    }

    public VueArbre(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(VERTICAL);
        setBackgroundColor(palette.surface);

        LinearLayout entete = new LinearLayout(contexte);
        entete.setOrientation(HORIZONTAL);
        entete.setGravity(Gravity.CENTER_VERTICAL);
        int marge = GraphiteVue.dp(contexte, 16);
        entete.setPadding(marge, marge, marge, marge);

        TextView nom = new TextView(contexte);
        GraphiteVue.typographie(nom, Graphite.SOUS_TITRE);
        nom.setTextColor(palette.texte);
        nom.setSingleLine(true);
        nom.setTag("nom");
        entete.addView(nom, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Bouton nouveau = new Bouton(contexte);
        nouveau.setVariante(jo.joide.app.android.ui.choix.ChoixBouton.Variante.TONAL);
        nouveau.setTexte(R.string.atelier_nouveau);
        nouveau.setOnClickListener(vue -> proposerNouveau());
        entete.addView(nouveau, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addView(entete, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        ScrollView defile = new ScrollView(contexte);
        colonne = new LinearLayout(contexte);
        colonne.setOrientation(VERTICAL);
        colonne.setPadding(0, 0, 0, marge);
        defile.addView(colonne, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(defile, new LayoutParams(LayoutParams.MATCH_PARENT, 0,
                1f));
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurArbre ecouteur) {
        this.auditeur = ecouteur;
    }

    /** Estompe les sorties pendant qu'elles se construisent (leçon 5). */
    public void setSortiesEnCours(boolean enCours) {
        this.sortiesEnCours = enCours;
    }

    /** Pose l'arbre : les nœuds visibles composés par l'état pur. */
    public void setArbre(List<Noeud> noeuds, String nomProjet) {
        TextView entete = findViewWithTag("nom");
        if (entete != null) {
            entete.setText(nomProjet == null ? "" : nomProjet);
        }
        colonne.removeAllViews();
        int montres = 0;
        boolean tronque = false;
        for (Noeud noeud : noeuds) {
            if (noeud.profondeur() == 0) {
                continue;
            }
            if (montres >= PLAFOND_NOEUDS) {
                tronque = true;
                break;
            }
            colonne.addView(ligne(noeud));
            if (noeud.sorties()) {
                colonne.addView(noteSorties());
            }
            montres++;
        }
        if (tronque) {
            TextView note = new TextView(getContext());
            GraphiteVue.typographie(note, Graphite.LEGENDE);
            note.setTextColor(palette.texteEstompe);
            note.setText(getContext().getString(
                    R.string.atelier_arbre_tronque, PLAFOND_NOEUDS));
            int retrait = GraphiteVue.dp(getContext(), 20);
            note.setPadding(retrait, GraphiteVue.dp(getContext(), 8),
                    retrait, 0);
            colonne.addView(note);
        }
    }

    private View ligne(Noeud noeud) {
        Context contexte = getContext();
        // lot L4 (ADR-083) : la rangée se gonfle depuis item_noeud —
        // plus de construction programmatique dans les listes (§2)
        jo.joide.app.android.databinding.ItemNoeudBinding item =
                jo.joide.app.android.databinding.ItemNoeudBinding
                        .inflate(LayoutInflater.from(contexte), colonne,
                                false);
        LinearLayout rangee = (LinearLayout) item.getRoot()
                .getChildAt(0);
        int retrait = GraphiteVue.dp(contexte,
                Math.min(6 + noeud.profondeur() * 16, 70));
        rangee.setPadding(retrait, 0, GraphiteVue.dp(contexte, 8), 0);
        boolean actif = noeud.actif();
        item.getRoot().setOnClickListener(vue -> {
            if (auditeur == null) {
                return;
            }
            if (noeud.sorties()) {
                // les sorties ne s'ouvrent pas : jamais des sources
                return;
            }
            if (noeud.dossier()) {
                auditeur.surDossier(noeud.chemin(), !noeud.ouvert());
            } else {
                auditeur.surFichier(noeud.chemin());
            }
        });
        item.getRoot().setOnLongClickListener(vue -> {
            if (auditeur == null || noeud.sorties()) {
                return false;
            }
            auditeur.surMenuContextuel(noeud.chemin(), noeud.dossier());
            return true;
        });

        // le chevron : dossiers OUVRABLES seulement (les sorties et
        // les fichiers portent la pastille de type)
        if (noeud.dossier() && !noeud.sorties()) {
            item.chevronNoeud.setVisibility(View.VISIBLE);
            item.chevronNoeud.setCouleur(noeud.ouvert()
                    ? palette.texteSecondaire : palette.texteEstompe);
            item.chevronNoeud.setRotation(noeud.ouvert() ? 90f : 0f);
        } else {
            item.chevronNoeud.setVisibility(View.GONE);
        }

        // la pastille de type (§3.8) : J ambre / ‹› réussite /
        // ⚙ accent / dossier glyphe texte2 — tout vient des jetons
        String badge;
        int fondPastille;
        int textePastille;
        if (noeud.dossier()) {
            badge = "▤";
            fondPastille = 0x00000000;
            textePastille = palette.texteSecondaire;
        } else if (noeud.sorties()) {
            badge = "⚙";
            fondPastille = 0x00000000;
            textePastille = palette.texteEstompe;
        } else {
            String bas = noeud.nom().toLowerCase();
            if (bas.endsWith(".java")) {
                badge = "J";
                fondPastille = resoudre(R.attr.jo_pastille_ambre);
                textePastille = palette.texte;
            } else if (bas.endsWith(".kt") || bas.endsWith(".kts")) {
                badge = "K";
                fondPastille = resoudre(R.attr.jo_pastille_violet);
                textePastille = palette.texte;
            } else if (bas.endsWith(".xml")) {
                badge = "‹›";
                fondPastille = resoudre(R.attr.jo_pastille_reussite);
                textePastille = palette.texte;
            } else {
                badge = "⚙";
                fondPastille = resoudre(R.attr.jo_pastille_accent);
                textePastille = palette.texte;
            }
        }
        item.pastilleNoeud.setText(badge);
        item.pastilleNoeud.setTextColor(textePastille);
        GradientDrawable fondBadge = new GradientDrawable();
        fondBadge.setCornerRadius(GraphiteVue.dp(contexte, 6f));
        if (fondPastille != 0x00000000) {
            fondBadge.setColor(fondPastille);
        }
        item.pastilleNoeud.setBackground(fondBadge);

        // le nom : actif accent semi-gras, sorties estompées mono
        item.nomNoeud.setText(noeud.nom());
        if (actif) {
            item.nomNoeud.setTextColor(palette.accent);
            item.nomNoeud.setTypeface(GraphiteVue.police(contexte,
                    600, false));
        } else if (noeud.sorties()) {
            item.nomNoeud.setTextColor(palette.texteEstompe);
            item.nomNoeud.setTypeface(Typeface.create("monospace",
                    Typeface.NORMAL));
        } else {
            item.nomNoeud.setTextColor(noeud.dossier()
                    ? palette.texte : palette.texteSecondaire);
        }

        // la puce « build #n… » sur les sorties en cours (§3.8)
        if (noeud.sorties() && sortiesEnCours) {
            item.puceSorties.setText(getContext().getString(
                    R.string.atelier_sorties_en_cours));
            item.puceSorties.setVisibility(View.VISIBLE);
            rangee.setAlpha(0.5f);
        } else {
            item.puceSorties.setVisibility(View.GONE);
        }

        // la barre active : 3 dp accent (inset 9, §3.8)
        item.barreNoeud.setVisibility(actif ? View.VISIBLE
                : View.GONE);
        if (actif) {
            GradientDrawable fondActif = new GradientDrawable();
            fondActif.setColor(Graphite.melange(palette.accent, 0.14f,
                    palette.surface));
            fondActif.setCornerRadius(GraphiteVue.dp(contexte, 10));
            item.getRoot().setBackground(fondActif);
        }
        return item.getRoot();
    }

    /** Résout un attribut de thème en couleur (jamais une valeur
     *  codée en dur, franchise §0.4). */
    private int resoudre(int attribut) {
        TypedValue valeur = new TypedValue();
        getContext().getTheme().resolveAttribute(attribut, valeur,
                true);
        if (valeur.type == TypedValue.TYPE_ATTRIBUTE) {
            android.content.res.TypedArray suivant = getContext()
                    .obtainStyledAttributes(new int[]{valeur.data});
            int couleur = suivant.getColor(0, 0);
            suivant.recycle();
            return couleur;
        }
        return valeur.data;
    }

    private View noteSorties() {
        Context contexte = getContext();
        TextView note = new TextView(contexte);
        GraphiteVue.typographie(note, Graphite.LEGENDE);
        note.setTextColor(palette.texteEstompe);
        note.setText(R.string.atelier_sorties_note);
        int retrait = GraphiteVue.dp(contexte, 20);
        note.setPadding(retrait, 0, retrait, GraphiteVue.dp(contexte, 10));
        note.setAlpha(sortiesEnCours ? 0.7f : 1f);
        return note;
    }

    private void proposerNouveau() {
        Context contexte = getContext();
        String fichier = contexte.getString(R.string.atelier_nouveau_fichier);
        String dossier = contexte.getString(R.string.atelier_nouveau_dossier);
        Dialogue.menu(contexte, palette,
                contexte.getString(R.string.atelier_nouveau),
                new CharSequence[]{fichier, dossier},
                (dialog, lequel) -> {
                    if (auditeur != null) {
                        auditeur.surNouveau(lequel == 0);
                    }
                });
    }
}
