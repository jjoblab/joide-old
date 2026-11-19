package jo.joide.app.android.ecrans.mcp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import com.google.android.material.button.MaterialButton;

import jo.joide.app.android.AgentService;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ActivityCallbackMcpBinding;
import jo.joide.app.android.ui.ActiviteJoide;

/**
 * L'écran de rappel OAuth MCP (lot U, ADR-066 ; racine XML + Material au
 * lot A2 de l'ADR-085) : le navigateur revient à
 * {@code joide://mcp/oauth} — CET écran récupère le code et l'état, les
 * porte au service de l'agent, et raconte HONNÊTEMENT ce qui se passe :
 * « signé » ou le motif de l'échec, jamais un silence. L'atelier reste
 * derrière ; un bouton rend la main.
 *
 * <p>Coque mince (ADR-085 décision 2) : la hiérarchie vit dans
 * {@code activity_callback_mcp.xml} (MaterialCardView + MaterialButton
 * tonal habillés Graphite), le montage passe par
 * {@link ActiviteJoide#monter(Function)}, aucune vue n'est instanciée en
 * code — seule la liaison du service et le récit restent ici.</p>
 */
public final class EcranCallbackMcp extends ActiviteJoide {

    private static final String PREFIXE_SUCCES = "signed-in:";
    private static final String PREFIXE_ECHEC = "failed:";

    private AgentService.AgentBinder agent;
    private ActivityCallbackMcpBinding ecran;
    private MaterialButton bouton;
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
        ecran = monter(ActivityCallbackMcpBinding::inflate);
        bouton = ecran.boutonRappel;
        bouton.setOnClickListener(vue -> finish());
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
            ecran.statutRappel.postDelayed(this::completer, 200L);
            return;
        }
        ecran.statutRappel.setText(R.string.mcp_rappel_echange);
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
        ecran.statutRappel.setText(texte);
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
