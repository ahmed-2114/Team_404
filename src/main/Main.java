
package main;

import GUI.Simulator;
import GUI.StartupDialog;
import interpreter.Interpreter;
import memory.DiskManager;
import memory.Memory;
import memory.MemoryWord;
import mutex.MutexManager;
import process.PCB;
import process.Process;
import queues.Blocked;
import queues.Ready;
import scheduler.HRRNScheduler;
import scheduler.MLFQScheduler;
import scheduler.RRScheduler;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String[] PROGRAM_FILES = {
        "programs/Program 1.txt",
        "programs/Program_2.txt",
        "programs/Program_3.txt"
    };

    public static void main(String[] args) {
        final String[] schedulerChoice = {null};
        final int[] timeSliceChoice = {2};
        final int[][] arrivalChoice = {null};

        try {
            SwingUtilities.invokeAndWait(() -> {
                StartupDialog dialog = new StartupDialog(null);
                dialog.setVisible(true);

                if (!dialog.isConfirmed()) {
                    System.out.println("Simulation cancelled.");
                    System.exit(0);
                }

                schedulerChoice[0] = dialog.getSelectedScheduler();
                timeSliceChoice[0] = dialog.getTimeSlice();
                arrivalChoice[0]   = dialog.getArrivalTimes();
            });
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        String schedulerName = schedulerChoice[0];
        int timeSlice        = timeSliceChoice[0];
        int[] arrivalTimes   = arrivalChoice[0];

        startSimulation(schedulerName, timeSlice, arrivalTimes);
    }

    private static void startSimulation(String schedulerName, int timeSlice, int[] arrivalTimes) {
        new SimulationSession(schedulerName, timeSlice, arrivalTimes.clone());
    }

    private static final class SimulationSession {

        private final String schedulerName;
        private final int timeSlice;
        private final int[] arrivalTimes;

        private final Memory memory;
        private final DiskManager diskManager;
        private final Blocked blockedQueue;
        private final Ready readyQueue;
        private final MutexManager mutexManager;
        private final Interpreter interpreter;
        private final List<Process> allProcesses;
        private final Simulator gui;

        private final RRScheduler rrScheduler;
        private final HRRNScheduler hrrnScheduler;
        private final MLFQScheduler mlfqScheduler;

        private final boolean[] created;

        private int pidCounter = 1;
        private int clock = 0;
        private boolean finished;

        private SimulationSession(String schedulerName, int timeSlice, int[] arrivalTimes) {
            this.schedulerName = schedulerName;
            this.timeSlice = timeSlice;
            this.arrivalTimes = arrivalTimes;

            memory = new Memory();
            diskManager = new DiskManager();
            blockedQueue = new Blocked();
            readyQueue = new Ready();
            mutexManager = new MutexManager(blockedQueue, readyQueue);
            interpreter = new Interpreter(memory, mutexManager);
            allProcesses = new ArrayList<>();
            created = new boolean[arrivalTimes.length];

            gui = new Simulator(memory, readyQueue, blockedQueue, allProcesses);
            gui.setSchedulerName(schedulerName);
            interpreter.setExecutionIO(gui);
            diskManager.setEventLogger(gui::notifyDiskEvent);
            mutexManager.setLogger(gui::log);

            RRScheduler rr = null;
            HRRNScheduler hrrn = null;
            MLFQScheduler mlfq = null;

            switch (schedulerName) {
                case "RR":
                    rr = new RRScheduler(readyQueue, blockedQueue, interpreter, memory,
                            diskManager, allProcesses, timeSlice, gui);
                    break;
                case "HRRN":
                    hrrn = new HRRNScheduler(readyQueue, blockedQueue, interpreter, memory,
                            diskManager, allProcesses, gui);
                    break;
                case "MLFQ":
                    mlfq = new MLFQScheduler(blockedQueue, interpreter, memory,
                            diskManager, allProcesses, gui);
                    mutexManager.setMLFQScheduler(mlfq);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported scheduler: " + schedulerName);
            }

            rrScheduler = rr;
            hrrnScheduler = hrrn;
            mlfqScheduler = mlfq;

            gui.setStepHandler(this::stepCycle);
            gui.setResetHandler(this::resetSimulation);

            logSessionHeader();
            gui.setClockValue(clock);
            gui.refresh();
        }

        private boolean stepCycle() {
            if (finished) {
                return false;
            }

            gui.log("---------- Clock Cycle: " + clock + " ----------");
            gui.setClockValue(clock);
            gui.logMemoryForClock(clock);

            for (int i = 0; i < arrivalTimes.length; i++) {
                if (!created[i] && arrivalTimes[i] <= clock) {
                    Process process = createProcess(pidCounter, PROGRAM_FILES[i], clock,
                            memory, diskManager, allProcesses, gui);

                    if (process != null) {
                        pidCounter++;
                        created[i] = true;
                        if (mlfqScheduler != null) {
                            allProcesses.add(process);
                            mlfqScheduler.addProcess(process);
                        } else {
                            readyQueue.add(process);
                            allProcesses.add(process);
                        }

                        gui.log("[ARRIVAL] P" + process.getPcb().getProcessID()
                                + " arrived → added to ready queue");
                        gui.refresh();
                    }
                }
            }

            if (rrScheduler != null) rrScheduler.schedule(clock);
            if (hrrnScheduler != null) hrrnScheduler.schedule(clock);
            if (mlfqScheduler != null) mlfqScheduler.schedule(clock);

            boolean allCreated = true;
            for (boolean wasCreated : created) {
                if (!wasCreated) {
                    allCreated = false;
                    break;
                }
            }

            if (allCreated && allFinished(allProcesses)) {
                finished = true;
                gui.setRunningProcess(null, "");
                gui.log("\n========== ALL PROCESSES FINISHED ==========");
                gui.log("[SIMULATION] Clock stopped.");
                gui.logMemoryForClock(clock);
                gui.refresh();
                return false;
            }

            clock++;
            return true;
        }

        private void resetSimulation() {
            gui.dispose();
            startSimulation(schedulerName, timeSlice, arrivalTimes);
        }

        private void logSessionHeader() {
            gui.log("========== OS SIMULATOR STARTED ==========");
            gui.log("Scheduler : " + schedulerName);
            if (!schedulerName.equals("HRRN")) {
                gui.log("Time Slice : " + timeSlice);
            }
            gui.log("Arrivals  : P1@" + arrivalTimes[0] + "  P2@" + arrivalTimes[1] + "  P3@" + arrivalTimes[2]);
            gui.log("==========================================\n");
        }
    }

    private static Process createProcess(int pid, String filename, int clock,
                                         Memory memory, DiskManager diskManager,
                                         List<Process> allProcesses, Simulator gui) {
    	

        List<String> instructions = loadInstructions(filename);
        if (instructions == null) return null;

        int totalWords = Memory.PCB_WORDS + Memory.VARIABLE_WORDS + instructions.size();
        int lower = memory.allocate(totalWords);

        if (lower == -1) {
            gui.log("[MEMORY] Not enough space for P" + pid + " — swapping out a process");
//            while (lower == -1) {
//                Process victim = findVictim(allProcesses);
//                if (victim == null) break;
//                diskManager.saveToDisk(victim, memory);
//                memory.free(victim.getPcb().getLowerBound(), victim.getPcb().getUpperBound());
//                gui.log("[SWAP OUT] P" + victim.getPcb().getProcessID() + " swapped to disk");
//                lower = memory.allocate(totalWords);
//            }
            Process victim = findVictim(allProcesses);
            if (victim != null) {
                diskManager.saveToDisk(victim, memory);
                memory.free(victim.getPcb().getLowerBound(), victim.getPcb().getUpperBound());
                gui.log("[SWAP OUT] P" + victim.getPcb().getProcessID() + " swapped to disk");
                lower = memory.allocate(totalWords);
            }
        }

        if (lower == -1) {
            gui.log("[ERROR] Still not enough memory for P" + pid);
            return null;
        }

        int upper = lower + totalWords - 1;

        PCB pcb = new PCB(pid, lower, upper, clock, instructions.size());
        pcb.setSyncCallback(memory::syncPCB);

        for (int i = 0; i < Memory.VARIABLE_WORDS; i++) {
            int variableIndex = Memory.getVariableStart(lower) + i;
            memory.write(variableIndex, new MemoryWord("var_" + pid + "_" + i, ""));
        }

        int instrStart = Memory.getInstructionStart(lower);
        for (int i = 0; i < instructions.size(); i++) {
            memory.write(instrStart + i, new MemoryWord("instr_" + pid + "_" + i, instructions.get(i)));
        }

        Process process = new Process(pcb, instructions);

        gui.log("[CREATED] P" + pid + " loaded into memory [" + lower + "-" + upper + "]");
        gui.refresh();

        return process;
    }

    private static List<String> loadInstructions(String filename) {
        List<String> instructions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) instructions.add(line.trim());
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Could not load: " + filename);
            return null;
        }
        return instructions;
    }

    private static Process findVictim(List<Process> allProcesses) {
     for (Process p : allProcesses) {
//        	if (p.getPcb().getState() == PCB.ProcessState.READY
//        	        && !p.isOnDisk()) {
            if ((p.getPcb().getState() == PCB.ProcessState.READY || p.getPcb().getState() == PCB.ProcessState.BLOCKED)
                    && !p.isOnDisk()
                    && p.getPcb().getState() != PCB.ProcessState.FINISHED) {
                return p;
            }
        }
        return null;
    }

    private static boolean allFinished(List<Process> allProcesses) {
        if (allProcesses.isEmpty()) return false;
        for (Process p : allProcesses) {
            if (p.getPcb().getState() != PCB.ProcessState.FINISHED) return false;
        }
        return true;
    }
}

