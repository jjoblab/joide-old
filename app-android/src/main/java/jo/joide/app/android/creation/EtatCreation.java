package jo.joide.app.android.creation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jo.joide.app.android.accueil.EtatAccueil;
import jo.joide.app.android.accueil.ProjetsRecents;

/**
 * L'état d'écran pur de l'assistant de création (ADR-É4) : trois
 * étapes — Modèle, Identité, Récapitulatif — la progression toujours
 * visible, la validation au fil de la saisie (le bouton Continuer
 * reflète la validité, jamais d'alerte tardive), le formulaire
 * piloté par le modèle (les champs paquet et minSdk n'apparaissent
 * que si le modèle a des modules Android).
 *
 * <p>Classe PURE, testée JVM : sélection et défauts du modèle,
 * bascule des modules avec refus motivé (un module requis par un
 * module conservé ne se retire pas), dérivation du paquet tant que
 * l'utilisateur ne l'a pas touché, récapitulatif EXACT (l'arbre est
 * la liste des fichiers que la génération produira — jamais une
 * promesse séparée de l'écriture). Rien n'est créé avant la sortie
 * de cet état : l'écriture tout ou rien vit dans
 * {@link EcrireProjet}.</p>
 */
public final class EtatCreation {

    /** Les trois étapes de l'assistant. */
    public enum Etape {
        /** Galerie de modèles. */
        MODELE,
        /** Formulaire piloté par le modèle. */
        IDENTITE,
        /** Récapitulatif avant création. */
        RECAPITULATIF;

        /** Le numéro visible de l'étape (1 à 3). */
        public int numero() {
            return ordinal() + 1;
        }
    }

    /** Le motif de refus d'une bascule de module. */
    public record Bascule(boolean faite, String requisPar) {
    }

    /** Une ligne de l'arborescence du récapitulatif. */
    public record LigneArbo(int profondeur, String texte,
                            boolean annotation) {
    }

    /** Une ligne de la fiche du récapitulatif. */
    public record LigneRecap(String cle, String valeur) {
    }

    /** La raison d'un nom invalide. */
    public enum RaisonNom {
        /** Le nom est vide. */
        VIDE,
        /** Le nom dépasse 64 caractères. */
        LONG
    }

    private final List<ModeleProjet.Modele> modeles;
    private final List<ProjetsRecents.Entree> existants;

    private Etape etape = Etape.MODELE;
    private String filtre = "";
    private ModeleProjet.Categorie categorie;
    private String modeleChoisi;

    private String nom = GenerateurProjet.NOM_DEFAUT;
    private String paquet = "";
    private boolean paquetTouche;
    private int minSdk = 28;
    private List<String> modules = List.of();
    private String dernierModuleRequis;

    /**
     * @param modeles les modèles exploitables (analysés, sans problème)
     * @param existants les projets connus de l'accueil (défauts uniques)
     */
    public EtatCreation(List<ModeleProjet.Modele> modeles,
                        List<ProjetsRecents.Entree> existants) {
        this.modeles = List.copyOf(Objects.requireNonNull(modeles,
                "modeles"));
        this.existants = List.copyOf(Objects.requireNonNull(existants,
                "existants"));
    }

    // ---- Étape 1 : la galerie ----

    /** Les modèles visibles : filtre de recherche puis catégorie. */
    public List<ModeleProjet.Modele> modelesVisibles() {
        String cherche = filtre.trim().toLowerCase(Locale.ROOT);
        List<ModeleProjet.Modele> visibles = new ArrayList<>();
        for (ModeleProjet.Modele modele : modeles) {
            if (categorie != null && modele.categorie() != categorie) {
                continue;
            }
            if (!cherche.isEmpty() && !modele.nom().toLowerCase(
                    Locale.ROOT).contains(cherche)
                    && !modele.id().toLowerCase(Locale.ROOT)
                            .contains(cherche)) {
                continue;
            }
            visibles.add(modele);
        }
        return visibles;
    }

