package jo.joide.app.android.ecrans.atelier;

import android.content.Context;
import android.view.LayoutInflater;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.net.Uri;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jo.joide.agent.Permissions;
import jo.joide.agent.ReductionImages;
import jo.joide.agent.espace.EspaceCheckpoint;
import jo.joide.ide.agent.EspaceTravailIde;
import jo.joide.app.android.AgentService;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.agent.FournisseurCameraAgent;
import jo.joide.app.android.atelier.agent.ReducteurImagesAndroid;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.atelier.agent.ConversationsAgent;
import jo.joide.app.android.atelier.agent.PieceJointe;
import jo.joide.app.android.databinding.PopupModelesBinding;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.PopupAncre;
import jo.joide.app.android.ui.Puce;

/**
 * L6 — le contrôleur de l'agent : le service, le tour, les pièces
 * jointes, les connexions MCP, le sélecteur de modèle, les usages,
 * l'historique, les permissions et les questions. La vérité reste
 * dans EtatAgentChat (porté par l'écran) et le service ; le
 * contrôleur orchestre et met à jour les vues via l'écran. Les corps
 * viennent d'EcranAtelier sans changement de comportement
 * (extraction mécanique, mêmes commentaires).
 */
final class ControleurAgent {

    private final EcranAtelier atelier;

    ControleurAgent(EcranAtelier atelier) {
        this.atelier = atelier;
    }

    /** L'arrêt du tour (le bouton ■ du composeur). */
    void arreterTour() {
        if (atelier.agent != null) {
            atelier.agent.arreter();
        }
    }

    /** Le mode de permission posé par la puce d'en-tête. */
    void poserMode(Permissions.Mode mode) {
        atelier.etatAgent.poserMode(mode);
        if (atelier.agent != null) {
            atelier.agent.poserMode(mode);
        }
        atelier.vueAgent.rafraichir();
    }

    /** Une pièce retirée du composeur (le tap sur sa puce). */
    void retirerPiece(int index) {
        if (index >= 0 && index < atelier.piecesAgent.size()) {
            atelier.piecesAgent.remove(index);
            atelier.vueAgent.poserPieces(
                    PieceJointe.noms(atelier.piecesAgent));
        }
    }

    /** Le résultat SAF/caméra de l'écran (l'activité délègue). */
    void surResultatActivite(int requete, int resultat,
                             Intent donnees) {
        if (resultat != android.app.Activity.RESULT_OK) {
            return;
        }
        if (requete == EcranAtelier.REQUETE_IMAGE && donnees != null
                && donnees.getData() != null) {
            attacherImage(donnees.getData(),
                    nomDadresse(donnees.getData()));
        } else if (requete == EcranAtelier.REQUETE_PHOTO) {
            Path photo = FournisseurCameraAgent.dernierePhoto();
            if (photo != null && Files.isRegularFile(photo)) {
                attacherImage(android.net.Uri.fromFile(photo.toFile()),
                        photo.getFileName().toString());
            }
        }
    }

    /** Le débranchement du service (onDestroy de l'écran). */
    void debrancherService() {
        if (atelier.agent != null) {
            try {
                atelier.unbindService(connexionAgent());
            } catch (RuntimeException e) {
                // le service partait déjà
            }
            atelier.agent = null;
        }
    }

    void ouvrirAgent() {
        if (atelier.tiroirAgent == null) {
            return;
        }
        atelier.tiroirAgent.ouvrir();
        if (atelier.agent != null) {
            brancherEspaceAgent();
            rejouerJournalAgent();
            return;
        }
        AgentService.demarrer(atelier);
        atelier.bindService(new Intent(atelier, AgentService.class),
                connexionAgent(), Context.BIND_AUTO_CREATE);
    }

    void fermerAgent() {
        if (atelier.tiroirAgent != null) {
            atelier.tiroirAgent.fermer();
        }
    }

