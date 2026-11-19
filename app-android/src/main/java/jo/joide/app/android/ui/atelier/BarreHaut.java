package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import jo.joide.app.android.R;
import jo.joide.app.android.databinding.AtelierBarreHautBinding;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.Puce;

/**
 * Atelier v2, lot L2 (ADR-083) — la barre supérieure (§3.1) : la
 * vue est MINCE (elle gonfle {@code atelier_barre_haut} et expose
 * son Binding) ; l'état reste dans {@code EtatAtelier} et le câblage
 * vit dans l'écran. L'UI mince : pas de logique testable ici.
 * Lot A9 (ADR-085) : la classe EST le LinearLayout horizontal de la
 * barre — la racine du layout est un {@code <merge>}, un niveau de
 * hiérarchie de moins par zone (I3) ; orientation, gravité et retrait
 * 10 dp s'y posent, la hauteur 52 dp vit dans le layout parent.
 */
public final class BarreHaut extends LinearLayout {

    /** Le binding du layout. */
    public final AtelierBarreHautBinding binding;

    public BarreHaut(Context contexte) {
        this(contexte, null);
    }

    public BarreHaut(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int retrait = GraphiteVue.dp(contexte, 10);
        setPadding(retrait, 0, retrait, 0);
        binding = AtelierBarreHautBinding.inflate(
                LayoutInflater.from(contexte), this);
    }

    /** Le nom du projet (16/20 semi-gras). */
    public void poserNomProjet(String nom) {
        binding.nomProjet.setText(nom);
    }

    /** La puce « n modules ». */
    public Puce puceModules() {
        return binding.puceModules;
    }

    /** La pilule d'état de l'index. */
    public Pilule piluleIndex() {
        return binding.piluleIndex;
    }

    /** Le point d'activité de l'agent : visible UNIQUEMENT pendant un
     *  tour (§3.1). */
    public void poserPointAgent(boolean visible) {
        binding.pointAgent.setVisibility(
                visible ? View.VISIBLE : View.GONE);
    }

    /** La couleur d'accent courante (pour styler la pilule depuis
     *  l'écran sans lire Graphite deux fois). */
    public int accent() {
        return GraphiteVue.palette(getContext()).accent;
    }
}
