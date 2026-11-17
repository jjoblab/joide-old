package jo.joide.agent.outils;

import java.util.ArrayList;
import java.util.List;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.agent.espace.EspaceTravailAgent;
import jo.joide.deps.json.JsonValue;

/**
 * Le plan, la mémoire, les questions et le web — les outils v1 qui
 * restent (lot O, ADR-059) : {@code todo_write} (carte épinglée),
 * {@code read_memory}/{@code write_memory} (AGENTS.md du projet),
 * {@code web_fetch} au contenu ENVELOPPÉ
 * {@code <contenu-non-fiable source=…>} et {@code http_request}
 * façon curl, {@code ask_user} (1 à 4 questions, 2 à 4 options — le
 * modèle reçoit la consigne de ne jamais terminer une réponse par
 * une question en texte, c'est dans le prompt système).
 */
public final class OutilsDivers {

    private OutilsDivers() {
    }

    /** todo_write : remplace le plan (le modèle porte la liste complète). */
    public static OutilAgent ecrirePlan(PlanAgent plan) {
        return OutilSimple.de(
                new DescOutil("todo_write",
                        "Write the full task plan. Each item has a "
                                + "text and a done status; the plan "
                                + "card is pinned to the UI.",
                        Schemas.objet(
                                "{\"items\":{\"type\":\"array\","
                                        + "\"items\":{\"type\":"
                                        + "\"object\",\"properties\":"
                                        + "{\"text\":{\"type\":"
                                        + "\"string\"},\"done\":"
                                        + "{\"type\":\"boolean\"}},"
                                        + "\"required\":[\"text\","
                                        + "\"done\"],"
                                        + "\"additionalProperties\":"
                                        + "false}}}"),
                        false, 2_000),
                (arguments, jeton) -> {
                    List<PlanAgent.Element> elements = new ArrayList<>();
                    for (JsonValue item : arguments.member("items")
                            .items()) {
                        elements.add(new PlanAgent.Element(
                                item.member("text").asString(),
                                Boolean.TRUE.equals(
                                        item.member("done").asBoolean())));
                    }
                    plan.remplacer(elements);
                    return "plan updated (" + elements.size()
                            + " items, "
                            + elements.stream().filter(e -> e.fait)
                                    .count() + " done)";
                });
    }

    /** read_memory : lit AGENTS.md à la racine du projet. */
    public static OutilAgent lireMemoire(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("read_memory",
                        "Read the project memory (AGENTS.md at the "
                                + "project root): instructions, "
                                + "notes, conventions.",
                        Schemas.aucun(), false, 20_000),
                (arguments, jeton) -> {
                    String memoire = espace.lireMemoire();
                    return memoire.isEmpty()
                            ? "project memory is empty"
                            : memoire;
                });
    }

    /** write_memory : écrit AGENTS.md. */
    public static OutilAgent ecrireMemoire(EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("write_memory",
                        "Write the project memory (AGENTS.md): "
                                + "persist instructions and notes "
                                + "across conversations.",
                        Schemas.objet("{\"content\":{\"type\":"
                                + "\"string\"}}"),
                        true, 2_000),
                (arguments, jeton) -> {
                    espace.ecrireMemoire(
                            Schemas.texte(arguments, "content", ""));
                    return "memory written";
                });
    }

    /** ask_user : pose une à quatre questions à choix. */
    public static OutilAgent demanderUtilisateur(
            EspaceTravailAgent espace) {
        return OutilSimple.de(
                new DescOutil("ask_user",
                        "Ask the user 1 to 4 questions, each with 2 "
                                + "to 4 options. The user can always "
                                + "answer freely or skip. Prefer "
                                + "this over ending a reply with a "
                                + "question in text.",
                        Schemas.objet(
                                "{\"questions\":{\"type\":\"array\","
                                        + "\"items\":{\"type\":"
                                        + "\"object\",\"properties\":"
                                        + "{\"question\":{\"type\":"
                                        + "\"string\"},\"options\":"
                                        + "{\"type\":\"array\","
                                        + "\"items\":{\"type\":"
                                        + "\"string\"}}},"
                                        + "\"required\":[\"question\","
                                        + "\"options\"],"
                                        + "\"additionalProperties\":"
                                        + "false}}}"),
                        false, 8_000),
                (arguments, jeton) -> {
                    List<jo.joide.agent.espace.QuestionUtilisateur>
                            questions = new ArrayList<>();
                    for (JsonValue question : arguments
                            .member("questions").items()) {
                        List<String> options = new ArrayList<>();
                        for (JsonValue option : question
                                .member("options").items()) {
                            options.add(String.valueOf(option.asString()));
                        }
                        questions.add(
                                new jo.joide.agent.espace
                                        .QuestionUtilisateur(
                                        question.member("question")
                                                .asString(), options));
                    }
                    if (questions.isEmpty() || questions.size() > 4) {
                        throw new IllegalArgumentException(
                                "ask_user takes 1 to 4 questions");
                    }
                    return espace.demanderUtilisateur(questions);
                });
    }
}
