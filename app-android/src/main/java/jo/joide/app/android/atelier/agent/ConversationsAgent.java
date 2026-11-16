package jo.joide.app.android.atelier.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jo.joide.agent.SessionsAgent;

/**
 * Les conversations de l'agent (lot S, ADR-064) : le répertoire des
 * sessions persistées — une conversation = UN fichier
 * {@link SessionsAgent} nommé par son instant de naissance (l'identité
 * est le nom de fichier, le format reste {@code joide.session/1}).
 *
 * <p>Classe PURE sur {@link Path} : l'hôte passe le répertoire
 * ({@code filesDir/agent/conversations}), la logique — titres dérivés
 * du premier message, résumés ordonnés du plus récent au plus ancien,
 * éviction du plus ancien au-delà du plafond, migration de la session
 * unique du lot P — vit ICI, testée JVM. Aucune décision dans les
 * vues ; aucun Android.</p>
 */
public final class ConversationsAgent {

    /** Bornes du titre résumé (première ligne du premier message). */
    public static final int TITRE_MAX = 80;

    /** Conversations gardées au plus (la plus ancienne part). */
    public static final int CONVERSATIONS_MAX = 50;

    /** Le suffixe des fichiers de conversation. */
    static final String SUFFIXE = ".json";

    /** Un résumé de conversation pour l'historique. */
    public static final class Resume {

        /** L'identité (nom de fichier sans suffixe). */
        public final String id;
        /** Le titre dérivé (première ligne du premier message, bornée). */
        public final String titre;
        /** Le nombre d'échanges complétés (un échange = question + réponse). */
        public final int echanges;
        /** L'instant de dernière modification (epoch ms). */
        public final long modifiee;

        Resume(String id, String titre, int echanges, long modifiee) {
            this.id = id;
            this.titre = titre;
            this.echanges = echanges;
            this.modifiee = modifiee;
        }

        @Override
        public String toString() {
            return "Resume[" + id + ", " + echanges + " échange(s)]";
        }
    }

    private final Path repertoire;
    private final Clock horloge;

    /**
     * @param repertoire le répertoire des conversations (créé au
     *                   premier accès, jamais une exception)
     * @param horloge    l'horloge des identités (les tests injectent
     *                   la leur)
     */
    public ConversationsAgent(Path repertoire, Clock horloge) {
        this.repertoire = Objects.requireNonNull(repertoire, "repertoire");
        this.horloge = horloge == null ? Clock.systemUTC() : horloge;
    }

    /**
     * L'identité d'une conversation neuve : l'instant courant en
     * millisecondes — croissant, unique tant qu'une conversation ne
     * naît pas deux fois à la même milliseconde (l'écriture départage
     * en suffixant).
     *
     * @return l'identité neuve
     */
    public String nouvelleIdentite() {
        return String.valueOf(horloge.millis());
    }

    /**
     * La conversation active : la plus récemment modifiée, ou une
     * identité neuve si le répertoire est vide (la conversation naît
     * paresseusement — elle n'existe sur disque qu'au premier tour
     * complété).
     *
     * @return l'identité active
     */
    public String activeOuNeuve() {
        List<Resume> resumes = lister();
        return resumes.isEmpty() ? nouvelleIdentite()
                : resumes.get(0).id;
    }

    /**
     * Les résumés, de la plus récemment modifiée à la plus ancienne.
     * Un fichier illisible ou un nom étranger est IGNORÉ — jamais
     * l'historique entier ne casse sur une conversation corrompue.
     *
     * @return les résumés ordonnés
     */
    public List<Resume> lister() {
        List<Resume> resumes = new ArrayList<>();
        if (!Files.isDirectory(repertoire)) {
            return resumes;
        }
        try (var entrees = Files.list(repertoire)) {
            for (Path fichier : entrees
                    .filter(f -> f.getFileName().toString()
                            .endsWith(SUFFIXE))
                    .sorted()
                    .toList()) {
                Resume resume = resumer(fichier);
                if (resume != null) {
                    resumes.add(resume);
                }
            }
        } catch (IOException e) {
            // répertoire illisible : historique vide, jamais un crash
        }
        resumes.sort(Comparator.comparingLong(
                        (Resume resume) -> resume.modifiee)
                .reversed());
        return resumes;
    }

