package main;

import interpreter.Interpreter;
import memory.Memory;
import memory.MemoryWord;
import mutex.MutexManager;
import process.PCB;
import process.Process;
import queues.Blocked;
import queues.Ready;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HeadlessSmokeTest {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -p bin -m Team_404/main.HeadlessSmokeTest <pid> <program-file>");
            return;
        }

        int pid = Integer.parseInt(args[0]);
        String programFile = args[1];

        Memory memory = new Memory();
        Blocked blockedQueue = new Blocked();
        Ready readyQueue = new Ready();
        MutexManager mutexManager = new MutexManager(blockedQueue, readyQueue);
        Interpreter interpreter = new Interpreter(memory, mutexManager);

        Process process = createProcess(pid, programFile, memory);
        if (process == null) {
            System.out.println("[TEST] Could not create process for file: " + programFile);
            return;
        }

        process.getPcb().setState(PCB.ProcessState.RUNNING);
        System.out.println("[TEST] Starting headless run for P" + pid + " using " + programFile);

        while (process.hasNextInstruction()) {
            boolean executed = interpreter.executeInstruction(process);
            if (!executed) {
                System.out.println("[TEST] Process blocked before completion. State=" + process.getPcb().getState());
                break;
            }
        }

        if (!process.hasNextInstruction()) {
            process.getPcb().setState(PCB.ProcessState.FINISHED);
        }

        System.out.println("[TEST] Final PCB: " + process.getPcb());
        printVariableArea(memory, process);
    }

    private static Process createProcess(int pid, String filename, Memory memory) {
        List<String> instructions = loadInstructions(filename);
        if (instructions == null) {
            return null;
        }

        int totalWords = Memory.PCB_WORDS + Memory.VARIABLE_WORDS + instructions.size();
        int lower = memory.allocate(totalWords);
        if (lower == -1) {
            System.out.println("[TEST] Not enough memory to allocate process");
            return null;
        }

        int upper = lower + totalWords - 1;
        PCB pcb = new PCB(pid, lower, upper, 0, instructions.size());
        pcb.setSyncCallback(memory::syncPCB);

        for (int i = 0; i < Memory.VARIABLE_WORDS; i++) {
            int variableIndex = Memory.getVariableStart(lower) + i;
            memory.write(variableIndex, new MemoryWord("var_" + pid + "_" + i, ""));
        }

        int instructionStart = Memory.getInstructionStart(lower);
        for (int i = 0; i < instructions.size(); i++) {
            memory.write(instructionStart + i, new MemoryWord("instr_" + pid + "_" + i, instructions.get(i)));
        }

        return new Process(pcb, instructions);
    }

    private static List<String> loadInstructions(String filename) {
        List<String> instructions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    instructions.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.out.println("[TEST] Failed to read program file: " + filename);
            return null;
        }
        return instructions;
    }

    private static void printVariableArea(Memory memory, Process process) {
        int lower = process.getPcb().getLowerBound();
        int variableStart = Memory.getVariableStart(lower);
        int variableEnd = Memory.getVariableEnd(lower);

        System.out.println("[TEST] Variable area:");
        for (int index = variableStart; index <= variableEnd; index++) {
            MemoryWord word = memory.read(index);
            System.out.println("  [" + index + "] " + (word == null ? "EMPTY" : word));
        }
    }
}