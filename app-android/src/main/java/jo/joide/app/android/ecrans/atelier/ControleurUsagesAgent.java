package jo.joide.app.android.ecrans.atelier;

import android.view.LayoutInflater;
import android.view.View;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.databinding.ItemAssiseUsagesBinding;
import jo.joide.app.android.databinding.PopupUsagesBinding;

/**
 * Lot A7 (ADR-085, découpage de {@code ControleurAgent}) — le
 * contrôleur des USAGES de l'agent : un COMPTEUR par fenêtre plafonnée
 * (requêtes, jetons), sa bande colorée et son pourcentage, puis les
 * trois pieds de la conversation et l'état du banc de clés (lot T,
 * ADR-065 — la manière CodeAssist). Le popup porte son bouton
 * d'actualisation — l'occupation se relit sans rouvrir. Corps dans
 * {@code popup_usages.xml} gonflé par binding : aucune vue du cadre
 * n'est instanciée ici.
 */
final class ControleurUsagesAgent {

    private final HoteAtelier hote;

    ControleurUsagesAgent(HoteAtelier hote) {
        this.hote = hote;
    }

    /**
     * L'écran Usages (centré avec voile au lot A7, décision 12) :
     * le popup se suffit (Actualiser, Fermeture), jamais un seul OK.
     */
    void ouvrir() {
        if (hote.agent() == null || hote.vueAgent() == null) {
            return;
        }
        PopupUsagesBinding corps = PopupUsagesBinding.inflate(
                LayoutInflater.from(hote.contexte()));
        corps.titreUsages.setText(R.string.agent_usages_titre);
        corps.rafraichirUsages.setOnClickListener(vue -> construire(corps));
        corps.fermerUsages.setOnClickListener(
                vue -> hote.popups().fermer());
        construire(corps);
        hote.popups().centrer(corps.getRoot(),
                hote.texte(R.string.agent_usages_titre));
    }

    /** Le corps de l'écran Usages : compteurs, pieds, banc. */
    private void construire(PopupUsagesBinding corps) {
        // ---- les compteurs du régulateur (l'occupation des fenêtres
        // du modèle courant — la bande prévient AVANT le 429) ----
        double[] occupations = hote.agent().occupationsModele();
        boolean plafondConnu = occupations[0] > 0 || occupations[1] > 0;
        // les compteurs se montrent PLEINS ou pas du tout : sans plafond
        // appris, deux lignes vides ne s'affichent jamais — l'écran le
        // dit honnêtement à la place (reprise d'audit : le libellé et
        // la visibilité avaient été perdus à la migration)
        corps.compteurRequetesUsages.getRoot().setVisibility(
                plafondConnu ? View.VISIBLE : View.GONE);
        corps.compteurJetonsUsages.getRoot().setVisibility(
                plafondConnu ? View.VISIBLE : View.GONE);
        corps.sansPlafondUsages.setVisibility(
                plafondConnu ? View.GONE : View.VISIBLE);
        if (!plafondConnu) {
            corps.sansPlafondUsages.setText(hote.texte(
                    R.string.agent_usages_sans_plafond));
        }
        if (plafondConnu) {
            poserCompteur(corps.compteurRequetesUsages,
                    hote.texte(R.string.agent_usages_minute_requetes),
                    occupations[0]);
            poserCompteur(corps.compteurJetonsUsages,
                    hote.texte(R.string.agent_usages_minute_jetons),
                    occupations[1]);
        }

        // ---- les trois pieds de la conversation (ce qui est facturé
        // est facturé — la légende compacte du précédent CodeAssist) ----
        jo.joide.agent.UsageLlm usage = hote.agent().usageConversation();
        corps.conversationUsages.setText(hote.texte(
                R.string.agent_usages_conversation_compacte,
                EtatAgentChat.jetonsCompacts(usage.entrees),
                EtatAgentChat.jetonsCompacts(usage.sorties),
                EtatAgentChat.jetonsCompacts(usage.lecturesCache)));

        // ---- l'état du banc de clés ----
        int taille = hote.agent().tailleBanc();
        corps.bancUsages.setText(taille == 0
                ? hote.texte(R.string.agent_usages_banc_vide)
                : hote.texte(R.string.agent_usages_banc, taille));

        corps.assisesUsages.removeAllViews();
        LayoutInflater gonfleur = LayoutInflater.from(hote.contexte());
        for (var assise : hote.agent().bancParRaison().entrySet()) {
            ItemAssiseUsagesBinding ligne = ItemAssiseUsagesBinding
                    .inflate(gonfleur, corps.assisesUsages, true);
            ligne.assiseUsages.setText(hote.texte(
                    R.string.agent_usages_assis,
                    assise.getValue(), libelleGenre(assise.getKey())));
        }
    }

    /**
     * Un compteur d'occupation : le pourcentage, la bande dont le
     * REMPLISSAGE suit la fraction et la teinte de la bande (accent,
     * alerte, erreur — BandesUsage, ADR-062 : l'alerte précède le
     * 429 ; couleur calculée par ligne, décision 8).
     */
    private void poserCompteur(
            jo.joide.app.android.databinding.ItemCompteurUsagesBinding
                    compteur, String nom, double fraction) {
        compteur.nomCompteur.setText(nom);
        compteur.pourcentageCompteur.setText(hote.texte(
                R.string.agent_usages_occupation,
                (int) Math.round(fraction * 100)));
        float rempli = (float) Math.max(0.02, Math.min(1, fraction));
        android.widget.LinearLayout.LayoutParams paramsRemplissage =
                (android.widget.LinearLayout.LayoutParams)
                        compteur.remplissageCompteur.getLayoutParams();
        paramsRemplissage.weight = rempli;
        compteur.remplissageCompteur.setLayoutParams(paramsRemplissage);
        android.widget.LinearLayout.LayoutParams paramsReste =
                (android.widget.LinearLayout.LayoutParams)
                        compteur.resteCompteur.getLayoutParams();
        paramsReste.weight = 1f - rempli;
        compteur.resteCompteur.setLayoutParams(paramsReste);
        // la teinte dit la bande (accent, alerte, erreur)
        jo.joide.agent.BandesUsage.Bande bande =
                jo.joide.agent.BandesUsage.bandeDe(fraction);
        int couleur = switch (bande) {
            case CRITIQUE -> hote.palette().erreur;
            case ATTENTION -> hote.palette().alerte;
            default -> hote.palette().accent;
        };
        compteur.remplissageCompteur.getBackground().mutate()
                .setTint(couleur);
    }

    /** Le libellé localisé d'un genre d'assise du banc. */
    private CharSequence libelleGenre(
            jo.joide.agent.ErreursLlm.Genre genre) {
        return hote.texte(switch (genre) {
            case LIMITE_DEBIT -> R.string.agent_genre_limite_debit;
            case LIMITE_JOUR -> R.string.agent_genre_limite_jour;
            case QUOTA_EPUISE -> R.string.agent_genre_quota;
            case CLE_INVALIDE -> R.string.agent_genre_cle_invalide;
            case CLE_SANS_DROIT -> R.string.agent_genre_cle_sans_droit;
            default -> R.string.agent_genre_autre;
        });
    }
}