    /**
     * Écrit une conversation (et évique la plus ancienne au-delà du
     * plafond — l'éviction part de la plus ancienne, jamais de
     * l'active).
     *
     * @param id      l'identité
     * @param session la session à persister
     */
    public void ecrire(String id, SessionsAgent.Session session) {
        try {
            Files.createDirectories(repertoire);
            SessionsAgent.ecrire(fichier(id), session);
        } catch (IOException e) {
            // la conversation part au mieux : le tour vit en mémoire
            return;
        }
        eviction();
    }

    /**
     * Lit une conversation.
     *
     * @param id l'identité
     * @return la session, ou null si absente ou illisible
     */
    public SessionsAgent.Session lire(String id) {
        try {
            return SessionsAgent.lire(fichier(id));
        } catch (IOException | RuntimeException e) {
            // conversation illisible : l'appelant repart du vide
            return null;
        }
    }

    /**
     * Supprime une conversation.
     *
     * @param id l'identité
     * @return vrai si le fichier existait et est parti
     */
    public boolean supprimer(String id) {
        try {
            return Files.deleteIfExists(fichier(id));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * La migration du lot P : l'unique {@code session.json} devient
     * la conversation la plus ancienne du répertoire — le propriétaire
     * qui ouvre l'historique retrouve sa conversation d'avant le lot
     * S, elle n'est ni perdue ni dupliquée (la source part).
     *
     * @param ancienne le chemin du fichier de session unique
     */
    public void migrerSessionUnique(Path ancienne) {
        if (ancienne == null || !Files.isRegularFile(ancienne)) {
            return;
        }
        try {
            Files.createDirectories(repertoire);
            String identite = String.valueOf(
                    Files.getLastModifiedTime(ancienne).toMillis());
            Path cible = fichier(identite);
            if (!Files.exists(cible)) {
                Files.move(ancienne, cible,
                        StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.deleteIfExists(ancienne);
            }
        } catch (IOException e) {
            // la migration part au mieux : la session unique reste
            // lisible par la version précédente, jamais de perte
        }
    }

    /** Le fichier d'une identité (les caractères hostiles neutralisés). */
    private Path fichier(String id) {
        String nom = id == null ? "" : id.replaceAll("[^0-9A-Za-z_-]",
                "_");
        return repertoire.resolve(nom + SUFFIXE);
    }

    /** Le résumé d'un fichier, ou null si illisible. */
    private static Resume resumer(Path fichier) {
        String nom = fichier.getFileName().toString();
        String id = nom.substring(0, nom.length() - SUFFIXE.length());
        try {
            SessionsAgent.Session session = SessionsAgent.lire(fichier);
            long modifiee = Files.getLastModifiedTime(fichier)
                    .toMillis();
            return new Resume(id, titre(session.transcript),
                    session.transcript.size() / 2, modifiee);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Le titre : première ligne du premier message, bornée. */
    private static String titre(List<String> transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return "";
        }
        String premier = transcript.get(0);
        int finLigne = premier.indexOf('\n');
        String ligne = finLigne >= 0
                ? premier.substring(0, finLigne) : premier;
        ligne = ligne.trim();
        return ligne.length() > TITRE_MAX
                ? ligne.substring(0, TITRE_MAX - 1) + "…" : ligne;
    }

    /** L'éviction au-delà du plafond (la plus ancienne part). */
    private void eviction() {
        List<Resume> resumes = lister();
        for (int i = CONVERSATIONS_MAX; i < resumes.size(); i++) {
            supprimer(resumes.get(i).id);
        }
    }
}
