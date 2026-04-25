package process;

import java.util.function.Consumer;

public class PCB {
    public enum ProcessState {
        NEW, READY, RUNNING, BLOCKED, FINISHED
        //new,ready,waiting,running,terminated
    }

    private int processID;
    private ProcessState state;
    private int programCounter;
    private int lowerBound;   // Start index in memory
    private int upperBound;   // End index in memory

    // For HRRN scheduling
    private int arrivalTime;
    private int burstTime;    // Total instructions
    private int waitingTime;
    private Consumer<PCB> syncCallback;

    public PCB(int processID, int lowerBound, int upperBound, int arrivalTime, int burstTime) {
        this.processID = processID;
        this.state = ProcessState.NEW;
        this.programCounter = 0;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.waitingTime = 0;
    }

    // Response Ratio for HRRN = (waitingTime + burstTime) / burstTime
    public double getResponseRatio() {
        return (double)(waitingTime + burstTime) / burstTime;
    }

    public void incrementWaitingTime() { waitingTime++; }
    public void incrementPC() {
        programCounter++;
        sync();
        }

    public void setSyncCallback(Consumer<PCB> syncCallback) {
        this.syncCallback = syncCallback;
        sync();
        }

    private void sync() {
        if (syncCallback != null) {
        	syncCallback.accept(this);
        }
        }

    // Getters and Setters
    public int getProcessID() { 
    	return processID; 
    	}
    public ProcessState getState() { 
    	return state; 
    	}
    public void setState(ProcessState state) { 
        this.state = state;
        sync();
    	}
    public int getProgramCounter() { 
    	return programCounter;
    	}
    public void setProgramCounter(int pc) { 
        this.programCounter = pc; 
        sync();
    	}
    public int getLowerBound() { 
    	return lowerBound; 
    	}
    public int getUpperBound() {
    	return upperBound; 
    	}
    public int getArrivalTime() {
    	return arrivalTime; 
    	}
    public int getBurstTime() { 
    	return burstTime;
    	}
    public int getWaitingTime() {
    	return waitingTime;
    	}
    public void setLowerBound(int lb) { 
        this.lowerBound = lb;
        sync();
    	}
    public void setUpperBound(int ub) {
        this.upperBound = ub;
        sync();
    	}

    @Override
    public String toString() {
        return "PCB[PID=" + processID + ", State=" + state +
               ", PC=" + programCounter + ", Bounds=[" + lowerBound + "-" + upperBound + "]]";
    }
}