package jo.joide.app.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;

import java.util.function.Function;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

/**
 * L'activité de base des ÉCRANS (lot L2+, correction du crash du
 * 2026-11-18 ; ADR-085 décision 2 au lot A1). Les layouts XML lisent les
 * {@code ?attr/jo_*} et les composants Material 3 exigent un thème
 * descendant de {@code Theme.Material3.*} : le thème des réglages
 * (Sombre/Clair/Système, ADR-048) est donc posé AVANT toute inflation —
 * {@code setTheme} précède {@code super.onCreate}, invariant protégé.
 *
 * <p>Depuis ADR-085 (directive D1) la coque est
 * {@link AppCompatActivity} : le retour système passe par
 * {@link androidx.activity.OnBackPressedDispatcher} (hérité), les
 * résultats d'activité par {@code registerForActivityResult}, et
 * l'inflation d'un écran se fait par {@link #monter(Function)} — une
 * seule voie de montage, le binding d'écran posé par
 * {@code setContentView(binding.getRoot())}. Les activités restent des
 * coques minces : cycle de vie, intents, retour et résultats ; la mise
 * en page vit dans le layout racine, la logique dans les états purs.</p>
 */
public abstract class ActiviteJoide extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle state) {
        setTheme(GraphiteVue.resTheme(this));
        super.onCreate(state);
    }

    /**
     * Monte le binding d'écran : gonfle, pose la racine et rend le
     * binding. Le thème est déjà posé (il l'est AVANT
     * {@code super.onCreate} ci-dessus) — l'inflation se fait donc sous
     * le thème Graphite Material 3, jamais sous le thème du manifeste.
     *
     * @param <B>       le type du binding d'écran (généré par ViewBinding)
     * @param inflateur la fabrique du binding (p. ex.
     *                  {@code ActivityAccueilBinding::inflate})
     * @return le binding monté
     */
    protected final <B extends ViewBinding> B monter(
            Function<LayoutInflater, B> inflateur) {
        B binding = inflateur.apply(getLayoutInflater());
        setContentView(binding.getRoot());
        return binding;
    }
}
