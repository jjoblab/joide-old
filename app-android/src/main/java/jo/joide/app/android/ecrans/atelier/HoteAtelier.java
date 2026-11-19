package jo.joide.app.android.ecrans.atelier;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import jo.joide.app.android.AgentService;
import jo.joide.app.android.atelier.EcrituresFusionnees;
import jo.joide.app.android.atelier.EtatApercu;
import jo.joide.app.android.atelier.EtatAtelier;
import jo.joide.app.android.atelier.EtatJournal;
import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.atelier.EtatPopups;
import jo.joide.app.android.atelier.EtatProblemes;
import jo.joide.app.android.atelier.VueApercu;
import jo.joide.app.android.atelier.VueArbre;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.atelier.agent.PieceJointe;
import jo.joide.app.android.atelier.agent.VueAgent;
import jo.joide.app.android.console.EtatConsole;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.PortePopups;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.TiroirPousse;
import jo.joide.app.android.ui.atelier.BarreOnglets;
import jo.joide.app.android.ui.atelier.FeuilleBasse;
import jo.joide.app.android.ui.atelier.FilAriane;
import jo.joide.app.android.ui.atelier.TiroirFichiers;
import jo.joide.ide.apercu.ApercuLayouts;
import jo.joide.ide.apercu.ConfigurationApercu;
import jo.joide.ide.editeur.SceneEditeur;
import jo.joide.ide.langages.MoteursLangage;
import jo.joide.ide.sync.LanguageModelSync;
import jo.joide.model.Workspace;

/**
 * Lot A6 (ADR-085 décision 3) — L'HÔTE DE L'ATELIER : l'interface
 * ÉTROITE qui remplace l'accès des quatre contrôleurs aux champs
 * package-private d'{@code EcranAtelier} (constat C2 : 685 accès
 * directs, des contrôleurs-morceaux de l'activité — pas des
 * collaborateurs). AUCUN contrôleur ne référence l'écran ; ils
 * dépendent d'ICI, testables avec un faux hôte.
 *
 * <p>L'interface porte l'union exacte de ce que les contrôleurs
 * atteignaient hier — l'état d'UI, l'état de la session (lecture ET
 * pose : le montage de session vit hors de l'écran), les zones, les
 * mises à jour et les services d'activité (contexte, fil UI,
 * navigation, services, contrats de résultat). Le lot A7 y ajoute
 * le PORTE-POPUPS (menus ancrés, cartes centrées sur voile) et son
 * état pur ({@link EtatPopups} : ordre du retour, brouillons de
 * saisie), et retire la demande de résultat dépréciée (contrats
 * typés d'{@code Ecrans}) ; A8/A9 consolident — chaque retrait
 * retire un membre.</p>
 */
public interface HoteAtelier {

    // ------------------------------------------------------------------
    // L'écran et ses services (la coque délègue, les contrôleurs ne
    // touchent JAMAIS l'activité directement)
    // ------------------------------------------------------------------

    /** Le contexte de l'écran (les popups s'y gonflent, les services
     *  s'y lient). */
    Context contexte();

    /** Exécute l'action sur le fil de l'UI. */
    void executerSurFilUi(Runnable action);

    /** La chaîne localisée (values/ et values-fr/). */
    String texte(int id);

    /** La chaîne localisée mise en forme. */
    String texte(int id, Object... args);

    /** Le répertoire des fichiers privés (cache m2, cidx, outillage). */
    File dossierFichiers();

    /** Le résolveur de contenu (les URI SAF, les types MIME). */
    ContentResolver resolveurContenu();

    /** L'appareil possède-t-il un appareil photo ? */
    boolean possedeCamera();

    /** Ouvre un écran (paramètres, navigateur OAuth). */
    void ouvrirEcran(Intent intention);

    /** Le contrat SAF d'image du tiroir agent (Ecrans.CHOISIR_IMAGE,
     *  décision 13 — lot A7 : le code 47101 disparaît). */
    ActivityResultLauncher<String> choisirImageAgent();