    /** Le texte de recherche courant. */
    public String filtre() {
        return filtre;
    }

    /** Pose le texte de recherche. */
    public void filtrer(String texte) {
        filtre = texte == null ? "" : texte;
    }

    /** La catégorie choisie (null : toutes). */
    public ModeleProjet.Categorie categorie() {
        return categorie;
    }

    /** Pose la catégorie (null : toutes). */
    public void changerCategorie(ModeleProjet.Categorie nouvelle) {
        categorie = nouvelle;
    }

    /** L'identifiant du modèle choisi, ou null. */
    public String modeleChoisi() {
        return modeleChoisi;
    }

    /** Le modèle choisi, ou null. */
    public ModeleProjet.Modele modele() {
        if (modeleChoisi == null) {
            return null;
        }
        for (ModeleProjet.Modele modele : modeles) {
            if (modele.id().equals(modeleChoisi)) {
                return modele;
            }
        }
        return null;
    }

    /**
     * Sélectionne un modèle et pose les défauts de l'identité : nom
     * unique parmi les projets existants, paquet dérivé, minSdk 28,
     * modules par défaut du modèle.
     *
     * @param id l'identifiant du modèle
     * @return vrai si le modèle existe et a été sélectionné
     */
    public boolean selectionnerModele(String id) {
        ModeleProjet.Modele trouve = null;
        for (ModeleProjet.Modele modele : modeles) {
            if (modele.id().equals(id)) {
                trouve = modele;
                break;
            }
        }
        if (trouve == null) {
            return false;
        }
        modeleChoisi = id;
        nom = EtatAccueil.nomDefautUnique(existants,
                GenerateurProjet.NOM_DEFAUT);
        paquetTouche = false;
        paquet = GenerateurProjet.paquetDefaut(nom);
        minSdk = 28;
        List<String> defauts = new ArrayList<>();
        for (ModeleProjet.ModuleModele module : trouve.modules()) {
            if (module.defaut()) {
                defauts.add(module.id());
            }
        }
        modules = List.copyOf(defauts);
        return true;
    }

    // ---- Étape 2 : l'identité ----

    /** Le nom saisi. */
    public String nom() {
        return nom;
    }

    /** Pose le nom ; le paquet se redérive tant qu'il n'est pas touché. */
    public void changerNom(String nouveau) {
        nom = nouveau == null ? "" : nouveau;
        if (!paquetTouche) {
            paquet = GenerateurProjet.paquetDefaut(nom);
        }
    }

    /** Le paquet saisi. */
    public String paquet() {
        return paquet;
    }

    /** Pose le paquet (touché : il ne suit plus le nom). */
    public void changerPaquet(String nouveau) {
        paquet = nouveau == null ? "" : nouveau;
        paquetTouche = true;
    }

    /**
     * Le paquet suit-il encore le nom (l'utilisateur ne l'a pas
     * touché) ? La vue s'en sert pour re-poser le champ du paquet à
     * chaque frappe du nom — le code d'hier dérivait déjà le paquet
     * mais ne le montrait jamais (lot A4).
     */
    public boolean suitPaquet() {
        return !paquetTouche;
    }

    /** Le minSdk choisi. */
    public int minSdk() {
        return minSdk;
    }

    /** Pose le minSdk (ignoré hors bornes). */
    public void changerMinSdk(int niveau) {
        if (GenerateurProjet.MIN_SDK.contains(niveau)) {
            minSdk = niveau;
        }
    }

    /** Les modules choisis, dans l'ordre du modèle. */
    public List<String> modulesChoisis() {
        return modules;
    }

    /** Le modèle expose-t-il le champ paquet (modules Android) ? */
    public boolean champsPaquet() {
        ModeleProjet.Modele modele = modele();
        return modele != null && modele.modules().stream()
                .anyMatch(m -> m.type().startsWith("android-"));
    }

    /** Le modèle expose-t-il le segment minSdk ? */
    public boolean champsMinSdk() {
        return champsPaquet();
    }

