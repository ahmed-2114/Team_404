package GUI;

import interpreter.Interpreter;
import memory.Memory;

import memory.MemoryWord;
import process.PCB;
import process.Process;
import queues.Blocked;
import queues.Ready;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BooleanSupplier;

public class Simulator extends JFrame implements Interpreter.ExecutionIO {

    // ── Color Palette ──
    private static final Color BG_DARK       = new Color(10, 12, 18);
    private static final Color BG_PANEL      = new Color(18, 22, 32);
    private static final Color BG_CARD       = new Color(24, 30, 44);
    private static final Color ACCENT_BLUE   = new Color(56, 139, 253);
    private static final Color ACCENT_GREEN  = new Color(35, 209, 139);
    private static final Color ACCENT_ORANGE = new Color(255, 153, 51);
    private static final Color ACCENT_RED    = new Color(255, 85, 85);
    private static final Color ACCENT_PURPLE = new Color(155, 109, 255);
    private static final Color TEXT_PRIMARY  = new Color(220, 230, 255);
    private static final Color TEXT_DIM      = new Color(100, 115, 145);
    private static final Color BORDER_COLOR  = new Color(35, 45, 65);

    // ── Fonts ──
    private static final Font FONT_TITLE   = new Font("Monospaced", Font.BOLD, 13);
    private static final Font FONT_BODY    = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font FONT_SMALL   = new Font("Monospaced", Font.PLAIN, 11);
    private static final Font FONT_HEADER  = new Font("Monospaced", Font.BOLD, 18);
    private static final Font FONT_COUNTER = new Font("Monospaced", Font.BOLD, 28);

    // ── State ──
    private Memory memory;
    private Ready readyQueue;
    private Blocked blockedQueue;
    private List<Process> allProcesses;
    

    private Process runningProcess = null;
    private String currentInstruction = "";
    private String schedulerName = "Round Robin";
    private BooleanSupplier stepHandler = () -> false;
    private Runnable resetHandler = () -> {};
    private int currentClock = 0;

    // ── UI Components ──
    private JLabel clockLabel;
    private JLabel runningLabel;
    private JLabel instructionLabel;
    private JLabel schedulerLabel;
    private JTextArea logArea;
    private JTextArea outputArea;
    private JTextArea memorySnapshotArea;
    private JTextArea diskSnapshotArea;
    private JTable memoryTable;
    private DefaultTableModel memoryTableModel;
    private JPanel readyQueuePanel;
    private JPanel blockedQueuePanel;
    private JButton runButton;
    private JButton pauseButton;
    private Timer autoTimer;
    

    public Simulator(Memory memory, Ready readyQueue, Blocked blockedQueue,
                        List<Process> allProcesses) {
        this.memory = memory;
        this.readyQueue = readyQueue;
        this.blockedQueue = blockedQueue;
        this.allProcesses = allProcesses;

        setupFrame();
        buildUI();
        refresh();
        setVisible(true);
    }

