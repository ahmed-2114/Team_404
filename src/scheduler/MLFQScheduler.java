package scheduler;

import GUI.Simulator;
import interpreter.Interpreter;
import memory.DiskManager;
import memory.Memory;
import process.PCB;
import process.Process;
import queues.Blocked;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MLFQScheduler {

    private static final int levels = 4;

    private Queue<Process>[] queues;
    private Blocked blockedQueue;
    private Interpreter interpreter;
    private Memory memory;
    private DiskManager diskManager;
    private List<Process> allProcesses;
    private Simulator gui;

    // Quantum for each level: 2^i
    // Level 0 -> 1, Level 1 -> 2, Level 2 -> 4, Level 3 -> 8 (RR)
    private int[] quantums = {1, 2, 4, 8};

    @SuppressWarnings("unchecked")
    public MLFQScheduler(Blocked blockedQueue, Interpreter interpreter, Memory memory,
                         DiskManager diskManager, List<Process> allProcesses, Simulator gui) {
        this.blockedQueue = blockedQueue;
        this.interpreter = interpreter;
        this.memory = memory;
        this.diskManager = diskManager;
        this.allProcesses = allProcesses;
        this.gui = gui;
        queues = new LinkedList[levels];
        for (int i = 0; i < levels; i++) {
            queues[i] = new LinkedList<>();
        }
    }

    // Add a new process — always starts at highest priority (level 0)
    public void addProcess(Process process) {
        process.getPcb().setState(PCB.ProcessState.READY);
        queues[0].add(process);
        gui.log("[MLFQ] P" + process.getPcb().getProcessID() + " added to Queue 0");
    }

    // Add a process back after being unblocked — goes back to level 0
    public void addUnblockedProcess(Process process) {
        process.getPcb().setState(PCB.ProcessState.READY);
        queues[0].add(process);
        gui.log("[MLFQ] P" + process.getPcb().getProcessID()
                + " unblocked -> returned to Queue 0");
    }

    public void schedule(int clock) {
        // Find highest priority non-empty queue
        int level = -1;
        for (int i = 0; i < levels; i++) {
            if (!queues[i].isEmpty()) {
                level = i;
                break;
            }
        }

        if (level == -1) {
            gui.setRunningProcess(null, "");
            gui.logMlfqQueueSnapshot("[MLFQ] No processes to schedule at clock " + clock, queues);
            return;
        }

        Process selected = queues[level].poll();
        if (!diskManager.ensureResident(selected, memory, allProcesses)) {
            gui.log("[MLFQ] Failed to restore P" + selected.getPcb().getProcessID() + " from disk");
            gui.setRunningProcess(null, "");
            return;
        }
        selected.getPcb().setState(PCB.ProcessState.RUNNING);
        int quantum = quantums[level];

        gui.logMlfqQueueSnapshot("[MLFQ] Clock " + clock + " -> Running: P"
                + selected.getPcb().getProcessID()
                + " from Queue " + level
            + " | Quantum: " + quantum, queues);

        int instructionsExecuted = 0;

        while (instructionsExecuted < quantum && selected.hasNextInstruction()) {

            String instr = selected.getCurrentInstruction();

            gui.setRunningProcess(selected, instr);
            gui.refresh();

            boolean executed = interpreter.executeInstruction(selected);

            gui.log("[P" + selected.getPcb().getProcessID() + "] -> " + instr);

            if (!executed) {
                gui.logMlfqQueueSnapshot("[MLFQ] P" + selected.getPcb().getProcessID() + " is BLOCKED", queues);
                gui.setRunningProcess(null, "");
                return;
            }

            instructionsExecuted++;
        }

        if (!selected.hasNextInstruction()) {
            selected.getPcb().setState(PCB.ProcessState.FINISHED);
            gui.logMlfqQueueSnapshot("[MLFQ] P" + selected.getPcb().getProcessID() + " FINISHED", queues);
            gui.setRunningProcess(null, "");
            memory.free(selected.getPcb().getLowerBound(), selected.getPcb().getUpperBound());
        } else {
            // Used full quantum -> demote to lower queue
            int nextLevel = Math.min(level + 1, levels - 1);
            selected.getPcb().setState(PCB.ProcessState.READY);
            queues[nextLevel].add(selected);
                gui.logMlfqQueueSnapshot("[MLFQ] P" + selected.getPcb().getProcessID()
                    + " demoted to Queue " + nextLevel, queues);
        }

        gui.refresh();
    }

    public boolean isEmpty() {
        for (Queue<Process> q : queues) {
            if (!q.isEmpty()) return false;
        }
        return true;
    }
    public Queue<Process>[] getQueues() {
        return queues;
    }
}