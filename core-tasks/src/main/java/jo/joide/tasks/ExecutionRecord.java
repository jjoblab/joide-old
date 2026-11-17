package jo.joide.tasks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskInput.DirInput;
import jo.joide.tasks.TaskInput.FileInput;
import jo.joide.tasks.TaskInput.PropertyInput;
import jo.joide.tasks.TaskOutput.DirOutput;
import jo.joide.tasks.TaskOutput.FileOutput;

/**
 * État de la dernière exécution d'une tâche : empreinte, entrées et
 * sorties déclarées, empreintes des sorties, durée mesurée.
 *
 * <p>L'enregistrement sert de raccourci au démon chaud (comparaison
 * en mémoire, sans disque), de base au delta d'entrées et de
 * référence pour la vérification des sorties touchées. Le codage
 * binaire est stable et reproductible : le même état donne les mêmes
 * octets.</p>
 *
 * @param taskKey       clé stable de la tâche (son nom)
 * @param fingerprint   empreinte d'entrées de la dernière exécution
 * @param inputs        entrées déclarées à la dernière exécution
 * @param outputs       sorties déclarées
 * @param outputHashes  empreinte par sortie, sous forme de chemin
 *                      ancré textuel
 * @param durationMs    durée mesurée, en millisecondes
 */
public final class ExecutionRecord {

    private static final int FORMAT_VERSION = 1;

    private final String taskKey;
    private final String fingerprint;
    private final TaskInputs inputs;
    private final TaskOutputs outputs;
    private final Map<String, String> outputHashes;
    private final long durationMs;

    /**
     * @param taskKey      clé de la tâche
     * @param fingerprint  empreinte d'entrées
     * @param inputs       entrées déclarées
     * @param outputs      sorties déclarées
     * @param outputHashes empreintes des sorties
     * @param durationMs   durée mesurée
     */
    public ExecutionRecord(String taskKey, String fingerprint,
                           TaskInputs inputs, TaskOutputs outputs,
                           Map<String, String> outputHashes,
                           long durationMs) {
        this.taskKey = Objects.requireNonNull(taskKey, "taskKey");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.outputs = Objects.requireNonNull(outputs, "outputs");
        this.outputHashes = Map.copyOf(
                Objects.requireNonNull(outputHashes, "outputHashes"));
        this.durationMs = durationMs;
    }

    /** @return la clé stable de la tâche */
    public String taskKey() {
        return taskKey;
    }

    /** @return l'empreinte d'entrées de la dernière exécution */
    public String fingerprint() {
        return fingerprint;
    }

    /** @return les entrées déclarées à la dernière exécution */
    public TaskInputs inputs() {
        return inputs;
    }

    /** @return les sorties déclarées */
    public TaskOutputs outputs() {
        return outputs;
    }

    /** @return l'empreinte par sortie, en chemin ancré textuel */
    public Map<String, String> outputHashes() {
        return outputHashes;
    }

