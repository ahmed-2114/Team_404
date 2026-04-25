package interpreter;

import memory.Memory;

import memory.MemoryWord;
import mutex.MutexManager;
import process.PCB;
import process.Process;

import java.io.*;
import java.util.Scanner;

public class Interpreter {

    private Memory memory;
    private MutexManager mutexManager;
    private Scanner scanner;

    public Interpreter(Memory memory, MutexManager mutexManager) {
        this.memory = memory;
        this.mutexManager = mutexManager;
        this.scanner = new Scanner(System.in);
    }

    // Execute the current instruction of a process
    // Returns true if instruction executed, false if process is blocked
    public boolean executeInstruction(Process process) {
        String instruction = process.getCurrentInstruction();
        if (instruction == null) return false;

        int pid = process.getPcb().getProcessID();
        System.out.println("[P" + pid + "] Executing: " + instruction);

        String[] parts = instruction.trim().split("\\s+", 3);
        String command = parts[0];

        switch (command) {
            case "assign":
                if (!hasOperandCount(parts, 3, process, instruction)) {
                    process.getPcb().incrementPC();
                    return true;
                }
                handleAssign(process, parts);
                break;
            case "print":
                if (!hasOperandCount(parts, 2, process, instruction)) {
                    process.getPcb().incrementPC();
                    return true;
                }
                handlePrint(process, parts);
                break;
            case "writeFile":
                if (!hasOperandCount(parts, 3, process, instruction)) {
                    process.getPcb().incrementPC();
                    return true;
                }
                handleWriteFile(process, parts);
                break;
            case "readFile":
                if (!hasOperandCount(parts, 2, process, instruction)) {
                    process.getPcb().incrementPC();
                    return true;
                }
                handleReadFile(process, parts);
                break;
            case "printFromTo":
                if (!hasOperandCount(parts, 3, process, instruction)) {
                    process.getPcb().incrementPC();
                    return true;
                }
                handlePrintFromTo(process, parts);
                break;
            case "semWait":
                if (!hasOperandCount(parts, 2, process, instruction)) {
                    process.getPcb().incrementPC();
                    return true;
                }
                boolean acquired = mutexManager.semWait(parts[1], process);
                if (!acquired) {
                    // Process is blocked, do not increment PC
                    System.out.println("[P" + pid + "] BLOCKED waiting for resource: " + parts[1]);
                    return false;
                }
                break;
            case "semSignal":
                if (!hasOperandCount(parts, 2, process, instruction)) {
                    process.getPcb().incrementPC();
                    return true;
                }
                mutexManager.semSignal(parts[1], process);
                break;
            default:
                System.out.println("[P" + pid + "] Unknown instruction: " + instruction);
        }

        // Move program counter forward
        process.getPcb().incrementPC();
        return true;
    }
    
 // New helper — returns file contents as a String
    private String readFileContents(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString().trim();
        } catch (IOException e) {
            System.out.println("[ERROR] Could not read file: " + filename);
            return "";
        }
    }

    private boolean hasOperandCount(String[] parts, int expectedCount, Process process, String instruction) {
        if (parts.length >= expectedCount) {
            return true;
        }

        System.out.println("[P" + process.getPcb().getProcessID() + "] Malformed instruction: " + instruction);
        return false;
    }

    private String resolveValue(Process process, String token) {
        String variableValue = readVariable(process, token);
        if (variableValue != null) {
            return variableValue;
        }
        return token;
    }

    // assign x y  →  store variable x = y in process memory
    private void handleAssign(Process process, String[] parts) {
        String varName = parts[1];
        String value = parts[2];

        if (value.equals("input")) {
            System.out.println("Please enter a value:");
            value = scanner.nextLine();
        } else if (value.startsWith("readFile ")) {
            String filenameVar = value.substring("readFile ".length()).trim();
            String filename = resolveValue(process, filenameVar);
            value = readFileContents(filename);
        } else {
            value = resolveValue(process, value);
        }

        writeVariable(process, varName, value);
    }

    // print x  →  print value of variable x
    private void handlePrint(Process process, String[] parts) {
        System.out.println(resolveValue(process, parts[1]));
    }

    // writeFile x y  →  write value of variable y to file named by variable x
    private void handleWriteFile(Process process, String[] parts) {
        String filename = resolveValue(process, parts[1]);
        String data = resolveValue(process, parts[2]);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(data);
            writer.newLine();
            System.out.println("[P" + process.getPcb().getProcessID() + "] Wrote to file: " + filename);
        } catch (IOException e) {
            System.out.println("[ERROR] Could not write to file: " + filename);
        }
    }

    // readFile x  →  read contents of file named by variable x
    private void handleReadFile(Process process, String[] parts) {
        String filename = resolveValue(process, parts[1]);
        String content = readFileContents(filename);
        System.out.println("[P" + process.getPcb().getProcessID() + "] File contents:\n" + content);
        writeVariable(process, "readOutput", content);
    }

    // printFromTo x y  →  print all numbers from x to y
    private void handlePrintFromTo(Process process, String[] parts) {
        String fromStr = resolveValue(process, parts[1]);
        String toStr = resolveValue(process, parts[2]);

        try {
            int from = Integer.parseInt(fromStr.trim());
            int to = Integer.parseInt(toStr.trim());
            for (int i = from; i <= to; i++) {
                System.out.println(i);
            }
        } catch (NumberFormatException e) {
            System.out.println("[ERROR] printFromTo requires integer values.");
        }
    }

    // Write a variable into the process's memory block (variable area)
    public void writeVariable(Process process, String varName, String value) {
        int lower = process.getPcb().getLowerBound();
        int pid = process.getPcb().getProcessID();
        int variableStart = Memory.getVariableStart(lower);
        int variableEnd = Memory.getVariableEnd(lower);
        int emptySlot = -1;

        for (int i = variableStart; i <= variableEnd; i++) {
            MemoryWord word = memory.read(i);
            if (word != null && word.getKey().equals(varName)) {
                word.setValue(value);
                return;
            }

            if (emptySlot == -1 && (word == null || word.getKey().startsWith("var_" + pid + "_"))) {
                emptySlot = i;
            }
        }

        if (emptySlot != -1) {
            memory.write(emptySlot, new MemoryWord(varName, value));
            return;
        }

        System.out.println("[ERROR] No memory space for variable: " + varName);
    }

    // Read a variable's value from the process's memory block
    public String readVariable(Process process, String varName) {
        int lower = process.getPcb().getLowerBound();
        int variableStart = Memory.getVariableStart(lower);
        int variableEnd = Memory.getVariableEnd(lower);

        for (int i = variableStart; i <= variableEnd; i++) {
            MemoryWord word = memory.read(i);
            if (word != null && word.getKey().equals(varName)) {
                return word.getValue().toString();
            }
        }
        return null;
    }
}