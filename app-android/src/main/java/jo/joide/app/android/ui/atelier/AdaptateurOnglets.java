package jo.joide.app.android.ui.atelier;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.ModeleOnglets;
import jo.joide.app.android.databinding.ItemOngletBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Lot A6 (ADR-085 décisions 10 et 11) — les onglets FERMABLES de
 * l'atelier (§3.2) en {@code RecyclerView} horizontal +
 * {@code ListAdapter}/DiffUtil : l'onglet fermable (× sur l'actif) et
 * l'appui long « fermer un autre onglet » exigent une vue d'onglet
 * personnalisée ({@code item_onglet} du lot L2) — TabLayout garderait
 * SA mécanique de sélection (indicateur et ripple propres) qui
 * doublerait l'indicateur maison 2 dp. La mise à jour se borne au
 * DIFF (l'activation ne re-lier que l'onglet changé, plus de
 * removeAllViews()/ré-inflation complète — constat I4 appliqué à la
 * bande, signalé au lot A5). TOUT se RE-POSE au bind (les vues se
 * recyclent, un état posé ne s'y annulerait jamais seul).
 */
final class AdaptateurOnglets
        extends ListAdapter<OngletVu, AdaptateurOnglets.Tenu> {

    /** Les gestes d'onglet (activation, fermeture). */
    interface Auditeur {
        /** Un onglet est activé (tap). */
        void surActivation(String chemin);

        /** L'onglet demandé est fermé (× ou appui long sur un autre). */
        void surFermeture(String chemin);
    }

    private final Auditeur auditeur;

    AdaptateurOnglets(Auditeur ecouteur) {
        super(new DiffUtil.ItemCallback<OngletVu>() {
            @Override
            public boolean areItemsTheSame(OngletVu ancien,
                                            OngletVu neuf) {
                return ancien.memeOnglet(neuf);
            }

            @Override
            public boolean areContentsTheSame(OngletVu ancien,
                                               OngletVu neuf) {
                return ancien.memeRendu(neuf);
            }
        });
        auditeur = ecouteur;
    }

    /** Le comptoir d'un onglet (le binding de {@code item_onglet}). */
    static final class Tenu extends RecyclerView.ViewHolder {
        final ItemOngletBinding binding;

        Tenu(ItemOngletBinding b) {
            super(b.getRoot());
            binding = b;
        }
    }

    @Override
    public Tenu onCreateViewHolder(ViewGroup parent, int type) {
        return new Tenu(ItemOngletBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Tenu tenu, int position) {
        OngletVu vu = getItem(position);
        ModeleOnglets.EtatOnglet onglet = vu.onglet();
        Graphite.Palette palette =
                GraphiteVue.palette(tenu.binding.getRoot().getContext());
        // les couleurs d'un item, depuis le modèle (franchise : tout
        // vient des ?attr/jo_* résolus par GraphiteVue) — RE-POSÉES à
        // chaque bind, les vues se recyclent
        tenu.binding.nomOnglet.setTextColor(vu.actif() ? palette.texte
                : palette.texteSecondaire);
        tenu.binding.getRoot().setBackgroundResource(vu.actif()
                ? R.drawable.onglet_actif_fond
                : android.R.color.transparent);
        tenu.binding.indicateurOnglet.setVisibility(
                vu.actif() ? View.VISIBLE : View.GONE);
        tenu.binding.btnFermerOnglet.setVisibility(
                vu.actif() ? View.VISIBLE : View.GONE);
        tenu.binding.pointOnglet.setVisibility(
                onglet.erreur || onglet.modifie
                        ? View.VISIBLE : View.GONE);
        GradientDrawable point = new GradientDrawable();
        point.setShape(GradientDrawable.OVAL);
        point.setColor(onglet.erreur ? palette.erreur
                : palette.alerte);
        tenu.binding.pointOnglet.setBackground(point);

        GradientDrawable pastille = new GradientDrawable();
        pastille.setCornerRadius(GraphiteVue.dp(
                tenu.binding.getRoot().getContext(), 5f));
        switch (onglet.badge) {
            case JAVA -> pastille.setColor(resoudre(
                    tenu.binding.getRoot().getContext(),
                    R.attr.jo_pastille_ambre));
            case KOTLIN -> pastille.setColor(resoudre(
                    tenu.binding.getRoot().getContext(),
                    R.attr.jo_pastille_violet));
            case XML -> pastille.setColor(resoudre(
                    tenu.binding.getRoot().getContext(),
                    R.attr.jo_pastille_reussite));
            default -> pastille.setColor(resoudre(
                    tenu.binding.getRoot().getContext(),
                    R.attr.jo_pastille_accent));
        }
        tenu.binding.pastilleOnglet.setBackground(pastille);

        tenu.binding.pastilleOnglet.setText(onglet.badge.glyphe);
        tenu.binding.nomOnglet.setText(onglet.nom);
        tenu.binding.getRoot().setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surActivation(onglet.chemin);
            }
        });
        tenu.binding.btnFermerOnglet.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surFermeture(onglet.chemin);
            }
        });
        tenu.binding.getRoot().setOnLongClickListener(vue -> {
            // §3.2 : appui long sur un autre onglet → fermeture simple
            // de CET onglet (le menu complet va au popup du lot A7) ;
            // l'action est RÉELLE
            if (auditeur != null && !vu.actif()) {
                auditeur.surFermeture(onglet.chemin);
                return true;
            }
            return false;
        });
    }

    /** Résout un attribut de thème en couleur (jamais une valeur
     *  codée en dur). */
    private static int resoudre(android.content.Context contexte,
                                int attribut) {
        TypedValue valeur = new TypedValue();
        contexte.getTheme().resolveAttribute(attribut, valeur, true);
        if (valeur.type == TypedValue.TYPE_ATTRIBUTE) {
            android.content.res.TypedArray suivant = contexte
                    .obtainStyledAttributes(new int[]{valeur.data});
            int couleur = suivant.getColor(0, Color.TRANSPARENT);
            suivant.recycle();
            return couleur;
        }
        return valeur.data;
    }
}