    /** Le contrat de photo caméra du tiroir agent
     *  (Ecrans.PRENDRE_PHOTO, décision 13 — lot A7 : le code 47102
     *  disparaît). */
    ActivityResultLauncher<Uri> prendrePhotoAgent();

    /** Démarre un service de premier plan. */
    void demarrerServiceAvantPlan(Intent intention);

    /** Lie un service (l'agent). */
    void lierService(Intent intention, ServiceConnection connexion,
                     int drapeaux);

    /** Détache un service lié. */
    void delierService(ServiceConnection connexion);

    // ------------------------------------------------------------------
    // Les états (la vérité ; certains se posent — le montage vit hors
    // de l'écran)
    // ------------------------------------------------------------------

    /** L'état de l'atelier (onglets, surcouches, retour, build). */
    EtatAtelier etat();

    /** L'état de la console de build. */
    EtatConsole etatConsole();

    /** L'état de l'aperçu. */
    EtatApercu etatApercu();

    /** L'état des problèmes LSP. */
    EtatProblemes etatProblemes();

    /** L'état du journal de l'IDE. */
    EtatJournal etatJournal();

    /** L'état du logcat. */
    EtatLogcat etatLogcat();

    /** L'état du tiroir agent. */
    EtatAgentChat etatAgent();

    /** Remplace l'état du tiroir agent (le journal rejoué). */
    void poserEtatAgent(EtatAgentChat etat);

    /** L'état des popups de l'atelier (ordre du retour, brouillons
     *  de saisie — pur, testé JVM, lot A7). */
    EtatPopups etatPopups();

    /** Le porte-popups de l'atelier (menus ancrés, cartes centrées
     *  sur voile — la voie d'appel uniforme du lot A7). */
    PortePopups popups();

    /** La palette Graphite courante. */
    Graphite.Palette palette();

    /** La source des réglages persistés. */
    Reglages.Source sourceReglages();

    /** L'exécuteur de fond de l'atelier. */
    ExecutorService fond();

    // ------------------------------------------------------------------
    // La session (le montage et la fermeture vivent dans la classe
    // dédiée du lot A6 — la session n'est plus un champ d'écran)
    // ------------------------------------------------------------------

    /** La racine du projet monté (null tant qu'il ne l'est pas). */
    Path racineProjet();

    /** Le modèle d'espace de travail (null tant que non monté). */
    Workspace modele();

    /** Les moteurs de langage (null tant que non montés). */
    MoteursLangage moteurs();

    /** La sync des modèles de langage (null tant que non montée). */
    LanguageModelSync sync();

    /** La scène éditeur celeris (null tant que non montée). */
    SceneEditeur scene();

    /** Le moteur d'aperçu LayoutLib (null tant que non monté). */
    ApercuLayouts apercu();

    /** La configuration d'aperçu du snapshot (null tant que non
     *  montée). */
    ConfigurationApercu configApercuBase();

    /** Les écritures fusionnées (le dernier gagne, lot F). */
    EcrituresFusionnees ecritures();

    /** Le fichier courant (null si aucun). */
    String fichierCourant();

    /** Pose le fichier courant. */
    void poserFichierCourant(String fichier);

    /** Le XML courant (le texte de la dernière mise en page ouverte). */
    String xmlCourant();

    /** Pose le XML courant. */
    void poserXmlCourant(String xml);

    /** Les fichiers de l'arborescence. */
    List<String> fichiersArbre();

    /** Pose les fichiers de l'arborescence. */
    void poserFichiersArbre(List<String> fichiers);

    /** Des sorties de build existent (l'arbre les estompe). */
    boolean sortiesPresentes();

    /** Pose l'existence des sorties de build. */
    void poserSortiesPresentes(boolean presentes);

    /** Un build court (la feuille le dit). */
    boolean buildEnCours();

    /** Pose l'état de build. */
    void poserBuildEnCours(boolean enCours);

    /** Les dossiers dépliés de l'arbre. */
    Set<String> dossiersOuverts();

    /** Le filtre du tiroir fichiers (vide = tout). */
    String filtreTiroir();