    /**
     * Bascule un module : le retirer est refusé s'il est requis par
     * un module conservé (le refus est nommé, jamais silencieux).
     *
     * @param id l'identifiant du module
     * @return la bascule faite, ou le refus et son responsable
     */
    public Bascule basculerModule(String id) {
        ModeleProjet.Modele modele = modele();
        if (modele == null) {
            return new Bascule(false, null);
        }
        if (modules.contains(id)) {
            for (ModeleProjet.ModuleModele module : modele.modules()) {
                if (modules.contains(module.id())
                        && module.requiert().contains(id)) {
                    dernierModuleRequis = module.id();
                    return new Bascule(false, module.id());
                }
            }
            List<String> apres = new ArrayList<>(modules);
            apres.remove(id);
            modules = List.copyOf(apres);
            return new Bascule(true, null);
        }
        for (ModeleProjet.ModuleModele module : modele.modules()) {
            if (module.id().equals(id)) {
                // l'ordre du modèle est conservé : le joide.toml
                // généré reste déterministe, bascules après bascules
                List<String> apres = new ArrayList<>(modules);
                apres.add(id);
                apres.sort(java.util.Comparator.comparingInt(
                        candidat -> indexModele(modele, candidat)));
                modules = List.copyOf(apres);
                dernierModuleRequis = null;
                return new Bascule(true, null);
            }
        }
        return new Bascule(false, null);
    }

