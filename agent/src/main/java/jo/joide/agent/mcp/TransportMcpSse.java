package jo.joide.agent.mcp;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jo.joide.agent.TransportHttp;
import jo.joide.platform.CancelToken;

/**
 * Le vieux transport HTTP+SSE de MCP (révision 2024-11-05, lot R,
 * ADR-063) : un GET ouvre un flux d'événements dont le premier
 * événement {@code endpoint} nomme où POSTer, et toutes les réponses
 * arrivent sur ce flux. Le repli des serveurs qui ne parlent que ce
 * transport : un POST refusé par le Streamable retente ici une fois
 * (et rapporte l'échec ORIGINAL si le repli échoue aussi — le serveur
 * a plus de chances d'être un Streamable cassé). Le corps du flux se
 * lit par le port OUVERT du transport ({@code getSse}, ligne à
 * ligne), comme partout dans le module.
 */
public final class TransportMcpSse implements TransportMcp {

    private final String url;
    private final TransportMcpFlux.FabriqueEntetes entetes;
    private final TransportHttp transport;
    private final CancelToken jeton;

    private final CompletableFuture<String> pointFin =
            new CompletableFuture<>();
    private volatile boolean ferme;
    private volatile TransportMcp.Remettre remettre = message -> {
    };
    private volatile TransportHttp.ReponseOuverte flux;

    /** @param url l'URL du point de terminaison SSE @param entetes la fabrique des en-têtes @param transport le transport partagé @param jeton l'annulation */
    public TransportMcpSse(String url,
                           TransportMcpFlux.FabriqueEntetes entetes,
                           TransportHttp transport,
                           CancelToken jeton) {
        this.url = url;
        this.entetes = entetes;
        this.transport = transport;
        this.jeton = jeton == null ? CancelToken.NEVER : jeton;
    }

    @Override
    public void demarrer(TransportMcp.Remettre remettre,
                         TransportMcp.OnFerme onFerme) throws Exception {
        this.remettre = remettre;
        Map<String, String> entetesDeRequete =
                new LinkedHashMap<>(entetes.entetes());
        entetesDeRequete.put("accept", "text/event-stream");
        try {
            this.flux = transport.getSse(url, entetesDeRequete, jeton);
        } catch (TransportHttp.EchecHttp echec) {
            throw TransportMcpFlux.classeEchec(echec, false);
        }
        Thread lecteurFond = new Thread(() -> {
            Throwable echec = null;
            try {
                lireFlux();
            } catch (Throwable t) {
                echec = t;
            }
            if (!ferme) {
                pointFin.completeExceptionally(echec == null
                        ? new IOException(
                        "The server closed the event stream.")
                        : echec);
                onFerme.onFerme(echec);
            }
        }, "mcp-sse-lecteur");
        lecteurFond.setDaemon(true);
        lecteurFond.start();
        try {
            pointFin.get(ProtocoleMcp.DELAI_CONNEXION_MS,
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new ErreurMcp.EchecHttp(0,
                    "The server never named its POST endpoint: "
                            + e.getMessage());
        }
    }

    /** Le flux vivant : {@code endpoint} d'abord, les réponses ensuite. */
    private void lireFlux() throws IOException {
        TransportHttp.ReponseOuverte fluxVivant = this.flux;
        String evenement = "message";
        StringBuilder donnees = new StringBuilder();
        boolean aDonnees = false;
        String ligne;
        while ((ligne = fluxVivant.ligne()) != null) {
            jeton.checkCancelled();
            if (ligne.isEmpty()) {
                if (aDonnees) {
                    traiter(evenement, donnees.toString());
                }
                evenement = "message";
                donnees.setLength(0);
                aDonnees = false;
                continue;
            }
            if (ligne.startsWith(":")) {
                continue;
            }
            int deuxPoints = ligne.indexOf(':');
            String champ = deuxPoints < 0 ? ligne
                    : ligne.substring(0, deuxPoints);
            String valeur = deuxPoints < 0 ? ""
                    : ligne.substring(deuxPoints + 1);
            if (valeur.startsWith(" ")) {
                valeur = valeur.substring(1);
            }
            if ("event".equals(champ)) {
                evenement = valeur;
            } else if ("data".equals(champ)) {
                if (aDonnees) {
                    donnees.append('\n');
                }
                donnees.append(valeur);
                aDonnees = true;
            }
        }
        if (aDonnees) {
            traiter(evenement, donnees.toString());
        }
    }

    private void traiter(String evenement, String donnees) {
        if ("endpoint".equals(evenement)) {
            String resolu = resoudre(donnees.trim());
            if (resolu == null) {
                pointFin.completeExceptionally(new IOException(
                        "The server named an invalid endpoint: "
                                + donnees));
            } else {
                pointFin.complete(resolu);
            }
            return;
        }
        remettre.message(donnees);
    }

    /** Résout l'endpoint relatif ou absolu nommé par le serveur. */
    private String resoudre(String endpoint) {
        try {
            java.net.URI base = java.net.URI.create(url);
            java.net.URI cible = base.resolve(endpoint);
            String scheme = cible.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            return cible.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void envoyer(String message) throws IOException {
        String cible = pointFin.join();
        Map<String, String> entetesDeRequete =
                new LinkedHashMap<>(entetes.entetes());
        entetesDeRequete.putIfAbsent("content-type",
                "application/json");
        try {
            transport.post(cible, entetesDeRequete, message, jeton);
        } catch (TransportHttp.EchecHttp echec) {
            throw TransportMcpFlux.classeEchec(echec, false);
        }
    }

    @Override
    public void close() {
        ferme = true;
        TransportHttp.ReponseOuverte fluxVivant = flux;
        if (fluxVivant != null) {
            fluxVivant.fermer();
        }
    }
}