    /** @return la durée mesurée, en millisecondes */
    public long durationMs() {
        return durationMs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExecutionRecord)) {
            return false;
        }
        ExecutionRecord that = (ExecutionRecord) other;
        return taskKey.equals(that.taskKey)
                && fingerprint.equals(that.fingerprint)
                && inputs.equals(that.inputs)
                && outputs.equals(that.outputs)
                && outputHashes.equals(that.outputHashes)
                && durationMs == that.durationMs;
    }

    @Override
    public int hashCode() {
        return taskKey.hashCode() * 31 + fingerprint.hashCode() * 7
                + inputs.hashCode() + outputs.hashCode()
                + outputHashes.hashCode() + Long.hashCode(durationMs);
    }

    @Override
    public String toString() {
        return "ExecutionRecord[taskKey=" + taskKey + ", fingerprint="
                + fingerprint + ", inputs=" + inputs + ", outputs=" + outputs
                + ", outputHashes=" + outputHashes + ", durationMs="
                + durationMs + "]";
    }

    /**
     * @return l'enregistrement codé en octets, de façon stable
     */
    public byte[] encode() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(FORMAT_VERSION);
                out.writeUTF(taskKey);
                out.writeUTF(fingerprint);
                out.writeLong(durationMs);
                writeInputs(out, inputs);
                writeOutputs(out, outputs);
                out.writeInt(outputHashes.size());
                for (Map.Entry<String, String> entry : outputHashes.entrySet()) {
                    out.writeUTF(entry.getKey());
                    out.writeUTF(entry.getValue());
                }
            }
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory stream failed", impossible);
        }
    }

    /**
     * @param bytes octets produits par {@link #encode()}
     * @return l'enregistrement décodé
     * @throws IOException si les octets sont illisibles ou d'une
     *                     version inconnue
     */
    public static ExecutionRecord decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("unknown record version: " + version);
            }
            String taskKey = in.readUTF();
            String fingerprint = in.readUTF();
            long durationMs = in.readLong();
            TaskInputs inputs = readInputs(in);
            TaskOutputs outputs = readOutputs(in);
            int hashCount = in.readInt();
            Map<String, String> outputHashes = new LinkedHashMap<>();
            for (int i = 0; i < hashCount; i++) {
                outputHashes.put(in.readUTF(), in.readUTF());
            }
            return new ExecutionRecord(taskKey, fingerprint, inputs, outputs,
                    outputHashes, durationMs);
        }
    }

    private static void writeInputs(DataOutputStream out, TaskInputs inputs)
            throws IOException {
        List<TaskInput> list = inputs.asList();
        out.writeInt(list.size());
        for (TaskInput input : list) {
            if (input instanceof FileInput file) {
                out.writeByte('F');
                out.writeUTF(file.path().toString());
                out.writeBoolean(file.hashContent());
            } else if (input instanceof DirInput dir) {
                out.writeByte('D');
                out.writeUTF(dir.path().toString());
            } else {
                throw new IOException("unsupported input type");
            }
        }
        Map<String, String> properties = inputs.properties();
        out.writeInt(properties.size());
        for (Map.Entry<String, String> property : properties.entrySet()) {
            out.writeUTF(property.getKey());
            out.writeUTF(property.getValue());
        }
    }

    private static TaskInputs readInputs(DataInputStream in) throws IOException {
        TaskInputs.Builder builder = TaskInputs.builder();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            byte kind = in.readByte();
            switch (kind) {
                case 'F' -> builder.add(new FileInput(
                        RootedPath.parse(in.readUTF()), in.readBoolean()));
                case 'D' -> builder.add(new DirInput(RootedPath.parse(in.readUTF())));
                default -> throw new IOException("unknown input kind: " + kind);
            }
        }
        int propertyCount = in.readInt();
        for (int i = 0; i < propertyCount; i++) {
            builder.property(in.readUTF(), in.readUTF());
        }
        return builder.build();
    }

    private static void writeOutputs(DataOutputStream out, TaskOutputs outputs)
            throws IOException {
        List<TaskOutput> list = outputs.asList();
        out.writeInt(list.size());
        for (TaskOutput output : list) {
            if (output instanceof FileOutput file) {
                out.writeByte('F');
                out.writeUTF(file.path().toString());
            } else if (output instanceof DirOutput dir) {
                out.writeByte('D');
                out.writeUTF(dir.path().toString());
            } else {
                throw new IOException("unsupported output type");
            }
        }
    }

    private static TaskOutputs readOutputs(DataInputStream in) throws IOException {
        TaskOutputs.Builder builder = TaskOutputs.builder();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            byte kind = in.readByte();
            String path = in.readUTF();
            if (kind == 'F') {
                builder.file(RootedPath.parse(path));
            } else if (kind == 'D') {
                builder.dir(RootedPath.parse(path));
            } else {
                throw new IOException("unknown output kind: " + kind);
            }
        }
        return builder.build();
    }
}
