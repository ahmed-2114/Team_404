package process;

import java.util.List;

public class Process {
    private PCB pcb;
    private List<String> instructions; // Raw instruction lines
    private boolean onDisk;            // Is this process swapped to disk?

    public Process(PCB pcb, List<String> instructions) {
        this.pcb = pcb;
        this.instructions = instructions;
        this.onDisk = false;
    }

    public boolean hasNextInstruction() {
        return pcb.getProgramCounter() < instructions.size();
    }

    public String getCurrentInstruction() {
        int pc = pcb.getProgramCounter();
        if (pc < instructions.size()) {
            return instructions.get(pc);
        }
        return null;
    }

    public PCB getPcb() { 
    	return pcb; 
    	}
    public List<String> getInstructions() { 
    	return instructions; 
    	}
    public boolean isOnDisk() { 
    	return onDisk;
    	}
    public void setOnDisk(boolean onDisk) { 
    	this.onDisk = onDisk; 
    	}

    @Override
    public String toString() {
        return "Process[PID=" + pcb.getProcessID() + ", onDisk=" + onDisk + ", " + pcb + "]";
    }
}