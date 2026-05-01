import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * TypingRaceGUI.java  –  Keyboard Sprint Championship — Graphical Interface
 *
 * A full-featured Swing GUI for the Typing Race Simulator.
 * Architecture is completely different from a simple JProgressBar layout:
 *
 *   • Setup screen with passage chooser, seat count, difficulty modifiers,
 *     and per-typist customisation (style, keyboard, accessories).
 *   • Race screen with a hand-drawn canvas track, animated typist tokens,
 *     live WPM counters, stamina bars, and turn log.
 *   • Results screen with per-typist stats table and leaderboard.
 *
 * All three screens live inside a JTabbedPane so the user can switch freely
 * before the race starts, but tabs are locked during a running race.
 */
public class TypingRaceGUI extends JFrame {

    // ============================================================
    //  Constants & Palette
    // ============================================================

    private static final int WINDOW_W   = 900;
    private static final int WINDOW_H   = 680;
    private static final int TICK_MS    = 220;   // ms between simulation ticks
    private static final int MAX_TYPISTS = 6;

    // Colour palette
    private static final Color BG_DARK      = new Color(18, 18, 30);
    private static final Color BG_PANEL     = new Color(28, 28, 45);
    private static final Color ACCENT_GOLD  = new Color(255, 200, 50);
    private static final Color ACCENT_CYAN  = new Color(50, 220, 200);
    private static final Color TEXT_LIGHT   = new Color(220, 220, 235);
    private static final Color TEXT_DIM     = new Color(130, 130, 160);
    private static final Color TRACK_BG     = new Color(38, 38, 60);
    private static final Color TRACK_LINE   = new Color(60, 60, 90);
    private static final Color FINISH_LINE  = new Color(255, 80, 80);

    // Predefined typist colours (one per seat)
    private static final Color[] TYPIST_COLORS = {
        new Color(255, 100, 100),
        new Color(100, 180, 255),
        new Color(100, 255, 140),
        new Color(255, 180, 50),
        new Color(210, 100, 255),
        new Color(255, 140, 50)
    };

    // Predefined passages (short / medium / long)
    private static final String[] PASSAGE_LABELS = {
        "Short  (25 chars)",
        "Medium (50 chars)",
        "Long   (80 chars)",
        "Custom…"
    };
    private static final int[] PASSAGE_LENGTHS = { 25, 50, 80, 0 };

    // Typing styles: name → accuracy modifier
    private static final String[] STYLES     = { "Touch Typist", "Hunt & Peck", "Phone Thumbs", "Voice-to-Text" };
    private static final double[] STYLE_MODS = {  0.05,          -0.10,          -0.05,           0.00 };

    // Keyboard types: name → speed modifier (displayed only; affects visual WPM)
    private static final String[] KEYBOARDS  = { "Mechanical", "Membrane", "Touchscreen", "Stenography" };
    private static final double[] KB_MODS    = {  1.05,         1.00,        0.90,          1.20 };

    // Accessories: name → accuracy effect
    private static final String[] ACCESSORIES  = { "None", "Wrist Support", "Energy Drink", "Noise-Cancelling Headphones" };
    private static final double[] ACCESSORY_MOD = { 0.0,   0.02,            0.03,           0.04 };

    // ============================================================
    //  State
    // ============================================================

    private JTabbedPane tabbedPane;

    // -- Setup tab widgets --
    private JComboBox<String> passageCombo;
    private JSpinner          customLengthSpinner;
    private JSpinner          seatCountSpinner;
    private JCheckBox         autocorrectCheck;
    private JCheckBox         caffeineCheck;
    private JCheckBox         nightShiftCheck;

    // Per-seat setup rows
    private JTextField[]  nameFields;
    private JTextField[]  symbolFields;
    private JComboBox[]   styleCombo;
    private JComboBox[]   keyboardCombo;
    private JComboBox[]   accessoryCombo;
    private JSlider[]     accuracySliders;
    private JLabel[]      accuracyLabels;

    // -- Race tab widgets --
    private TrackCanvas   trackCanvas;
    private JTextArea     logArea;
    private JLabel        statusLabel;
    private JButton       startButton;
    private JButton       resetButton;

    // -- Seat rows panel (direct reference for refreshSeatRows) --
    private JPanel        seatsPanel;

