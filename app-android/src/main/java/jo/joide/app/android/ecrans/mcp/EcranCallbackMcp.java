package jo.joide.app.android.ecrans.mcp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import jo.joide.app.android.AgentService;
import jo.joide.app.android.R;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.choix.ChoixBouton;

/**
 * L'écran de rappel OAuth MCP (lot U, ADR-066) : le navigateur
 * revient à {@code joide://mcp/oauth} — CET écran transparent
 * récupère le code et l'état, les porte au service de l'agent, et
 * raconte HONNÊTEMENT ce qui se passe : « signé » ou le motif de
 * l'échec, jamais un silence. L'atelier reste derrière ; un bouton
 * rend la main.
 */
public final class EcranCallbackMcp extends Activity {

    private static final String PREFIXE_SUCCES = "signed-in:";
    private static final String PREFIXE_ECHEC = "failed:";

    private AgentService.AgentBinder agent;
    private TextView statut;
    private Bouton bouton;
    private int essaisLiaison;

    private final ServiceConnection connexion =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName nom,
                                               IBinder service) {
                    agent = (AgentService.AgentBinder) service;
                    completer();
                }

                @Override
                public void onServiceDisconnected(
                        ComponentName nom) {
                    agent = null;
                }
            };

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        Graphite.Palette palette = GraphiteVue.palette(this);

        FrameLayout racine = new FrameLayout(this);
        racine.setBackgroundColor(Graphite.melange(
                palette.texte, 0.35f, palette.fond));

        LinearLayout carte = new LinearLayout(this);
        carte.setOrientation(LinearLayout.VERTICAL);
        int rayon = GraphiteVue.dp(this, Graphite.RAYON_CARTE);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(rayon);
        fond.setStroke(GraphiteVue.dp(this, 1), palette.bordFin);
        carte.setBackground(fond);
        int marge = GraphiteVue.dp(this, 24);
        FrameLayout.LayoutParams paramsCarte = new FrameLayout
                .LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        paramsCarte.setMargins(marge, marge, marge, marge);
        paramsCarte.gravity = Gravity.CENTER;
        racine.addView(carte, paramsCarte);

        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.mcp_rappel_titre);
        titre.setPadding(marge, marge, marge, 0);
        carte.addView(titre);

        statut = new TextView(this);
        GraphiteVue.typographie(statut, Graphite.CORPS);
        statut.setTextColor(palette.texteSecondaire);
        statut.setText(R.string.mcp_rappel_en_cours);
        int vertical = GraphiteVue.dp(this, 14);
        statut.setPadding(marge, vertical, marge, vertical);
        carte.addView(statut);

        bouton = new Bouton(this);
        bouton.setTexte(getString(R.string.action_ok));
        bouton.setVariante(ChoixBouton.Variante.TONAL);
        int bas = GraphiteVue.dp(this, 16);
        LinearLayout.LayoutParams paramsBouton = new LinearLayout
                .LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsBouton.setMargins(marge, 0, marge, bas);
        bouton.setOnClickListener(vue -> finish());
        bouton.setVisibility(View.GONE);
        carte.addView(bouton, paramsBouton);

        setContentView(racine);
        // le service de l'agent est la SEULE route : il porte le
        // gestionnaire MCP et le flux de signature vivant
        startService(new Intent(this, AgentService.class));
        bindService(new Intent(this, AgentService.class), connexion,
                Context.BIND_AUTO_CREATE);
    }

    /** Le code et l'état partent au service ; le résultat s'affiche. */
    private void completer() {
        Uri donnees = getIntent() == null ? null
                : getIntent().getData();
        if (donnees == null) {
            raconter(getString(R.string.mcp_rappel_illusion));
            return;
        }
        if (agent == null) {
            // service pas encore lié : le lien revient après la
            // connexion — jamais un rappel perdu en silence, mais
            // un service mort n'attend pas éternellement
            if (++essaisLiaison > 50) {
                raconter(getString(R.string.mcp_rappel_service));
                return;
            }
            statut.postDelayed(this::completer, 200L);
            return;
        }
        statut.setText(R.string.mcp_rappel_echange);
        agent.completerOAuthMcp(donnees.toString(), message -> {
            if (isFinishing()) {
                return;
            }
            runOnUiThread(() -> {
                if (message.startsWith(PREFIXE_SUCCES)) {
                    raconter(getString(R.string.mcp_rappel_signe,
                            message.substring(PREFIXE_SUCCES.length())));
                } else if (message.startsWith(PREFIXE_ECHEC)) {
                    raconter(getString(R.string.mcp_rappel_echec,
                            message.substring(PREFIXE_ECHEC.length())));
                } else {
                    raconter(message);
                }
            });
        });
    }

    /** Pose le texte final et montre le bouton de sortie. */
    private void raconter(CharSequence texte) {
        statut.setText(texte);
        bouton.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (agent != null) {
            unbindService(connexion);
            agent = null;
        }
        super.onDestroy();
    }
}
