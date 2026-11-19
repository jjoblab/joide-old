package jo.joide.app.android.ecrans.atelier;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;

import java.io.IOException;
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
import jo.joide.app.android.atelier.agent.PieceJointe;
import jo.joide.app.android.databinding.PopupPermissionDiffBinding;
import jo.joide.app.android.ui.PortePopups;

/**
 * L6 — le contrôleur de l'agent : le service, le tour, les pièces
 * jointes et la permission sur diff déplié. La vérité reste dans
 * EtatAgentChat (porté par l'hôte) et le service ; le contrôleur
 * orchestre et met à jour les vues via l'hôte.
 *
 * <p>Lot A7 (ADR-085, D3 et découpage) : les menus deviennent des
 * popups ancrés ({@code popup_menu_noeud} gonflé par le
 * porte-popups), la permission devient la carte centrée
 * {@code popup_permission_diff} sur voile, les résultats SAF/caméra
 * passent par les CONTRATS TYPÉS {@code Ecrans.CHOISIR_IMAGE} et
 * {@code Ecrans.PRENDRE_PHOTO} (décision 13 : les codes 47101 et
 * 47102 disparaissent). Les MODELS, USAGES, HISTORIQUE et CONNEXIONS
 * vivent désormais dans leurs contrôleurs ({@code ControleurModelesAgent},
 * {@code ControleurUsagesAgent}, {@code ControleurHistoriqueAgent},
 * {@code ControleurConnexionsMcp}). Plus AUCUNE vue du cadre
 * n'est instanciée ici : les corps vivent dans {@code popup_*.xml}
 * gonflés par binding.</p>
 *
 * <p>Lot A6 (ADR-085 décision 3) : le contrôleur dépend de
 * {@link HoteAtelier} — plus un champ d'écran.</p>
 */
final class ControleurAgent {

    private final HoteAtelier hote;
    private final ControleurModelesAgent modeles;
    private final ControleurUsagesAgent usages;
    private final ControleurHistoriqueAgent historique;
    private final ControleurConnexionsMcp connexions;

    ControleurAgent(HoteAtelier hote, ControleurModelesAgent modeles,
                    ControleurUsagesAgent usages,
                    ControleurHistoriqueAgent historique,
                    ControleurConnexionsMcp connexions) {
        this.hote = hote;
        this.modeles = modeles;
        this.usages = usages;
        this.historique = historique;
        this.connexions = connexions;
    }

    /** L'arrêt du tour (le bouton ■ du composeur). */
    void arreterTour() {
        if (hote.agent() != null) {
            hote.agent().arreter();
        }
    }

    /** L'écran Usages (la bande d'avertissement tapée — lot C4). */
    void ouvrirUsages() {
        usages.ouvrir();
    }

    /**
     * Lot C4 : la bande d'avertissement d'usage du tiroir suit le tour
     * — la fenêtre qui PORTE le modèle courant (la plus chargée des
     * deux) à ≥ 80 % se dit au-dessus du composeur, la manière
     * l'IDE de référence du bandeau de limite (le tap ouvre l'écran Usages).
     */
    private void majAvertissementUsage() {
        if (hote.agent() == null || hote.vueAgent() == null) {
            return;
        }
        long[] compteurs = hote.agent().compteursModele();
        if (compteurs[1] <= 0 && compteurs[3] <= 0) {
            // sans plafond appris, la bande ne supplie JAMAIS
            hote.vueAgent().poserAvertissementUsage(0, true, 0, null);
            return;
        }
        double[] occupations = hote.agent().occupationsModele();
        boolean requetes = compteurs[1] > 0
                && occupations[0] >= occupations[1];
        double fraction = Math.max(occupations[0], occupations[1]);
        long restant = requetes
                ? Math.max(0, compteurs[1] - compteurs[0])
                : Math.max(0, compteurs[3] - compteurs[2]);
        long[] relances = hote.agent().relancesModele();
        long relance = requetes ? relances[0] : relances[1];
        String texte = null;
        if (relance > 0) {
            texte = ControleurUsagesAgent.texteRelance(
                    hote, relance - System.currentTimeMillis());
        }
        hote.vueAgent().poserAvertissementUsage(
                fraction, requetes, restant, texte);
    }

    /** Le mode de permission posé par la puce d'en-tête. */
    void poserMode(Permissions.Mode mode) {
        hote.etatAgent().poserMode(mode);
        if (hote.agent() != null) {
            hote.agent().poserMode(mode);
        }
        hote.vueAgent().rafraichir();
    }

