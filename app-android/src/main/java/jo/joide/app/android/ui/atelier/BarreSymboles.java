package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.ModeleBarreSymboles;
import jo.joide.app.android.databinding.AtelierBarreSymbolesBinding;
import jo.joide.app.android.databinding.ItemToucheBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L2 (ADR-083) — la barre de symboles (§3.5) :
 * VISIBLE SEULEMENT quand le clavier est affiché (l'écran branche le
 * détecteur d'insets) ; la vue MINCE gonfle {@code atelier_barre_symboles}
 * et une {@code item_touche} par touche de {@link ModeleBarreSymboles}.
 * Toucher = insertion intelligente via l'InputConnection de la vue
 * éditeur (fermeture auto des paires) ; flick haut = variante ; la
 * rangée d'actions « ⋯ » porte ses verbes réels. AUCUNE touche ne
 * prend le focus (le clavier système ne se ferme pas).
 */
public final class BarreSymboles extends FrameLayout {

    /** Un auditeur d'insertion (la vue éditeur est connectée par
     *  l'écran — la vue ne connaît pas celéris). */
    public interface Auditeur {
        /** Le texte demandé s'insère à la position du curseur. */
        void inserer(String texte);

        /** Le curseur bouge de delta caractères (le trackpad). */
        void deplacerCurseur(int delta);

        /** La ligne courante est commentée (« // » en tête). */
        void commenterLigne();

        /** La ligne courante est dupliquée sous elle-même. */
        void dupliquerLigne();
    }

    /** Le binding de la barre. */
    public final AtelierBarreSymbolesBinding binding;

    private Auditeur auditeur;

    public BarreSymboles(Context contexte) {
        this(contexte, null);
    }

    public BarreSymboles(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        binding = AtelierBarreSymbolesBinding.inflate(
                LayoutInflater.from(contexte), this, true);
        binding.barreSymboles.setContentDescription(
                getContext().getString(
                        R.string.atelier_desc_barre_symboles));
        gonflerTouches();
    }

    /** Pose l'auditeur (l'écran applique l'insertion dans celéris —
     *  la vue ne connaît pas la brique). */
    public void setAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /** Les touches, dans l'ordre du modèle pur. */
    private void gonflerTouches() {
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        LayoutInflater gonfleur = LayoutInflater.from(getContext());
        LinearLayout rangee = binding.barreSymboles;
        // les touches symboles (rangée par défaut)
        for (ModeleBarreSymboles.Touche touche
                : ModeleBarreSymboles.touches()) {
            ajouterToucheSymbole(rangee, gonfleur, palette, touche);
        }
        // la rangée d'actions « ⋯ » (§3.5) : des VERBES RÉELS —
        // commenter la ligne courante, la dupliquer, revenir aux
        // symboles ; « problème suivant » rejoindra la rangée au lot
        // L3 avec la source LSP (franchise : jamais un bouton mort)
        ajouterToucheAction(rangee, gonfleur, palette,
                getContext().getString(R.string.atelier_action_commenter),
                v -> { if (auditeur != null) { auditeur.commenterLigne(); } });
        ajouterToucheAction(rangee, gonfleur, palette,
                getContext().getString(R.string.atelier_action_dupliquer),
                v -> { if (auditeur != null) { auditeur.dupliquerLigne(); } });
        ajouterToucheAction(rangee, gonfleur, palette,
                getContext().getString(R.string.atelier_action_symboles),
                v -> basculer(true));
    }

    /** Une touche de la rangée de SYMBOLES (avec la variante flick à
     *  venir au lot L3 — le toucher insère déjà). */
    private void ajouterToucheSymbole(LinearLayout rangee,
                                      LayoutInflater gonfleur,
                                      Graphite.Palette palette,
                                      ModeleBarreSymboles.Touche touche) {
        ItemToucheBinding item = ItemToucheBinding.inflate(
                gonfleur, rangee, true);
        TextView toucheVue = item.touche;
        if (touche.tab) {
            toucheVue.setTextColor(palette.accent);
            toucheVue.setTextSize(12f);
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams)
                            toucheVue.getLayoutParams();
            params.weight = 1.6f;
            params.setMarginStart(0);
            toucheVue.setLayoutParams(params);
            toucheVue.setContentDescription(getContext()
                    .getString(R.string.atelier_desc_touche_tab));
        } else if (touche.actions) {
            toucheVue.setContentDescription(getContext()
                    .getString(R.string.atelier_desc_touche_actions));
        } else {
            toucheVue.setContentDescription(getContext()
                    .getString(R.string.atelier_desc_touche_symbole,
                            touche.glyphe));
        }
        // le geste de la touche : flick HAUT = la variante (( → )),
        // appui LONG puis glisser = le trackpad du curseur (§3.5) —
        // le toucher simple garde l'insertion intelligente
        toucheVue.setOnClickListener(vue -> insererTouche(touche));
        toucheVue.setOnLongClickListener(vue -> {
            trackpad = true;
            return true;
        });
        toucheVue.setOnTouchListener((vue, evenement) -> {
            switch (evenement.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    doigtY = evenement.getY();
                    doigtX = evenement.getX();
                    doigtInstant = android.os.SystemClock
                            .uptimeMillis();
                    trackpad = false;
                    pasTrackpad = 0f;
                    return false;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float dx = evenement.getX() - doigtX;
                    if (trackpad && auditeur != null) {
                        // un pas de 14 dp déplace d'un caractère
                        pasTrackpad += dx;
                        float pas = GraphiteVue.dp(getContext(), 14f);
                        while (Math.abs(pasTrackpad) >= pas) {
                            auditeur.deplacerCurseur(
                                    pasTrackpad > 0 ? 1 : -1);
                            pasTrackpad -= pas
                                    * Math.signum(pasTrackpad);
                        }
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP,
                     MotionEvent.ACTION_CANCEL -> {
                    if (trackpad) {
                        trackpad = false;
                        return true;
                    }
                    long duree = android.os.SystemClock.uptimeMillis()
                            - doigtInstant;
                    float dy = evenement.getY() - doigtY;
                    float dx2 = evenement.getX() - doigtX;
                    boolean flickHaut = dy
                            < -GraphiteVue.dp(getContext(), 22f)
                            && Math.abs(dx2)
                            < GraphiteVue.dp(getContext(), 40f)
                            && duree < 400;
                    if (flickHaut) {
                        String variante =
                                ModeleBarreSymboles.varianteDe(touche);
                        if (variante != null && auditeur != null) {
                            auditeur.inserer(variante);
                            return true;
                        }
                    }
                    return false;
                }
                default -> {
                }
            }
            return false;
        });
    }



    /** Une touche verbe de la rangée d'actions (texte sans mono). */
    private void ajouterToucheAction(LinearLayout rangee,
                                     LayoutInflater gonfleur,
                                     Graphite.Palette palette,
                                     String libelle,
                                     android.view.View.OnClickListener action) {
        ItemToucheBinding item = ItemToucheBinding.inflate(
                gonfleur, rangee, true);
        TextView toucheVue = item.touche;
        toucheVue.setText(libelle);
        toucheVue.setTypeface(null, android.graphics.Typeface.BOLD);
        toucheVue.setTextSize(12f);
        toucheVue.setTextColor(palette.accent);
        toucheVue.setOnClickListener(action);
        toucheVue.setTag("action");
        toucheVue.setVisibility(View.GONE);
    }

    // ---- le geste des touches (flick variante + trackpad) ----
    private float doigtX;
    private float doigtY;
    private long doigtInstant;
    private boolean trackpad;
    private float pasTrackpad;

    /** La bascule symboles ↔ actions : UNE rangée, deux contenus —
     *  les verbes d'actions remplacent les touches, jamais les deux
     *  en même temps. */
    private void basculer(boolean symboles) {
        for (int i = 0; i < binding.barreSymboles.getChildCount(); i++) {
            View touche = binding.barreSymboles.getChildAt(i);
            boolean estAction = "action".equals(touche.getTag());
            touche.setVisibility(symboles == estAction
                    ? View.GONE : View.VISIBLE);
        }
    }

    /** L'insertion : la paire pour les ouvrantes (insertion
     *  intelligente), le caractère sinon — via l'InputConnection de
     *  la vue éditeur. Le flick (variante) se traite dans le geste. */
    private void insererTouche(ModeleBarreSymboles.Touche touche) {
        if (touche.actions) {
            basculer(false);
            return;
        }
        String texte = ModeleBarreSymboles.insertionDe(touche);
        if (texte == null || auditeur == null) {
            return;
        }
        auditeur.inserer(texte);
    }

    /** Affiche/masque la barre (l'écran la branche sur l'apparition
     *  du clavier — WindowInsets du cadre, pas d'AndroidX) : la vue
     *  ENTIÈRE disparaît (jamais une bande vide de 42 dp). */
    public void poserVisible(boolean visible) {
        setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
