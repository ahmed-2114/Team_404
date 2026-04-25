package memory;

import process.Process;
import process.PCB;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public class DiskManager {

    private static final String DISK_FOLDER = "disk/";
    private Consumer<String> eventLogger;

    public DiskManager() {
        // Create the disk folder if it doesn't exist
        File folder = new File(DISK_FOLDER);
        if (!folder.exists()) {
            folder.mkdir();
        }
        eventLogger = message -> {};
    }

    public void setEventLogger(Consumer<String> eventLogger) {
        this.eventLogger = eventLogger != null ? eventLogger : message -> {};
    }

    // Save a process's memory block to disk as a text file
    public void saveToDisk(Process process, Memory memory) {
        int pid = process.getPcb().getProcessID();
        String filename = DISK_FOLDER + "process_" + pid + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {

            // Write PCB info
            writer.write("PID:" + process.getPcb().getProcessID()); writer.newLine();
            writer.write("STATE:" + process.getPcb().getState()); writer.newLine();
            writer.write("PC:" + process.getPcb().getProgramCounter()); writer.newLine();
            writer.write("LOWER:" + process.getPcb().getLowerBound()); writer.newLine();
            writer.write("UPPER:" + process.getPcb().getUpperBound()); writer.newLine();
            writer.write("ARRIVAL:" + process.getPcb().getArrivalTime()); writer.newLine();
            writer.write("BURST:" + process.getPcb().getBurstTime()); writer.newLine();
            writer.write("WAITING:" + process.getPcb().getWaitingTime()); writer.newLine();

            // Write instructions
            writer.write("INSTRUCTIONS_START"); writer.newLine();
            for (String instruction : process.getInstructions()) {
                writer.write(instruction); writer.newLine();
            }
            writer.write("INSTRUCTIONS_END"); writer.newLine();

            // Write memory words in process's block
            writer.write("MEMORY_START"); writer.newLine();
            int lower = process.getPcb().getLowerBound();
            int upper = process.getPcb().getUpperBound();
            for (int i = lower; i <= upper; i++) {
                MemoryWord word = memory.read(i);
                if (word != null) {
                    writer.write(i + "," + word.getKey() + "," + word.getValue());
                } else {
                    writer.write(i + ",EMPTY,EMPTY");
                }
                writer.newLine();
            }
            writer.write("MEMORY_END"); writer.newLine();

            process.setOnDisk(true);
            emit("[SWAP OUT] P" + pid + " saved to " + filename);

        } catch (IOException e) {
            emit("[DISK ERROR] Could not save process " + pid + ": " + e.getMessage());
        }
    }

    public boolean ensureResident(Process process, Memory memory, List<Process> allProcesses) {
        if (!process.isOnDisk()) {
            return true;
        }

        int requiredSize = process.getPcb().getUpperBound() - process.getPcb().getLowerBound() + 1;
        while (memory.allocate(requiredSize) == -1) {
            Process victim = findSwapVictim(allProcesses, process);
            if (victim == null) {
                emit("[DISK ERROR] No available victim to swap out for process "
                    + process.getPcb().getProcessID());
                return false;
            }

            saveToDisk(victim, memory);
            memory.free(victim.getPcb().getLowerBound(), victim.getPcb().getUpperBound());
        }

        return loadIntoProcess(process, memory);
    }

    private Process findSwapVictim(List<Process> allProcesses, Process excludedProcess) {
        for (Process candidate : allProcesses) {
            if (candidate == excludedProcess) {
                continue;
            }

            PCB.ProcessState state = candidate.getPcb().getState();
            if (!candidate.isOnDisk()
                    && state != PCB.ProcessState.RUNNING
                    && state != PCB.ProcessState.FINISHED
                    
) {
                return candidate;
            }
        }

        return null;
    }

    private boolean loadIntoProcess(Process process, Memory memory) {
        String filename = DISK_FOLDER + "process_" + process.getPcb().getProcessID() + ".txt";
        File file = new File(filename);

        if (!file.exists()) {
            emit("[DISK ERROR] No disk file found for process " + process.getPcb().getProcessID());
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;

            int readPID = Integer.parseInt(reader.readLine().split(":")[1]);
            String stateStr = reader.readLine().split(":")[1];
            int pc = Integer.parseInt(reader.readLine().split(":")[1]);
            int lower = Integer.parseInt(reader.readLine().split(":")[1]);
            int upper = Integer.parseInt(reader.readLine().split(":")[1]);
            reader.readLine(); // ARRIVAL
            reader.readLine(); // BURST
            reader.readLine(); // WAITING

            reader.readLine(); // INSTRUCTIONS_START
            while (!(line = reader.readLine()).equals("INSTRUCTIONS_END")) {
                // Instructions are already stored on the existing Process object.
            }

            int blockSize = upper - lower + 1;
            int newLower = memory.allocate(blockSize);
            if (newLower == -1) {
                emit("[DISK ERROR] Not enough memory to load process " + readPID);
                return false;
            }
            int newUpper = newLower + blockSize - 1;

            reader.readLine(); // MEMORY_START
            int offset = newLower - lower;
            while (!(line = reader.readLine()).equals("MEMORY_END")) {
                String[] parts = line.split(",", 3);
                int index = Integer.parseInt(parts[0]) + offset;
                String key = parts[1];
                String value = parts[2];
                if (!key.equals("EMPTY")) {
                    memory.write(index, new MemoryWord(key, value));
                }
            }

            process.getPcb().setLowerBound(newLower);
            process.getPcb().setUpperBound(newUpper);
            process.getPcb().setProgramCounter(pc);
            process.getPcb().setState(PCB.ProcessState.valueOf(stateStr));
            process.setOnDisk(false);

                emit("[SWAP IN] P" + readPID + " restored into memory ["
                    + newLower + "-" + newUpper + "]");

            file.delete();
            return true;
        } catch (IOException e) {
                emit("[DISK ERROR] Could not load process " + process.getPcb().getProcessID()
                    + ": " + e.getMessage());
            return false;
        }
    }

    // Load a process back from disk into memory
    public Process loadFromDisk(int pid, Memory memory) {
        String filename = DISK_FOLDER + "process_" + pid + ".txt";
        File file = new File(filename);

        if (!file.exists()) {
            emit("[DISK ERROR] No disk file found for process " + pid);
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;

            // Read PCB fields
            int readPID = Integer.parseInt(reader.readLine().split(":")[1]);
            String stateStr = reader.readLine().split(":")[1];
            int pc = Integer.parseInt(reader.readLine().split(":")[1]);
            int lower = Integer.parseInt(reader.readLine().split(":")[1]);
            int upper = Integer.parseInt(reader.readLine().split(":")[1]);
            int arrival = Integer.parseInt(reader.readLine().split(":")[1]);
            int burst = Integer.parseInt(reader.readLine().split(":")[1]);
            int waiting = Integer.parseInt(reader.readLine().split(":")[1]);

            // Read instructions
            List<String> instructions = new ArrayList<>();
            reader.readLine(); // INSTRUCTIONS_START
            while (!(line = reader.readLine()).equals("INSTRUCTIONS_END")) {
                instructions.add(line);
            }

            // Try to allocate memory for the process
            int newLower = memory.allocate(upper - lower + 1);
            if (newLower == -1) {
                emit("[DISK ERROR] Not enough memory to load process " + pid);
                return null;
            }
            int newUpper = newLower + (upper - lower) ;

            // Read and restore memory words
            reader.readLine(); // MEMORY_START
            int offset = newLower - lower;
            while (!(line = reader.readLine()).equals("MEMORY_END")) {
                String[] parts = line.split(",", 3);
                int index = Integer.parseInt(parts[0]) + offset;
                String key = parts[1];
                String value = parts[2];
                if (!key.equals("EMPTY")) {
                    memory.write(index, new MemoryWord(key, value));
                }
            }

            // Rebuild PCB
            PCB pcb = new PCB(readPID, newLower, newUpper, arrival, burst);
            pcb.setSyncCallback(memory::syncPCB);
            pcb.setProgramCounter(pc);
            pcb.setState(PCB.ProcessState.valueOf(stateStr));

            Process process = new Process(pcb, instructions);
            process.setOnDisk(false);

            emit("[SWAP IN] P" + pid + " restored into memory [" + newLower + "-" + newUpper + "]");

            // Delete disk file after loading
            file.delete();

            return process;

        } catch (IOException e) {
            emit("[DISK ERROR] Could not load process " + pid + ": " + e.getMessage());
            return null;
        }
    }

    // Check if a process has a disk file
    public boolean isOnDisk(int pid) {
        return new File(DISK_FOLDER + "process_" + pid + ".txt").exists();
    }

    private void emit(String message) {
        System.out.println(message);
        eventLogger.accept(message);
    }
}