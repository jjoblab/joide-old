package jo.joide.app.android.atelier;

import android.content.Context;
import android.graphics.Typeface;
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
        LinearLayout rangee = new LinearLayout(contexte);
        rangee.setOrientation(HORIZONTAL);
        rangee.setGravity(Gravity.CENTER_VERTICAL);
        int retrait = GraphiteVue.dp(contexte,
                Math.min(6 + noeud.profondeur() * 14, 62));
        rangee.setPadding(retrait, GraphiteVue.dp(contexte, 1),
                GraphiteVue.dp(contexte, 12), GraphiteVue.dp(contexte, 1));
        rangee.setMinimumHeight(GraphiteVue.dp(contexte, 36));
        rangee.setClickable(true);

        if (noeud.actif()) {
            GradientDrawable fond = new GradientDrawable();
            fond.setColor(Graphite.melange(palette.accent, 0.14f,
                    palette.surface));
            fond.setCornerRadius(GraphiteVue.dp(contexte, 9));
            rangee.setBackground(fond);
        }

        if (noeud.dossier() && !noeud.sorties()) {
            TextView chevron = new TextView(contexte);
            GraphiteVue.typographie(chevron, Graphite.NOTE);
            chevron.setTextColor(noeud.ouvert() ? palette.texteSecondaire
                    : palette.texteEstompe);
            chevron.setText(noeud.ouvert() ? "▾" : "▸");
            chevron.setPadding(0, 0, GraphiteVue.dp(contexte, 6), 0);
            rangee.addView(chevron, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            View puce = new View(contexte);
            GradientDrawable rond = new GradientDrawable();
            rond.setShape(GradientDrawable.OVAL);
            rond.setColor(noeud.sorties() ? palette.texteEstompe
                    : Graphite.tuileTonale(palette.accent, palette.surface));
            puce.setBackground(rond);
            LinearLayout.LayoutParams puceParams = new LinearLayout.LayoutParams(
                    GraphiteVue.dp(contexte, 5), GraphiteVue.dp(contexte, 5));
            puceParams.rightMargin = GraphiteVue.dp(contexte, 9);
            puceParams.leftMargin = GraphiteVue.dp(contexte, 1);
            rangee.addView(puce, puceParams);
        }

        TextView libelle = new TextView(contexte);
        GraphiteVue.typographie(libelle, Graphite.NOTE);
        libelle.setSingleLine(true);
        libelle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        if (noeud.actif()) {
            libelle.setTextColor(palette.accent);
            libelle.setTypeface(GraphiteVue.police(contexte, 600, false));
        } else if (noeud.sorties()) {
            libelle.setTextColor(palette.texteEstompe);
            libelle.setTypeface(Typeface.create("monospace",
                    Typeface.NORMAL));
        } else {
            libelle.setTextColor(noeud.dossier() ? palette.texte
                    : palette.texteSecondaire);
        }
        libelle.setText(noeud.nom());
        rangee.addView(libelle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        rangee.setOnClickListener(vue -> {
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
        if (sortiesEnCours && noeud.sorties()) {
            rangee.setAlpha(0.5f);
        }
        return rangee;
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