    /** Pose le filtre du tiroir fichiers. */
    void poserFiltreTiroir(String filtre);

    /** Les pièces jointes du composeur agent. */
    List<PieceJointe> piecesAgent();

    /** Les pièces du message en attente du service agent. */
    List<PieceJointe> piecesEnAttenteAgent();

    /** Le message parti pendant la liaison du service agent. */
    String messageEnAttenteAgent();

    /** Pose le message en attente de l'agent. */
    void poserMessageEnAttenteAgent(String message);

    /** Le binder du service agent (null tant que non lié). */
    AgentService.AgentBinder agent();

    /** Pose le binder du service agent. */
    void poserAgent(AgentService.AgentBinder binder);

    // ------------------------------------------------------------------
    // Les zones (les vues minces, montées ou différées)
    // ------------------------------------------------------------------

    /** L'arborescence (tiroir compact ou colonne amarrée). */
    VueArbre vueArbre();

    /** Le tiroir agent (monté à la demande). */
    VueAgent vueAgent();

    /** La feuille basse. */
    FeuilleBasse feuille();

    /** La bande d'onglets. */
    BarreOnglets barreOnglets();

    /** Le fil d'Ariane. */
    FilAriane filAriane();

    /** Le bouton d'aperçu de la bande d'onglets (écran compact). */
    View boutonApercuOnglets();

    /** Le tiroir agent (le tiroir poussé droite ; se MONTE à la
     *  demande — l'appel gonfle le stub la première fois). */
    TiroirPousse tiroirAgent();

    /** Le tiroir agent est-il ouvert (faux s'il n'est pas monté) ? */
    boolean tiroirAgentOuvert();

    /** Le tiroir fichiers (écran compact ; null en grand écran). */
    TiroirFichiers tiroirFichiers();

    /** La pilule d'état de l'index. */
    Pilule piluleIndex();

    /** La puce « n modules ». */
    Puce puceModules();

    /** Le point d'activité de l'agent (§3.1). */
    View pointAgent();

    /** Le contrôleur de l'édition (les actions croisées : la
     *  suppression d'un fichier ferme son onglet). */
    ControleurEdition controleurEdition();

    // ------------------------------------------------------------------
    // Les mises à jour et le montage différé (l'UI suit l'état —
    // l'écran d'hier orchestrait, la vue d'écran déroule)
    // ------------------------------------------------------------------

    /** Met à jour volets, feuille, FAB et puits. */
    void majVolets();

    /** Met à jour la console (montée à la demande au premier besoin). */
    void majConsole();

    /** Met à jour le FAB/pilule de build. */
    void majFab();

    /** Met à jour l'arborescence. */
    void majArbre();

    /** Met à jour le puits (état vide sans onglet actif). */
    void majPuits();

    /** Met à jour le verdict console de la feuille. */
    void majVerdictConsole();

    /** Met à jour la pilule d'état de l'index. */
    void majPiluleIndex();

    /** Recharge l'arborescence depuis le disque (fil de fond). */
    void rechargerArbre();

    /** Reconfigure le moteur d'aperçu depuis l'état. */
    void reconfigurerApercu();

    /** Écrit les modifications en attente sur le disque. */
    void ecrireEnAttente();

    /** L'erreur franche : l'état vide du puits la porte. */
    void montrerErreur(String message);

    /** Pose le titre et l'aide de l'état vide du puits. */
    void poserEtatVide(int titre, int aide);

    /** Pose le nom du projet dans la barre (le montage de session). */
    void poserNomProjet(String nom);

    /** Attache la scène éditeur et sa couche de popups dans le puits
     *  du layout racine (le conteneur déclaré, jamais reconstruit). */
    void poserSceneEditeur(View scene, View couchePopups);

    /** Détache la scène éditeur du puits (la fermeture d'IDE). */
    void viderEditeur();

    /** L'écouteur de rendu du moteur d'aperçu (le montage de session
     *  le pose sur le moteur). */
    ApercuLayouts.EcouteurRendu ecouteurApercu();
}
