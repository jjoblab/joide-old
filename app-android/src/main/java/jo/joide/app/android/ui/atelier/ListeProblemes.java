package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.EtatProblemes;
import jo.joide.app.android.databinding.ItemProblemeBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L3 (ADR-083) — la liste de la section Problèmes
 * (§3.6) : lignes gonflées depuis {@code item_probleme}, groupées
 * par fichier (en-tête 12 semi-gras texte2 « Facture.java · 2 ») ;
* tap → ouvre le fichier à la ligne et replie la feuille. La vue est
 * MINCE : elle lit l'état pur {@link EtatProblemes}.
 */
public final class ListeProblemes extends ScrollView {

    /** Un auditeur (l'ouverture d'un problème). */
    public interface Auditeur {
        /** Ouvre le fichier à la ligne (et replie la feuille). */
        void surOuvrir(String chemin, int ligne);
    }

    /** La colonne des lignes groupées. */
    private final LinearLayout rangee;
    private Auditeur auditeur;

    public ListeProblemes(Context contexte) {
        this(contexte, null);
    }

    public ListeProblemes(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        rangee = new LinearLayout(contexte);
        rangee.setOrientation(LinearLayout.VERTICAL);
        addView(rangee, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    /** Pose l'auditeur. */
    public void setAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /** Redessine depuis l'état pur (groupes par fichier, §3.6). */
    public void maj(EtatProblemes etat) {
        rangee.removeAllViews();
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        LayoutInflater gonfleur = LayoutInflater.from(getContext());
        var groupes = etat.parFichier();
        boolean premierGroupe = true;
        for (var entree : groupes.entrySet()) {
            String fichier = entree.getKey();
            var problemes = entree.getValue();
            boolean premier = premierGroupe;
            if (!premierGroupe) {
                View separateur = new View(getContext());
                separateur.setBackgroundColor(palette.bordFin);
                rangee.addView(separateur, new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        GraphiteVue.dp(getContext(), 1f)));
            }
            premierGroupe = false;
            // l'en-tête de groupe : « Facture.java · 2 »
            TextView enTete = new TextView(getContext());
            enTete.setText(fichier.substring(
                    fichier.lastIndexOf('/') + 1) + " · "
                    + problemes.size());
            enTete.setTextSize(12f);
            enTete.setTypeface(null, android.graphics.Typeface.BOLD);
            enTete.setTextColor(palette.texteSecondaire);
            enTete.setPadding(0, GraphiteVue.dp(getContext(),
                    premier ? 0 : 8), 0,
                    GraphiteVue.dp(getContext(), 2));
            rangee.addView(enTete, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            for (EtatProblemes.Probleme probleme : problemes) {
                ItemProblemeBinding item = ItemProblemeBinding.inflate(
                        gonfleur, rangee, false);
                item.glypheProbleme.setText(
                        probleme.erreur() ? "✕" : "▲");
                item.glypheProbleme.setTextColor(
                        probleme.erreur() ? palette.erreur
                                : palette.alerte);
                item.messageProbleme.setText(probleme.message());
                item.ligneProbleme.setText(getString(
                        R.string.atelier_probleme_ligne,
                        probleme.ligne()));
                item.getRoot().setOnClickListener(vue -> {
                    if (auditeur != null) {
                        auditeur.surOuvrir(probleme.chemin(),
                                probleme.ligne());
                    }
                });
                rangee.addView(item.getRoot());
            }
        }
    }

    private String getString(int ressource, Object... arguments) {
        return getContext().getString(ressource, arguments);
    }
}