    /** Une pièce retirée du composeur (le tap sur sa puce). */
    void retirerPiece(int index) {
        if (index >= 0 && index < hote.piecesAgent().size()) {
            hote.piecesAgent().remove(index);
            hote.vueAgent().poserPieces(
                    PieceJointe.noms(hote.piecesAgent()));
        }
    }

    // ------------------------------------------------------------------
    // Les résultats SAF/caméra : les CONTRATS TYPÉS (décision 13, lot
    // A7 — les codes 47101/47102 et onActivityResult disparaissent)
    // ------------------------------------------------------------------

    /** L'image choisie arrive TYPÉE (contrat Ecrans.CHOISIR_IMAGE). */
    void surImageChoisie(Uri adresse) {
        if (adresse == null) {
            // annulation : aucun attachement silencieux
            return;
        }
        attacherImage(adresse, nomDadresse(adresse));
    }

    /** La photo prise arrive TYPÉE (contrat Ecrans.PRENDRE_PHOTO). */
    void surPhotoPrise(boolean prise) {
        if (!prise) {
            // annulation du capteur : aucun attachement silencieux
            return;
        }
        Path photo = FournisseurCameraAgent.dernierePhoto();
        if (photo != null && Files.isRegularFile(photo)) {
            attacherImage(Uri.fromFile(photo.toFile()),
                    photo.getFileName().toString());
        }
    }

    /** Le débranchement du service (onDestroy de l'écran). */
    void debrancherService() {
        if (hote.agent() != null) {
            try {
                hote.delierService(connexionAgent());
            } catch (RuntimeException e) {
                // le service partait déjà
            }
            hote.poserAgent(null);
        }
    }

    // ------------------------------------------------------------------
    // Le service de l'agent (premier plan, pattern ADR-043)
    // ------------------------------------------------------------------

    void ouvrirAgent() {
        // le tiroir se MONTE à la demande (stub) : l'appel gonfle la
        // surcouche la première fois
        hote.tiroirAgent().ouvrir();
        if (hote.agent() != null) {
            brancherEspaceAgent();
            historique.rejouerJournal();
            return;
        }
        AgentService.demarrer(hote.contexte());
        hote.lierService(new Intent(hote.contexte(), AgentService.class),
                connexionAgent(), Context.BIND_AUTO_CREATE);
    }

    void fermerAgent() {
        if (hote.tiroirAgentOuvert()) {
            hote.tiroirAgent().fermer();
        }
    }

