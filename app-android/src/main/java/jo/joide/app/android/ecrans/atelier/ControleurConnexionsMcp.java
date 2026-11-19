package jo.joide.app.android.ecrans.atelier;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jo.joide.agent.mcp.ConfigServeurMcp;
import jo.joide.agent.mcp.GestionnaireMcp;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ItemServeurConnexionsBinding;
import jo.joide.app.android.databinding.PopupConnexionsMcpBinding;

/**
 * Lot A7 (ADR-085, découpage de {@code ControleurAgent}) — le
 * contrôleur des CONNEXIONS MCP : l'écran Connexions câblant
 * l'ADR-063 (les serveurs déclarés par le projet, leur état publié,
 * le geste d'approbation qui seul connecte — un dépôt cloné n'est pas
 * un choix de l'utilisateur), la lecture bornée du {@code .mcp.json}
 * et la clé d'approbation PERSISTÉE par projet (lot T, ADR-065, liée
 * à la signature du fichier). Le popup vit dans
 * {@code popup_connexions_mcp.xml} gonflé par binding : aucune vue
 * du cadre n'est instanciée ici.
 */
final class ControleurConnexionsMcp {

    private final HoteAtelier hote;

    ControleurConnexionsMcp(HoteAtelier hote) {
        this.hote = hote;
    }

    /**
     * L'écran Connexions (centré avec voile au lot A7, décision 12).
     */
    void ouvrir() {
        if (hote.agent() == null) {
            return;
        }
        List<ConfigServeurMcp> configs = hote.agent().configsMcp();
        boolean approuve = hote.agent().approuveMcp();
        Map<String, GestionnaireMcp.EtatServeur> etats =
                new HashMap<>();
        for (var etat : hote.agent().etatsMcp()) {
            etats.put(etat.nom, etat);
        }

        PopupConnexionsMcpBinding corps =
                PopupConnexionsMcpBinding.inflate(
                        LayoutInflater.from(hote.contexte()));
        corps.titreConnexions.setText(R.string.agent_connexions_titre);
        corps.fermerConnexions.setOnClickListener(
                vue -> hote.popups().fermer());
        if (configs.isEmpty()) {
            // reprise d'audit : le libellé de l'état vide restait à
            // poser (la ligne se montrait muette)
            corps.videConnexions.setText(
                    R.string.agent_connexions_vide);
            corps.videConnexions.setVisibility(View.VISIBLE);
            corps.avertissementConnexions.setVisibility(View.GONE);
            corps.actionConnexions.setVisibility(View.GONE);
        } else {
            corps.avertissementConnexions.setText(approuve
                    ? R.string.agent_connexions_actives
                    : R.string.agent_connexions_avertissement);
            corps.actionConnexions.setText(approuve
                    ? R.string.agent_connexions_desactiver
                    : R.string.agent_connexions_activer);
            corps.actionConnexions.setOnClickListener(vue -> {
                if (hote.agent() != null) {
                    hote.agent().poserApprobationMcp(!approuve);
                }
                // la décision se souvient par projet (lot T), liée à
                // la signature du .mcp.json qui l'a motivée
                hote.sourceReglages().ecrire(cleApprobationMcp(),
                        !approuve ? "1" : "0");
                ouvrir();
            });
            if (approuve) {
                corps.compteOutilsConnexions.setVisibility(View.VISIBLE);
                corps.compteOutilsConnexions.setText(hote.texte(
                        R.string.agent_connexions_outils,
                        hote.agent().outilsMcp()));
            }
            LayoutInflater gonfleur = LayoutInflater.from(
                    hote.contexte());
            for (ConfigServeurMcp config : configs) {
                ItemServeurConnexionsBinding ligne =
                        ItemServeurConnexionsBinding.inflate(gonfleur,
                                corps.serveursConnexions, true);
                poserLigne(ligne, config, approuve,
                        etats.get(config.nom));
            }
        }
        hote.popups().centrer(corps.getRoot(),
                hote.texte(R.string.agent_connexions_titre));
    }

