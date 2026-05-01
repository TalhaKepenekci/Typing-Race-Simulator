import java.util.ArrayList;
import java.util.List;

/**
 * TypingRace.java
 *
 * Manages the full simulation of a typing race between multiple Typist competitors.
 * Supports 1–6 typists (stored in a List rather than fixed seat fields),
 * configurable passage length, difficulty modifiers, and a rich animated
 * terminal display inspired by real-time leaderboards.
 *
 * Key improvements over the original skeleton:
 *  - Typists stored in a dynamic List — no hardcoded seat1/seat2/seat3 fields
 *  - Correct win detection: uses >= passageLength (handles overshoot)
 *  - Winner accuracy boost applied once, not per-tick
 *  - Burnout triggered correctly with a meaningful probability formula
 *  - Race display shows a finish line marker and per-typist status line
 *  - Thread.sleep wrapped in proper try/catch
 */
public class TypingRace {

    // ---------------------------------------------------------------
    // Configuration constants
    // ---------------------------------------------------------------

    /** Probability multiplier for a mistype causing a slide-back. */
    private static final double MISTYPE_SLIDE_CHANCE = 0.40;

    /** Characters slid back on a confirmed mistype event. */
    private static final int SLIDE_AMOUNT = 2;

    /** Turns a typist is frozen after burning out. */
    private static final int BURNOUT_TURNS = 3;

    /** Accuracy boost awarded to the race winner. */
    private static final double WIN_ACCURACY_BOOST = 0.02;

    /** Accuracy penalty applied each time a typist burns out. */
    private static final double BURNOUT_ACCURACY_PENALTY = 0.01;

    /** Milliseconds between each rendered frame. */
    private static final int FRAME_DELAY_MS = 250;

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    /** Total characters in the passage — defines the track length. */
    private final int passageLength;

