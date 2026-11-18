package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.ModeleOnglets;
import jo.joide.app.android.databinding.AtelierOngletsBinding;
import jo.joide.app.android.databinding.ItemOngletBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L2 (ADR-083) — la bande d'onglets (§3.2). La vue
 * est MINCE : elle gonfle {@code atelier_onglets} et se dessine
 * depuis le modèle pur {@link ModeleOnglets} — le propriétaire de
 * l'état reste {@code EtatAtelier}. Le fil d'Ariane vit dans sa
 * propre vue ({@link FilAriane}).
 */
public final class BarreOnglets extends FrameLayout {

    /** Un auditeur d'onglets (activation, fermeture). */
    public interface Auditeur {
        /** Un onglet est activé (tap). */
        void surActivation(String chemin);

        /** L'onglet demandé est fermé (×). */
        void surFermeture(String chemin);

        /** Le bouton d'aperçu est demandé. */
        void surApercu();
    }

    /** Le binding de la bande. */
    public final AtelierOngletsBinding binding;

    private Auditeur auditeur;

    public BarreOnglets(Context contexte) {
        this(contexte, null);
    }

    public BarreOnglets(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        binding = AtelierOngletsBinding.inflate(
                LayoutInflater.from(contexte), this, true);
        binding.boutonApercu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surApercu();
            }
        });
    }

    /** Pose l'auditeur (l'écran garde le câblage). */
    public void setAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /** Le bouton d'aperçu (visible téléphone, onglet XML actif). */
    public View boutonApercu() {
        return binding.boutonApercu;
    }

    /** Redessine la bande depuis le modèle pur. */
    public void poser(ModeleOnglets modele) {
        binding.rangeeOnglets.removeAllViews();
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        LayoutInflater gonfleur = LayoutInflater.from(getContext());
        for (ModeleOnglets.EtatOnglet onglet : modele.onglets()) {
            ItemOngletBinding item = ItemOngletBinding.inflate(
                    gonfleur, binding.rangeeOnglets, false);
            boolean actif = onglet.chemin.equals(modele.actif());
            stylerItem(item, onglet, actif, palette);
            item.nomOnglet.setText(onglet.nom);
            item.pastilleOnglet.setText(onglet.badge.glyphe);
            item.getRoot().setOnClickListener(vue -> {
                if (auditeur != null) {
                    auditeur.surActivation(onglet.chemin);
                }
            });
            item.btnFermerOnglet.setOnClickListener(vue -> {
                if (auditeur != null) {
                    auditeur.surFermeture(onglet.chemin);
                }
            });
            item.getRoot().setOnLongClickListener(vue -> {
                // §3.2 : appui long sur un autre onglet → fermer les
                // autres (le menu complet va au popup L6) ; fermeture
                // simple ici : le menu dédié reste à faire pour la
                // franchise — cette action est RÉELLE.
                if (auditeur != null && !actif) {
                    auditeur.surFermeture(onglet.chemin);
                    return true;
                }
                return false;
            });
            binding.rangeeOnglets.addView(item.getRoot());
        }
        // l'actif visible : défilement minimal vers l'onglet actif
        binding.rangeeOnglets.post(() -> {
            int index = modele.indexActif();
            if (index >= 0 && index < binding.rangeeOnglets
                    .getChildCount()) {
                View actifVue = binding.rangeeOnglets.getChildAt(index);
                HorizontalScrollView hsv = binding.defilementOnglets;
                hsv.smoothScrollTo(Math.max(0, actifVue.getLeft()
                        - hsv.getWidth() / 2
                        + actifVue.getWidth() / 2), 0);
            }
        });
    }

    /** Les couleurs d'un item, depuis le modèle (franchise : tout
     *  vient des ?attr/jo_* résolus par GraphiteVue). */
    private void stylerItem(ItemOngletBinding item,
                            ModeleOnglets.EtatOnglet onglet,
                            boolean actif, Graphite.Palette palette) {
        item.nomOnglet.setTextColor(actif ? palette.texte
                : palette.texteSecondaire);
        item.getRoot().setBackgroundResource(actif
                ? R.drawable.onglet_actif_fond
                : android.R.color.transparent);
        item.indicateurOnglet.setVisibility(
                actif ? View.VISIBLE : View.GONE);
        item.btnFermerOnglet.setVisibility(
                actif ? View.VISIBLE : View.GONE);
        item.pointOnglet.setVisibility(onglet.erreur || onglet.modifie
                ? View.VISIBLE : View.GONE);
        GradientDrawable point = new GradientDrawable();
        point.setShape(GradientDrawable.OVAL);
        point.setColor(onglet.erreur ? palette.erreur
                : palette.alerte);
        item.pointOnglet.setBackground(point);

        GradientDrawable pastille = new GradientDrawable();
        pastille.setCornerRadius(GraphiteVue.dp(getContext(), 5f));
        switch (onglet.badge) {
            case JAVA -> pastille.setColor(resoudre(
                    R.attr.jo_pastille_ambre));
            case KOTLIN -> pastille.setColor(resoudre(
                    R.attr.jo_pastille_violet));
            case XML -> pastille.setColor(resoudre(
                    R.attr.jo_pastille_reussite));
            default -> pastille.setColor(resoudre(
                    R.attr.jo_pastille_accent));
        }
        item.pastilleOnglet.setBackground(pastille);
    }

    /** Résout un attribut de thème en couleur (jamais une valeur
     *  codée en dur). */
    private int resoudre(int attribut) {
        android.util.TypedValue valeur = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(attribut, valeur, true);
        if (valeur.type == android.util.TypedValue.TYPE_ATTRIBUTE) {
            android.content.res.TypedArray suivant = getContext()
                    .obtainStyledAttributes(new int[]{valeur.data});
            int couleur = suivant.getColor(0, Color.TRANSPARENT);
            suivant.recycle();
            return couleur;
        }
        return valeur.data;
    }
}
