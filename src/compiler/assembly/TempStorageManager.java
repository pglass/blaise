package compiler.assembly;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/*
 * This manages some temporary storage slots as we generate code,
 * ensuring we get unique labels for these slots.
 *
 * In particular, this is useful for loading ints to the float stack,
 * since FILD requires a memory location.
 */
public class TempStorageManager {
    /* public so things can iterate over the memory locations */
    public HashMap<Integer, List<Boolean>> memLocations;

    public TempStorageManager() {
        this.memLocations = new HashMap<Integer, List<Boolean>>();
    }

    /* Return the label of a memory location of size bytes */
    public String acquireTempStorage(int size) {
        List<Boolean> storage = this.memLocations.get(size);
        if (storage == null) {
            this.memLocations.put(size, new LinkedList<Boolean>());
            this.memLocations.get(size).add(true);
            return this.formatLabel(size, 0);
        } else {
            for (int i = 0; i < storage.size(); ++i) {
                if (storage.get(i))
                    return this.formatLabel(size, i);
            }
            this.memLocations.get(size).add(true);
            return this.formatLabel(size, this.memLocations.size() - 1);
        }
    }

    /* Free a storage location of the given size */
    public void freeTempStorage(int size) {
        List<Boolean> storage = this.memLocations.get(size);
        if (storage != null) {
            for (int i = 0; i < storage.size(); ++i) {
                if (storage.get(i)) {
                    storage.set(i, false);
                    return;
                }
            }
        }
    }

    public String formatLabel(int size, int index) {
        return String.format("TMP_%s_%s", size, index);
    }
}
