package GUI;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class StartupDialog extends JDialog {

    // ── Colors ──
    private static final Color BG_DARK    = new Color(10, 12, 18);
    private static final Color BG_PANEL   = new Color(18, 22, 32);
    private static final Color BG_CARD    = new Color(24, 30, 44);
    private static final Color ACCENT_BLUE   = new Color(56, 139, 253);
    private static final Color ACCENT_GREEN  = new Color(35, 209, 139);
    private static final Color ACCENT_ORANGE = new Color(255, 153, 51);
    private static final Color TEXT_PRIMARY  = new Color(220, 230, 255);
    private static final Color TEXT_DIM      = new Color(100, 115, 145);
    private static final Color BORDER_COLOR  = new Color(35, 45, 65);

    private static final Font FONT_TITLE  = new Font("Monospaced", Font.BOLD, 13);
    private static final Font FONT_BODY   = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font FONT_HEADER = new Font("Monospaced", Font.BOLD, 20);

    // ── Result fields ──
    private String selectedScheduler = null;
    private int timeSlice = 2;
    private int[] arrivalTimes = {0, 1, 4};
    private boolean confirmed = false;

    // ── UI ──
    private JComboBox<String> schedulerBox;
    private JSpinner timeSliceSpinner;
    private JPanel timeSlicePanel;
    private JSpinner[] arrivalSpinners = new JSpinner[3];

    public StartupDialog(JFrame parent) {
        super(parent, "OS Simulator — Configuration", true);
        setSize(500, 540);
        setResizable(false);
        setLocationRelativeTo(parent);
        setUndecorated(false);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
            new EmptyBorder(20, 24, 20, 24)
        ));

        JLabel title = new JLabel("◈  OS SIMULATOR");
        title.setFont(FONT_HEADER);
        title.setForeground(ACCENT_BLUE);

        JLabel sub = new JLabel("Configure simulation parameters");
        sub.setFont(FONT_BODY);
        sub.setForeground(TEXT_DIM);

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 4));
        text.setBackground(BG_PANEL);
        text.add(title);
        text.add(sub);
        panel.add(text);
        return panel;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(BG_DARK);
        form.setBorder(new EmptyBorder(20, 24, 20, 24));

        // ── Scheduler selection ──
        form.add(sectionLabel("Scheduling Algorithm"));
        form.add(Box.createVerticalStrut(8));

        schedulerBox = new JComboBox<>(new String[]{
            "Round Robin (RR)",
            "Highest Response Ratio Next (HRRN)",
            "Multi-Level Feedback Queue (MLFQ)"
        });
        styleComboBox(schedulerBox);
        schedulerBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        form.add(schedulerBox);

        form.add(Box.createVerticalStrut(20));

        // ── Time slice (only for RR / MLFQ) ──
        timeSlicePanel = new JPanel(new BorderLayout(12, 0));
        timeSlicePanel.setBackground(BG_DARK);
        timeSlicePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel timeSliceLeft = new JPanel(new GridLayout(2, 1));
        timeSliceLeft.setBackground(BG_DARK);
        timeSliceLeft.add(sectionLabel("Time Slice (instructions)"));
        JLabel timeSliceNote = new JLabel("  Used by RR and MLFQ base quantum");
        timeSliceNote.setFont(new Font("Monospaced", Font.PLAIN, 10));
        timeSliceNote.setForeground(TEXT_DIM);
        timeSliceLeft.add(timeSliceNote);

        timeSliceSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        styleSpinner(timeSliceSpinner);
        timeSliceSpinner.setMaximumSize(new Dimension(80, 36));
        timeSliceSpinner.setPreferredSize(new Dimension(80, 36));

        timeSlicePanel.add(timeSliceLeft, BorderLayout.CENTER);
        timeSlicePanel.add(timeSliceSpinner, BorderLayout.EAST);
        form.add(timeSlicePanel);

        form.add(Box.createVerticalStrut(20));

        // ── Arrival times ──
        form.add(sectionLabel("Process Arrival Times (clock cycles)"));
        form.add(Box.createVerticalStrut(8));

        JPanel arrivalPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        arrivalPanel.setBackground(BG_DARK);
        arrivalPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        String[] processLabels = {"Program 1", "Program 2", "Program 3"};
        int[] defaults = {0, 1, 4};

        for (int i = 0; i < 3; i++) {
            JPanel cell = new JPanel(new GridLayout(2, 1, 0, 4));
            cell.setBackground(BG_CARD);
            cell.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 10, 8, 10)
            ));

            JLabel lbl = new JLabel(processLabels[i]);
            lbl.setFont(FONT_BODY);
            lbl.setForeground(ACCENT_GREEN);

            arrivalSpinners[i] = new JSpinner(new SpinnerNumberModel(defaults[i], 0, 100, 1));
            styleSpinner(arrivalSpinners[i]);

            cell.add(lbl);
            cell.add(arrivalSpinners[i]);
            arrivalPanel.add(cell);
        }

        form.add(arrivalPanel);

        // Show/hide time slice based on scheduler
        schedulerBox.addActionListener(e -> {
            int idx = schedulerBox.getSelectedIndex();
            // Hide time slice for HRRN (index 1)
            timeSlicePanel.setVisible(idx != 1);
            revalidate();
            repaint();
        });

        return form;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 12));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_COLOR));

        JButton cancelBtn = makeButton("Cancel", TEXT_DIM);
        JButton startBtn  = makeButton("▶  Start Simulation", ACCENT_GREEN);

        cancelBtn.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        startBtn.addActionListener(e -> {
            // Collect values
            int idx = schedulerBox.getSelectedIndex();
            if (idx == 0) selectedScheduler = "RR";
            else if (idx == 1) selectedScheduler = "HRRN";
            else selectedScheduler = "MLFQ";

            timeSlice = (int) timeSliceSpinner.getValue();
            for (int i = 0; i < 3; i++) {
                arrivalTimes[i] = (int) arrivalSpinners[i].getValue();
            }

            confirmed = true;
            dispose();
        });

        panel.add(cancelBtn);
        panel.add(startBtn);
        return panel;
    }

    // ── Helpers ──
    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_TITLE);
        label.setForeground(TEXT_DIM);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void styleComboBox(JComboBox<String> box) {
        box.setBackground(BG_CARD);
        box.setForeground(TEXT_PRIMARY);
        box.setFont(FONT_BODY);
        box.setBorder(new LineBorder(BORDER_COLOR, 1));
        box.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? ACCENT_BLUE.darker() : BG_CARD);
                setForeground(TEXT_PRIMARY);
                setFont(FONT_BODY);
                setBorder(new EmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setBackground(BG_CARD);
        spinner.setForeground(TEXT_PRIMARY);
        spinner.setFont(FONT_BODY);
        spinner.setBorder(new LineBorder(BORDER_COLOR, 1));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(BG_CARD);
            tf.setForeground(TEXT_PRIMARY);
            tf.setFont(FONT_BODY);
            tf.setCaretColor(ACCENT_BLUE);
            tf.setBorder(new EmptyBorder(4, 8, 4, 8));
        }
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
            new EmptyBorder(8, 20, 8, 20)
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

    private Color blend(Color base, Color accent, float ratio) {
        float clampedRatio = Math.max(0f, Math.min(1f, ratio));
        int red = Math.round(base.getRed() * (1 - clampedRatio) + accent.getRed() * clampedRatio);
        int green = Math.round(base.getGreen() * (1 - clampedRatio) + accent.getGreen() * clampedRatio);
        int blue = Math.round(base.getBlue() * (1 - clampedRatio) + accent.getBlue() * clampedRatio);
        return new Color(red, green, blue);
    }

    // ── Getters ──
    public boolean isConfirmed()       { return confirmed; }
    public String getSelectedScheduler() { return selectedScheduler; }
    public int getTimeSlice()          { return timeSlice; }
    public int[] getArrivalTimes()     { return arrivalTimes; }
}
