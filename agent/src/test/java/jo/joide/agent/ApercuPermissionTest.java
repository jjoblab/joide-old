package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.agent.espace.EspaceFaux;
import jo.joide.agent.outils.OutilsV1;
import jo.joide.agent.outils.PlanAgent;
import jo.joide.platform.CancelToken;

/**
 * La permission porte le VRAI diff (lot P, ADR-É11 point 4) : le
 * gate reçoit le diff Myers entre le contenu courant et le contenu
 * proposé pour write/create/edit — jamais un résumé d'arguments
 * déguisé, jamais un nom de fichier seul.
 */
class ApercuPermissionTest {

    @TempDir
    Path bac;

    private EspaceFaux espace;

    @BeforeEach
    void preparer() throws IOException {
        espace = new EspaceFaux(bac);
        espace.ecrireFichier("src/Main.java",
                "ligne1\nligne2\nligne3\n");
    }

    private ExecuteurOutils executeur(EspaceFaux espace)
            throws IOException {
        return new ExecuteurOutils(port(espace),
                chemin -> {
                    try {
                        return espace.lireFichier(chemin);
                    } catch (IOException e) {
                        return null;
                    }
                }, 4);
    }

    private ExecuteurOutils.PortOutils port(EspaceFaux espace)
            throws IOException {
        List<OutilAgent> outils = OutilsV1.tous(espace,
                transportVide(), new PlanAgent());
        return new ExecuteurOutils.PortOutils() {
            @Override
            public OutilAgent outil(String nom) {
                for (OutilAgent outil : outils) {
                    if (outil.description().nom.equals(nom)) {
                        return outil;
                    }
                }
                return null;
            }

            @Override
            public List<OutilAgent> outils() {
                return outils;
            }
        };
    }

    private jo.joide.agent.TransportHttp transportVide() {
        return new jo.joide.agent.TransportHttp() {
            @Override
            public ReponseSse postSse(String url,
                                      java.util.Map<String, String> entetes,
                                      String corps, CancelToken jeton) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Reponse post(String url,
                                java.util.Map<String, String> entetes,
                                String corps, CancelToken jeton) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Reponse get(String url,
                               java.util.Map<String, String> entetes,
                               CancelToken jeton) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void leGateRecoitLeVraiDiffDunWriteFile() throws IOException {
        AtomicReference<Permissions.DemandeEcriture> demande =
                new AtomicReference<>();
        Permissions.PortePermission gate = d -> {
            demande.set(d);
            return Permissions.Decision.REFUSEE;
        };
        PartieContenu appel = PartieContenu.appel("id1",
                "write_file",
                "{\"path\":\"src/Main.java\",\"content\":"
                        + "\"ligne1\\nligne2 modifiee\\nligne3\\n\"}");
        executeur(espace).executer(List.of(appel),
                Permissions.Mode.DEMANDE_CHAQUE, gate,
                CancelToken.NEVER, null);
        Permissions.DemandeEcriture capture = demande.get();
        assertEquals("write_file", capture.outil);
        assertEquals("src/Main.java", capture.cible);
        assertTrue(capture.diffApercu.contains("-ligne2"),
                "le diff doit montrer l'ancienne ligne : "
                        + capture.diffApercu);
        assertTrue(capture.diffApercu.contains("+ligne2 modifiee"),
                "le diff doit montrer la nouvelle ligne : "
                        + capture.diffApercu);
        assertTrue(capture.diffApercu.contains("@@"),
                "le diff est unifié : " + capture.diffApercu);
    }

    @Test
    void leEditFileDiffLaSimulationExacte() throws IOException {
        AtomicReference<Permissions.DemandeEcriture> demande =
                new AtomicReference<>();
        Permissions.PortePermission gate = d -> {
            demande.set(d);
            return Permissions.Decision.ACCORDEE;
        };
        PartieContenu appel = PartieContenu.appel("id2", "edit_file",
                "{\"path\":\"src/Main.java\","
                        + "\"old_text\":\"ligne2\","
                        + "\"new_text\":\"LIGNE2\"}");
        executeur(espace).executer(List.of(appel),
                Permissions.Mode.DEMANDE_CHAQUE, gate,
                CancelToken.NEVER, null);
        String diff = demande.get().diffApercu;
        assertTrue(diff.contains("-ligne2") && diff.contains("+LIGNE2"),
                "le diff de edit_file : " + diff);
        // la simulation n'a PAS ecrit : ligne3 reste, ligne1 reste
        assertTrue(diff.contains(" ligne3"));
    }

    @Test
    void laCreationDepuisRienDiffContreVide() throws IOException {
        espace = new EspaceFaux(bac);
        AtomicReference<Permissions.DemandeEcriture> demande =
                new AtomicReference<>();
        Permissions.PortePermission gate = d -> {
            demande.set(d);
            return Permissions.Decision.REFUSEE;
        };
        PartieContenu appel = PartieContenu.appel("id3",
                "create_file",
                "{\"path\":\"nouveau.txt\",\"content\":\"un\\n\"}");
        executeur(espace).executer(List.of(appel),
                Permissions.Mode.DEMANDE_CHAQUE, gate,
                CancelToken.NEVER, null);
        assertTrue(demande.get().diffApercu.contains("+un"),
                "creation = tout ajout : "
                        + demande.get().diffApercu);
        assertTrue(Files.notExists(bac.resolve("nouveau.txt")),
                "un refus ne touche jamais le disque");
    }

    @Test
    void lOperationNommeeResteUnResumeSansFauxDiff() throws IOException {
        AtomicReference<Permissions.DemandeEcriture> demande =
                new AtomicReference<>();
        Permissions.PortePermission gate = d -> {
            demande.set(d);
            return Permissions.Decision.REFUSEE;
        };
        PartieContenu appel = PartieContenu.appel("id4",
                "delete_path",
                "{\"path\":\"src/Main.java\"}");
        executeur(espace).executer(List.of(appel),
                Permissions.Mode.DEMANDE_CHAQUE, gate,
                CancelToken.NEVER, null);
        String apercu = demande.get().diffApercu;
        assertTrue(apercu.startsWith("delete_path("),
                "operation nommee, pas un faux diff : " + apercu);
    }

    @Test
    void sansLecteurLApercuResteLAnalyseDAncienne() throws IOException {
        AtomicReference<Permissions.DemandeEcriture> demande =
                new AtomicReference<>();
        Permissions.PortePermission gate = d -> {
            demande.set(d);
            return Permissions.Decision.REFUSEE;
        };
        PartieContenu appel = PartieContenu.appel("id5",
                "write_file",
                "{\"path\":\"src/Main.java\",\"content\":\"x\"}");
        try (ExecuteurOutils executeur = new ExecuteurOutils(
                portOuLesOutilsSontPresents(), 4)) {
            executeur.executer(List.of(appel),
                    Permissions.Mode.DEMANDE_CHAQUE, gate,
                    CancelToken.NEVER, null);
        }
        assertTrue(demande.get().diffApercu.startsWith("write_file("),
                "sans lecteur : resume d'arguments, jamais un crash");
    }

    private ExecuteurOutils.PortOutils portOuLesOutilsSontPresents() {
        try {
            return port(espace);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
