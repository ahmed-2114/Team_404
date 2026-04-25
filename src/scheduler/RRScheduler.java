
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

public class RRScheduler {

    private Ready readyQueue;
    private Blocked blockedQueue;
    private Interpreter interpreter;
    private Memory memory;
    private DiskManager diskManager;
    private List<Process> allProcesses;
    private int timeSlice;
    private Simulator gui;

    // ✅ NEW: keep track of current running process
    private Process currentProcess = null;
    private int remainingQuantum = 0;

    public RRScheduler(Ready readyQueue, Blocked blockedQueue,
                       Interpreter interpreter, Memory memory,
                       DiskManager diskManager, List<Process> allProcesses,
                       int timeSlice, Simulator gui) {
        this.readyQueue = readyQueue;
        this.blockedQueue = blockedQueue;
        this.interpreter = interpreter;
        this.memory = memory;
        this.diskManager = diskManager;
        this.allProcesses = allProcesses;
        this.timeSlice = timeSlice;
        this.gui = gui;
    }

    public void schedule(int clock) {

        // 🟡 Step 1: pick new process if none is running
        if (currentProcess == null) {
            if (readyQueue.isEmpty()) {
                gui.log("[RR] No processes in ready queue at clock " + clock);
                return;
            }

            currentProcess = readyQueue.poll();
            if (!diskManager.ensureResident(currentProcess, memory, allProcesses)) {
                gui.log("[RR] Failed to restore P" + currentProcess.getPcb().getProcessID() + " from disk");
                currentProcess = null;
                return;
            }
            currentProcess.getPcb().setState(PCB.ProcessState.RUNNING);
            remainingQuantum = timeSlice;

            gui.log("[RR] Clock " + clock + " → Running: P"
                    + currentProcess.getPcb().getProcessID()
                    + " | Time Slice: " + timeSlice);
        }

        // 🟡 Step 2: show running process
        gui.setRunningProcess(currentProcess, currentProcess.getCurrentInstruction());
        printQueues();

        // 🟡 Step 3: execute ONE instruction
        if (currentProcess.hasNextInstruction()) {

            String instr = currentProcess.getCurrentInstruction();
            boolean executed = interpreter.executeInstruction(currentProcess);

            gui.log("[P" + currentProcess.getPcb().getProcessID() + "] → " + instr);

            // 🔴 Case: BLOCKED
            if (!executed) {
                gui.log("[RR] P" + currentProcess.getPcb().getProcessID() + " is BLOCKED");
                currentProcess = null; // release CPU
                return;
            }

            remainingQuantum--;
        }

        // 🟢 Case 1: process finished
        if (!currentProcess.hasNextInstruction()) {
            currentProcess.getPcb().setState(PCB.ProcessState.FINISHED);

            gui.log("[RR] P" + currentProcess.getPcb().getProcessID() + " FINISHED");

            memory.free(currentProcess.getPcb().getLowerBound(),
                        currentProcess.getPcb().getUpperBound());

            currentProcess = null;
        }

        // 🟡 Case 2: quantum finished → move to back
        else if (remainingQuantum == 0) {
            gui.log("[RR] P" + currentProcess.getPcb().getProcessID()
                    + " time slice expired → moved to back of ready queue");

            currentProcess.getPcb().setState(PCB.ProcessState.READY);
            readyQueue.add(currentProcess);
            currentProcess = null;
        }

        gui.refresh();
        printQueues();
    }

    public void setTimeSlice(int timeSlice) {
        this.timeSlice = timeSlice;
    }

    public int getTimeSlice() {
        return timeSlice;
    }

    private void printQueues() {
        readyQueue.print();
        blockedQueue.print();
        gui.refresh();
    }
}