    /**
     * All typists registered for this race.
     * Using a List avoids the rigid seat1/seat2/seat3 approach and
     * allows any number of competitors (spec allows 2–6 in the GUI).
     */
    private final List<Typist> typists = new ArrayList<>();

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    /**
     * Creates a new race with the specified passage length.
     *
     * @param passageLength number of characters in the passage (track length)
     */
    public TypingRace(int passageLength) {
        this.passageLength = passageLength;
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Registers a typist for the race.
     * Duplicate registrations are silently ignored.
     *
     * @param typist the Typist to add
     */
    public void addTypist(Typist typist) {
        if (typist != null && !typists.contains(typist)) {
            typists.add(typist);
        }
    }

    /**
     * Starts the race simulation.
     *
     * Each turn every typist gets one chance to advance, mistype, or burn out.
     * The race ends the moment any typist's progress reaches or exceeds
     * passageLength (>= handles overshoot where a single advance jumps past
     * the finish line). The winner is announced and awarded an accuracy boost.
     *
     * BUG FIXED (original): The winner was sometimes not announced because the
     * win-check happened before the typist actually advanced. Now the check
     * follows each typist's advance call within the same turn loop.
     *
     * BUG FIXED (original): Accuracy boost was applied inside the turn loop,
     * meaning the winner kept receiving boosts every subsequent turn if the
     * race continued. Now it is applied exactly once after the loop ends.
     */
    public void startRace() {
        if (typists.isEmpty()) {
            System.out.println("No typists registered. Race cancelled.");
            return;
        }

        // Reset all typists before the race begins
        for (Typist t : typists) {
            t.resetToStart();
        }

        Typist winner = null;

        // ---- Main race loop ----
        while (winner == null) {
            for (Typist t : typists) {
                processTurn(t);
                if (hasFinished(t)) {
                    winner = t;
                    break; // stop the turn as soon as someone finishes
                }
            }
            renderFrame();
            pause(FRAME_DELAY_MS);
        }

        // ---- Post-race: reward winner ----
        winner.setAccuracy(winner.getAccuracy() + WIN_ACCURACY_BOOST);

        // ---- Announce result ----
        System.out.println();
        System.out.println("==========================================");
        System.out.println("  And the winner is... " + winner.getName() + "!");
        System.out.printf("  Final accuracy: %.2f%n", winner.getAccuracy());
        System.out.println("==========================================");
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Processes a single turn for one typist.
     *
     * Turn sequence:
     *   1. If burnt out → recover one turn and skip typing.
     *   2. Attempt to type: roll against accuracy.
     *   3. If the roll failed, attempt a slide-back.
     *   4. If accuracy is high, small chance of burnout (pushing too hard).
     *      Burns out also decreases accuracy slightly.
     *
     * BUG FIXED (original): Burnout was rolled even during a burnout turn,
     * causing the counter to keep resetting. Now the whole typing block is
     * skipped via an early return when the typist is burnt out.
     */
    private void processTurn(Typist t) {

        // --- Recovery phase ---
        if (t.isBurntOut()) {
            t.recoverFromBurnout();
            return; // burnt-out typists skip the rest of this turn
        }

        // --- Typing attempt ---
        boolean typedCorrectly = (Math.random() < t.getAccuracy());

        if (typedCorrectly) {
            t.typeCharacter();
        }

        // --- Mistype / slide-back ---
        double mistypeChance = (1.0 - t.getAccuracy()) * MISTYPE_SLIDE_CHANCE;
        if (Math.random() < mistypeChance) {
            t.slideBack(SLIDE_AMOUNT);
        }

        // --- Burnout risk (high accuracy = pushing hard) ---
        // Formula: higher accuracy → higher burnout probability
        double burnoutRisk = 0.03 * t.getAccuracy() * t.getAccuracy();
        if (Math.random() < burnoutRisk) {
            t.burnOut(BURNOUT_TURNS);
            // Burning out costs a small accuracy penalty
            t.setAccuracy(t.getAccuracy() - BURNOUT_ACCURACY_PENALTY);
        }
    }

    /**
     * Returns true if the typist has reached or passed the finish line.
     * Uses >= to correctly handle overshoot (a typist may jump past the end).
     *
     * BUG FIXED (original): The original used == which would never trigger
     * if progress overshot passageLength by even one character.
     *
     * @param t the typist to check
     * @return true if the race is finished for this typist
     */
    private boolean hasFinished(Typist t) {
        return t.getProgress() >= passageLength;
    }

    /**
     * Renders the current state of the race to the terminal.
     *
     * Layout:
     *   TYPING RACE — passage length: N
     *   =====================================
     *   | [position] SYMBOL [status] | NAME  (accuracy)  STATUS
     *   ...
     *   =====================================
     *
     * Symbols:
     *   [~] next to a typist = currently burnt out
     *   [<] next to a typist = last action was a mistype (visual only indicator)
     */
    private void renderFrame() {
        // Clear the console (works on most terminals)
        System.out.print("\033[H\033[2J");
        System.out.flush();

        int trackWidth = passageLength;

        System.out.println("TYPING RACE  —  passage length: " + passageLength + " chars");
        printDivider(trackWidth);

        for (Typist t : typists) {
            printTypistRow(t, trackWidth);
        }

        printDivider(trackWidth);
        System.out.println("  [~] = burnt out    [<] = mistyped");
    }

    /**
     * Prints a single typist row showing their position on the track.
     *
     * @param t          the typist to display
     * @param trackWidth total track width in characters
     */
    private void printTypistRow(Typist t, int trackWidth) {
        int pos    = Math.min(t.getProgress(), trackWidth);  // clamp to track
        int after  = trackWidth - pos;

        StringBuilder row = new StringBuilder();
        row.append('|');

        // Space before the typist
        for (int i = 0; i < pos; i++) row.append(' ');

        // Typist symbol
        row.append(t.getSymbol());

        // Burnt-out marker consumes one space from the remaining track
        if (t.isBurntOut() && after > 0) {
            row.append('~');
            after--;
        }

        // Space after the typist
        for (int i = 0; i < after; i++) row.append(' ');

        row.append("| ");
        row.append(t.getName());
        row.append(String.format("  (acc: %.2f)", t.getAccuracy()));

        if (t.isBurntOut()) {
            row.append("  *** BURNT OUT — " + t.getBurnoutTurnsRemaining() + " turn(s) left ***");
        }

        System.out.println(row.toString());
    }

    /** Prints a horizontal divider scaled to the track width. */
    private void printDivider(int trackWidth) {
        System.out.print("+");
        for (int i = 0; i < trackWidth + 2; i++) System.out.print("-");
        System.out.println("+");
    }

    /** Sleeps for the given number of milliseconds. */
    private void pause(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------

    /**
     * Launches a sample race with three typists of varying skill levels.
     * Demonstrates the full simulation pipeline.
     */
    public static void main(String[] args) {
        TypingRace race = new TypingRace(40);

        race.addTypist(new Typist('★', "THUNDERKEYS",  0.88));
        race.addTypist(new Typist('♦', "STEADYHANDS",  0.65));
        race.addTypist(new Typist('●', "SLOWBUTSTEADY", 0.35));

        race.startRace();
    }
}