    private static int indexModele(ModeleProjet.Modele modele,
                                   String idModule) {
        for (int i = 0; i < modele.modules().size(); i++) {
            if (modele.modules().get(i).id().equals(idModule)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Le module qui a bloqué la dernière bascule refusée. */
    public String moduleRefusePar() {
        return dernierModuleRequis;
    }

    // ---- Validation au fil de la saisie ----

    /** La raison d'un nom invalide, ou null s'il est valide. */
    public RaisonNom raisonNom() {
        if (nom.trim().isEmpty()) {
            return RaisonNom.VIDE;
        }
        if (nom.trim().length() > 64) {
            return RaisonNom.LONG;
        }
        return null;
    }

    /** Le nom est-il valide ? */
    public boolean nomValide() {
        return raisonNom() == null;
    }

    /** Le paquet est-il valide (ou absent des champs du modèle) ? */
    public boolean paquetValide() {
        return !champsPaquet()
                || GenerateurProjet.paquetValide(paquet.trim());
    }

    /** Au moins un module est-il conservé (si le modèle en propose) ? */
    public boolean modulesValides() {
        ModeleProjet.Modele modele = modele();
        return modele == null || modele.modules().isEmpty()
                || !modules.isEmpty();
    }

    /** L'identité est-elle valide dans son ensemble ? */
    public boolean identiteValide() {
        return nomValide() && paquetValide() && modulesValides();
    }

    /** Le bouton Continuer/Créer de l'étape courante est-il actif ? */
    public boolean continuerPossible() {
        return switch (etape) {
            case MODELE -> modeleChoisi != null;
            case IDENTITE -> identiteValide();
            case RECAPITULATIF -> true;
        };
    }

    // ---- Navigation ----

    /** L'étape courante. */
    public Etape etape() {
        return etape;
    }

    /** Passe à l'étape suivante si elle est permise. */
    public void suivante() {
        if (!continuerPossible() && etape != Etape.RECAPITULATIF) {
            return;
        }
        if (etape == Etape.MODELE && modeleChoisi != null) {
            etape = Etape.IDENTITE;
        } else if (etape == Etape.IDENTITE && identiteValide()) {
            etape = Etape.RECAPITULATIF;
        }
    }

    /** Remonte d'une étape ; depuis l'étape 1, l'assistant se quitte. */
    public void precedente() {
        if (etape == Etape.RECAPITULATIF) {
            etape = Etape.IDENTITE;
        } else if (etape == Etape.IDENTITE) {
            etape = Etape.MODELE;
        }
    }

    /** L'assistant est-il sur l'étape 1 (le retour système quitte) ? */
    public boolean surPremiereEtape() {
        return etape == Etape.MODELE;
    }

    // ---- Récapitulatif (exact : la génération elle-même) ----

    /** L'identité courante. */
    public GenerateurProjet.Identite identite() {
        return new GenerateurProjet.Identite(nom.trim(), modules,
                paquet.trim(), minSdk);
    }

    /**
     * La génération courante : les fichiers EXACTS qui seront écrits
     * (le récapitulatif ne promet rien que la création ne tienne pas).
     *
     * @return la génération, ou null sans modèle choisi
     */
    public GenerateurProjet.Generation generation() {
        ModeleProjet.Modele modele = modele();
        if (modele == null) {
            return null;
        }
        return GenerateurProjet.generer(modele, identite());
    }

    /** Les lignes de la fiche du récapitulatif. */
    public List<LigneRecap> lignesRecap() {
        ModeleProjet.Modele modele = modele();
        if (modele == null) {
            return List.of();
        }
        List<LigneRecap> lignes = new ArrayList<>();
        lignes.add(new LigneRecap("modele", modele.nom()));
        lignes.add(new LigneRecap("nom", nom.trim()));
        if (!modules.isEmpty()) {
            StringBuilder valeur = new StringBuilder();
            for (int i = 0; i < modules.size(); i++) {
                if (i > 0) {
                    valeur.append(", ");
                }
                valeur.append(':').append(modules.get(i));
            }
            lignes.add(new LigneRecap("modules", valeur.toString()));
        }
        if (champsPaquet()) {
            lignes.add(new LigneRecap("paquet", paquet.trim()));
            lignes.add(new LigneRecap("minSdk",
                    String.valueOf(minSdk)));
        }
        return lignes;
    }

    /**
     * L'arborescence du récapitulatif : les répertoires et fichiers
     * de la génération, avec une annotation par module (« · :id »).
     */
    public List<LigneArbo> arborescence() {
        GenerateurProjet.Generation generation = generation();
        if (generation == null) {
            return List.of();
        }
        List<LigneArbo> lignes = new ArrayList<>();
        lignes.add(new LigneArbo(0, nom.trim() + "/", false));
        lignes.add(new LigneArbo(1,
                jo.joide.model.WorkspaceLoader.WORKSPACE_FILE, false));
        List<String> dossiersVus = new ArrayList<>();
        for (GenerateurProjet.FichierGenere fichier : generation.fichiers()) {
            String chemin = fichier.chemin();
            if (jo.joide.model.WorkspaceLoader.WORKSPACE_FILE
                    .equals(chemin)) {
                continue;
            }
            String[] segments = chemin.split("/");
            for (int profondeur = 0; profondeur < segments.length - 1;
                    profondeur++) {
                String prefixe = String.join("/",
                        java.util.Arrays.copyOfRange(segments, 0,
                                profondeur + 1));
                if (dossiersVus.contains(prefixe)) {
                    continue;
                }
                dossiersVus.add(prefixe);
                ModeleProjet.Modele modele = modele();
                boolean annotationModule = modele != null
                        && profondeur == 0
                        && modele.modules().stream().anyMatch(m ->
                                m.id().equals(segments[0]));
                lignes.add(new LigneArbo(profondeur + 1,
                        segments[profondeur] + "/",
                        annotationModule));
                if (annotationModule && modele != null) {
                    ModeleProjet.ModuleModele module = modele.modules()
                            .stream().filter(m -> m.id()
                                    .equals(segments[0])).findFirst()
                            .orElse(null);
                    if (module != null) {
                        lignes.add(new LigneArbo(profondeur + 2,
                                "· module :" + module.id(), true));
                    }
                }
            }
            lignes.add(new LigneArbo(segments.length,
                    segments[segments.length - 1], false));
        }
        return lignes;
    }
}