    // -- Results tab widgets --
    private JTable        resultsTable;
    private DefaultTableModel resultsModel;
    private JTable        leaderboardTable;
    private DefaultTableModel leaderboardModel;

    // -- Simulation state --
    private List<Typist>  activeTypists = new ArrayList<>();
    private int[]         burnoutCounts;        // per typist
    private int[]         ticksElapsed;         // per typist (for WPM)
    private int[]         totalRaceTicks;       // stores finish tick
    private boolean[]     finishedFlags;
    private int           passageLength;
    private boolean       raceRunning = false;
    private Timer         simTimer;

    // Difficulty flags
    private boolean useAutocorrect, useCaffeine, useNightShift;

    // Leaderboard: name → cumulative points
    private Map<String, Integer> leaderboard = new LinkedHashMap<>();

    // ============================================================
    //  Constructor / Init
    // ============================================================

    public TypingRaceGUI() {
        super("⌨  Keyboard Sprint Championship");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_W, WINDOW_H);
        setMinimumSize(new Dimension(800, 580));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        initComponents();
        setVisible(true);
    }

    private void initComponents() {
        tabbedPane = new JTabbedPane();
        styleTabbedPane(tabbedPane);

        tabbedPane.addTab("⚙  Setup",   buildSetupPanel());
        tabbedPane.addTab("🏁  Race",    buildRacePanel());
        tabbedPane.addTab("📊  Results", buildResultsPanel());

        add(tabbedPane);
    }

    // ============================================================
    //  Setup Panel
    // ============================================================

    private JPanel buildSetupPanel() {
        JPanel root = darkPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        // ---- Top: passage + global options ----
        JPanel topRow = darkPanel(new GridLayout(1, 2, 10, 0));

        JPanel passageBox = titledBox("Passage");
        passageCombo = new JComboBox<>(PASSAGE_LABELS);
        customLengthSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 200, 5));
        customLengthSpinner.setEnabled(false);
        styleCombo(passageCombo);
        passageCombo.addActionListener(e ->
            customLengthSpinner.setEnabled(passageCombo.getSelectedIndex() == 3));
        passageBox.add(labeledRow("Passage Length:", passageCombo));
        passageBox.add(labeledRow("Custom chars:  ", customLengthSpinner));

        JPanel seatBox = titledBox("Race Config");
        seatCountSpinner = new JSpinner(new SpinnerNumberModel(3, 2, MAX_TYPISTS, 1));
        styleSpinner(seatCountSpinner);
        seatCountSpinner.addChangeListener(e -> refreshSeatRows());
        seatBox.add(labeledRow("Number of Typists:", seatCountSpinner));
        autocorrectCheck = styledCheck("Autocorrect ON  (halves slide-back)");
        caffeineCheck    = styledCheck("Caffeine Mode  (speed boost, burnout risk)");
        nightShiftCheck  = styledCheck("Night Shift     (accuracy –0.05 all)");
        seatBox.add(autocorrectCheck);
        seatBox.add(caffeineCheck);
        seatBox.add(nightShiftCheck);

        topRow.add(passageBox);
        topRow.add(seatBox);

        // ---- Middle: per-seat rows ----
        int maxSeats = MAX_TYPISTS;
        nameFields     = new JTextField[maxSeats];
        symbolFields   = new JTextField[maxSeats];
        styleCombo     = new JComboBox[maxSeats];
        keyboardCombo  = new JComboBox[maxSeats];
        accessoryCombo = new JComboBox[maxSeats];
        accuracySliders = new JSlider[maxSeats];
        accuracyLabels  = new JLabel[maxSeats];

        seatsPanel = darkPanel(new GridLayout(maxSeats, 1, 0, 4));

        String[] defaultNames    = {"THUNDERKEYS","STEADYHANDS","SLOWBUTSTEADY","KEYMASTER","CAFFEINE_CODER","DOUBLETAP"};
        String[] defaultSymbols  = {"★","♦","●","▲","■","◆"};
        double[] defaultAccuracy = {0.88, 0.65, 0.35, 0.75, 0.55, 0.45};

        for (int i = 0; i < maxSeats; i++) {
            final int idx = i;
            JPanel row = darkPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, TYPIST_COLORS[i]));

            nameFields[i] = new JTextField(defaultNames[i], 11);
            styleText(nameFields[i]);

            symbolFields[i] = new JTextField(defaultSymbols[i], 2);
            styleText(symbolFields[i]);

            styleCombo[i]    = new JComboBox<>(STYLES);
            keyboardCombo[i] = new JComboBox<>(KEYBOARDS);
            accessoryCombo[i] = new JComboBox<>(ACCESSORIES);
            styleCombo(styleCombo[i]);
            styleCombo(keyboardCombo[i]);
            styleCombo(accessoryCombo[i]);

            accuracySliders[i] = new JSlider(0, 100, (int)(defaultAccuracy[i] * 100));
            accuracySliders[i].setBackground(BG_PANEL);
            accuracyLabels[i] = dimLabel(String.format("%.2f", defaultAccuracy[i]));
            accuracySliders[i].addChangeListener(e ->
                accuracyLabels[idx].setText(String.format("%.2f", accuracySliders[idx].getValue() / 100.0)));

            row.add(colorLabel("Seat " + (i+1) + ":", TYPIST_COLORS[i]));
            row.add(nameFields[i]);
            row.add(dimLabel("sym:"));  row.add(symbolFields[i]);
            row.add(dimLabel("style:")); row.add(styleCombo[i]);
            row.add(dimLabel("kb:"));    row.add(keyboardCombo[i]);
            row.add(dimLabel("acc:"));   row.add(accuracySliders[i]); row.add(accuracyLabels[i]);
            row.add(dimLabel("acc+:"));  row.add(accessoryCombo[i]);

            seatsPanel.add(row);
        }

        JScrollPane seatScroll = new JScrollPane(seatsPanel);
        seatScroll.setBackground(BG_DARK);
        seatScroll.getViewport().setBackground(BG_DARK);
        seatScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ACCENT_CYAN, 1), "  Typist Configuration  ",
            TitledBorder.LEFT, TitledBorder.TOP, new Font("Monospaced", Font.BOLD, 12), ACCENT_CYAN));

        // ---- Bottom: launch button ----
        JButton launchBtn = accentButton("▶  Launch Race");
        launchBtn.addActionListener(e -> launchRace());
        JPanel btnPanel = darkPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(launchBtn);

        root.add(topRow,     BorderLayout.NORTH);
        root.add(seatScroll, BorderLayout.CENTER);
        root.add(btnPanel,   BorderLayout.SOUTH);

        refreshSeatRows();
        return root;
    }

    /** Show/hide seat rows based on the seat count spinner. */
    private void refreshSeatRows() {
        int count = (int) seatCountSpinner.getValue();
        Component[] rows = seatsPanel.getComponents();
        for (int i = 0; i < rows.length; i++) {
            rows[i].setVisible(i < count);
        }
        seatsPanel.revalidate();
        seatsPanel.repaint();
    }

    // ============================================================
    //  Race Panel
    // ============================================================

    private JPanel buildRacePanel() {
        JPanel root = darkPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Track canvas
        trackCanvas = new TrackCanvas();
        trackCanvas.setPreferredSize(new Dimension(WINDOW_W - 20, 320));

        // Log area
        logArea = new JTextArea(6, 40);
        logArea.setEditable(false);
        logArea.setBackground(BG_PANEL);
        logArea.setForeground(TEXT_DIM);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(TRACK_LINE), "  Race Log  ",
            TitledBorder.LEFT, TitledBorder.TOP, new Font("Monospaced", Font.BOLD, 11), TEXT_DIM));

        // Status + buttons
        statusLabel = new JLabel("Press ▶ Launch Race from the Setup tab to begin.");
        statusLabel.setForeground(ACCENT_GOLD);
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 13));

        startButton = accentButton("⏸  Pause");
        startButton.setEnabled(false);
        startButton.addActionListener(e -> togglePause());

        resetButton = accentButton("↺  Reset");
        resetButton.addActionListener(e -> resetRace());

        JPanel btnRow = darkPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.add(statusLabel);
        btnRow.add(Box.createHorizontalStrut(20));
        btnRow.add(startButton);
        btnRow.add(resetButton);

        root.add(trackCanvas,  BorderLayout.CENTER);
        root.add(logScroll,    BorderLayout.SOUTH);
        root.add(btnRow,       BorderLayout.NORTH);
        return root;
    }

    // ============================================================
    //  Results Panel
    // ============================================================

    private JPanel buildResultsPanel() {
        JPanel root = darkPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        String[] resCols = {"Typist", "WPM", "Accuracy %", "Errors", "Burnouts", "Points"};
        resultsModel = new DefaultTableModel(resCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        resultsTable = styledTable(resultsModel);

        String[] lbCols = {"Rank", "Typist", "Total Points", "Races"};
        leaderboardModel = new DefaultTableModel(lbCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        leaderboardTable = styledTable(leaderboardModel);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            titledScroll("  Race Results  ", resultsTable),
            titledScroll("  Leaderboard  ", leaderboardTable));
        split.setDividerLocation(250);
        split.setBackground(BG_DARK);
        split.setBorder(null);

        root.add(split, BorderLayout.CENTER);
        return root;
    }

    // ============================================================
    //  Simulation Logic
    // ============================================================

    /** Reads setup widgets, builds typists, starts the timer. */
    private void launchRace() {
        if (raceRunning) return;

        // Determine passage length
        int pIdx = passageCombo.getSelectedIndex();
        passageLength = (pIdx < 3) ? PASSAGE_LENGTHS[pIdx]
                                   : (int) customLengthSpinner.getValue();

        // Read difficulty flags
        useAutocorrect = autocorrectCheck.isSelected();
        useCaffeine    = caffeineCheck.isSelected();
        useNightShift  = nightShiftCheck.isSelected();

        // Build typists
        int count = (int) seatCountSpinner.getValue();
        activeTypists.clear();

        for (int i = 0; i < count; i++) {
            String nm  = nameFields[i].getText().trim();
            String sym = symbolFields[i].getText().trim();
            char   ch  = sym.isEmpty() ? (char)('①' + i) : sym.charAt(0);

            double acc = accuracySliders[i].getValue() / 100.0;
            acc += STYLE_MODS  [styleCombo[i].getSelectedIndex()];
            acc += ACCESSORY_MOD[accessoryCombo[i].getSelectedIndex()];
            if (useNightShift) acc -= 0.05;
            acc = Math.max(0.05, Math.min(1.0, acc));

            Typist t = new Typist(ch, nm.isEmpty() ? "Typist" + (i+1) : nm, acc);
            activeTypists.add(t);
            if (!leaderboard.containsKey(t.getName())) leaderboard.put(t.getName(), 0);
        }

        burnoutCounts  = new int[count];
        ticksElapsed   = new int[count];
        totalRaceTicks = new int[count];
        finishedFlags  = new boolean[count];

        logArea.setText("");
        trackCanvas.setRace(activeTypists, passageLength, TYPIST_COLORS);
        tabbedPane.setSelectedIndex(1);

        startButton.setEnabled(true);
        raceRunning = true;
        statusLabel.setText("Race in progress…");

        simTimer = new Timer();
        simTimer.scheduleAtFixedRate(new TimerTask() {
            int globalTick = 0;
            public void run() {
                SwingUtilities.invokeLater(() -> simulateTick(globalTick++));
            }
        }, 0, TICK_MS);
    }

    /** Runs one simulation tick for every active typist. */
    private void simulateTick(int tick) {
        if (!raceRunning) return;

        boolean anyoneFinished = false;
        Typist winner = null;

        for (int i = 0; i < activeTypists.size(); i++) {
            if (finishedFlags[i]) continue;

            Typist t = activeTypists.get(i);
            ticksElapsed[i]++;

            String event = processTurn(t, tick, i);
            if (!event.isEmpty()) log(tick, t.getName(), event);

            if (t.getProgress() >= passageLength) {
                finishedFlags[i]  = true;
                totalRaceTicks[i] = ticksElapsed[i];
                anyoneFinished    = true;
                if (winner == null) winner = t;
                log(tick, t.getName(), "FINISHED! 🏆");
            }
        }

        trackCanvas.repaint();

        if (anyoneFinished) {
            endRace(winner);
        }
    }

    /**
     * Processes one turn for a typist.
     * Returns a short description of what happened (for the log).
     */
    private String processTurn(Typist t, int tick, int idx) {
        if (t.isBurntOut()) {
            t.recoverFromBurnout();
            return "";
        }

        String event = "";
        double acc = t.getAccuracy();

        // Caffeine boost: first 10 ticks get +0.08 accuracy; after that burnout risk up
        boolean caffeineActive = useCaffeine && tick < 10;
        if (caffeineActive) acc = Math.min(1.0, acc + 0.08);

        // Type attempt
        if (Math.random() < acc) {
            t.typeCharacter();
        }

        // Mistype / slide-back
        int slideAmt = useAutocorrect ? 1 : 2;
        double mistypeChance = (1.0 - acc) * 0.40;
        if (Math.random() < mistypeChance) {
            t.slideBack(slideAmt);
            event = "mistyped → –" + slideAmt;
        }

        // Burnout
        double burnoutRisk = useCaffeine && tick >= 10
            ? 0.06 * acc * acc
            : 0.03 * acc * acc;
        if (Math.random() < burnoutRisk) {
            t.burnOut(3);
            t.setAccuracy(t.getAccuracy() - 0.01);
            burnoutCounts[idx]++;
            event = "BURNT OUT (3 turns)";
        }

        return event;
    }

    /** Stops the timer, calculates stats, populates the results tab. */
    private void endRace(Typist winner) {
        if (simTimer != null) simTimer.cancel();
        raceRunning = false;
        startButton.setEnabled(false);
        statusLabel.setText("Race over!  Winner: " + winner.getName() + " 🏆");

        // Accuracy boost for winner
        winner.setAccuracy(winner.getAccuracy() + 0.02);

        // Populate results table
        resultsModel.setRowCount(0);
        int pts1st = 5, pts2nd = 3, pts3rd = 1;
        int[] positions = calcPositions();

        for (int i = 0; i < activeTypists.size(); i++) {
            Typist t   = activeTypists.get(i);
            int ticks  = totalRaceTicks[i] > 0 ? totalRaceTicks[i] : ticksElapsed[i];
            double secs = ticks * TICK_MS / 1000.0;
            int wpm     = secs > 0 ? (int)((passageLength / 5.0) / (secs / 60.0)) : 0;
            double accPct = 100.0 * t.getAccuracy();
            int pts = (positions[i] == 1) ? pts1st : (positions[i] == 2) ? pts2nd : (positions[i] == 3) ? pts3rd : 0;

            resultsModel.addRow(new Object[]{
                t.getName(), wpm, String.format("%.1f%%", accPct),
                "—", burnoutCounts[i], pts
            });

            leaderboard.merge(t.getName(), pts, Integer::sum);
        }

        // Leaderboard
        leaderboardModel.setRowCount(0);
        leaderboard.entrySet().stream()
            .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
            .forEach(e -> leaderboardModel.addRow(new Object[]{
                leaderboardModel.getRowCount() + 1,
                e.getKey(), e.getValue(), "—"
            }));

        tabbedPane.setSelectedIndex(2);
    }

    private int[] calcPositions() {
        int n = activeTypists.size();
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) {
            pos[i] = 1;
            for (int j = 0; j < n; j++) {
                if (i != j && totalRaceTicks[j] > 0 &&
                    (totalRaceTicks[i] == 0 || totalRaceTicks[j] < totalRaceTicks[i]))
                    pos[i]++;
            }
        }
        return pos;
    }

    private void togglePause() {
        // Simple toggle: stop/restart timer
        if (raceRunning) {
            if (simTimer != null) simTimer.cancel();
            raceRunning = false;
            startButton.setText("▶  Resume");
        } else {
            raceRunning = true;
            startButton.setText("⏸  Pause");
            int[] counter = {0};
            simTimer = new Timer();
            simTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    SwingUtilities.invokeLater(() -> simulateTick(counter[0]++));
                }
            }, 0, TICK_MS);
        }
    }

    private void resetRace() {
        if (simTimer != null) simTimer.cancel();
        raceRunning = false;
        startButton.setEnabled(false);
        startButton.setText("⏸  Pause");
        statusLabel.setText("Race reset. Configure and launch again.");
        activeTypists.clear();
        trackCanvas.clear();
        logArea.setText("");
        tabbedPane.setSelectedIndex(0);
    }

    private void log(int tick, String name, String msg) {
        logArea.append(String.format("[T%03d] %-16s %s%n", tick, name, msg));
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ============================================================
    //  TrackCanvas — custom-painted race track
    // ============================================================

    /**
     * Paints the race track with animated typist tokens.
     * Each typist is drawn as a coloured circle with their symbol inside,
     * positioned along a horizontal lane.
     */
    static class TrackCanvas extends JPanel {

        private List<Typist> typists = new ArrayList<>();
        private Color[]      colors;
        private int          trackLen;

        TrackCanvas() {
            setBackground(BG_DARK);
        }

        void setRace(List<Typist> t, int len, Color[] c) {
            this.typists  = t;
            this.trackLen = len;
            this.colors   = c;
            repaint();
        }

        void clear() {
            typists = new ArrayList<>();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int W = getWidth(), H = getHeight();
            if (typists == null || typists.isEmpty()) {
                g2.setColor(TEXT_DIM);
                g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
                g2.drawString("Launch a race to see the track.", W/2 - 120, H/2);
                return;
            }

            int n         = typists.size();
            int laneH     = Math.max(40, (H - 40) / n);
            int trackX    = 80;
            int trackW    = W - 120;
            int tokenR    = 16;

            // Track background
            g2.setColor(TRACK_BG);
            g2.fillRoundRect(trackX - 5, 15, trackW + 10, H - 30, 12, 12);

            // Lane dividers
            g2.setColor(TRACK_LINE);
            for (int i = 1; i < n; i++) {
                int y = 15 + i * laneH;
                g2.drawLine(trackX, y, trackX + trackW, y);
            }

            // Finish line
            int finX = trackX + trackW - 5;
            g2.setColor(FINISH_LINE);
            g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1, new float[]{6, 4}, 0));
            g2.drawLine(finX, 15, finX, H - 15);
            g2.setStroke(new BasicStroke(1));
            g2.setFont(new Font("Monospaced", Font.BOLD, 10));
            g2.drawString("FINISH", finX - 22, 12);

            // Start line
            g2.setColor(TEXT_DIM);
            g2.drawLine(trackX, 15, trackX, H - 15);

            // Draw typists
            for (int i = 0; i < n; i++) {
                Typist t   = typists.get(i);
                Color  col = (colors != null && i < colors.length) ? colors[i] : Color.WHITE;

                double ratio  = trackLen > 0 ? (double) t.getProgress() / trackLen : 0;
                ratio = Math.min(ratio, 1.0);
                int tokenX = trackX + (int)(ratio * (trackW - tokenR * 2)) + tokenR;
                int laneY  = 15 + i * laneH + laneH / 2;

                // Stamina bar (thin strip above token)
                int barW   = 60;
                int barX   = tokenX - barW / 2;
                double stam = 1.0 - (t.isBurntOut() ? 1.0 : (1.0 - ratio) * 0.3);
                g2.setColor(new Color(60, 60, 80));
                g2.fillRoundRect(barX, laneY - tokenR - 8, barW, 5, 3, 3);
                g2.setColor(col.darker());
                g2.fillRoundRect(barX, laneY - tokenR - 8, (int)(barW * stam), 5, 3, 3);

                // Token shadow
                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillOval(tokenX - tokenR + 3, laneY - tokenR + 3, tokenR * 2, tokenR * 2);

                // Token fill
                if (t.isBurntOut()) {
                    g2.setColor(col.darker().darker());
                } else {
                    g2.setColor(col);
                }
                g2.fillOval(tokenX - tokenR, laneY - tokenR, tokenR * 2, tokenR * 2);

                // Token border
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(tokenX - tokenR, laneY - tokenR, tokenR * 2, tokenR * 2);
                g2.setStroke(new BasicStroke(1));

                // Symbol inside token
                g2.setFont(new Font("Dialog", Font.BOLD, 13));
                String sym = String.valueOf(t.getSymbol());
                FontMetrics fm = g2.getFontMetrics();
                int sw = fm.stringWidth(sym);
                g2.setColor(t.isBurntOut() ? Color.GRAY : Color.WHITE);
                g2.drawString(sym, tokenX - sw / 2, laneY + fm.getAscent() / 2 - 2);

                // Name label
                g2.setFont(new Font("Monospaced", Font.BOLD, 11));
                g2.setColor(col);
                g2.drawString(t.getName(), 5, laneY + 4);

                // Progress %
                int pct = trackLen > 0 ? (int)(ratio * 100) : 0;
                g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g2.setColor(TEXT_DIM);
                g2.drawString(pct + "%", tokenX + tokenR + 4, laneY + 4);

                // Burnt-out flash
                if (t.isBurntOut()) {
                    g2.setColor(new Color(255, 80, 80, 160));
                    g2.setFont(new Font("Monospaced", Font.BOLD, 10));
                    g2.drawString("💤 BURNT OUT", tokenX + tokenR + 2, laneY - 4);
                }
            }
        }
    }

    // ============================================================
    //  Styling Helpers
    // ============================================================

    private static JPanel darkPanel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(BG_DARK);
        return p;
    }

    private static JPanel titledBox(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ACCENT_CYAN, 1), "  " + title + "  ",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Monospaced", Font.BOLD, 12), ACCENT_CYAN));
        return p;
    }

    private static JPanel labeledRow(String lbl, JComponent comp) {
        JPanel row = darkPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.setBackground(BG_PANEL);
        JLabel l = new JLabel(lbl);
        l.setForeground(TEXT_LIGHT);
        l.setFont(new Font("Monospaced", Font.PLAIN, 12));
        row.add(l); row.add(comp);
        return row;
    }

    private static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT_GOLD);
        b.setForeground(BG_DARK);
        b.setFont(new Font("Monospaced", Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6, 16, 6, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JCheckBox styledCheck(String text) {
        JCheckBox c = new JCheckBox(text);
        c.setBackground(BG_PANEL);
        c.setForeground(TEXT_LIGHT);
        c.setFont(new Font("Monospaced", Font.PLAIN, 11));
        return c;
    }

    private static JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_DIM);
        l.setFont(new Font("Monospaced", Font.PLAIN, 11));
        return l;
    }

    private static JLabel colorLabel(String text, Color c) {
        JLabel l = new JLabel(text);
        l.setForeground(c);
        l.setFont(new Font("Monospaced", Font.BOLD, 12));
        return l;
    }

    private static void styleCombo(JComboBox<?> c) {
        c.setBackground(BG_PANEL);
        c.setForeground(TEXT_LIGHT);
        c.setFont(new Font("Monospaced", Font.PLAIN, 11));
    }

    private static void styleSpinner(JSpinner s) {
        s.getEditor().getComponent(0).setBackground(BG_PANEL);
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setForeground(TEXT_LIGHT);
    }

    private static void styleText(JTextField f) {
        f.setBackground(BG_PANEL);
        f.setForeground(TEXT_LIGHT);
        f.setCaretColor(TEXT_LIGHT);
        f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(TRACK_LINE),
            new EmptyBorder(2, 4, 2, 4)));
    }

    private static void styleTabbedPane(JTabbedPane tp) {
        tp.setBackground(BG_DARK);
        tp.setForeground(TEXT_LIGHT);
        tp.setFont(new Font("Monospaced", Font.BOLD, 13));
    }

    private static JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setBackground(BG_PANEL);
        t.setForeground(TEXT_LIGHT);
        t.setGridColor(TRACK_LINE);
        t.setFont(new Font("Monospaced", Font.PLAIN, 12));
        t.getTableHeader().setBackground(BG_DARK);
        t.getTableHeader().setForeground(ACCENT_CYAN);
        t.getTableHeader().setFont(new Font("Monospaced", Font.BOLD, 12));
        t.setRowHeight(24);
        t.setSelectionBackground(ACCENT_GOLD.darker());
        return t;
    }

    private static JScrollPane titledScroll(String title, JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBackground(BG_DARK);
        sp.getViewport().setBackground(BG_PANEL);
        sp.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ACCENT_CYAN, 1), title,
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Monospaced", Font.BOLD, 12), ACCENT_CYAN));
        return sp;
    }

    // ============================================================
    //  Entry Point
    // ============================================================

    public static void startRaceGUI() {
        SwingUtilities.invokeLater(TypingRaceGUI::new);
    }

    public static void main(String[] args) {
        startRaceGUI();
    }
}
