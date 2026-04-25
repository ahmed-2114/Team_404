package GUI;

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
import java.util.ArrayList;
import java.util.List;

public class Simulator extends JFrame {

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
    

    private int clock = 0;
    private Process runningProcess = null;
    private String currentInstruction = "";
    private String schedulerName = "Round Robin";

    // ── UI Components ──
    private JLabel clockLabel;
    private JLabel runningLabel;
    private JLabel instructionLabel;
    private JLabel schedulerLabel;
    private JTextArea logArea;
    private JTable memoryTable;
    private DefaultTableModel memoryTableModel;
    private JPanel readyQueuePanel;
    private JPanel blockedQueuePanel;
    private JButton stepButton;
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

        panel.add(sectionLabel("▸ MEMORY  [40 words]"), BorderLayout.NORTH);

        String[] cols = {"ADDR", "KEY", "VALUE"};
        memoryTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        memoryTable = new JTable(memoryTableModel);
        styleTable(memoryTable);

        JScrollPane scroll = new JScrollPane(memoryTable);
        styleScroll(scroll);
        panel.add(scroll, BorderLayout.CENTER);

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

        // Bottom: log
        JPanel logPanel = new JPanel(new BorderLayout(0, 6));
        logPanel.setBackground(BG_DARK);
        logPanel.add(sectionLabel("▸ EXECUTION LOG"), BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setFont(FONT_SMALL);
        logArea.setBackground(BG_CARD);
        logArea.setForeground(TEXT_PRIMARY);
        logArea.setCaretColor(ACCENT_BLUE);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane logScroll = new JScrollPane(logArea);
        styleScroll(logScroll);
        logPanel.add(logScroll, BorderLayout.CENTER);
        panel.add(logPanel, BorderLayout.CENTER);

        return panel;
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

        stepButton  = makeButton("⏭  STEP",  ACCENT_BLUE);
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
            stepButton.setEnabled(false);
        });

        pauseButton.addActionListener(e -> {
            autoTimer.stop();
            runButton.setEnabled(true);
            pauseButton.setEnabled(false);
            stepButton.setEnabled(true);
        });

        stepButton.addActionListener(e -> onStep());

        resetButton.addActionListener(e -> {
            autoTimer.stop();
            clock = 0;
            logArea.setText("");
            refresh();
            runButton.setEnabled(true);
            pauseButton.setEnabled(false);
            stepButton.setEnabled(true);
        });

        bar.add(stepButton);
        bar.add(runButton);
        bar.add(pauseButton);
        bar.add(resetButton);

        return bar;
    }

    // ── Called when step button is pressed or auto-timer fires ──
    private void onStep() {
        clock++;
        clockLabel.setText("CLOCK: " + clock);
        refresh();
        log("── Clock " + clock + " ──");
    }

    // ── Refresh all UI components from current state ──
    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            updateMemoryTable();
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

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ── UI Helpers ──
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
        btn.setFont(FONT_TITLE);
        btn.setForeground(accent);
        btn.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 20));
        btn.setBorder(new CompoundBorder(
            new LineBorder(accent, 1),
            new EmptyBorder(6, 18, 6, 18)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 50));
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 20));
            }
        });
        return btn;
    }
    
    public void setClockValue(int clock) {
        SwingUtilities.invokeLater(() -> clockLabel.setText("CLOCK: " + clock));
    }
}