    private android.content.ServiceConnection connexionAgent() {
        return new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(
                    android.content.ComponentName nom,
                    android.os.IBinder service) {
                hote.poserAgent((AgentService.AgentBinder) service);
                brancherEspaceAgent();
                hote.agent().poserEcouteur(VueAgentEntree -> hote.executerSurFilUi(
                        () -> surEntreeAgent(VueAgentEntree)));
                historique.rejouerJournal();
                // le message parti pendant le bind s'en va MAINTENANT
                hote.executerSurFilUi(() -> depecherAttenteAgent());
            }

            @Override
            public void onServiceDisconnected(
                    android.content.ComponentName nom) {
                hote.poserAgent(null);
            }
        };
    }

    /** Construit l'espace de travail du projet pour l'agent de
     *  l'hôte (le binder du service). */
    private void brancherEspaceAgent() {
        if (hote.agent() == null || hote.racineProjet() == null || hote.scene() == null) {
            return;
        }
        EspaceTravailIde espace = new EspaceTravailIde(hote.racineProjet(),
                hote.modele()).poserMoteurs(hote.moteurs());
        espace.poserOverlayEditeur(chemin -> {
            String actif = hote.etat().actif();
            if (actif != null && actif.equals(chemin)) {
                return hote.scene().texte();
            }
            return null;
        });
        hote.agent().poserEspace(new EspaceCheckpoint(espace));
        Reglages reglages = Reglages.lire(hote.sourceReglages());
        hote.agent().poserModele(reglages.fournisseurIa, reglages.modeleIa);
        // le .mcp.json du projet, lu borné, part au service : les
        // serveurs déclarés sont parsés, pas activés (ADR-063)
        hote.agent().poserMcpJson(connexions.lireMcpJson());
        // lot C6 : les serveurs ajoutés à la main et le bloc des
        // compétences actives partent AU SERVICE dès la liaison —
        // une session qui revient les retrouve appliqués
        connexions.poserServeursManuels();
        connexions.poserCompetences();
        // l'approbation PERSISTÉE par projet (lot T, ADR-065) : liée
        // à la signature du fichier qui l'a motivée — le même
        // .mcp.json retrouve ses serveurs, un fichier changé redemande
        // le geste explicite de l'utilisateur
        if ("1".equals(hote.sourceReglages().lire(
                connexions.cleApprobationMcp()))) {
            hote.agent().poserApprobationMcp(true);
        }
        hote.etatAgent().poserModele(reglages.modeleIa);
        hote.etatAgent().poserMode(EtatAgentChat.TourEtat.EN_COURS
                == hote.etatAgent().etat()
                ? hote.etatAgent().mode() : Permissions.Mode.DEMANDE_CHAQUE);
    }

    /** L'entrée live du service : événement ou message. */
    private void surEntreeAgent(AgentService.Entree entree) {
        if (entree.estMessage()) {
            hote.etatAgent().envoyer(entree.message, entree.pieces);
        } else if (entree.estReponse()) {
            hote.etatAgent().restaurerReponse(entree.reponse);
        } else {
            hote.etatAgent().surEvenement(entree.evenement);
        }
        historique.majQuestions();
        hote.vueAgent().poserEtat(hote.etatAgent());
        majAvertissementUsage();
        // la fin d'un tour libère le message retenu pendant le bind
        // (session revenue sur un tour vivant)
        if (hote.messageEnAttenteAgent() != null
                && hote.etatAgent().etat() != EtatAgentChat.TourEtat.EN_COURS) {
            depecherAttenteAgent();
        }
        if (hote.pointAgent() != null) {
            hote.pointAgent().setVisibility(
                    hote.etatAgent().etat()
                            == EtatAgentChat.TourEtat.EN_COURS
                            ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        Permissions.DemandeEcriture permission =
                hote.agent() == null ? null : hote.agent().permissionEnAttente();
        if (permission != null) {
            montrerPermissionAgent(permission);
        }
    }

    void envoyerAgent(String message) {
        if (hote.agent() == null) {
            ouvrirAgent();
        }
        if (hote.agent() == null) {
            // le bind est ASYNCHRONE : le message part dès la liaison
            // (onServiceConnected) — plus jamais perdu silencieusement
            // (fix du 2026-11-17 : le premier message d'une session
            // disparaissait quand le service n'était pas encore lié)
            hote.poserMessageEnAttenteAgent(
                    PieceJointe.fondre(message, hote.piecesAgent()));
            hote.piecesEnAttenteAgent().clear();
            hote.piecesEnAttenteAgent().addAll(hote.piecesAgent());
            hote.piecesAgent().clear();
            hote.vueAgent().poserPieces(List.of());
            return;
        }
        if (hote.agent().tourEnCours()) {
            // le service n'empile jamais deux tours — les pièces
            // restent posées, l'utilisateur renverra après l'arrêt
            return;
        }
        // les fichiers et l'arbre se fondent dans le message, les
        // images partent multimodales (lot S)
        String fondu = PieceJointe.fondre(message, hote.piecesAgent());
        hote.agent().envoyer(fondu, List.copyOf(hote.piecesAgent()));
        hote.piecesAgent().clear();
        hote.vueAgent().poserPieces(List.of());
    }

    /** Le départ du message retenu : la liaison du service est là
     *  (ou le tour en cours vient de finir). */
    private void depecherAttenteAgent() {
        if (hote.agent() == null || hote.messageEnAttenteAgent() == null
                || hote.agent().tourEnCours()) {
            return;
        }
        String message = hote.messageEnAttenteAgent();
        List<PieceJointe> pieces = List.copyOf(hote.piecesEnAttenteAgent());
        hote.poserMessageEnAttenteAgent(null);
        hote.piecesEnAttenteAgent().clear();
        hote.agent().envoyer(message, pieces);
    }

    // ------------------------------------------------------------------
    // Les pièces jointes (lot S, ADR-064) : image, photo, fichier
    // courant, projet — le menu ne montre que ce que l'appareil
    // sait faire (caméra présente, fichier actif ouvert)
    // ------------------------------------------------------------------

    /** Le menu + ancré à son bouton (lot T) : chaque entrée est
     *  câblée, jamais morte. */
    void ouvrirAjoutsAgent() {
        if (hote.vueAgent() == null) {
            return;
        }
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_ajouter_image),
                this::choisirImageAgent));
        if (hote.possedeCamera()) {
            entrees.add(new PortePopups.EntreeMenu(
                    hote.texte(R.string.agent_ajouter_photo),
                    this::prendrePhotoAgent));
        }
        if (hote.etat().actif() != null) {
            entrees.add(new PortePopups.EntreeMenu(
                    hote.texte(R.string.agent_ajouter_fichier),
                    this::joindreFichierCourant));
        }
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_ajouter_projet),
                this::joindreProjet));
        hote.popups().menu(hote.vueAgent().ancreAjouter(),
                null, 240, entrees);
    }

    /**
     * Le choix d'une image : SAF par le CONTRAT TYPÉ
     * {@code Ecrans.CHOISIR_IMAGE} (GetContent image/*) — le code
     * 47101 et onActivityResult disparaissent (décision 13).
     */
    private void choisirImageAgent() {
        hote.choisirImageAgent().launch("image/*");
    }

    /**
     * La photo : cible du cache servie par le fournisseur local, au
     * CONTRAT TYPÉ {@code Ecrans.PRENDRE_PHOTO} (le code 47102
     * disparaît) ; l'échec de préparation se dit nommément.
     */
    private void prendrePhotoAgent() {
        try {
            android.net.Uri cible = FournisseurCameraAgent.preparer(
                    hote.contexte());
            hote.prendrePhotoAgent().launch(cible);
        } catch (IOException | RuntimeException e) {
            hote.popups().information(
                    hote.texte(R.string.agent_ajouter),
                    hote.texte(R.string.agent_image_indisponible),
                    hote.texte(R.string.action_ok));
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
                     hote.resolveurContenu().openInputStream(adresse)) {
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
            hote.popups().information(
                    hote.texte(R.string.agent_ajouter),
                    hote.texte(R.string.agent_image_indisponible),
                    hote.texte(R.string.action_ok));
            return;
        }
        if (octets.length > 8_000_000) {
            hote.popups().information(
                    hote.texte(R.string.agent_ajouter),
                    hote.texte(R.string.agent_image_trop_grande),
                    hote.texte(R.string.action_ok));
            return;
        }
        String mediaType = hote.resolveurContenu().getType(adresse);
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
            hote.popups().information(
                    hote.texte(R.string.agent_ajouter),
                    hote.texte(R.string.agent_image_trop_grande),
                    hote.texte(R.string.action_ok));
            return;
        }
        hote.piecesAgent().add(PieceJointe.image(
                nom.isEmpty() ? "image" : nom,
                reduite.image.mediaType, reduite.image.donnees));
        hote.vueAgent().poserPieces(PieceJointe.noms(hote.piecesAgent()));
    }

    /** Le fichier courant : cité en bloc borné (le modèle a read_file). */
    private void joindreFichierCourant() {
        String actif = hote.etat().actif();
        if (actif == null || hote.scene() == null) {
            return;
        }
        hote.piecesAgent().add(PieceJointe.fichier(actif,
                PieceJointe.bornerTexte(hote.scene().texte())));
        hote.vueAgent().poserPieces(PieceJointe.noms(hote.piecesAgent()));
    }

    /** La structure du projet : l'arbre borné en lignes. */
    private void joindreProjet() {
        String arbre = PieceJointe.arbre(hote.fichiersArbre());
        if (arbre.isEmpty()) {
            return;
        }
        hote.piecesAgent().add(PieceJointe.projet(arbre));
        hote.vueAgent().poserPieces(PieceJointe.noms(hote.piecesAgent()));
    }

    // ------------------------------------------------------------------
    // Le menu de l'agent (lot S, ADR-064) : nouvelle conversation,
    // historique, modèle, connexions, usages — le contenu du menu ne
    // vit QUE ici, la vue n'annonce que les actions câblées
    // ------------------------------------------------------------------

    /** Le menu ⋯ ancré à son icône (lot T) : chaque entrée est
     *  câblée, jamais morte. */
    void ouvrirMenuAgentOptions() {
        if (hote.vueAgent() == null) {
            return;
        }
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_menu_nouvelle),
                historique::nouvelle));
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_menu_historique),
                historique::ouvrir));
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_modele_titre),
                modeles::ouvrir));
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_connexions_titre),
                connexions::ouvrir));
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_usages_titre),
                usages::ouvrir));
        hote.popups().menu(hote.vueAgent().ancreMenu(),
                null, 220, entrees);
    }

    // ------------------------------------------------------------------
    // Le mode de permission (ADR-É11 point 3) : le menu ancré de la
    // puce d'en-tête (persisté par projet — un projet de test n'a pas
    // à subir DEMANDE_CHAQUE, un projet réel ne doit pas subir
    // ACCEPTATION_AUTO par défaut)
    // ------------------------------------------------------------------

    /** Le menu des modes de permission, ancré à la puce du tiroir. */
    void ouvrirMenuMode() {
        if (hote.vueAgent() == null) {
            return;
        }
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_mode_demande),
                () -> poserMode(Permissions.Mode.DEMANDE_CHAQUE)));
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_mode_auto),
                () -> poserMode(Permissions.Mode.ACCEPTATION_AUTO)));
        entrees.add(new PortePopups.EntreeMenu(
                hote.texte(R.string.agent_mode_plan),
                () -> poserMode(Permissions.Mode.PLAN_SEULEMENT)));
        hote.popups().menu(hote.vueAgent().ancreMode(),
                hote.texte(R.string.agent_permission_titre), 220,
                entrees);
    }

    // ------------------------------------------------------------------
    // La permission sur diff déplié (fermer = refuser)
    // ------------------------------------------------------------------

    /**
     * La permission sur le diff déplié (popup CENTRÉ avec voile, lot
     * A7 — décision 12) : la fermeture par retour, voile ou bouton
     * revient à REFUSER (défaut sûr, ADR-É11 point 3) — la garde
     * un coup rend le double appel impossible.
     */
    private void montrerPermissionAgent(
            Permissions.DemandeEcriture demande) {
        if (hote.etatAgent().permissionEnAttente() != null) {
            return;
        }
        hote.etatAgent().poserPermission(demande);
        hote.vueAgent().poserEtat(hote.etatAgent());
        PopupPermissionDiffBinding corps =
                PopupPermissionDiffBinding.inflate(
                        LayoutInflater.from(hote.contexte()));
        corps.surTitrePermission.setText(
                R.string.agent_permission_ecriture);
        corps.outilPermission.setText(demande.outil);
        corps.ciblePermission.setText(demande.cible);
        corps.diffPermission.setText(demande.diffApercu);
        // le diff défile DANS sa hauteur plafonnée (la maquette
        // agent.html borne la zone, jamais la carte entière)
        corps.diffPermission.setMovementMethod(
                new ScrollingMovementMethod());
        boolean[] repondu = {false};
        corps.refuserPermission.setText(R.string.agent_refuser);
        corps.refuserPermission.setOnClickListener(vue -> {
            repondu[0] = true;
            hote.popups().fermer();
            repondrePermissionAgent(false);
        });
        corps.appliquerPermission.setText(R.string.agent_appliquer);
        corps.appliquerPermission.setOnClickListener(vue -> {
            repondu[0] = true;
            hote.popups().fermer();
            repondrePermissionAgent(true);
        });
        hote.popups().centrer(corps.getRoot(),
                hote.texte(R.string.agent_permission_ecriture),
                () -> {
                    if (!repondu[0]) {
                        // fermer sans choisir : REFUSER (défaut sûr)
                        repondrePermissionAgent(false);
                    }
                });
    }

    void repondrePermissionAgent(boolean accordee) {
        hote.etatAgent().repondrePermission(accordee);
        if (hote.agent() != null) {
            hote.agent().repondrePermission(accordee);
        }
        hote.vueAgent().rafraichir();
    }

    void repondreQuestionAgent(int index, int choix) {
        hote.etatAgent().repondre(index, choix, null);
        java.util.List<String> reponses = hote.etatAgent().reponses();
        if (reponses != null && hote.agent() != null) {
            hote.agent().repondreQuestions(reponses);
        }
        hote.vueAgent().rafraichir();
    }

    /**
     * « Demander à l'IA » (ADR-É11 point 10) : le tiroir s'ouvre, le
     * composeur est prérempli avec le fichier ACTIF quoté (v1 : le
     * document entier borné ; la sélection précise attend l'API
     * celeris — écart consigné au backlog).
     */
    void demanderIa() {
        ouvrirAgent();
        String actif = hote.etat().actif();
        if (actif == null || hote.scene() == null) {
            return;
        }
        String texte = hote.scene().texte();
        int plafond = 8_000;
        String borne = texte.length() > plafond
                ? texte.substring(0, plafond)
                        + "\n[… tronqué — "
                        + (texte.length() - plafond)
                        + " caractères de plus]"
                : texte;
        hote.etatAgent().poserPreRemplissage(actif, borne);
        hote.vueAgent().poserEtat(hote.etatAgent());
    }
}
