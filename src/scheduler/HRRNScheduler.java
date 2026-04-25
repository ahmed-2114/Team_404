package scheduler;

import GUI.Simulator;
import interpreter.Interpreter;
import memory.DiskManager;
import memory.Memory;
import process.PCB;
import process.Process;
import queues.Blocked;
import queues.Ready;

import java.util.List;

public class HRRNScheduler {

    private Ready readyQueue;
    private Blocked blockedQueue;
    private Interpreter interpreter;
    private Memory memory;
    private DiskManager diskManager;
    private List<Process> allProcesses;
    private Simulator gui;

    public HRRNScheduler(Ready readyQueue, Blocked blockedQueue, Interpreter interpreter, Memory memory,
                         DiskManager diskManager, List<Process> allProcesses, Simulator gui) {
        this.readyQueue = readyQueue;
        this.blockedQueue = blockedQueue;
        this.interpreter = interpreter;
        this.memory = memory;
        this.diskManager = diskManager;
        this.allProcesses = allProcesses;
        this.gui = gui;
    }

    public void schedule(int clock) {
        if (readyQueue.isEmpty()) {
            gui.setRunningProcess(null, "");
            gui.log("[HRRN] No processes in ready queue at clock " + clock);
            return;
        }

        // Increment waiting time for all processes in the ready queue
        for (Process p : readyQueue.getQueue()) {
            p.getPcb().incrementWaitingTime();
        }

        // Pick the process with the highest response ratio
        Process selected = selectHighestRR();
        if (selected == null) return;

        // Remove from ready queue and restore from disk if needed
        readyQueue.remove(selected);
        if (!diskManager.ensureResident(selected, memory, allProcesses)) {
            gui.log("[HRRN] Failed to restore P" + selected.getPcb().getProcessID() + " from disk");
            gui.setRunningProcess(null, "");
            return;
        }

        selected.getPcb().setState(PCB.ProcessState.RUNNING);

        gui.log("[HRRN] Clock " + clock + " -> Selected: P" + selected.getPcb().getProcessID()
                + " (Response Ratio: " + String.format("%.2f", selected.getPcb().getResponseRatio()) + ")");

        printQueues();

        // HRRN is non-preemptive: run until the process finishes or blocks
        while (selected.hasNextInstruction()) {

            String instr = selected.getCurrentInstruction();

            // Show the currently executing instruction in the GUI
            gui.setRunningProcess(selected, instr);
            gui.refresh();

            boolean executed = interpreter.executeInstruction(selected);

            gui.log("[P" + selected.getPcb().getProcessID() + "] -> " + instr);

            if (!executed) {
                // Process got blocked on a mutex
                gui.log("[HRRN] P" + selected.getPcb().getProcessID() + " is BLOCKED");
                gui.setRunningProcess(null, "");
                printQueues();
                return;
            }
        }

        // Process finished all instructions
        selected.getPcb().setState(PCB.ProcessState.FINISHED);
        gui.log("[HRRN] P" + selected.getPcb().getProcessID() + " FINISHED");

        // Clear the running display
        gui.setRunningProcess(null, "");

        // Free memory
        memory.free(selected.getPcb().getLowerBound(), selected.getPcb().getUpperBound());

        printQueues();
        gui.refresh();
    }

    // Find process with highest response ratio in the ready queue
    private Process selectHighestRR() {
        Process best = null;
        double bestRatio = -1;

        for (Process p : readyQueue.getQueue()) {
            double ratio = p.getPcb().getResponseRatio();
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = p;
            }
        }

        return best;
    }

    private void printQueues() {
        readyQueue.print();
        blockedQueue.print();
        gui.refresh();
    }
}