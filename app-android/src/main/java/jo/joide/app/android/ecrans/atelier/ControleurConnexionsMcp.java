package jo.joide.app.android.ecrans.atelier;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jo.joide.agent.mcp.ConfigServeurMcp;
import jo.joide.agent.mcp.GestionnaireMcp;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ItemCompetenceConnexionsBinding;
import jo.joide.app.android.databinding.ItemServeurConnexionsBinding;
import jo.joide.app.android.databinding.PopupConnexionsMcpBinding;
import jo.joide.app.android.databinding.PopupEditeurServeurBinding;
import jo.joide.app.android.databinding.PopupNouvelleCompetenceBinding;

/**
 * Lot C6 — le contrôleur des CONNEXIONS au dessin de référence :
 * l'écran vit en deux sections. LES SERVEURS MCP : ceux du projet
 * (le {@code .mcp.json}, le geste d'approbation ADR-063 — un dépôt
 * cloné n'est pas un choix) et ceux ajoutés à la main par leur URL
 * (l'éditeur : nom, portée, type HTTP/SSE/STDIO, en-têtes ou
 * commande/arguments/environnement — la portée projet écrit dans le
 * {@code .mcp.json}, la portée tous-les-projets vit dans les
 * réglages). LES COMPÉTENCES : les dossiers à SKILL.md de l'app et
 * du projet, chacun activable, la nouvelle compétence naît avec son
 * squelette — le bloc des compétences actives rejoint le contexte
 * du tour.
 *
 * <p>Persisté : les serveurs manuels sous
 * {@code atelier.agent.mcp.serveurs} (même grammaire que le
 * .mcp.json), les compétences éteintes sous
 * {@code atelier.agent.competences.eteintes}. L'approbation du
 * projet reste liée à la SIGNATURE de son .mcp.json (lot T,
 * ADR-065).</p>
 */
final class ControleurConnexionsMcp {

    private final HoteAtelier hote;

    /** La clé des serveurs manuels (grammaire .mcp.json). */
    static final String CLE_SERVEURS_MANUELS =
            "atelier.agent.mcp.serveurs";
    /** La clé des compétences éteintes (préfixe app:/projet:). */
    static final String CLE_COMPETENCES_ETEINTES =
            "atelier.agent.competences.eteintes";

    /** Le plafond des octets lus par SKILL.md (8 Kio, borné). */
    private static final int PLAFOND_SKILL = 8192;

    /** Une compétence découverte sur l'appareil. */
    private record Competence(String nom, String portee,
                              String description, boolean eteinte) {
    }

    ControleurConnexionsMcp(HoteAtelier hote) {
        this.hote = hote;
    }

    /** L'écran Connexions (centré avec voile, lot A7 décision 12). */
    void ouvrir() {
        if (hote.agent() == null) {
            return;
        }
        // les serveurs manuels et les compétences partent AU SERVICE
        // à l'ouverture — l'écran montre ce qui est appliqué
        poserServeursManuels();
        poserCompetences();

        PopupConnexionsMcpBinding corps =
                PopupConnexionsMcpBinding.inflate(
                        LayoutInflater.from(hote.contexte()));
        corps.titreConnexions.setText(R.string.agent_connexions_titre);
        corps.fermerConnexions.setOnClickListener(
                vue -> hote.popups().fermer());

        List<ConfigServeurMcp> projet = GestionnaireMcp.lireFichier(
                lireMcpJson());
        boolean approuve = hote.agent().approuveMcp();
        if (!projet.isEmpty()) {
            // le geste d'approbation : seul choix de l'utilisateur
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
        } else {
            corps.avertissementConnexions.setVisibility(View.GONE);
            corps.actionConnexions.setVisibility(View.GONE);
        }

        Map<String, GestionnaireMcp.EtatServeur> etats =
                new HashMap<>();
        for (GestionnaireMcp.EtatServeur etat : hote.agent().etatsMcp()) {
            etats.put(etat.nom, etat);
        }
        LayoutInflater gonfleur = LayoutInflater.from(hote.contexte());
        boolean serveursPoses = false;
        for (ConfigServeurMcp config : projet) {
            serveursPoses = true;
            poserLigne(corps.serveursConnexions, gonfleur, config,
                    true, !approuve, etats.get(config.nom));
        }
        for (ConfigServeurMcp config : serveursManuels()) {
            serveursPoses = true;
            poserLigne(corps.serveursConnexions, gonfleur, config,
                    false, config.desactive, etats.get(config.nom));
        }
        corps.videServeursConnexions.setText(
                serveursPoses ? ""
                        : hote.texte(
                        R.string.agent_connexions_serveurs_vide));
        corps.videServeursConnexions.setVisibility(
                serveursPoses ? View.GONE : View.VISIBLE);
        corps.actionAjouterServeurConnexions.setOnClickListener(
                vue -> editerServeur(null));

        // ---- les compétences : l'app et le projet ----
        List<Competence> competences = competences();
        boolean competencesPosees = false;
        for (Competence competence : competences) {
            competencesPosees = true;
            poserCompetence(corps.competencesConnexions, gonfleur,
                    competence);
        }
        corps.videCompetencesConnexions.setText(
                competencesPosees ? ""
                        : hote.texte(
                        R.string.agent_connexions_competences_vide));
        corps.videCompetencesConnexions.setVisibility(
                competencesPosees ? View.GONE : View.VISIBLE);
        corps.actionNouvelleCompetenceConnexions.setOnClickListener(
                vue -> nouvelleCompetence());
        List<String> dossiers = dossiersCompetences();
        if (!dossiers.isEmpty()) {
            StringBuilder texte = new StringBuilder(hote.texte(
                    R.string.agent_connexions_dossiers_competences));
            for (String dossier : dossiers) {
                texte.append('\n').append(dossier);
            }
            corps.dossiersCompetencesConnexions.setText(texte);
            corps.dossiersCompetencesConnexions.setVisibility(
                    View.VISIBLE);
        }
        hote.popups().centrer(corps.getRoot(),
                hote.texte(R.string.agent_connexions_titre));
    }

