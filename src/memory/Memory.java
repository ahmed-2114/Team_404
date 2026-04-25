package memory;

import process.PCB;

public class Memory {
    public static final int PCB_WORDS = 5;
    public static final int VARIABLE_WORDS = 3;

    private static final int SIZE = 40;
    private MemoryWord[] words;

    public Memory() {
        words = new MemoryWord[SIZE];
    }

    // Allocate a contiguous block of 'size' words, returns start index or -1 if not enough space
    public int allocate(int size) {
        int count = 0;
        int start = -1;
        for (int i = 0; i < SIZE; i++) {
            if (words[i] == null) {
                if (count == 0) start = i;
                count++;
                if (count == size) return start;
            } else {
                count = 0;
                start = -1;
            }
        }
        return -1; // Not enough contiguous space
    }

    // Free all words in a process's memory block
    public void free(int lower, int upper) {
        for (int i = lower; i <= upper; i++) {
            words[i] = null;
        }
    }

    public void write(int index, MemoryWord word) {
        if (index >= 0 && index < SIZE) {
            words[index] = word;
        }
    }

    public MemoryWord read(int index) {
        if (index >= 0 && index < SIZE) {
            return words[index];
        }
        return null;
    }

    public int getSize() { return SIZE; }

    public static int getVariableStart(int lowerBound) {
        return lowerBound + PCB_WORDS;
    }

    public static int getVariableEnd(int lowerBound) {
        return getVariableStart(lowerBound) + VARIABLE_WORDS - 1;
    }

    public static int getInstructionStart(int lowerBound) {
        return getVariableEnd(lowerBound) + 1;
    }

    public void syncPCB(PCB pcb) {
        int lower = pcb.getLowerBound();
        int pid = pcb.getProcessID();

        write(lower, new MemoryWord("pid_" + pid, pid));
        write(lower + 1, new MemoryWord("state_" + pid, pcb.getState().name()));
        write(lower + 2, new MemoryWord("pc_" + pid, pcb.getProgramCounter()));
        write(lower + 3, new MemoryWord("lower_" + pid, pcb.getLowerBound()));
        write(lower + 4, new MemoryWord("upper_" + pid, pcb.getUpperBound()));
    }

    // Find a process's lower bound by its PID stored in PCB area
    public int findProcessStart(int pid) {
        for (int i = 0; i < SIZE; i++) {
            if (words[i] != null &&
                words[i].getKey().equals("pid_" + pid)) {
                return i;
            }
        }
        return -1;
    }

    // Print memory in human-readable format
    public void printMemory() {
        System.out.println("\n========== MEMORY STATE ==========");
        for (int i = 0; i < SIZE; i++) {
            if (words[i] != null) {
                System.out.printf("[%02d] %s%n", i, words[i].toString());
            } else {
                System.out.printf("[%02d] EMPTY%n", i);
            }
        }
        System.out.println("==================================\n");
    }

    public MemoryWord[] getWords() { return words; }
}