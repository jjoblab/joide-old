package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.platform.RootName;
import jo.joide.platform.RootedPath;

/**
 * Codage binaire des enregistrements d'exécution : aller-retour
 * exact, reproductibilité octet à octet, rejet des versions inconnues.
 */
class ExecutionRecordTest {

    private ExecutionRecord sample() {
        RootedPath input = RootedPath.of(RootName.PROJECT, "src/a.txt");
        RootedPath output = RootedPath.of(RootName.OUT, "a.txt");
        return new ExecutionRecord("mod:task", "deadbeef",
                TaskInputs.builder()
                        .file(input)
                        .fileSignalsOnly(RootedPath.of(RootName.M2, "jar"))
                        .dir(RootedPath.of(RootName.PROJECT, "src"))
                        .property("niveau", "17")
                        .build(),
                TaskOutputs.builder().file(output).build(),
                Map.of(output.toString(), "cafe01"), 42L);
    }

    @Test
    void roundTripPreservesEveryField() throws IOException {
        ExecutionRecord record = sample();
        ExecutionRecord decoded = ExecutionRecord.decode(record.encode());
        assertEquals(record, decoded);
        assertEquals(record.inputs().asList(), decoded.inputs().asList());
        assertEquals(record.outputHashes(), decoded.outputHashes());
        assertEquals(record.durationMs(), decoded.durationMs());
    }

    @Test
    void encodingIsReproducible() {
        ExecutionRecord record = sample();
        assertEquals(java.util.HexFormat.of().formatHex(record.encode()),
                java.util.HexFormat.of().formatHex(record.encode()));
    }

    @Test
    void differentRecordsEncodeDifferently() {
        assertNotEquals(java.util.HexFormat.of().formatHex(sample().encode()),
                java.util.HexFormat.of().formatHex(new ExecutionRecord(
                        "mod:autre", "deadbeef", sample().inputs(),
                        sample().outputs(), sample().outputHashes(), 42L)
                        .encode()));
    }

    @Test
    void unknownVersionIsRejected() {
        ExecutionRecord record = sample();
        byte[] bytes = record.encode();
        bytes[0] = 0x7f;
        assertThrows(IOException.class, () -> ExecutionRecord.decode(bytes));
    }
}