    private android.content.ServiceConnection connexionAgent() {
        return new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(
                    android.content.ComponentName nom,
                    android.os.IBinder service) {
                atelier.agent = (AgentService.AgentBinder) service;
                brancherEspaceAgent();
                atelier.agent.poserEcouteur(VueAgentEntree -> atelier.runOnUiThread(
                        () -> surEntreeAgent(VueAgentEntree)));
                rejouerJournalAgent();
                // le message parti pendant le bind s'en va MAINTENANT
                atelier.runOnUiThread(() -> depecherAttenteAgent());
            }

            @Override
            public void onServiceDisconnected(
                    android.content.ComponentName nom) {
                atelier.agent = null;
            }
        };
    }

    /** Construit l'espace de travail du projet pour l'atelier.agent. */
    private void brancherEspaceAgent() {
        if (atelier.agent == null || atelier.racineProjet == null || atelier.scene == null) {
            return;
        }
        EspaceTravailIde espace = new EspaceTravailIde(atelier.racineProjet,
                atelier.modele).poserMoteurs(atelier.moteurs);
        espace.poserOverlayEditeur(chemin -> {
            String actif = atelier.etat.actif();
            if (actif != null && actif.equals(chemin)) {
                return atelier.scene.texte();
            }
            return null;
        });
        atelier.agent.poserEspace(new EspaceCheckpoint(espace));
        Reglages reglages = Reglages.lire(atelier.sourceReglages);
        atelier.agent.poserModele(reglages.fournisseurIa, reglages.modeleIa);
        // le .mcp.json du projet, lu borné, part au service : les
        // serveurs déclarés sont parsés, pas activés (ADR-063)
        atelier.agent.poserMcpJson(lireMcpJson());
        // l'approbation PERSISTÉE par projet (lot T, ADR-065) : liée
        // à la signature du fichier qui l'a motivée — le même
        // .mcp.json retrouve ses serveurs, un fichier changé redemande
        // le geste explicite de l'utilisateur
        if ("1".equals(atelier.sourceReglages.lire(cleApprobationMcp()))) {
            atelier.agent.poserApprobationMcp(true);
        }
        atelier.etatAgent.poserModele(reglages.modeleIa);
        atelier.etatAgent.poserMode(EtatAgentChat.TourEtat.EN_COURS
                == atelier.etatAgent.etat()
                ? atelier.etatAgent.mode() : Permissions.Mode.DEMANDE_CHAQUE);
    }

    /** Rejoue le journal du service dans l'état pur (retour d'écran). */
    private void rejouerJournalAgent() {
        if (atelier.agent == null) {
            return;
        }
        atelier.etatAgent = new EtatAgentChat();
        for (AgentService.Entree entree : atelier.agent.journal()) {
            if (entree.estMessage()) {
                atelier.etatAgent.envoyer(entree.message, entree.pieces);
            } else if (entree.estReponse()) {
                atelier.etatAgent.restaurerReponse(entree.reponse);
            } else {
                atelier.etatAgent.surEvenement(entree.evenement);
            }
        }
        majQuestionsAgent();
        atelier.vueAgent.poserEtat(atelier.etatAgent);
    }

    /** L'entrée live du service : événement ou message. */
    private void surEntreeAgent(AgentService.Entree entree) {
        if (entree.estMessage()) {
            atelier.etatAgent.envoyer(entree.message, entree.pieces);
        } else if (entree.estReponse()) {
            atelier.etatAgent.restaurerReponse(entree.reponse);
        } else {
            atelier.etatAgent.surEvenement(entree.evenement);
        }
        majQuestionsAgent();
        atelier.vueAgent.poserEtat(atelier.etatAgent);
        // la fin d'un tour libère le message retenu pendant le bind
        // (session revenue sur un tour vivant)
        if (atelier.messageEnAttenteAgent != null
                && atelier.etatAgent.etat() != EtatAgentChat.TourEtat.EN_COURS) {
            depecherAttenteAgent();
        }
        if (atelier.pointAgent != null) {
            atelier.pointAgent.setVisibility(
                    atelier.etatAgent.etat()
                            == EtatAgentChat.TourEtat.EN_COURS
                            ? View.VISIBLE : View.GONE);
        }
        Permissions.DemandeEcriture permission =
                atelier.agent == null ? null : atelier.agent.permissionEnAttente();
        if (permission != null) {
            montrerPermissionAgent(permission);
        }
    }

    /** Les questions en attente deviennent une carte (ask_user). */
    private void majQuestionsAgent() {
        if (atelier.agent == null) {
            return;
        }
        java.util.List<jo.joide.agent.espace.QuestionUtilisateur>
                questions = atelier.agent.questionsEnAttente();
        if (questions != null && !questions.isEmpty()
                && atelier.etatAgent.reponses() == null) {
            atelier.etatAgent.poserQuestions(questions);
        }
    }

    /** Le dialogue de permission sur diff déplié (fermer = refuser). */
    private void montrerPermissionAgent(
            Permissions.DemandeEcriture demande) {
        if (atelier.etatAgent.permissionEnAttente() != null) {
            return;
        }
        atelier.etatAgent.poserPermission(demande);
        atelier.vueAgent.poserEtat(atelier.etatAgent);
        ScrollView defilement = new ScrollView(atelier);
        TextView diff = new TextView(atelier);
        diff.setTypeface(GraphiteVue.police(atelier, 400, true));
        diff.setTextSize(11.5f);
        diff.setTextColor(atelier.palette.texteSecondaire);
        diff.setText(demande.diffApercu);
        int marge = GraphiteVue.dp(atelier, 24);
        diff.setPadding(marge, GraphiteVue.dp(atelier, 6), marge, 0);
        defilement.addView(diff, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        Dialogue.autorisation(atelier, atelier.palette,
                atelier.getString(R.string.agent_permission_ecriture),
                demande.outil + " — " + demande.cible,
                defilement,
                R.string.agent_refuser, R.string.agent_appliquer,
                (dialog, lequel) -> repondrePermissionAgent(
                        lequel == android.content.DialogInterface
                                .BUTTON_POSITIVE),
                dialog -> repondrePermissionAgent(false));
    }

    void envoyerAgent(String message) {
        if (atelier.agent == null) {
            ouvrirAgent();
        }
        if (atelier.agent == null) {
            // le bind est ASYNCHRONE : le message part dès la liaison
            // (onServiceConnected) — plus jamais perdu silencieusement
            // (fix du 2026-11-17 : le premier message d'une session
            // disparaissait quand le service n'était pas encore lié)
            atelier.messageEnAttenteAgent =
                    PieceJointe.fondre(message, atelier.piecesAgent);
            atelier.piecesEnAttenteAgent.clear();
            atelier.piecesEnAttenteAgent.addAll(atelier.piecesAgent);
            atelier.piecesAgent.clear();
            atelier.vueAgent.poserPieces(List.of());
            return;
        }
        if (atelier.agent.tourEnCours()) {
            // le service n'empile jamais deux tours — les pièces
            // restent posées, l'utilisateur renverra après l'arrêt
            return;
        }
        // les fichiers et l'arbre se fondent dans le message, les
        // images partent multimodales (lot S)
        String fondu = PieceJointe.fondre(message, atelier.piecesAgent);
        atelier.agent.envoyer(fondu, List.copyOf(atelier.piecesAgent));
        atelier.piecesAgent.clear();
        atelier.vueAgent.poserPieces(List.of());
    }

    /** Le départ du message retenu : la liaison du service est là
     *  (ou le tour en cours vient de finir). */
    private void depecherAttenteAgent() {
        if (atelier.agent == null || atelier.messageEnAttenteAgent == null
                || atelier.agent.tourEnCours()) {
            return;
        }
        String message = atelier.messageEnAttenteAgent;
        List<PieceJointe> pieces = List.copyOf(atelier.piecesEnAttenteAgent);
        atelier.messageEnAttenteAgent = null;
        atelier.piecesEnAttenteAgent.clear();
        atelier.agent.envoyer(message, pieces);
    }

    // ------------------------------------------------------------------
    // Les pièces jointes (lot S, ADR-064) : image, photo, fichier
    // courant, projet — le menu ne montre que ce que l'appareil
    // sait faire (caméra présente, fichier actif ouvert).
    // ------------------------------------------------------------------

    /** Le menu + ancré à son bouton (lot T) : chaque entrée est câblée, jamais morte. */
    void ouvrirAjoutsAgent() {
        if (atelier.vueAgent == null) {
            return;
        }
        List<CharSequence> elements = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        elements.add(atelier.getString(R.string.agent_ajouter_image));
        actions.add(0);
        boolean camera = atelier.getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CAMERA_ANY);
        if (camera) {
            elements.add(atelier.getString(R.string.agent_ajouter_photo));
            actions.add(1);
        }
        if (atelier.etat.actif() != null) {
            elements.add(atelier.getString(R.string.agent_ajouter_fichier));
            actions.add(2);
        }
        elements.add(atelier.getString(R.string.agent_ajouter_projet));
        actions.add(3);
        LinearLayout colonne = new LinearLayout(atelier);
        colonne.setOrientation(LinearLayout.VERTICAL);
        android.widget.PopupWindow[] fenetre = new android.widget
                .PopupWindow[1];
        Runnable fermer = () -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        };
        for (int i = 0; i < elements.size(); i++) {
            CharSequence nom = elements.get(i);
            int action = actions.get(i);
            colonne.addView(PopupAncre.ligneMenu(atelier, atelier.palette, nom,
                    () -> {
                        fermer.run();
                        if (action == 0) {
                            choisirImageAgent();
                        } else if (action == 1) {
                            prendrePhotoAgent();
                        } else if (action == 2) {
                            joindreFichierCourant();
                        } else {
                            joindreProjet();
                        }
                    }));
        }
        fenetre[0] = PopupAncre.montrer(atelier.vueAgent.ancreAjouter(),
                atelier.palette, colonne, 240);
    }

    /** Le choix d'une image : SAF, lecture bornée, réduction. */
    private void choisirImageAgent() {
        Intent intention = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intention.addCategory(Intent.CATEGORY_OPENABLE);
        intention.setType("image/*");
        try {
            atelier.startActivityForResult(intention, EcranAtelier.REQUETE_IMAGE);
        } catch (android.content.ActivityNotFoundException e) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.agent_ajouter),
                    atelier.getString(R.string.agent_image_indisponible),
                    R.string.action_ok);
        }
    }

    /** La photo : cible du cache servie par le fournisseur local. */
    private void prendrePhotoAgent() {
        try {
            android.net.Uri cible = FournisseurCameraAgent.preparer(atelier);
            Intent intention = new Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intention.putExtra(
                    android.provider.MediaStore.EXTRA_OUTPUT, cible);
            intention.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            atelier.startActivityForResult(intention, EcranAtelier.REQUETE_PHOTO);
        } catch (IOException | RuntimeException e) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.agent_ajouter),
                    atelier.getString(R.string.agent_image_indisponible),
                    R.string.action_ok);
        }
    }

    /** Le nom lisible d'une URI SAF (dernier segment, décodé). */
    private String nomDadresse(android.net.Uri adresse) {
        String chemin = adresse == null ? ""
                : String.valueOf(adresse.getLastPathSegment());
        int barre = chemin.lastIndexOf('/');
        return barre >= 0 ? chemin.substring(barre + 1) : chemin;
    }

    /**
     * Attache une image : lecture bornée (8 Mio), type de média,
     * réduction au budget — une image qui ne passe pas est REFUSÉE
     * nommément, jamais attachée en silence.
     */
    private void attacherImage(android.net.Uri adresse, String nom) {
        byte[] octets;
        try (java.io.InputStream flux =
                     atelier.getContentResolver().openInputStream(adresse)) {
            if (flux == null) {
                return;
            }
            // lecture bornée à la main (readNBytes exige l'API 33,
            // le min est 28) : un octet de plus révèle le dépassement
            java.io.ByteArrayOutputStream tampon =
                    new java.io.ByteArrayOutputStream();
            byte[] morceau = new byte[65_536];
            int lus;
            while (tampon.size() <= 8_000_000
                    && (lus = flux.read(morceau)) > 0) {
                tampon.write(morceau, 0, lus);
            }
            octets = tampon.toByteArray();
        } catch (IOException | RuntimeException e) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.agent_ajouter),
                    atelier.getString(R.string.agent_image_indisponible),
                    R.string.action_ok);
            return;
        }
        if (octets.length > 8_000_000) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.agent_ajouter),
                    atelier.getString(R.string.agent_image_trop_grande),
                    R.string.action_ok);
            return;
        }
        String mediaType = atelier.getContentResolver().getType(adresse);
        if (mediaType == null || !mediaType.startsWith("image/")) {
            mediaType = ReducteurImagesAndroid.mediaTypeDe(nom);
        }
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(octets);
        ReductionImages.ResultatImage reduite =
                ReductionImages.reduire(mediaType, base64,
                        new ReducteurImagesAndroid(),
                        ReductionImages.BUDGET_OCTETS_DEFAUT);
        if (reduite.image == null) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.agent_ajouter),
                    atelier.getString(R.string.agent_image_trop_grande),
                    R.string.action_ok);
            return;
        }
        atelier.piecesAgent.add(PieceJointe.image(
                nom.isEmpty() ? "image" : nom,
                reduite.image.mediaType, reduite.image.donnees));
        atelier.vueAgent.poserPieces(PieceJointe.noms(atelier.piecesAgent));
    }

    /** Le fichier courant : cité en bloc borné (le modèle a read_file). */
    private void joindreFichierCourant() {
        String actif = atelier.etat.actif();
        if (actif == null || atelier.scene == null) {
            return;
        }
        atelier.piecesAgent.add(PieceJointe.fichier(actif,
                PieceJointe.bornerTexte(atelier.scene.texte())));
        atelier.vueAgent.poserPieces(PieceJointe.noms(atelier.piecesAgent));
    }

    /** La structure du projet : l'arbre borné en lignes. */
    private void joindreProjet() {
        String arbre = PieceJointe.arbre(atelier.fichiersArbre);
        if (arbre.isEmpty()) {
            return;
        }
        atelier.piecesAgent.add(PieceJointe.projet(arbre));
        atelier.vueAgent.poserPieces(PieceJointe.noms(atelier.piecesAgent));
    }

    // ------------------------------------------------------------------
    // Le menu de l'agent (lot S, ADR-064) : nouvelle conversation,
    // historique — le contenu du menu ne vit QUE ici, la vue
    // n'annonce que les actions câblées.
    // ------------------------------------------------------------------

    /** Le menu ⋯ ancré à son icône (lot T) : chaque entrée est câblée, jamais morte. */
    void ouvrirMenuAgentOptions() {
        if (atelier.vueAgent == null) {
            return;
        }
        LinearLayout colonne = new LinearLayout(atelier);
        colonne.setOrientation(LinearLayout.VERTICAL);
        android.widget.PopupWindow[] fenetre = new android.widget
                .PopupWindow[1];
        Runnable fermer = () -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        };
        colonne.addView(PopupAncre.ligneMenu(atelier, atelier.palette,
                atelier.getString(R.string.agent_menu_nouvelle), () -> {
                    fermer.run();
                    nouvelleConversationAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(atelier, atelier.palette,
                atelier.getString(R.string.agent_menu_historique), () -> {
                    fermer.run();
                    ouvrirHistoriqueAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(atelier, atelier.palette,
                atelier.getString(R.string.agent_modele_titre), () -> {
                    fermer.run();
                    ouvrirModeleAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(atelier, atelier.palette,
                atelier.getString(R.string.agent_connexions_titre), () -> {
                    fermer.run();
                    ouvrirConnexionsAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(atelier, atelier.palette,
                atelier.getString(R.string.agent_usages_titre), () -> {
                    fermer.run();
                    ouvrirUsagesAgent();
                }));
        fenetre[0] = PopupAncre.montrer(atelier.vueAgent.ancreMenu(), atelier.palette,
                colonne, 220);
    }

    /**
     * Le {@code .mcp.json} du projet (lot S) : lu borné (64 Kio),
     * vide si absent ou illisible — jamais un crash d'ouverture.
     */
    private String lireMcpJson() {
        if (atelier.racineProjet == null) {
            return "";
        }
        try {
            Path cible = atelier.racineProjet.resolve(".mcp.json");
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
     * ajouté ou modifié redemande le geste explicite.
     */
    private String cleApprobationMcp() {
        String chemin = atelier.racineProjet == null ? ""
                : atelier.racineProjet.resolve(".mcp.json").toString();
        try {
            java.security.MessageDigest empreinte =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] octets = empreinte.digest((chemin + "\n"
                    + lireMcpJson()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(
                    "atelier.agent.mcp.approuve.");
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", octets[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 manque (impossible sur Android) : la clé retombe
            // sur le chemin seul — la persistance reste par projet
            return "atelier.agent.mcp.approuve."
                    + Integer.toHexString(chemin.hashCode());
        }
    }

    /**
     * L'écran Connexions (lot S, ADR-064 câblant l'ADR-063) : les
     * serveurs déclarés par le projet, leur état publié, le geste
     * d'approbation qui seul connecte — un dépôt cloné n'est pas un
     * choix de l'utilisateur.
     */
    private void ouvrirConnexionsAgent() {
        if (atelier.agent == null) {
            return;
        }
        List<jo.joide.agent.mcp.ConfigServeurMcp> configs =
                atelier.agent.configsMcp();
        boolean approuve = atelier.agent.approuveMcp();
        java.util.Map<String, jo.joide.agent.mcp.GestionnaireMcp
                .EtatServeur> etats = new java.util.HashMap<>();
        for (var etat : atelier.agent.etatsMcp()) {
            etats.put(etat.nom, etat);
        }

        LinearLayout corps = new LinearLayout(atelier);
        corps.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(atelier, 4);
        corps.setPadding(marge, marge, marge, marge);

        if (configs.isEmpty()) {
            TextView vide = new TextView(atelier);
            GraphiteVue.typographie(vide, Graphite.CORPS);
            vide.setTextColor(atelier.palette.texteEstompe);
            vide.setText(R.string.agent_connexions_vide);
            vide.setPadding(0, GraphiteVue.dp(atelier, 10), 0,
                    GraphiteVue.dp(atelier, 10));
            corps.addView(vide);
        } else {
            TextView avertissement = new TextView(atelier);
            GraphiteVue.typographie(avertissement, Graphite.LEGENDE);
            avertissement.setTextColor(atelier.palette.texteSecondaire);
            avertissement.setText(approuve
                    ? R.string.agent_connexions_actives
                    : R.string.agent_connexions_avertissement);
            avertissement.setPadding(0, marge, 0,
                    GraphiteVue.dp(atelier, 8));
            corps.addView(avertissement);

            TextView action = new TextView(atelier);
            GraphiteVue.typographie(action, Graphite.CORPS);
            action.setTextColor(atelier.palette.accent);
            action.setText(approuve
                    ? R.string.agent_connexions_desactiver
                    : R.string.agent_connexions_activer);
            action.setPadding(0, 0, 0, GraphiteVue.dp(atelier, 10));
            action.setOnClickListener(vue -> {
                if (atelier.agent != null) {
                    atelier.agent.poserApprobationMcp(!approuve);
                }
                // la décision se souvient par projet (lot T), liée à
                // la signature du .mcp.json qui l'a motivée
                atelier.sourceReglages.ecrire(cleApprobationMcp(),
                        !approuve ? "1" : "0");
                ouvrirConnexionsAgent();
            });
            corps.addView(action);

            if (approuve) {
                TextView compte = new TextView(atelier);
                GraphiteVue.typographie(compte, Graphite.LEGENDE);
                compte.setTextColor(atelier.palette.texteEstompe);
                compte.setText(atelier.getString(R.string.agent_connexions_outils,
                        atelier.agent.outilsMcp()));
                compte.setPadding(0, 0, 0, GraphiteVue.dp(atelier, 6));
                corps.addView(compte);
            }

            for (jo.joide.agent.mcp.ConfigServeurMcp config : configs) {
                corps.addView(ligneServeurMcp(config, approuve,
                        etats.get(config.nom)));
            }
        }

        ScrollView defilement = new ScrollView(atelier);
        defilement.addView(corps, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        // le popup ancré (lot T) : l'écran Connexions vit près du menu
        // qui l'appelle, jamais un dialogue centré qui coupe le fil
        if (atelier.vueAgent != null) {
            PopupAncre.montrer(atelier.vueAgent.ancreMenu(), atelier.palette,
                    defilement, 320);
        }
    }

    /** Une ligne de serveur MCP : nom, état, motif, reconnexion. */
    private View ligneServeurMcp(
            jo.joide.agent.mcp.ConfigServeurMcp config, boolean approuve,
            jo.joide.agent.mcp.GestionnaireMcp.EtatServeur etat) {
        LinearLayout ligne = new LinearLayout(atelier);
        ligne.setOrientation(LinearLayout.VERTICAL);
        int padding = GraphiteVue.dp(atelier, 10);
        ligne.setPadding(padding, padding, padding, padding);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(atelier.palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(atelier,
                Graphite.RAYON_COMPOSANT));
        fond.setStroke(GraphiteVue.dp(atelier, 1), atelier.palette.bordFin);
        ligne.setBackground(fond);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int marge = GraphiteVue.dp(atelier, 4);
        params.setMargins(0, marge, 0, marge);
        ligne.setLayoutParams(params);

        TextView nom = new TextView(atelier);
        GraphiteVue.typographie(nom, Graphite.CORPS);
        nom.setTextColor(atelier.palette.texte);
        nom.setText(config.nom);
        ligne.addView(nom);

        TextView statut = new TextView(atelier);
        GraphiteVue.typographie(statut, Graphite.LEGENDE);
        CharSequence texteStatut = !approuve
                ? atelier.getString(R.string.agent_connexions_non_active)
                : etat == null
                        ? atelier.getString(R.string.agent_connexions_statut_connexion)
                        : texteStatutMcp(etat.statut)
                                + (etat.motif == null ? ""
                                        : " — " + etat.motif);
        statut.setText(texteStatut);
        statut.setTextColor(approuve && etat != null
                && etat.statut == jo.joide.agent.mcp.GestionnaireMcp
                        .Statut.ECHEC
                ? atelier.palette.erreur : atelier.palette.texteEstompe);
        statut.setPadding(0, GraphiteVue.dp(atelier, 3), 0, 0);
        ligne.addView(statut);

        if (approuve && etat != null
                && (etat.statut == jo.joide.agent.mcp.GestionnaireMcp
                        .Statut.ECHEC
                || etat.statut == jo.joide.agent.mcp.GestionnaireMcp
                        .Statut.AUTH_REQUISE)) {
            // lot U : la signature OAuth — le parcours MCP se
            // complète : découverte, enregistrement du client,
            // navigateur, rappel joide://mcp/oauth, jetons, connexion
            TextView connecter = new TextView(atelier);
            GraphiteVue.typographie(connecter, Graphite.CORPS);
            connecter.setTextColor(atelier.palette.accent);
            connecter.setText(R.string.agent_connexions_se_connecter);
            connecter.setPadding(0, GraphiteVue.dp(atelier, 6), 0, 0);
            connecter.setOnClickListener(vue -> {
                if (atelier.agent == null) {
                    return;
                }
                atelier.agent.demanderOAuthMcp(config.nom,
                        url -> atelier.runOnUiThread(() -> {
                            try {
                                atelier.startActivity(new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)));
                            } catch (Exception e) {
                                Dialogue.information(
                                        atelier, atelier.palette,
                                        atelier.getString(R.string
                                                .agent_connexions_titre),
                                        atelier.getString(R.string
                                                .agent_connexions_oauth_echec),
                                        R.string.action_ok);
                            }
                        }),
                        erreur -> atelier.runOnUiThread(() -> Dialogue
                                .information(atelier,
                                        atelier.palette,
                                        atelier.getString(R.string
                                                .agent_connexions_titre),
                                        erreur,
                                        R.string.action_ok)));
            });
            ligne.addView(connecter);

            TextView reconnecter = new TextView(atelier);
            GraphiteVue.typographie(reconnecter, Graphite.CORPS);
            reconnecter.setTextColor(atelier.palette.accent);
            reconnecter.setText(R.string.agent_connexions_reconnecter);
            reconnecter.setPadding(0, GraphiteVue.dp(atelier, 6), 0, 0);
            reconnecter.setOnClickListener(vue -> {
                if (atelier.agent != null) {
                    atelier.agent.reconnecterMcp(config.nom);
                }
                ouvrirConnexionsAgent();
            });
            ligne.addView(reconnecter);
        }
        return ligne;
    }

    /** Le libellé localisé d'un statut MCP. */
    private CharSequence texteStatutMcp(
            jo.joide.agent.mcp.GestionnaireMcp.Statut statut) {
        return atelier.getString(switch (statut) {
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
     * Le sélecteur de modèle de la manière CodeAssist (lot T,
     * ADR-065) : une popup ANCRÉE à la puce du tiroir — la rangée de
     * fournisseurs, la recherche qui filtre, les favoris (étoile)
     * et les récents devant les suggestions, la coche sur le modèle
     * courant. Un modèle se choisit D'UN TAP : le couple part au
     * service, la puce se rafraîchit, la conversation continue. Le
     * modèle personnalisé (et l'URL de base) gardent leur dialogue.
     */
    void ouvrirModeleAgent() {
        if (atelier.vueAgent == null) {
            return;
        }
        Reglages reglages = Reglages.lire(atelier.sourceReglages);
        android.widget.PopupWindow[] fenetre =
                new android.widget.PopupWindow[1];
        Runnable fermer = () -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        };
        Runnable[] rafraichir = new Runnable[1];
        int[] fournisseurIndex = {reglages.fournisseurIa.ordinal()};

        // ---- la coque du XML (lot L6) : titre, fermer, la rangée
        // des fournisseurs, l'état de clé, la recherche, la liste —
        // les puces (état pointé) et les lignes de modèles restent
        // posées par code, la vérité reste dans les réglages
        PopupModelesBinding coque = PopupModelesBinding.inflate(
                LayoutInflater.from(atelier));
        LinearLayout colonne = coque.getRoot();
        Icone fermerIcone = coque.btnFermerModeles;
        fermerIcone.setCouleur(atelier.palette.texteSecondaire);
        fermerIcone.setOnClickListener(vue -> fermer.run());

        // la rangée des fournisseurs : une puce pointée dit le
        // choix — le TAP PERSISTE le fournisseur (fix du 2026-11-17 :
        // avant, seule la puce bougeait, le fournisseur des réglages
        // restait ANTHROPIC et la requête partait chez Anthropic —
        // « no key for anthropic » quel que soit le fournisseur
        // choisi) ; l'état de clé se lit du stockage chiffré et se
        // voit sous la rangée
        Reglages.FournisseurIa[] fournisseurs =
                Reglages.FournisseurIa.values();

        // l'état de clé du fournisseur pointé (fix du 2026-11-17 :
        // les chaînes agent_cle_posee/absente existaient mais ne se
        // voyaient NULLE PART — l'utilisateur ne savait pas que SA
        // clé n'était pas celle du fournisseur choisi). Un tap ouvre
        // les réglages IA.
        TextView etatCles = coque.etatCles;
        GraphiteVue.typographie(etatCles, Graphite.LEGENDE);
        etatCles.setOnClickListener(vue -> {
            try {
                atelier.startActivity(new Intent(atelier,
                        jo.joide.app.android.ecrans.parametres
                                .EcranParametres.class));
            } catch (RuntimeException e) {
                // les réglages restent atteignables par le menu
            }
        });
        final Runnable[] majEtatCles = new Runnable[1];
        majEtatCles[0] = () -> {
            Reglages.FournisseurIa courant =
                    fournisseurs[fournisseurIndex[0]];
            boolean posee = false;
            try {
                posee = jo.joide.app.android.parametres.KeystoreCles
                        .sur(atelier).estPosee(
                                nomStockageCle(courant));
            } catch (RuntimeException e) {
                // Keystore cassé : l'état reste « absente », jamais
                // un crash du sélecteur
            }
            etatCles.setTextColor(posee ? atelier.palette.texteSecondaire
                    : atelier.palette.alerte);
            etatCles.setText(atelier.getString(
                    posee ? R.string.agent_cle_etat_posee
                            : R.string.agent_cle_etat_absente,
                    nomFournisseur(courant)));
        };

        LinearLayout rangeeFournisseurs = coque.rangeeFournisseurs;

        // la recherche : le filtre des listes longues
        android.widget.EditText recherche = coque.rechercheModeles;
        recherche.setTextColor(atelier.palette.texte);
        recherche.setHintTextColor(atelier.palette.texteEstompe);
        recherche.setTypeface(GraphiteVue.police(atelier, 400, false));

        LinearLayout liste = coque.listeModeles;

        rafraichir[0] = () -> {
            liste.removeAllViews();
            remplirListeModeles(liste, fournisseurIndex[0],
                    recherche.getText().toString(), fermer,
                    rafraichir[0]);
        };
        recherche.addTextChangedListener(
                new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s,
                            int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s,
                            int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s) {
                        rafraichir[0].run();
                    }
                });
        rafraichir[0].run();

        fenetre[0] = PopupAncre.montrer(atelier.vueAgent.ancreModele(),
                atelier.palette, colonne, 320);
    }

    /** Les sections du sélecteur : favoris, récents, suggestions. */
    private void remplirListeModeles(LinearLayout liste,
                                     int fournisseurIndex,
                                     String requete, Runnable fermer,
                                     Runnable rafraichir) {
        Reglages reglages = Reglages.lire(atelier.sourceReglages);
        String[] catalogue = catalogueModele(fournisseurIndex);
        List<String> favoris = lireListeModeles(
                cleFavorisModeles(fournisseurIndex));
        List<String> recents = lireListeModeles(
                cleRecentsModeles(fournisseurIndex));
        String filtre = requete == null ? ""
                : requete.trim().toLowerCase(java.util.Locale.ROOT);
        List<String> sectionFavoris = new ArrayList<>();
        List<String> sectionRecents = new ArrayList<>();
        List<String> sectionSuggestions = new ArrayList<>();
        for (String modele : catalogue) {
            if (!filtre.isEmpty() && !modele.toLowerCase(
                    java.util.Locale.ROOT).contains(filtre)) {
                continue;
            }
            if (favoris.contains(modele)) {
                sectionFavoris.add(modele);
            } else if (recents.contains(modele)) {
                sectionRecents.add(modele);
            } else {
                sectionSuggestions.add(modele);
            }
        }
        boolean vide = sectionFavoris.isEmpty()
                && sectionRecents.isEmpty()
                && sectionSuggestions.isEmpty();
        if (!sectionFavoris.isEmpty()) {
            liste.addView(enteteSection(atelier.getString(
                    R.string.agent_modeles_favoris)));
        }
        for (String modele : sectionFavoris) {
            liste.addView(ligneModele(modele,
                    modele.equals(reglages.modeleIa), true,
                    fournisseurIndex, fermer, rafraichir));
        }
        if (!sectionRecents.isEmpty()) {
            liste.addView(enteteSection(atelier.getString(
                    R.string.agent_modeles_recents)));
        }
        for (String modele : sectionRecents) {
            liste.addView(ligneModele(modele,
                    modele.equals(reglages.modeleIa), false,
                    fournisseurIndex, fermer, rafraichir));
        }
        if (!sectionSuggestions.isEmpty()) {
            liste.addView(enteteSection(atelier.getString(
                    R.string.agent_modeles_suggestions)));
        }
        for (String modele : sectionSuggestions) {
            liste.addView(ligneModele(modele,
                    modele.equals(reglages.modeleIa), false,
                    fournisseurIndex, fermer, rafraichir));
        }
        if (vide && filtre.isEmpty()) {
            TextView videVue = new TextView(atelier);
            GraphiteVue.typographie(videVue, Graphite.LEGENDE);
            videVue.setTextColor(atelier.palette.texteEstompe);
            videVue.setText(R.string.agent_modeles_recherche);
            videVue.setPadding(GraphiteVue.dp(atelier, 14),
                    GraphiteVue.dp(atelier, 12), 0, 0);
            liste.addView(videVue);
        }
        // le modèle hors catalogue reste atteignable : le geste
        // personnalisé garde son dialogue (nom, URL de base)
        TextView personnalise = new TextView(atelier);
        GraphiteVue.typographie(personnalise, Graphite.CORPS);
        personnalise.setTextColor(atelier.palette.accent);
        personnalise.setText(R.string.agent_modeles_personnalise);
        int vertical = GraphiteVue.dp(atelier, 11);
        personnalise.setPadding(GraphiteVue.dp(atelier, 14), vertical,
                GraphiteVue.dp(atelier, 14), vertical);
        personnalise.setOnClickListener(vue -> {
            fermer.run();
            dialogueModelePersonnalise(fournisseurIndex);
        });
        liste.addView(personnalise);
    }

    /** L'en-tête discret d'une section du sélecteur. */
    private TextView enteteSection(CharSequence titre) {
        TextView vue = new TextView(atelier);
        GraphiteVue.typographie(vue, Graphite.LEGENDE);
        vue.setTextColor(atelier.palette.texteEstompe);
        vue.setText(titre);
        vue.setPadding(GraphiteVue.dp(atelier, 14),
                GraphiteVue.dp(atelier, 10),
                GraphiteVue.dp(atelier, 14),
                GraphiteVue.dp(atelier, 2));
        return vue;
    }

    /** Une ligne de modèle : la coche du courant, l'étoile du favori. */
    private View ligneModele(String modele, boolean courant,
                             boolean favori, int fournisseurIndex,
                             Runnable fermer, Runnable rafraichir) {
        LinearLayout ligne = new LinearLayout(atelier);
        ligne.setOrientation(LinearLayout.HORIZONTAL);
        ligne.setGravity(Gravity.CENTER_VERTICAL);
        ligne.setBackground(PopupAncre.fondAppuye(atelier.palette));
        ligne.setOnClickListener(vue -> {
            fermer.run();
            choisirModele(fournisseurIndex, modele);
        });
        TextView nom = new TextView(atelier);
        GraphiteVue.typographie(nom, Graphite.CORPS);
        nom.setTextColor(atelier.palette.texte);
        nom.setText(modele);
        nom.setSingleLine(true);
        nom.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ligne.addView(nom, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (courant) {
            Icone coche = new Icone(atelier);
            coche.setGlyphe(Icone.Glyphe.COCHE);
            coche.setCouleur(atelier.palette.accent);
            coche.setTraitDp(1.8f);
            LinearLayout.LayoutParams paramsCoche =
                    new LinearLayout.LayoutParams(
                    GraphiteVue.dp(atelier, 16),
                    GraphiteVue.dp(atelier, 16));
            paramsCoche.rightMargin = GraphiteVue.dp(atelier, 8);
            ligne.addView(coche, paramsCoche);
        }
        Icone etoile = new Icone(atelier);
        etoile.setGlyphe(Icone.Glyphe.ETOILE);
        etoile.setCouleur(favori ? atelier.palette.alerte : atelier.palette.bord);
        etoile.setContentDescription(
                atelier.getString(R.string.agent_modeles_favori));
        etoile.setClickable(true);
        etoile.setOnClickListener(vue -> {
            basculeFavori(fournisseurIndex, modele, !favori);
            rafraichir.run();
        });
        LinearLayout.LayoutParams paramsEtoile =
                new LinearLayout.LayoutParams(
                GraphiteVue.dp(atelier, 30),
                GraphiteVue.dp(atelier, 30));
        paramsEtoile.leftMargin = GraphiteVue.dp(atelier, 2);
        ligne.addView(etoile, paramsEtoile);
        int vertical = GraphiteVue.dp(atelier, 9);
        ligne.setPadding(GraphiteVue.dp(atelier, 14), vertical,
                GraphiteVue.dp(atelier, 10), vertical);
        return ligne;
    }

    /** Le catalogue suggéré d'un fournisseur (l'honnêteté : des
     * suggestions nommées, jamais une liste prétendue vivante). */
    private static String[] catalogueModele(int fournisseurIndex) {
        return switch (Reglages.FournisseurIa
                .values()[fournisseurIndex]) {
            case ANTHROPIC -> new String[]{
                    "claude-opus-4-1", "claude-sonnet-4-5",
                    "claude-haiku-4-5", "claude-3-7-sonnet",
                    "claude-3-5-haiku"};
            case GEMINI -> new String[]{
                    "gemini-2.5-pro", "gemini-2.5-flash",
                    "gemini-2.5-flash-lite", "gemini-2.0-flash"};
            case OPENROUTER -> new String[]{
                    "anthropic/claude-sonnet-4.5",
                    "openai/gpt-5.1", "google/gemini-2.5-pro",
                    "deepseek/deepseek-chat-v3.1",
                    "z-ai/glm-4.6", "qwen/qwen3-max"};
            default -> new String[]{
                    "gpt-5.1", "gpt-5", "gpt-4.1",
                    "gpt-4.1-mini", "gpt-4o", "o4-mini"};
        };
    }

    /** Le choix d'un modèle : écrit, pousse au service, retient. */
    private void choisirModele(int fournisseurIndex, String modele) {
        Reglages.FournisseurIa choisi = Reglages.FournisseurIa
                .values()[fournisseurIndex];
        Reglages.ecrireFournisseurIa(atelier.sourceReglages, choisi);
        Reglages.ecrireModeleIa(atelier.sourceReglages, modele);
        // lot U/7 : le modèle se retient POUR CE fournisseur aussi —
        // le modèle partagé ne traverse jamais les fournisseurs
        // (manière CodeAssist)
        Reglages.ecrireModelePour(atelier.sourceReglages, choisi, modele);
        if (atelier.agent != null) {
            atelier.agent.poserModele(choisi, modele);
        }
        atelier.etatAgent.poserModele(modele);
        atelier.vueAgent.rafraichir();
        pousserRecent(fournisseurIndex, modele);
    }

    /**
     * Le choix d'un fournisseur SEUL (tap de puce, fix du
     * 2026-11-17) : le fournisseur se PERSISTE à l'instant du choix —
     * avant, seuls le choix d'un MODÈLE ou le dialogue personnalisé
     * écrivaient le fournisseur : une puce tapée puis le popup fermé
     * laissait le fournisseur des réglages (ANTHROPIC par défaut) et
     * la requête partait chez Anthropic avec une clé absente
     * (« no key for anthropic », quel que soit le fournisseur choisi).
     *
     * <p>Le modèle suit, à la manière de choisirModele : le modèle
     * posé POUR CE fournisseur d'abord, le modèle partagé s'il lui
     * APPARTIENT, sinon la première suggestion du catalogue — le
     * garde d'appartenance ({@link Reglages#modeleAppartient})
     * empêche « claude-sonnet-4-5 » de partir chez OpenRouter.</p>
     */
    private void choisirFournisseur(Reglages.FournisseurIa choisi) {
        Reglages.ecrireFournisseurIa(atelier.sourceReglages, choisi);
        String modele = Reglages.lireModelePour(atelier.sourceReglages, choisi);
        if (modele.isEmpty()) {
            String partage = Reglages.lire(atelier.sourceReglages).modeleIa;
            if (partage != null
                    && Reglages.modeleAppartient(partage, choisi)) {
                modele = partage;
            }
        }
        if (!Reglages.modeleAppartient(modele, choisi)) {
            String[] catalogue = catalogueModele(choisi.ordinal());
            modele = catalogue.length > 0 ? catalogue[0] : "";
        }
        if (modele.isEmpty()) {
            // aucun modèle plausible pour ce fournisseur : le
            // fournisseur est déjà persisté, le modèle reste celui
            // des réglages — le garde du service refusera proprement
            return;
        }
        Reglages.ecrireModeleIa(atelier.sourceReglages, modele);
        Reglages.ecrireModelePour(atelier.sourceReglages, choisi, modele);
        if (atelier.agent != null) {
            atelier.agent.poserModele(choisi, modele);
        }
        atelier.etatAgent.poserModele(modele);
        atelier.vueAgent.rafraichir();
    }

    /** L'identifiant de stockage chiffré d'un fournisseur (mêmes
     *  noms qu'AgentService et Paramètres : anthropic, openai,
     *  gemini, openrouter). */
    private static String nomStockageCle(
            Reglages.FournisseurIa fournisseur) {
        return switch (fournisseur) {
            case ANTHROPIC -> "anthropic";
            case GEMINI -> "gemini";
            case OPENROUTER -> "openrouter";
            default -> "openai";
        };
    }

    /** L'étoile : basculer un favori (devant les suggestions). */
    private void basculeFavori(int fournisseurIndex, String modele,
                               boolean favori) {
        String cle = cleFavorisModeles(fournisseurIndex);
        List<String> favoris = lireListeModeles(cle);
        favoris.remove(modele);
        if (favori) {
            favoris.add(0, modele);
        }
        ecrireListeModeles(cle, favoris);
    }

    /** Un modèle choisi rejoint les récents (devant, borné à cinq). */
    private void pousserRecent(int fournisseurIndex, String modele) {
        String cle = cleRecentsModeles(fournisseurIndex);
        List<String> recents = lireListeModeles(cle);
        recents.remove(modele);
        recents.add(0, modele);
        while (recents.size() > 5) {
            recents.remove(recents.size() - 1);
        }
        ecrireListeModeles(cle, recents);
    }

    /** La liste persistée d'une clé (séparateur |, jamais vide). */
    private List<String> lireListeModeles(String cle) {
        String brut = atelier.sourceReglages.lire(cle);
        List<String> valeurs = new ArrayList<>();
        if (brut != null && !brut.isEmpty()) {
            for (String morceau : brut.split("\\|")) {
                if (!morceau.isEmpty()) {
                    valeurs.add(morceau);
                }
            }
        }
        return valeurs;
    }

    /** La liste persistée s'écrit d'un trait (séparateur |). */
    private void ecrireListeModeles(String cle, List<String> valeurs) {
        atelier.sourceReglages.ecrire(cle, String.join("|", valeurs));
    }

    private static String cleFavorisModeles(int fournisseurIndex) {
        return "atelier.agent.modeles.favoris." + fournisseurIndex;
    }

    private static String cleRecentsModeles(int fournisseurIndex) {
        return "atelier.agent.modeles.recents." + fournisseurIndex;
    }

    /**
     * Le dialogue du modèle personnalisé (lot S conservé) : le nom
     * libre et l'URL OpenAI-compatible s'écrivent au tap sur OK —
     * le couple part au service sans quitter la conversation.
     */
    private void dialogueModelePersonnalise(int fournisseurIndex) {
        Reglages reglages = Reglages.lire(atelier.sourceReglages);
        Reglages.FournisseurIa[] fournisseurs =
                Reglages.FournisseurIa.values();
        LinearLayout corps = new LinearLayout(atelier);
        corps.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(atelier, 4);
        corps.setPadding(marge, marge, marge, marge);

        TextView etiquetteFournisseur = new TextView(atelier);
        GraphiteVue.typographie(etiquetteFournisseur,
                Graphite.LEGENDE);
        etiquetteFournisseur.setTextColor(atelier.palette.texteEstompe);
        etiquetteFournisseur.setText(R.string.agent_fournisseur_section);
        etiquetteFournisseur.setPadding(0, marge, 0, marge);
        corps.addView(etiquetteFournisseur);
        TextView valeurFournisseur = new TextView(atelier);
        GraphiteVue.typographie(valeurFournisseur, Graphite.CORPS);
        valeurFournisseur.setTextColor(atelier.palette.texte);
        valeurFournisseur.setText(nomFournisseur(
                fournisseurs[Math.max(0, Math.min(fournisseurIndex,
                        fournisseurs.length - 1))]));
        corps.addView(valeurFournisseur);

        TextView etiquetteModele = new TextView(atelier);
        GraphiteVue.typographie(etiquetteModele, Graphite.LEGENDE);
        etiquetteModele.setTextColor(atelier.palette.texteEstompe);
        etiquetteModele.setText(R.string.parametres_modele_ia);
        etiquetteModele.setPadding(0, GraphiteVue.dp(atelier, 12), 0,
                marge);
        corps.addView(etiquetteModele);
        android.widget.EditText saisieModele =
                new android.widget.EditText(atelier);
        saisieModele.setText(reglages.modeleIa);
        saisieModele.setHint(R.string.parametres_modele_indice);
        saisieModele.setSingleLine(true);
        corps.addView(saisieModele);

        TextView etiquetteUrl = new TextView(atelier);
        GraphiteVue.typographie(etiquetteUrl, Graphite.LEGENDE);
        etiquetteUrl.setTextColor(atelier.palette.texteEstompe);
        etiquetteUrl.setText(R.string.parametres_url_ia);
        etiquetteUrl.setPadding(0, GraphiteVue.dp(atelier, 12), 0,
                marge);
        corps.addView(etiquetteUrl);
        android.widget.EditText saisieUrl =
                new android.widget.EditText(atelier);
        // lot U : l'URL d'OpenRouter est propre à la passerelle —
        // préremplie pour la voir, jamais devinée de l'utilisateur
        int indexCourant = Math.max(0, Math.min(fournisseurIndex,
                fournisseurs.length - 1));
        saisieUrl.setText(reglages.urlIa.isEmpty()
                && Reglages.FournisseurIa.OPENROUTER
                        == fournisseurs[indexCourant]
                ? AgentService.URL_OPENROUTER
                : reglages.urlIa);
        saisieUrl.setHint(R.string.parametres_url_indice);
        saisieUrl.setSingleLine(true);
        corps.addView(saisieUrl);

        TextView gererCles = new TextView(atelier);
        GraphiteVue.typographie(gererCles, Graphite.CORPS);
        gererCles.setTextColor(atelier.palette.accent);
        gererCles.setText(R.string.agent_cles_gerer);
        gererCles.setPadding(0, GraphiteVue.dp(atelier, 12), 0, 0);
        gererCles.setOnClickListener(vue -> {
            try {
                atelier.startActivity(new Intent(atelier,
                        jo.joide.app.android.ecrans.parametres
                                .EcranParametres.class));
            } catch (RuntimeException e) {
                // les réglages restent atteignables par le menu
            }
        });
        corps.addView(gererCles);

        Dialogue.saisie(atelier, atelier.palette,
                atelier.getString(R.string.agent_modele_titre), corps,
                R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }
                    Reglages.FournisseurIa choisi =
                            fournisseurs[Math.max(0,
                                    Math.min(fournisseurIndex,
                                            fournisseurs.length - 1))];
                    String modele = saisieModele.getText()
                            .toString().trim();
                    if (modele.isEmpty()) {
                        modele = reglages.modeleIa;
                    }
                    // le choix PERSISTE D'ABORD : une URL refusée ne
                    // doit JAMAIS annuler le fournisseur et le modèle
                    // (rapport du 2026-11-17 — l'URL écartée laissait
                    // tout le dialogue sur Anthropic par défaut)
                    Reglages.ecrireFournisseurIa(atelier.sourceReglages, choisi);
                    Reglages.ecrireModeleIa(atelier.sourceReglages, modele);
                    // lot U/7 : le modèle se retient POUR CE
                    // fournisseur aussi (manière CodeAssist)
                    Reglages.ecrireModelePour(atelier.sourceReglages, choisi,
                            modele);
                    String url = saisieUrl.getText().toString().trim();
                    try {
                        // la correction du 2026-11-18 : l'API
                        // personnalisée se rattache au provider
                        // choisi (une URL OpenRouter ne part plus
                        // chez OpenAI)
                        Reglages.ecrireUrlPour(atelier.sourceReglages, choisi,
                                url);
                    } catch (IllegalArgumentException e) {
                        Dialogue.information(atelier, atelier.palette,
                                atelier.getString(R.string.agent_modele_titre),
                                atelier.getString(
                                        R.string.parametres_valeur_invalide),
                                R.string.action_ok);
                        // pas de return : le choix est déjà persisté
                    }
                    if (atelier.agent != null) {
                        atelier.agent.poserModele(choisi, modele);
                    }
                    atelier.etatAgent.poserModele(modele);
                    atelier.vueAgent.rafraichir();
                });
    }

    /** Le nom lisible d'un fournisseur. */
    private CharSequence nomFournisseur(
            Reglages.FournisseurIa fournisseur) {
        return atelier.getString(switch (fournisseur) {
            case ANTHROPIC -> R.string.agent_fournisseur_anthropic;
            case GEMINI -> R.string.agent_fournisseur_gemini;
            case OPENROUTER -> R.string.agent_fournisseur_openrouter;
            default -> R.string.agent_fournisseur_openai;
        });
    }

    /**
     * L'écran Usages (lot T, ADR-065 — la manière CodeAssist) : un
     * COMPTEUR par fenêtre plafonnée (requêtes, jetons), sa bande
     * colorée et son pourcentage, puis les trois pieds de la
     * conversation et l'état du banc de clés. Le popup ancré porte
     * son bouton d'actualisation — l'occupation se relit sans rouvrir.
     */
    void ouvrirUsagesAgent() {
        if (atelier.agent == null || atelier.vueAgent == null) {
            return;
        }
        LinearLayout colonne = new LinearLayout(atelier);
        colonne.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(atelier, 6);
        colonne.setPadding(marge, marge, marge, marge);

        // ---- l'en-tête : titre, actualiser, fermer (la manière
        // CodeAssist — le dialogue se suffit, jamais un seul OK) ----
        LinearLayout tete = new LinearLayout(atelier);
        tete.setOrientation(LinearLayout.HORIZONTAL);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        TextView titre = new TextView(atelier);
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(atelier.palette.texte);
        titre.setText(R.string.agent_usages_titre);
        tete.addView(titre, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Icone rafraichir = new Icone(atelier);
        rafraichir.setGlyphe(Icone.Glyphe.RAFRAICHIR);
        rafraichir.setCouleur(atelier.palette.texteSecondaire);
        rafraichir.setClickable(true);
        rafraichir.setContentDescription(
                atelier.getString(R.string.agent_usages_rafraichir));
        LinearLayout corps = new LinearLayout(atelier);
        corps.setOrientation(LinearLayout.VERTICAL);
        rafraichir.setOnClickListener(vue -> {
            corps.removeAllViews();
            construireUsages(corps);
        });
        tete.addView(rafraichir, new LinearLayout.LayoutParams(
                GraphiteVue.dp(atelier, 30),
                GraphiteVue.dp(atelier, 30)));
        Icone fermer = new Icone(atelier);
        fermer.setGlyphe(Icone.Glyphe.CROIX);
        fermer.setCouleur(atelier.palette.texteSecondaire);
        fermer.setClickable(true);
        fermer.setContentDescription(atelier.getString(R.string.action_annuler));
        tete.addView(fermer, new LinearLayout.LayoutParams(
                GraphiteVue.dp(atelier, 30),
                GraphiteVue.dp(atelier, 30)));
        colonne.addView(tete);
        colonne.addView(corps);
        construireUsages(corps);

        android.widget.PopupWindow[] fenetre =
                new android.widget.PopupWindow[1];
        fermer.setOnClickListener(vue -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        });
        fenetre[0] = PopupAncre.montrer(atelier.vueAgent.ancreMenu(), atelier.palette,
                colonne, 300);
    }

    /** Le corps de l'écran Usages : compteurs, pieds, banc. */
    private void construireUsages(LinearLayout corps) {
        // ---- les compteurs du régulateur (l'occupation des fenêtres
        // du modèle courant — la bande prévient AVANT le 429) ----
        double[] occupations = atelier.agent.occupationsModele();
        double requetes = occupations[0];
        double jetons = occupations[1];
        boolean plafondConnu = requetes > 0 || jetons > 0;
        if (plafondConnu) {
            ligneCompteur(corps,
                    atelier.getString(R.string.agent_usages_minute_requetes),
                    requetes);
            ligneCompteur(corps,
                    atelier.getString(R.string.agent_usages_minute_jetons),
                    jetons);
        } else {
            TextView sansPlafond = new TextView(atelier);
            GraphiteVue.typographie(sansPlafond, Graphite.LEGENDE);
            sansPlafond.setTextColor(atelier.palette.texteEstompe);
            sansPlafond.setText(R.string.agent_usages_sans_plafond);
            sansPlafond.setPadding(0, GraphiteVue.dp(atelier, 6), 0,
                    GraphiteVue.dp(atelier, 4));
            corps.addView(sansPlafond);
        }

        // ---- les trois pieds de la conversation (ce qui est facturé
        // est facturé — la légende compacte du précédent CodeAssist)
        jo.joide.agent.UsageLlm usage = atelier.agent.usageConversation();
        TextView conversation = new TextView(atelier);
        GraphiteVue.typographie(conversation, Graphite.CORPS);
        conversation.setTextColor(atelier.palette.texte);
        conversation.setText(atelier.getString(
                R.string.agent_usages_conversation_compacte,
                EtatAgentChat.jetonsCompacts(usage.entrees),
                EtatAgentChat.jetonsCompacts(usage.sorties),
                EtatAgentChat.jetonsCompacts(usage.lecturesCache)));
        conversation.setPadding(0, GraphiteVue.dp(atelier, 10), 0,
                GraphiteVue.dp(atelier, 4));
        corps.addView(conversation);

        // ---- l'état du banc de clés ----
        int taille = atelier.agent.tailleBanc();
        TextView banc = new TextView(atelier);
        GraphiteVue.typographie(banc, Graphite.CORPS);
        banc.setTextColor(atelier.palette.texte);
        banc.setText(taille == 0
                ? atelier.getString(R.string.agent_usages_banc_vide)
                : atelier.getString(R.string.agent_usages_banc, taille));
        banc.setPadding(0, GraphiteVue.dp(atelier, 8), 0,
                GraphiteVue.dp(atelier, 2));
        corps.addView(banc);

        for (var assise : atelier.agent.bancParRaison().entrySet()) {
            TextView ligne = new TextView(atelier);
            GraphiteVue.typographie(ligne, Graphite.LEGENDE);
            ligne.setTextColor(atelier.palette.texteSecondaire);
            ligne.setText(atelier.getString(R.string.agent_usages_assis,
                    assise.getValue(),
                    libelleGenre(assise.getKey())));
            ligne.setPadding(0, GraphiteVue.dp(atelier, 3), 0, 0);
            corps.addView(ligne);
        }
    }

    /** Un compteur d'occupation : nom, pourcentage, sa bande colorée. */
    private void ligneCompteur(LinearLayout corps, String nom,
                               double fraction) {
        LinearLayout rangee = new LinearLayout(atelier);
        rangee.setOrientation(LinearLayout.HORIZONTAL);
        rangee.setGravity(Gravity.CENTER_VERTICAL);
        rangee.setPadding(0, GraphiteVue.dp(atelier, 6), 0, 0);
        TextView nomVue = new TextView(atelier);
        GraphiteVue.typographie(nomVue, Graphite.CORPS);
        nomVue.setTextColor(atelier.palette.texte);
        nomVue.setText(nom);
        rangee.addView(nomVue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView pourcentage = new TextView(atelier);
        GraphiteVue.typographie(pourcentage, Graphite.LEGENDE);
        pourcentage.setTextColor(atelier.palette.texteEstompe);
        pourcentage.setText(atelier.getString(
                R.string.agent_usages_occupation,
                (int) Math.round(fraction * 100)));
        rangee.addView(pourcentage);
        corps.addView(rangee);
        corps.addView(barreUsage(fraction));
    }

    /**
     * La bande d'usage : une piste pilule, un remplissage qui suit la
     * fraction — la couleur dit la bande (accent, alerte, erreur),
     * l'alerte précède l'erreur (BandesUsage, ADR-062).
     */
    private View barreUsage(double fraction) {
        float rempli = (float) Math.max(0.02,
                Math.min(1, fraction));
        LinearLayout barre = new LinearLayout(atelier);
        barre.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable piste =
                new android.graphics.drawable.GradientDrawable();
        piste.setColor(atelier.palette.surfaceHaute);
        piste.setCornerRadius(GraphiteVue.dp(atelier, 3));
        barre.setBackground(piste);
        barre.setClipToOutline(true);
        View remplissage = new View(atelier);
        jo.joide.agent.BandesUsage.Bande bande =
                jo.joide.agent.BandesUsage.bandeDe(fraction);
        int couleur = switch (bande) {
            case CRITIQUE -> atelier.palette.erreur;
            case ATTENTION -> atelier.palette.alerte;
            default -> atelier.palette.accent;
        };
        android.graphics.drawable.GradientDrawable jauge =
                new android.graphics.drawable.GradientDrawable();
        jauge.setColor(couleur);
        jauge.setCornerRadius(GraphiteVue.dp(atelier, 3));
        remplissage.setBackground(jauge);
        barre.addView(remplissage, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, rempli));
        View reste = new View(atelier);
        barre.addView(reste, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f - rempli));
        LinearLayout.LayoutParams params = new LinearLayout
                .LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(atelier, 6));
        params.setMargins(0, GraphiteVue.dp(atelier, 4), 0, 0);
        barre.setLayoutParams(params);
        return barre;
    }

    /** Le libellé localisé d'un genre d'assise du banc. */
    private CharSequence libelleGenre(
            jo.joide.agent.ErreursLlm.Genre genre) {
        return atelier.getString(switch (genre) {
            case LIMITE_DEBIT -> R.string.agent_genre_limite_debit;
            case LIMITE_JOUR -> R.string.agent_genre_limite_jour;
            case QUOTA_EPUISE -> R.string.agent_genre_quota;
            case CLE_INVALIDE -> R.string.agent_genre_cle_invalide;
            case CLE_SANS_DROIT -> R.string.agent_genre_cle_sans_droit;
            default -> R.string.agent_genre_autre;
        });
    }

    /** Nouvelle conversation : le service garde la vérité, l'état suit. */
    void nouvelleConversationAgent() {
        if (atelier.agent == null) {
            return;
        }
        if (!atelier.agent.nouvelleConversation()) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.agent_menu_titre),
                    atelier.getString(R.string.agent_tour_en_cours),
                    R.string.action_ok);
            return;
        }
        rejouerJournalAgent();
    }

    /**
     * L'historique des conversations : titre dérivé du premier
     * message, nombre d'échanges, la courante marquée, la
     * suppression demandée nommément.
     */
    void ouvrirHistoriqueAgent() {
        if (atelier.agent == null) {
            return;
        }
        java.util.List<ConversationsAgent.Resume> resumes =
                atelier.agent.conversations();
        ScrollView defilement = new ScrollView(atelier);
        LinearLayout colonne = new LinearLayout(atelier);
        colonne.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(atelier, 4);
        colonne.setPadding(marge, marge, marge, marge);
        defilement.addView(colonne, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        String active = atelier.agent.conversationActive();
        if (resumes.isEmpty()) {
            TextView vide = new TextView(atelier);
            GraphiteVue.typographie(vide, Graphite.CORPS);
            vide.setTextColor(atelier.palette.texteEstompe);
            vide.setText(atelier.getString(R.string.agent_historique_vide,
                    ConversationsAgent.CONVERSATIONS_MAX));
            vide.setPadding(0, GraphiteVue.dp(atelier, 12), 0,
                    GraphiteVue.dp(atelier, 12));
            colonne.addView(vide);
        }
        java.text.DateFormat formatDate =
                    java.text.DateFormat.getDateInstance(
                            java.text.DateFormat.SHORT);
        java.text.DateFormat formatHeure =
                    java.text.DateFormat.getTimeInstance(
                            java.text.DateFormat.SHORT);
        for (ConversationsAgent.Resume resume : resumes) {
            colonne.addView(ligneConversation(resume,
                    resume.id.equals(active), formatDate, formatHeure));
        }
        // le popup ancré (lot T) : l'historique vit près du menu qui
        // l'appelle, jamais un dialogue centré qui coupe le fil
        if (atelier.vueAgent != null) {
            PopupAncre.montrer(atelier.vueAgent.ancreMenu(), atelier.palette,
                    defilement, 320);
        }
    }

    /** Une ligne de l'historique : titre, méta, la courante marquée. */
    private View ligneConversation(ConversationsAgent.Resume resume,
                                   boolean active,
                                   java.text.DateFormat formatDate,
                                   java.text.DateFormat formatHeure) {
        Context contexte = atelier;
        LinearLayout ligne = new LinearLayout(contexte);
        ligne.setOrientation(LinearLayout.VERTICAL);
        int padding = GraphiteVue.dp(contexte, 10);
        ligne.setPadding(padding, padding, padding, padding);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(active ? atelier.palette.surfaceHaute : atelier.palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte,
                Graphite.RAYON_COMPOSANT));
        fond.setStroke(GraphiteVue.dp(contexte, 1), active
                ? atelier.palette.accent : atelier.palette.bordFin);
        ligne.setBackground(fond);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int marge = GraphiteVue.dp(contexte, 4);
        params.setMargins(0, marge, 0, marge);
        ligne.setLayoutParams(params);

        TextView titre = new TextView(contexte);
        GraphiteVue.typographie(titre, Graphite.CORPS);
        titre.setTextColor(atelier.palette.texte);
        titre.setMaxLines(2);
        titre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titre.setText(resume.titre.isEmpty()
                ? atelier.getString(R.string.agent_historique_sans_titre)
                : resume.titre);
        ligne.addView(titre);

        TextView meta = new TextView(contexte);
        GraphiteVue.typographie(meta, Graphite.LEGENDE);
        meta.setTextColor(active ? atelier.palette.accent
                : atelier.palette.texteEstompe);
        meta.setText((active
                ? atelier.getString(R.string.agent_historique_active) + " · "
                : "")
                + atelier.getString(R.string.agent_historique_echanges,
                        resume.echanges)
                + " · " + formatDate.format(new java.util.Date(
                        resume.modifiee))
                + " " + formatHeure.format(new java.util.Date(
                        resume.modifiee)));
        meta.setPadding(0, GraphiteVue.dp(contexte, 3), 0, 0);
        ligne.addView(meta);

        if (!active) {
            TextView reprendre = new TextView(contexte);
            GraphiteVue.typographie(reprendre, Graphite.CORPS);
            reprendre.setTextColor(atelier.palette.accent);
            reprendre.setText(R.string.agent_menu_historique);
            reprendre.setPadding(0, GraphiteVue.dp(contexte, 6), 0, 0);
            ligne.setOnClickListener(vue ->
                    reprendreConversationAgent(resume.id));
            ligne.addView(reprendre);
            TextView supprimer = new TextView(contexte);
            GraphiteVue.typographie(supprimer, Graphite.CORPS);
            supprimer.setTextColor(atelier.palette.erreur);
            supprimer.setText(R.string.agent_supprimer_conversation);
            supprimer.setPadding(0, GraphiteVue.dp(contexte, 2), 0, 0);
            supprimer.setOnClickListener(vue ->
                    supprimerConversationAgent(resume.id));
            ligne.addView(supprimer);
        }
        return ligne;
    }

    /** Reprend une conversation : l'état du tiroir suit le service. */
    void reprendreConversationAgent(String id) {
        if (atelier.agent == null) {
            return;
        }
        if (!atelier.agent.reprendreConversation(id)) {
            Dialogue.information(atelier, atelier.palette,
                    atelier.getString(R.string.agent_menu_titre),
                    atelier.getString(R.string.agent_tour_en_cours),
                    R.string.action_ok);
            return;
        }
        rejouerJournalAgent();
    }

    /** Supprime une conversation : la demande est nommée et confirmée. */
    private void supprimerConversationAgent(String id) {
        if (atelier.agent == null) {
            return;
        }
        Dialogue.confirmation(atelier, atelier.palette,
                atelier.getString(R.string.agent_supprimer_conversation),
                atelier.getString(R.string.agent_supprimer_detail),
                R.string.action_annuler, R.string.agent_menu_historique,
                (dialog, lequel) -> {
                    if (atelier.agent != null) {
                        atelier.agent.supprimerConversation(id);
                    }
                    ouvrirHistoriqueAgent();
                });
    }

    void repondrePermissionAgent(boolean accordee) {
        atelier.etatAgent.repondrePermission(accordee);
        if (atelier.agent != null) {
            atelier.agent.repondrePermission(accordee);
        }
        atelier.vueAgent.rafraichir();
    }

    void repondreQuestionAgent(int index, int choix) {
        atelier.etatAgent.repondre(index, choix, null);
        java.util.List<String> reponses = atelier.etatAgent.reponses();
        if (reponses != null && atelier.agent != null) {
            atelier.agent.repondreQuestions(reponses);
        }
        atelier.vueAgent.rafraichir();
    }

    /**
     * « Demander à l'IA » (ADR-É11 point 10) : le tiroir s'ouvre, le
     * composeur est prérempli avec le fichier ACTIF quoté (v1 : le
     * document entier borné ; la sélection précise attend l'API
     * celeris — écart consigné au backlog).
     */
    void demanderIa() {
        ouvrirAgent();
        String actif = atelier.etat.actif();
        if (actif == null || atelier.scene == null) {
            return;
        }
        String texte = atelier.scene.texte();
        int plafond = 8_000;
        String borne = texte.length() > plafond
                ? texte.substring(0, plafond)
                        + "\n[… tronqué — "
                        + (texte.length() - plafond)
                        + " caractères de plus]"
                : texte;
        atelier.etatAgent.poserPreRemplissage(actif, borne);
        atelier.vueAgent.poserEtat(atelier.etatAgent);
    }

}