    /**
     * Une carte de serveur au dessin de référence : le point d'état,
     * le nom, la pilule de portée, l'interrupteur (les serveurs
     * manuels vivent ; ceux du projet suivent l'approbation), la
     * cible en monospace, l'état publié, les actions.
     */
    private void poserLigne(LinearLayout conteneur,
                            LayoutInflater gonfleur,
                            ConfigServeurMcp config, boolean duProjet,
                            boolean eteint,
                            GestionnaireMcp.EtatServeur etat) {
        ItemServeurConnexionsBinding ligne =
                ItemServeurConnexionsBinding.inflate(gonfleur,
                        conteneur, true);
        ligne.nomServeur.setText(config.nom);
        ligne.porteeServeur.setText(duProjet
                ? R.string.agent_connexions_portee_projet
                : R.string.agent_connexions_portee_app);
        ligne.cibleServeur.setText(cible(config));
        GestionnaireMcp.Statut statutVu = duProjet
                ? (eteint ? GestionnaireMcp.Statut.DESACTIVE
                        : etat == null ? GestionnaireMcp.Statut.CONNEXION
                        : etat.statut)
                : etat == null ? GestionnaireMcp.Statut.CONNEXION
                        : etat.statut;
        teintePoint(ligne.pointServeur, statutVu);
        CharSequence texteStatut = duProjet && eteint
                ? hote.texte(R.string.agent_connexions_non_active)
                : etat == null
                        ? hote.texte(
                                R.string.agent_connexions_statut_connexion)
                        : libelleStatut(etat.statut)
                                + (etat.motif == null ? ""
                                        : " — " + etat.motif);
        ligne.statutServeur.setText(texteStatut);
        ligne.statutServeur.setTextColor(approuveEtEchec(duProjet, etat)
                ? hote.palette().erreur : hote.palette().texte);
        if (!duProjet) {
            // l'interrupteur d'un serveur manuel : le JSON se réécrit,
            // le service réapplique — le geste se sauvegarde
            ligne.interrupteurServeur.setChecked(!config.desactive);
            ligne.interrupteurServeur.setOnCheckedChangeListener(
                    (bouton, coche) -> basculerServeurManuel(
                            config.nom, !coche));
        } else {
            ligne.interrupteurServeur.setVisibility(View.GONE);
        }
        if (!duProjet && etat != null
                && (etat.statut == GestionnaireMcp.Statut.ECHEC
                || etat.statut == GestionnaireMcp.Statut.AUTH_REQUISE)) {
            // lot U : la signature OAuth — le parcours MCP complet
            ligne.connecterServeur.setVisibility(View.VISIBLE);
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
        ligne.modifierServeur.setOnClickListener(
                vue -> editerServeur(config));
        ligne.retirerServeur.setOnClickListener(vue -> {
            if (duProjet) {
                retirerServeurProjet(config.nom);
            } else {
                retirerServeurManuel(config.nom);
            }
        });
    }

    /** La cible d'un serveur : l'URL, ou la commande et ses arguments. */
    private String cible(ConfigServeurMcp config) {
        if (config.transport == ConfigServeurMcp.Transport.STDIO) {
            return (config.commande == null ? ""
                    : config.commande)
                    + (config.arguments == null ? ""
                    : " " + String.join(" ", config.arguments));
        }
        return config.url == null ? "" : config.url;
    }

    /** La teinte du point d'état (réussite, alerte, erreur, estompé). */
    private void teintePoint(ImageView point,
                             GestionnaireMcp.Statut statut) {
        int couleur = hote.palette().texteEstompe;
        if (statut == GestionnaireMcp.Statut.CONNECTE) {
            couleur = hote.palette().reussite;
        } else if (statut == GestionnaireMcp.Statut.AUTH_REQUISE) {
            couleur = hote.palette().alerte;
        } else if (statut == GestionnaireMcp.Statut.ECHEC) {
            couleur = hote.palette().erreur;
        }
        point.setColorFilter(couleur);
    }

    private boolean approuveEtEchec(boolean duProjet,
                                     GestionnaireMcp.EtatServeur etat) {
        return !duProjet && etat != null
                && etat.statut == GestionnaireMcp.Statut.ECHEC;
    }

    // ------------------------------------------------------------------
    // L'éditeur de serveur : ajouter (config nulle) ou modifier
    // ------------------------------------------------------------------

    /** L'éditeur au dessin de référence : nom, portée, type, cible. */
    private void editerServeur(ConfigServeurMcp existant) {
        PopupEditeurServeurBinding corps =
                PopupEditeurServeurBinding.inflate(
                        LayoutInflater.from(hote.contexte()));
        corps.titreEditeurServeur.setText(existant == null
                ? R.string.agent_connexions_ajout_titre
                : R.string.agent_connexions_edition_titre);
        corps.fermerEditeurServeur.setOnClickListener(
                vue -> hote.popups().fermer());
        corps.annulerEditeurServeur.setOnClickListener(
                vue -> hote.popups().fermer());
        boolean[] porteeProjet = {existant != null && existantDuProjet(
                existant.nom)};
        if (existant != null && existantDuProjet(existant.nom)) {
            corps.porteeProjetServeur.setTag(Boolean.TRUE);
        }
        corps.champNomServeur.setText(existant == null ? ""
                : existant.nom);
        ConfigServeurMcp.Transport type = existant == null
                ? ConfigServeurMcp.Transport.HTTP
                : existant.transport;
        corps.champUrlServeur.setText(existant == null
                || existant.url == null ? "" : existant.url);
        corps.champEntetesServeur.setText(existant == null
                ? "" : paires(existant.entetes, ':'));
        corps.champCommandeServeur.setText(existant == null
                || existant.commande == null ? ""
                : existant.commande);
        corps.champArgumentsServeur.setText(existant == null
                || existant.arguments == null ? ""
                : String.join("\n", existant.arguments));
        corps.champEnvironnementServeur.setText(existant == null
                ? "" : paires(existant.environnement, '='));
        final ConfigServeurMcp.Transport[] choisi = {type};
        majTypeServeur(corps, choisi);
        corps.typeHttpServeur.setOnClickListener(vue -> {
            choisi[0] = ConfigServeurMcp.Transport.HTTP;
            majTypeServeur(corps, choisi);
        });
        corps.typeSseServeur.setOnClickListener(vue -> {
            choisi[0] = ConfigServeurMcp.Transport.SSE;
            majTypeServeur(corps, choisi);
        });
        corps.typeStdioServeur.setOnClickListener(vue -> {
            choisi[0] = ConfigServeurMcp.Transport.STDIO;
            majTypeServeur(corps, choisi);
        });
        corps.porteeAppServeur.setOnClickListener(vue -> {
            porteeProjet[0] = false;
            majPorteeServeur(corps, porteeProjet[0]);
        });
        corps.porteeProjetServeur.setOnClickListener(vue -> {
            porteeProjet[0] = true;
            majPorteeServeur(corps, porteeProjet[0]);
        });
        majPorteeServeur(corps, porteeProjet[0]);
        corps.enregistrerServeur.setOnClickListener(vue -> {
            String nom = corps.champNomServeur.getText().toString()
                    .trim();
            String erreur = null;
            if (nom.isEmpty()) {
                erreur = hote.texte(R.string.agent_connexions_erreur_nom);
            } else if (choisi[0]
                    == ConfigServeurMcp.Transport.STDIO
                    && corps.champCommandeServeur.getText().toString()
                    .trim().isEmpty()) {
                erreur = hote.texte(
                        R.string.agent_connexions_erreur_commande);
            } else if (choisi[0]
                    != ConfigServeurMcp.Transport.STDIO) {
                String url = corps.champUrlServeur.getText()
                        .toString().trim();
                if (!url.startsWith("http://")
                        && !url.startsWith("https://")) {
                    erreur = hote.texte(
                            R.string.agent_connexions_erreur_url);
                }
            }
            if (erreur != null) {
                corps.erreurEditeurServeur.setText(erreur);
                corps.erreurEditeurServeur.setVisibility(View.VISIBLE);
                return;
            }
            ConfigServeurMcp config = new ConfigServeurMcp(nom,
                    choisi[0],
                    choisi[0] == ConfigServeurMcp.Transport.STDIO
                            ? null
                            : corps.champUrlServeur.getText()
                            .toString().trim(),
                    paires(corps.champEntetesServeur.getText()
                            .toString(), ':'),
                    choisi[0] == ConfigServeurMcp.Transport.STDIO
                            ? corps.champCommandeServeur.getText()
                            .toString().trim()
                            .isEmpty() ? null
                            : corps.champCommandeServeur.getText()
                            .toString().trim()
                            : null,
                    lignes(corps.champArgumentsServeur.getText()
                            .toString()),
                    paires(corps.champEnvironnementServeur.getText()
                            .toString(), '='),
                    false);
            if (porteeProjet[0]) {
                ecrireServeurProjet(config, existant == null ? ""
                        : existant.nom);
            } else {
                ecrireServeurManuel(config, existant == null
                        || existantDuProjet(existant.nom) ? ""
                        : existant.nom);
            }
            hote.popups().fermer();
            ouvrir();
        });
        hote.popups().centrer(corps.getRoot(), hote.texte(
                R.string.agent_connexions_ajout_titre));
    }

    /** La vue des pilules de type (la choisie porte l'accent). */
    private void majTypeServeur(PopupEditeurServeurBinding corps,
                                ConfigServeurMcp.Transport[] choisi) {
        boolean stdio = choisi[0]
                == ConfigServeurMcp.Transport.STDIO;
        corps.libelleUrlServeur.setVisibility(stdio ? View.GONE
                : View.VISIBLE);
        corps.champUrlServeur.setVisibility(stdio ? View.GONE
                : View.VISIBLE);
        corps.libelleEntetesServeur.setVisibility(stdio ? View.GONE
                : View.VISIBLE);
        corps.champEntetesServeur.setVisibility(stdio ? View.GONE
                : View.VISIBLE);
        corps.indiceEntetesServeur.setVisibility(stdio ? View.GONE
                : View.VISIBLE);
        corps.libelleCommandeServeur.setVisibility(stdio ? View.VISIBLE
                : View.GONE);
        corps.champCommandeServeur.setVisibility(stdio ? View.VISIBLE
                : View.GONE);
        corps.libelleArgumentsServeur.setVisibility(stdio
                ? View.VISIBLE : View.GONE);
        corps.champArgumentsServeur.setVisibility(stdio ? View.VISIBLE
                : View.GONE);
        corps.libelleEnvironnementServeur.setVisibility(stdio
                ? View.VISIBLE : View.GONE);
        corps.champEnvironnementServeur.setVisibility(stdio
                ? View.VISIBLE : View.GONE);
        teintePilule(corps.typeHttpServeur, choisi[0]
                == ConfigServeurMcp.Transport.HTTP);
        teintePilule(corps.typeSseServeur, choisi[0]
                == ConfigServeurMcp.Transport.SSE);
        teintePilule(corps.typeStdioServeur, stdio);
    }

    /** La vue des pilules de portée (la choisie porte l'accent). */
    private void majPorteeServeur(PopupEditeurServeurBinding corps,
                                   boolean projet) {
        teintePilule(corps.porteeAppServeur, !projet);
        teintePilule(corps.porteeProjetServeur, projet);
    }

    /** La pilule choisie : accent plein ; l'autre : texte estompé. */
    private void teintePilule(TextView pilule, boolean choisi) {
        pilule.setTextColor(choisi ? hote.palette().accent
                : hote.palette().texteEstompe);
    }

    // ------------------------------------------------------------------
    // Les serveurs manuels : le JSON des réglages (grammaire .mcp.json)
    // ------------------------------------------------------------------

    /** Le JSON brut des serveurs manuels (vide si absent). */
    private String jsonServeursManuels() {
        return hote.sourceReglages().lire(CLE_SERVEURS_MANUELS);
    }

    /** Les serveurs manuels parsés, dans l'ordre posé. */
    private List<ConfigServeurMcp> serveursManuels() {
        return GestionnaireMcp.lireFichier(jsonServeursManuels());
    }

    /** Un nom du .mcp.json du projet ? */
    private boolean existantDuProjet(String nom) {
        for (ConfigServeurMcp config : GestionnaireMcp.lireFichier(
                lireMcpJson())) {
            if (config.nom.equals(nom)) {
                return true;
            }
        }
        return false;
    }

    /** Pousse les serveurs manuels au service (sans approbation). */
    void poserServeursManuels() {
        if (hote.agent() != null) {
            hote.agent().poserServeursManuels(jsonServeursManuels());
        }
    }

    /** La bascule d'un serveur manuel : le JSON se réécrit, on réapplique. */
    private void basculerServeurManuel(String nom, boolean eteint) {
        List<ConfigServeurMcp> serveurs = serveursManuels();
        List<ConfigServeurMcp> nouveaux = new ArrayList<>();
        for (ConfigServeurMcp config : serveurs) {
            nouveaux.add(config.nom.equals(nom)
                    ? new ConfigServeurMcp(config.nom,
                    config.transport, config.url, config.entetes,
                    config.commande, config.arguments,
                    config.environnement, eteint)
                    : config);
        }
        hote.sourceReglages().ecrire(CLE_SERVEURS_MANUELS,
                ecrireJsonServeurs(nouveaux));
        poserServeursManuels();
        ouvrir();
    }

    /** Retire un serveur manuel : le JSON se réécrit, on réapplique. */
    private void retirerServeurManuel(String nom) {
        List<ConfigServeurMcp> nouveaux = new ArrayList<>();
        for (ConfigServeurMcp config : serveursManuels()) {
            if (!config.nom.equals(nom)) {
                nouveaux.add(config);
            }
        }
        hote.sourceReglages().ecrire(CLE_SERVEURS_MANUELS,
                ecrireJsonServeurs(nouveaux));
        poserServeursManuels();
        ouvrir();
    }

    /** Pose ou remplace un serveur manuel (le nom originel permet le
     *  renommage) ; le JSON se réécrit, on réapplique. */
    private void ecrireServeurManuel(ConfigServeurMcp config,
                                      String nomOriginel) {
        List<ConfigServeurMcp> nouveaux = new ArrayList<>();
        boolean remplace = false;
        for (ConfigServeurMcp existant : serveursManuels()) {
            if (!nomOriginel.isEmpty()
                    && existant.nom.equals(nomOriginel)) {
                nouveaux.add(config);
                remplace = true;
            } else {
                nouveaux.add(existant);
            }
        }
        if (!remplace) {
            nouveaux.add(config);
        }
        hote.sourceReglages().ecrire(CLE_SERVEURS_MANUELS,
                ecrireJsonServeurs(nouveaux));
        poserServeursManuels();
    }

    /**
     * Pose ou remplace un serveur DANS LE .mcp.json DU PROJET (la
     * portée du dessin de référence) : le fichier reste lisible par
     * les autres outils, borné à 64 Kio, créé avec son squelette s'il
     * n'existe pas — l'approbation se redemandera (ADR-065, la
     * signature change).
     */
    private void ecrireServeurProjet(ConfigServeurMcp config,
                                     String nomOriginel) {
        if (hote.racineProjet() == null) {
            ecrireServeurManuel(config, nomOriginel);
            return;
        }
        List<ConfigServeurMcp> serveurs = new ArrayList<>();
        for (ConfigServeurMcp existant : GestionnaireMcp.lireFichier(
                lireMcpJson())) {
            if (!nomOriginel.isEmpty()
                    && existant.nom.equals(nomOriginel)) {
                serveurs.add(config);
            } else {
                serveurs.add(existant);
            }
        }
        if (serveurs.stream().noneMatch(
                s -> s.nom.equals(config.nom))) {
            serveurs.add(config);
        }
        try {
            Path cible = hote.racineProjet().resolve(".mcp.json");
            Files.writeString(cible, ecrireJsonServeurs(serveurs),
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // le projet refusait l'écriture : le serveur part quand
            // même, en portée tous-les-projets — jamais perdu en
            // silence
            ecrireServeurManuel(config, nomOriginel);
        }
        if (hote.agent() != null) {
            hote.agent().poserMcpJson(lireMcpJson());
        }
    }

    /** Retire un serveur du .mcp.json du projet. */
    private void retirerServeurProjet(String nom) {
        if (hote.racineProjet() == null) {
            return;
        }
        List<ConfigServeurMcp> serveurs = new ArrayList<>();
        for (ConfigServeurMcp existant : GestionnaireMcp.lireFichier(
                lireMcpJson())) {
            if (!existant.nom.equals(nom)) {
                serveurs.add(existant);
            }
        }
        try {
            Files.writeString(hote.racineProjet().resolve(".mcp.json"),
                    ecrireJsonServeurs(serveurs),
                    StandardCharsets.UTF_8);
            if (hote.agent() != null) {
                hote.agent().poserMcpJson(lireMcpJson());
            }
        } catch (IOException | RuntimeException e) {
            // le fichier restait : l'état des lieux reste honnête à
            // la réouverture
        }
        ouvrir();
    }

    /** Le JSON des serveurs (la grammaire du .mcp.json). */
    private static String ecrireJsonServeurs(
            List<ConfigServeurMcp> serveurs) {
        StringBuilder json = new StringBuilder("{\n  \"mcpServers\": {");
        boolean premier = true;
        for (ConfigServeurMcp config : serveurs) {
            if (!premier) {
                json.append(',');
            }
            premier = false;
            json.append("\n    ").append(jsonChaine(config.nom))
                    .append(": {");
            json.append("\n      \"type\": \"")
                    .append(config.transport
                            == ConfigServeurMcp.Transport.SSE ? "sse"
                            : config.transport
                            == ConfigServeurMcp.Transport.STDIO
                            ? "stdio" : "http").append('"');
            if (config.url != null) {
                json.append(",\n      \"url\": ")
                        .append(jsonChaine(config.url));
            }
            if (config.commande != null) {
                json.append(",\n      \"command\": ")
                        .append(jsonChaine(config.commande));
            }
            if (config.arguments != null
                    && !config.arguments.isEmpty()) {
                json.append(",\n      \"args\": [");
                for (int i = 0; i < config.arguments.size(); i++) {
                    if (i > 0) {
                        json.append(", ");
                    }
                    json.append(jsonChaine(
                            config.arguments.get(i)));
                }
                json.append(']');
            }
            if (config.entetes != null && !config.entetes.isEmpty()) {
                json.append(",\n      \"headers\": {");
                boolean tete = true;
                for (Map.Entry<String, String> entete
                        : config.entetes.entrySet()) {
                    if (!tete) {
                        json.append(',');
                    }
                    tete = false;
                    json.append("\n        ")
                            .append(jsonChaine(entete.getKey()))
                            .append(": ")
                            .append(jsonChaine(entete.getValue()));
                }
                json.append("\n      }");
            }
            if (config.environnement != null
                    && !config.environnement.isEmpty()) {
                json.append(",\n      \"env\": {");
                boolean tete = true;
                for (Map.Entry<String, String> pair
                        : config.environnement.entrySet()) {
                    if (!tete) {
                        json.append(',');
                    }
                    tete = false;
                    json.append("\n        ")
                            .append(jsonChaine(pair.getKey()))
                            .append(": ")
                            .append(jsonChaine(pair.getValue()));
                }
                json.append("\n      }");
            }
            if (config.desactive) {
                json.append(",\n      \"disabled\": true");
            }
            json.append("\n    }");
        }
        json.append(serveurs.isEmpty() ? "" : "\n  ")
                .append("}\n}");
        return json.toString();
    }

    /** Une chaîne JSON échappée (nom, URL, en-tête). */
    private static String jsonChaine(String texte) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x",
                                (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    /** Les lignes non vides d'un champ multi-ligne. */
    private static List<String> lignes(String texte) {
        List<String> lignes = new ArrayList<>();
        for (String ligne : texte.split("\n")) {
            if (!ligne.trim().isEmpty()) {
                lignes.add(ligne.trim());
            }
        }
        return lignes;
    }

    /** Les paires « clé SEP valeur » d'un champ multi-ligne. */
    private static Map<String, String> paires(String texte,
                                              char separateur) {
        Map<String, String> paires = new LinkedHashMap<>();
        for (String ligne : texte.split("\n")) {
            int sep = ligne.indexOf(separateur);
            if (sep > 0) {
                paires.put(ligne.substring(0, sep).trim(),
                        ligne.substring(sep + 1).trim());
            }
        }
        return paires;
    }

    /** Les paires d'une table posée (affichage du champ). */
    private static String paires(Map<String, String> table,
                                 char separateur) {
        if (table == null || table.isEmpty()) {
            return "";
        }
        StringBuilder texte = new StringBuilder();
        for (Map.Entry<String, String> paire : table.entrySet()) {
            if (texte.length() > 0) {
                texte.append('\n');
            }
            texte.append(paire.getKey()).append(separateur == '='
                    ? '=' : ' ').append(paire.getValue());
        }
        return texte.toString();
    }

    // ------------------------------------------------------------------
    // Les compétences : les dossiers à SKILL.md de l'app et du projet
    // ------------------------------------------------------------------

    /** Le dossier des compétences de l'app. */
    private Path dossierCompetencesApp() {
        return hote.contexte().getFilesDir().toPath()
                .resolve("competences");
    }

    /** Le dossier des compétences du projet. */
    private Path dossierCompetencesProjet() {
        return hote.racineProjet() == null ? null
                : hote.racineProjet().resolve(".joide")
                .resolve("competences");
    }

    /** Les dossiers des compétences qui existent. */
    private List<String> dossiersCompetences() {
        List<String> dossiers = new ArrayList<>();
        if (Files.isDirectory(dossierCompetencesApp())) {
            dossiers.add(dossierCompetencesApp().toString());
        }
        Path projet = dossierCompetencesProjet();
        if (projet != null && Files.isDirectory(projet)) {
            dossiers.add(projet.toString());
        }
        return dossiers;
    }

    /** Les compétences découvertes (l'app puis le projet). */
    private List<Competence> competences() {
        List<Competence> trouvees = new ArrayList<>();
        List<String> eteintes = competencesEteintes();
        for (Path dossier : new Path[]{
                dossierCompetencesApp(), dossierCompetencesProjet()}) {
            if (dossier == null || !Files.isDirectory(dossier)) {
                continue;
            }
            try {
                List<Path> enfants = new ArrayList<>();
                try (var flux = Files.list(dossier)) {
                    flux.filter(Files::isDirectory)
                            .filter(p -> Files.isRegularFile(
                                    p.resolve("SKILL.md")))
                            .forEach(enfants::add);
                } catch (IOException e) {
                    continue;
                }
                enfants.sort(Path::compareTo);
                for (Path enfant : enfants) {
                    String nom = enfant.getFileName().toString();
                    String portee = dossier.equals(
                            dossierCompetencesApp()) ? "app" : "projet";
                    trouvees.add(new Competence(nom, portee,
                            description(enfant.resolve("SKILL.md")),
                            eteintes.contains(portee + ':' + nom)));
                }
            } catch (RuntimeException e) {
                // un dossier qui ne se liste pas se saute
            }
        }
        return trouvees;
    }

    /** La première ligne de description d'un SKILL.md, bornée. */
    private String description(Path skillMd) {
        try {
            byte[] octets = Files.readAllBytes(skillMd);
            if (octets.length > PLAFOND_SKILL) {
                byte[] borne = new byte[PLAFOND_SKILL];
                System.arraycopy(octets, 0, borne, 0, PLAFOND_SKILL);
                octets = borne;
            }
            for (String ligne : new String(octets,
                    StandardCharsets.UTF_8).split("\n")) {
                String propre = ligne.trim();
                if (propre.startsWith("#")) {
                    propre = propre.replaceFirst("^#+\\s*", "");
                }
                if (!propre.isEmpty()) {
                    return propre.length() > 160
                            ? propre.substring(0, 160) + "..."
                            : propre;
                }
            }
        } catch (IOException | RuntimeException e) {
            // illisible : la description reste vide, honnête
        }
        return "";
    }

    /** Les compétences éteintes (préfixées app:/projet:). */
    private List<String> competencesEteintes() {
        String brut = hote.sourceReglages()
                .lire(CLE_COMPETENCES_ETEINTES);
        List<String> eteintes = new ArrayList<>();
        if (brut != null && !brut.isBlank()) {
            for (String nom : brut.split("[\n,]")) {
                if (!nom.trim().isEmpty()) {
                    eteintes.add(nom.trim());
                }
            }
        }
        return eteintes;
    }

    /** Une carte de compétence : nom, portée, interrupteur, description. */
    private void poserCompetence(LinearLayout conteneur,
                                  LayoutInflater gonfleur,
                                  Competence competence) {
        ItemCompetenceConnexionsBinding ligne =
                ItemCompetenceConnexionsBinding.inflate(gonfleur,
                        conteneur, true);
        ligne.nomCompetence.setText(competence.nom);
        ligne.porteeCompetence.setText("app".equals(competence.portee)
                ? R.string.agent_connexions_portee_app
                : R.string.agent_connexions_portee_projet);
        ligne.descriptionCompetence.setText(competence.description);
        ligne.interrupteurCompetence.setChecked(!competence.eteinte);
        ligne.interrupteurCompetence.setOnCheckedChangeListener(
                (bouton, coche) -> basculerCompetence(
                        competence, coche));
    }

    /** La bascule d'une compétence : la liste se réécrit, on réapplique. */
    private void basculerCompetence(Competence competence,
                                    boolean allumee) {
        List<String> eteintes = new ArrayList<>(
                competencesEteintes());
        String cle = competence.portee + ':' + competence.nom;
        if (allumee) {
            eteintes.remove(cle);
        } else if (!eteintes.contains(cle)) {
            eteintes.add(cle);
        }
        hote.sourceReglages().ecrire(CLE_COMPETENCES_ETEINTES,
                String.join(",", eteintes));
        poserCompetences();
    }

    /** Le bloc des compétences actives part au service (le contexte
     *  du tour s'enrichit — chaque SKILL.md lu borné). */
    void poserCompetences() {
        if (hote.agent() == null) {
            return;
        }
        StringBuilder bloc = new StringBuilder();
        for (Competence competence : competences()) {
            if (competence.eteinte) {
                continue;
            }
            Path skillMd = ("app".equals(competence.portee)
                    ? dossierCompetencesApp()
                    : dossierCompetencesProjet())
                    .resolve(competence.nom).resolve("SKILL.md");
            bloc.append("\n\n# Skill: ").append(competence.nom)
                    .append('\n').append(contenuBorne(skillMd));
        }
        hote.agent().poserCompetences(bloc.length() > 2
                ? bloc.toString() : "");
    }

    /** Le contenu borné d'un SKILL.md (8 Kio). */
    private String contenuBorne(Path skillMd) {
        try {
            byte[] octets = Files.readAllBytes(skillMd);
            if (octets.length > PLAFOND_SKILL) {
                byte[] borne = new byte[PLAFOND_SKILL];
                System.arraycopy(octets, 0, borne, 0, PLAFOND_SKILL);
                octets = borne;
            }
            return new String(octets, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** La nouvelle compétence : le dossier et son SKILL.md naissent. */
    private void nouvelleCompetence() {
        PopupNouvelleCompetenceBinding corps =
                PopupNouvelleCompetenceBinding.inflate(
                        LayoutInflater.from(hote.contexte()));
        corps.fermerNouvelleCompetence.setOnClickListener(
                vue -> hote.popups().fermer());
        corps.annulerNouvelleCompetence.setOnClickListener(
                vue -> hote.popups().fermer());
        boolean[] porteeProjet = {hote.racineProjet() != null};
        corps.porteeProjetCompetence.setVisibility(
                hote.racineProjet() != null ? View.VISIBLE
                        : View.GONE);
        corps.porteeAppCompetence.setOnClickListener(vue -> {
            porteeProjet[0] = false;
            teintePilule(corps.porteeAppCompetence, true);
            teintePilule(corps.porteeProjetCompetence, false);
        });
        corps.porteeProjetCompetence.setOnClickListener(vue -> {
            porteeProjet[0] = true;
            teintePilule(corps.porteeAppCompetence, false);
            teintePilule(corps.porteeProjetCompetence, true);
        });
        teintePilule(corps.porteeAppCompetence,
                !porteeProjet[0] || hote.racineProjet() == null);
        teintePilule(corps.porteeProjetCompetence, porteeProjet[0]);
        corps.creerCompetence.setOnClickListener(vue -> {
            String nom = corps.champNomCompetence.getText()
                    .toString().trim().replaceAll("[^A-Za-z0-9_-]",
                            "-");
            Path dossier = porteeProjet[0]
                    && hote.racineProjet() != null
                    ? dossierCompetencesProjet()
                    : dossierCompetencesApp();
            try {
                Path cible = dossier.resolve(nom);
                Files.createDirectories(cible);
                Files.writeString(cible.resolve("SKILL.md"),
                        "# " + nom + "\n\nDescribe what this skill "
                                + "does.\n",
                        StandardCharsets.UTF_8);
                hote.popups().fermer();
                ouvrir();
            } catch (IOException | RuntimeException e) {
                corps.erreurNouvelleCompetence.setVisibility(
                        View.VISIBLE);
                corps.erreurNouvelleCompetence.setText(
                        R.string.agent_connexions_competence_echec);
            }
        });
        hote.popups().centrer(corps.getRoot(), hote.texte(
                R.string.agent_connexions_nouvelle_competence));
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
