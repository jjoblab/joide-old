package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.FormateurFilAriane;
import jo.joide.app.android.databinding.AtelierFilArianeBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L2 (ADR-083) — le fil d'Ariane (§3.3) : la vue
 * MINCE qui gonfle {@code atelier_fil_ariane} et applique les
 * segments du modèle pur {@link FormateurFilAriane} — segments
 * texte2 séparés par « › », le DERNIER (le symbole sous le curseur)
 * en couleur fonction. Le contenu vient du moteur de langage ; la
 * vue ne décide rien.
 */
public final class FilAriane extends FrameLayout {

    /** Le binding du layout. */
    public final AtelierFilArianeBinding binding;

    public FilAriane(Context contexte) {
        this(contexte, null);
    }

    public FilAriane(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        binding = AtelierFilArianeBinding.inflate(
                LayoutInflater.from(contexte), this, true);
    }

    /** Pose les segments (module › paquets › classe › symbole). */
    public void poser(String module, String paquet, String classe,
                      String symbole) {
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        SpannableStringBuilder compose =
                new SpannableStringBuilder();
        boolean premier = true;
        for (FormateurFilAriane.Segment segment
                : FormateurFilAriane.segments(module, paquet, classe,
                symbole)) {
            if (!premier) {
                compose.append("  ");
                compose.append(FormateurFilAriane.SEPARATEUR);
                compose.append("  ");
            }
            premier = false;
            SpannableString morceau = new SpannableString(
                    segment.texte());
            morceau.setSpan(new ForegroundColorSpan(
                            segment.symbole() ? couleurFonction()
                                    : palette.texteSecondaire),
                    0, morceau.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            compose.append(morceau);
        }
        binding.filAriane.setText(compose);
    }

    /** La couleur « fonction » (le dernier segment, §3.3) — jamais
     *  une valeur codée en dur : l'attribut du thème. */
    private int couleurFonction() {
        TypedValue valeur = new TypedValue();
        getContext().getTheme().resolveAttribute(
                R.attr.jo_syntaxe_fonction, valeur, true);
        if (valeur.type == TypedValue.TYPE_ATTRIBUTE) {
            android.content.res.TypedArray suivant = getContext()
                    .obtainStyledAttributes(new int[]{valeur.data});
            int couleur = suivant.getColor(0, Color.TRANSPARENT);
            suivant.recycle();
            return couleur;
        }
        return valeur.data;
    }
}