    /** Une ligne de serveur : nom, état, motif, reconnexion. */
    private void poserLigne(ItemServeurConnexionsBinding ligne,
                            ConfigServeurMcp config, boolean approuve,
                            GestionnaireMcp.EtatServeur etat) {
        ligne.nomServeur.setText(config.nom);
        CharSequence texteStatut = !approuve
                ? hote.texte(R.string.agent_connexions_non_active)
                : etat == null
                        ? hote.texte(R.string.agent_connexions_statut_connexion)
                        : libelleStatut(etat.statut)
                                + (etat.motif == null ? ""
                                        : " — " + etat.motif);
        ligne.statutServeur.setText(texteStatut);
        ligne.statutServeur.setTextColor(approuve && etat != null
                && etat.statut == GestionnaireMcp.Statut.ECHEC
                ? hote.palette().erreur : hote.palette().texteEstompe);
        if (approuve && etat != null
                && (etat.statut == GestionnaireMcp.Statut.ECHEC
                || etat.statut == GestionnaireMcp.Statut.AUTH_REQUISE)) {
            // lot U : la signature OAuth — le parcours MCP se
            // complète : découverte, enregistrement du client,
            // navigateur, rappel joide://mcp/oauth, jetons, connexion
            ligne.connecterServeur.setVisibility(View.VISIBLE);
            // reprise d'audit : les libellés d'hier restaient à poser
            // (les actions se montraient MUETTES)
            ligne.connecterServeur.setText(
                    R.string.agent_connexions_se_connecter);
            ligne.connecterServeur.setOnClickListener(vue -> {
                if (hote.agent() == null) {
                    return;
                }
                hote.agent().demanderOAuthMcp(config.nom,
                        url -> hote.executerSurFilUi(() -> {
                            try {
                                hote.ouvrirEcran(new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)));
                            } catch (Exception e) {
                                hote.popups().information(
                                        hote.texte(
                                                R.string.agent_connexions_titre),
                                        hote.texte(
                                                R.string.agent_connexions_oauth_echec),
                                        hote.texte(R.string.action_ok));
                            }
                        }),
                        erreur -> hote.executerSurFilUi(() ->
                                hote.popups().information(
                                        hote.texte(
                                                R.string.agent_connexions_titre),
                                        erreur,
                                        hote.texte(R.string.action_ok))));
            });
            ligne.reconnecterServeur.setVisibility(View.VISIBLE);
            ligne.reconnecterServeur.setText(
                    R.string.agent_connexions_reconnecter);
            ligne.reconnecterServeur.setOnClickListener(vue -> {
                if (hote.agent() != null) {
                    hote.agent().reconnecterMcp(config.nom);
                }
                ouvrir();
            });
        }
    }

    /** Le libellé localisé d'un statut MCP. */
    private CharSequence libelleStatut(GestionnaireMcp.Statut statut) {
        return hote.texte(switch (statut) {
            case CONNECTE -> R.string.agent_connexions_statut_connecte;
            case CONNEXION -> R.string.agent_connexions_statut_connexion;
            case AUTH_REQUISE -> R.string.agent_connexions_statut_auth;
            case ECHEC -> R.string.agent_connexions_statut_echec;
            case NON_SUPPORTE ->
                    R.string.agent_connexions_statut_non_supporte;
            default -> R.string.agent_connexions_statut_desactive;
        });
    }

    /**
     * Le {@code .mcp.json} du projet (lot S) : lu borné (64 Kio),
     * vide si absent ou illisible — jamais un crash d'ouverture.
     */
    String lireMcpJson() {
        if (hote.racineProjet() == null) {
            return "";
        }
        try {
            Path cible = hote.racineProjet().resolve(".mcp.json");
            if (!Files.isRegularFile(cible)) {
                return "";
            }
            byte[] octets = Files.readAllBytes(cible);
            if (octets.length > 65_536) {
                return "";
            }
            return new String(octets, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * La clé d'approbation MCP du projet (lot T) : liée à la
     * SIGNATURE du fichier (chemin + contenu passés au SHA-256) —
     * l'approbation vaut pour ce qui a été approuvé, un serveur
     * ajouté ou modifié redemande le geste explicite. La clé ne
     * RENOMME pas : « atelier.agent.mcp. » est un identifiant de
     * STOCKAGE (lot T, ADR-065), pas un champ d'écran — le
     * refactoring ne doit jamais perdre les approbations déjà
     * persistées.
     */
    String cleApprobationMcp() {
        String chemin = hote.racineProjet() == null ? ""
                : hote.racineProjet().resolve(".mcp.json").toString();
        try {
            MessageDigest empreinte =
                    MessageDigest.getInstance("SHA-256");
            byte[] octets = empreinte.digest((chemin + "\n"
                    + lireMcpJson()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(
                    "atelier.agent.mcp.approuve.");
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", octets[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 manque (impossible sur Android) : la clé retombe
            // sur le chemin seul — la persistance reste par projet
            return "atelier.agent.mcp.approuve."
                    + Integer.toHexString(chemin.hashCode());
        }
    }
}