    private void setupFrame() {
        setTitle("OS Simulator — GUC CSEN 602");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(), BorderLayout.NORTH);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        center.setDividerLocation(480);
        center.setDividerSize(2);
        center.setBorder(null);
        center.setBackground(BG_DARK);
        add(center, BorderLayout.CENTER);

        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    // ── TOP BAR ──
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_PANEL);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));
        bar.setPreferredSize(new Dimension(0, 60));

        // Title
        JLabel title = new JLabel("  ◈  OS SIMULATOR");
        title.setFont(FONT_HEADER);
        title.setForeground(ACCENT_BLUE);
        bar.add(title, BorderLayout.WEST);

        // Clock + scheduler info
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        infoPanel.setBackground(BG_PANEL);

        schedulerLabel = makeTag("RR", ACCENT_ORANGE);
        clockLabel = new JLabel("CLOCK: 0");
        clockLabel.setFont(FONT_COUNTER);
        clockLabel.setForeground(ACCENT_BLUE);

        infoPanel.add(schedulerLabel);
        infoPanel.add(clockLabel);
        infoPanel.add(Box.createHorizontalStrut(10));
        bar.add(infoPanel, BorderLayout.EAST);

        return bar;
    }

    // ── LEFT PANEL: Memory ──
    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(12, 12, 12, 6));

        panel.add(sectionLabel("▸ MEMORY AND DISK"), BorderLayout.NORTH);

        String[] cols = {"ADDR", "KEY", "VALUE"};
        memoryTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        memoryTable = new JTable(memoryTableModel);
        styleTable(memoryTable);

        JScrollPane scroll = new JScrollPane(memoryTable);
        styleScroll(scroll);

        memorySnapshotArea = createTextArea();
        memorySnapshotArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane memorySnapshotScroll = new JScrollPane(memorySnapshotArea);
        styleScroll(memorySnapshotScroll);

        diskSnapshotArea = createTextArea();
        diskSnapshotArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        diskSnapshotArea.setForeground(new Color(255, 226, 184));
        JScrollPane diskSnapshotScroll = new JScrollPane(diskSnapshotArea);
        styleScroll(diskSnapshotScroll);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(TEXT_PRIMARY);
        tabs.setFont(FONT_SMALL);
        tabs.addTab("Memory Grid", scroll);
        tabs.addTab("Cycle Snapshot", memorySnapshotScroll);
        tabs.addTab("Disk Format", diskSnapshotScroll);
        panel.add(tabs, BorderLayout.CENTER);

        return panel;
    }

    // ── RIGHT PANEL: Queues + Log ──
    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(12, 6, 12, 12));

        // Top: running process + queues
        JPanel top = new JPanel(new GridLayout(3, 1, 0, 8));
        top.setBackground(BG_DARK);
        top.setPreferredSize(new Dimension(0, 220));
        top.add(buildRunningCard());
        top.add(buildQueueCard("▸ READY QUEUE", ACCENT_GREEN, true));
        top.add(buildQueueCard("▸ BLOCKED QUEUE", ACCENT_RED, false));
        panel.add(top, BorderLayout.NORTH);

        JSplitPane bottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildLogPanel(), buildOutputPanel());
        bottom.setResizeWeight(0.62);
        bottom.setDividerSize(2);
        bottom.setBorder(null);
        bottom.setBackground(BG_DARK);
        panel.add(bottom, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout(0, 6));
        logPanel.setBackground(BG_DARK);
        logPanel.add(sectionLabel("▸ ACTIVITY"), BorderLayout.NORTH);

        logArea = createTextArea();

        JScrollPane logScroll = new JScrollPane(logArea);
        styleScroll(logScroll);
        logPanel.add(logScroll, BorderLayout.CENTER);
        return logPanel;
    }

    private JPanel buildOutputPanel() {
        JPanel outputPanel = new JPanel(new BorderLayout(0, 6));
        outputPanel.setBackground(BG_DARK);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARK);
        header.add(sectionLabel("▸ PROGRAM CONSOLE"), BorderLayout.WEST);

        JLabel hint = new JLabel("Shows user-facing output and input responses");
        hint.setFont(FONT_SMALL);
        hint.setForeground(TEXT_DIM);
        header.add(hint, BorderLayout.EAST);

        outputPanel.add(header, BorderLayout.NORTH);

        outputArea = createTextArea();
        outputArea.setForeground(new Color(204, 245, 229));

        JScrollPane outputScroll = new JScrollPane(outputArea);
        styleScroll(outputScroll);
        outputPanel.add(outputScroll, BorderLayout.CENTER);
        return outputPanel;
    }

    private JPanel buildRunningCard() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(10, 0));

        JLabel title = new JLabel("● RUNNING");
        title.setFont(FONT_TITLE);
        title.setForeground(ACCENT_GREEN);

        runningLabel = new JLabel("—");
        runningLabel.setFont(new Font("Monospaced", Font.BOLD, 15));
        runningLabel.setForeground(TEXT_PRIMARY);

        instructionLabel = new JLabel("—");
        instructionLabel.setFont(FONT_SMALL);
        instructionLabel.setForeground(TEXT_DIM);

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setBackground(BG_CARD);
        text.add(runningLabel);
        text.add(instructionLabel);

        card.add(title, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildQueueCard(String label, Color accent, boolean isReady) {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(10, 0));

        JLabel title = new JLabel(label);
        title.setFont(FONT_TITLE);
        title.setForeground(accent);
        title.setPreferredSize(new Dimension(140, 20));
        card.add(title, BorderLayout.WEST);

        JPanel slots = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        slots.setBackground(BG_CARD);
        card.add(slots, BorderLayout.CENTER);

        if (isReady) readyQueuePanel = slots;
        else blockedQueuePanel = slots;

        return card;
    }

    // ── BOTTOM BAR: Controls ──
    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        bar.setBackground(BG_PANEL);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_COLOR));

        runButton   = makeButton("▶  RUN",   ACCENT_GREEN);
        pauseButton = makeButton("⏸  PAUSE", ACCENT_ORANGE);
        JButton resetButton = makeButton("↺  RESET", ACCENT_RED);

        pauseButton.setEnabled(false);

        // Auto-run timer (500ms per step)
        autoTimer = new Timer(500, e -> onStep());

        runButton.addActionListener(e -> {
            autoTimer.start();
            runButton.setEnabled(false);
            pauseButton.setEnabled(true);
        });

        pauseButton.addActionListener(e -> {
            autoTimer.stop();
            runButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });

        resetButton.addActionListener(e -> {
            stopAutoRun();
            clearConsole();
            resetHandler.run();
            runButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });

        bar.add(runButton);
        bar.add(pauseButton);
        bar.add(resetButton);

        return bar;
    }

    // ── Called when step button is pressed or auto-timer fires ──
    private void onStep() {
        if (!stepHandler.getAsBoolean()) {
            stopAutoRun();
            runButton.setEnabled(false);
            pauseButton.setEnabled(false);
        }
    }

    // ── Refresh all UI components from current state ──
    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            updateMemoryTable();
            updateMemorySnapshot();
            updateDiskSnapshot();
            updateQueues();
            updateRunningProcess();
        });
    }

    private void updateMemoryTable() {
        SwingUtilities.invokeLater(() -> {
            memoryTableModel.setRowCount(0);
            MemoryWord[] words = memory.getWords();
            for (int i = 0; i < words.length; i++) {
                if (words[i] != null) {
                    memoryTableModel.addRow(new Object[]{
                        String.format("%02d", i),
                        words[i].getKey(),
                        words[i].getValue()
                    });
                } else {
                    memoryTableModel.addRow(new Object[]{
                        String.format("%02d", i), "—", "—"
                    });
                }
            }
        });
    }

    private void updateQueues() {
        SwingUtilities.invokeLater(() -> {
            readyQueuePanel.removeAll();
            // Make a copy before iterating
            List<Process> readyCopy = new ArrayList<>(readyQueue.getQueue());
            for (Process p : readyCopy) {
                readyQueuePanel.add(makeProcessChip("P" + p.getPcb().getProcessID(), ACCENT_GREEN));
            }
            if (readyCopy.isEmpty()) readyQueuePanel.add(emptyLabel());

            blockedQueuePanel.removeAll();
            List<Process> blockedCopy = new ArrayList<>(blockedQueue.getQueue());
            for (Process p : blockedCopy) {
                blockedQueuePanel.add(makeProcessChip("P" + p.getPcb().getProcessID(), ACCENT_RED));
            }
            if (blockedCopy.isEmpty()) blockedQueuePanel.add(emptyLabel());

            readyQueuePanel.revalidate();
            readyQueuePanel.repaint();
            blockedQueuePanel.revalidate();
            blockedQueuePanel.repaint();
        });
    }
    private void updateRunningProcess() {
        if (runningProcess != null) {
            runningLabel.setText("Process " + runningProcess.getPcb().getProcessID()
                    + "  [" + runningProcess.getPcb().getState() + "]");
            instructionLabel.setText("▸ " + currentInstruction);
        } else {
            runningLabel.setText("—");
            instructionLabel.setText("—");
        }
    }

    // ── Public setters called from Main/Scheduler ──
    public void setRunningProcess(Process p, String instruction) {
        this.runningProcess = p;
        this.currentInstruction = instruction;
        updateRunningProcess();
    }

    public void setSchedulerName(String name) {
        this.schedulerName = name;
        schedulerLabel.setText(name);
    }

    public void setStepHandler(BooleanSupplier stepHandler) {
        this.stepHandler = stepHandler != null ? stepHandler : () -> false;
    }

    public void setResetHandler(Runnable resetHandler) {
        this.resetHandler = resetHandler != null ? resetHandler : () -> {};
    }

    public void bindSimulationState(Memory memory, Ready readyQueue, Blocked blockedQueue,
                                    List<Process> allProcesses) {
        this.memory = memory;
        this.readyQueue = readyQueue;
        this.blockedQueue = blockedQueue;
        this.allProcesses = allProcesses;
        this.runningProcess = null;
        this.currentInstruction = "";
        updateRunningProcess();
    }

    public void clearLog() {
        SwingUtilities.invokeLater(() -> logArea.setText(""));
    }

    public void clearConsole() {
        SwingUtilities.invokeLater(() -> {
            logArea.setText("");
            if (outputArea != null) {
                outputArea.setText("");
            }
            if (memorySnapshotArea != null) {
                memorySnapshotArea.setText("");
            }
            if (diskSnapshotArea != null) {
                diskSnapshotArea.setText("");
            }
        });
    }

    public void stopAutoRun() {
        if (autoTimer != null && autoTimer.isRunning()) {
            autoTimer.stop();
        }
    }

    public void log(String message) {
        System.out.println(message);
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public String requestInput(Process process, String prompt, String instruction) {
        boolean wasAutoRunning = autoTimer != null && autoTimer.isRunning();
        stopAutoRun();
        setControlsPausedForInput();

        if (process != null) {
            setRunningProcess(process, instruction);
        }

        String processName = process == null ? "System" : "Process " + process.getPcb().getProcessID();
        String value;
        if (SwingUtilities.isEventDispatchThread()) {
            value = showInputDialog(processName, prompt, instruction);
        } else {
            final String[] result = new String[1];
            try {
                SwingUtilities.invokeAndWait(() -> result[0] = showInputDialog(processName, prompt, instruction));
            } catch (Exception e) {
                value = fallbackTerminalInput(prompt);
                appendProgramOutput(process, processName + " > " + value);
                return value;
            }
            value = result[0];
        }

        appendProgramOutput(process, processName + " > " + value);
        if (wasAutoRunning) {
            resumeAutoRunAfterInput();
        } else {
            setControlsReady();
        }
        return value;
    }

    @Override
    public void showProgramOutput(Process process, String output) {
        appendProgramOutput(process, output);
    }

    @Override
    public void showExecutionEvent(String message) {
        log(message);
    }

    @Override
    public void showError(String message) {
        log(message);
        appendProgramOutput(null, "Error: " + message.replace("[ERROR] ", ""));
    }

    public void logQueueSnapshot(String eventLabel) {
        log(eventLabel);
        log(buildReadyQueueConsoleLine());
        log(buildBlockedQueueConsoleLine());
    }

    public void logMlfqQueueSnapshot(String eventLabel, Queue<Process>[] queues) {
        log(eventLabel);
        log("--- MLFQ QUEUES ---");
        for (int i = 0; i < queues.length; i++) {
            log(buildMlfqQueueConsoleLine(i, queues[i]));
        }
        log("-------------------");
        log(buildBlockedQueueConsoleLine());
    }

    public void logMemoryForClock(int clock) {
        currentClock = clock;
        updateMemorySnapshot();
    }

    public void notifyDiskEvent(String message) {
        log(message);
        updateDiskSnapshot();
    }

    // ── UI Helpers ──
    private JTextArea createTextArea() {
        JTextArea area = new JTextArea();
        area.setFont(FONT_SMALL);
        area.setBackground(BG_CARD);
        area.setForeground(TEXT_PRIMARY);
        area.setCaretColor(ACCENT_BLUE);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        return area;
    }

    private void styleTable(JTable table) {
        table.setBackground(BG_CARD);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(FONT_SMALL);
        table.setRowHeight(22);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(ACCENT_BLUE.darker());
        table.setSelectionForeground(Color.WHITE);
        table.setShowGrid(true);
        table.getTableHeader().setBackground(BG_PANEL);
        table.getTableHeader().setForeground(ACCENT_BLUE);
        table.getTableHeader().setFont(FONT_TITLE);
        table.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);

        // Alternate row color
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setBackground(row % 2 == 0 ? BG_CARD : new Color(28, 35, 52));
                setForeground(val != null && val.equals("—") ? TEXT_DIM : TEXT_PRIMARY);
                setFont(FONT_SMALL);
                setBorder(new EmptyBorder(0, 8, 0, 0));
                if (sel) setBackground(ACCENT_BLUE.darker());
                return this;
            }
        });
    }

    private void styleScroll(JScrollPane scroll) {
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        scroll.getViewport().setBackground(BG_CARD);
        scroll.setBackground(BG_DARK);
        scroll.getVerticalScrollBar().setBackground(BG_PANEL);
        scroll.getHorizontalScrollBar().setBackground(BG_PANEL);
    }

    private JPanel makeCard() {
        JPanel card = new JPanel();
        card.setBackground(BG_CARD);
        card.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        return card;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_TITLE);
        label.setForeground(TEXT_DIM);
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        return label;
    }

    private JLabel makeTag(String text, Color color) {
        JLabel tag = new JLabel("  " + text + "  ");
        tag.setFont(FONT_SMALL);
        tag.setForeground(color);
        tag.setOpaque(true);
        tag.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        tag.setBorder(new LineBorder(color, 1));
        return tag;
    }

    private JLabel makeProcessChip(String text, Color color) {
        JLabel chip = new JLabel(" " + text + " ");
        chip.setFont(FONT_TITLE);
        chip.setForeground(color);
        chip.setOpaque(true);
        chip.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
        chip.setBorder(new LineBorder(color, 1));
        return chip;
    }

    private JLabel emptyLabel() {
        JLabel l = new JLabel("empty");
        l.setFont(FONT_SMALL);
        l.setForeground(TEXT_DIM);
        return l;
    }

    private JButton makeButton(String text, Color accent) {
        JButton btn = new JButton(text);
        Color normalBackground = blend(BG_PANEL, accent, 0.18f);
        Color hoverBackground = blend(BG_PANEL, accent, 0.33f);
        btn.setFont(FONT_TITLE);
        btn.setForeground(accent);
        btn.setBackground(normalBackground);
        btn.setBorder(new CompoundBorder(
            new LineBorder(accent, 1),
            new EmptyBorder(6, 18, 6, 18)
        ));
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setRolloverEnabled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(hoverBackground);
                btn.repaint();
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(normalBackground);
                btn.repaint();
            }
        });
        return btn;
    }
    
    public void setClockValue(int clock) {
        currentClock = clock;
        SwingUtilities.invokeLater(() -> clockLabel.setText("CLOCK: " + clock));
    }

    private void setControlsReady() {
        SwingUtilities.invokeLater(() -> {
            runButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });
    }

    private void setControlsPausedForInput() {
        SwingUtilities.invokeLater(() -> {
            runButton.setEnabled(false);
            pauseButton.setEnabled(false);
        });
    }

    private void resumeAutoRunAfterInput() {
        SwingUtilities.invokeLater(() -> {
            runButton.setEnabled(false);
            pauseButton.setEnabled(true);
            if (autoTimer != null && !autoTimer.isRunning()) {
                autoTimer.start();
            }
        });
    }

    private void appendProgramOutput(Process process, String message) {
        String prefix = process == null ? "" : "P" + process.getPcb().getProcessID() + "  ";
        SwingUtilities.invokeLater(() -> {
            outputArea.append(prefix + message + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private String showInputDialog(String processName, String prompt, String instruction) {
        JTextField inputField = new JTextField(18);
        inputField.setFont(FONT_BODY);
        inputField.setBackground(BG_PANEL);
        inputField.setForeground(TEXT_PRIMARY);
        inputField.setCaretColor(ACCENT_BLUE);
        inputField.setBorder(new CompoundBorder(
                new LineBorder(ACCENT_BLUE, 1),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BG_CARD);
        panel.setBorder(new EmptyBorder(8, 4, 0, 4));

        JLabel title = new JLabel(processName + " needs input");
        title.setFont(FONT_TITLE);
        title.setForeground(ACCENT_BLUE);

        JLabel promptLabel = new JLabel(prompt);
        promptLabel.setFont(FONT_BODY);
        promptLabel.setForeground(TEXT_PRIMARY);

        JLabel instructionLabel = new JLabel("Instruction: " + instruction);
        instructionLabel.setFont(FONT_SMALL);
        instructionLabel.setForeground(TEXT_DIM);

        JPanel textPanel = new JPanel(new GridLayout(3, 1, 0, 6));
        textPanel.setBackground(BG_CARD);
        textPanel.add(title);
        textPanel.add(promptLabel);
        textPanel.add(instructionLabel);

        panel.add(textPanel, BorderLayout.NORTH);
        panel.add(inputField, BorderLayout.CENTER);

        UIManager.put("OptionPane.background", BG_CARD);
        UIManager.put("Panel.background", BG_CARD);

        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Program Input",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (option != JOptionPane.OK_OPTION) {
            return "";
        }

        return inputField.getText().trim();
    }

    private String fallbackTerminalInput(String prompt) {
        System.out.println(prompt + ":");
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        return scanner.nextLine();
    }

    private void updateMemorySnapshot() {
        if (memorySnapshotArea == null || memory == null) {
            return;
        }

        StringBuilder snapshot = new StringBuilder();
        snapshot.append("Clock ").append(currentClock).append("\n");
        snapshot.append("--------------------------------\n");

        MemoryWord[] words = memory.getWords();
        for (int index = 0; index < words.length; index++) {
            MemoryWord word = words[index];
            snapshot.append(String.format("[%02d] ", index));
            if (word == null) {
                snapshot.append("EMPTY");
            } else {
                snapshot.append(word.getKey()).append(" = ").append(word.getValue());
            }
            snapshot.append('\n');
        }

        String text = snapshot.toString();
        SwingUtilities.invokeLater(() -> {
            memorySnapshotArea.setText(text);
            memorySnapshotArea.setCaretPosition(0);
        });
    }

    private void updateDiskSnapshot() {
        if (diskSnapshotArea == null) {
            return;
        }

        File diskFolder = new File("disk");
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("Disk files currently stored\n");
        snapshot.append("--------------------------------\n");

        File[] files = diskFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            snapshot.append("Disk is empty. No swapped-out process blocks.\n");
        } else {
            java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
            for (File file : files) {
                snapshot.append(file.getName()).append('\n');
                try {
                    snapshot.append(Files.readString(file.toPath()).trim());
                } catch (IOException e) {
                    snapshot.append("<Could not read disk file>");
                }
                snapshot.append("\n\n");
            }
        }

        String text = snapshot.toString();
        SwingUtilities.invokeLater(() -> {
            diskSnapshotArea.setText(text);
            diskSnapshotArea.setCaretPosition(0);
        });
    }

    private String buildReadyQueueConsoleLine() {
        StringBuilder builder = new StringBuilder("Ready Queue: [ ");
        for (Process process : readyQueue.getQueue()) {
            builder.append("P").append(process.getPcb().getProcessID()).append(" ");
        }
        builder.append("]");
        return builder.toString();
    }

    private String buildBlockedQueueConsoleLine() {
        StringBuilder builder = new StringBuilder("Blocked Queue: [ ");
        for (Process process : blockedQueue.getQueue()) {
            builder.append("P").append(process.getPcb().getProcessID()).append(" ");
        }
        builder.append("]");
        return builder.toString();
    }

    private String buildMlfqQueueConsoleLine(int level, Queue<Process> queue) {
        StringBuilder builder = new StringBuilder("  Queue ")
                .append(level)
                .append(" (Q=")
                .append(1 << level)
                .append("): [ ");
        for (Process process : queue) {
            builder.append("P").append(process.getPcb().getProcessID()).append(" ");
        }
        builder.append("]");
        return builder.toString();
    }

    private Color blend(Color base, Color accent, float ratio) {
        float clampedRatio = Math.max(0f, Math.min(1f, ratio));
        int red = Math.round(base.getRed() * (1 - clampedRatio) + accent.getRed() * clampedRatio);
        int green = Math.round(base.getGreen() * (1 - clampedRatio) + accent.getGreen() * clampedRatio);
        int blue = Math.round(base.getBlue() * (1 - clampedRatio) + accent.getBlue() * clampedRatio);
        return new Color(red, green, blue);
    }
}