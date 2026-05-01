/**
 * Typist.java
 *
 * Represents a single competitor participating in a Typing Race.
 * Each Typist has a unique identity (name + symbol), an accuracy rating,
 * a progress counter through the passage, and a burnout mechanism that
 * temporarily halts their typing when triggered.
 *
 * Encapsulation is enforced throughout: all fields are private and
 * accessed only through validated public methods.
 */
public class Typist {

    // ---------------------------------------------------------------
    // Fields (all private - encapsulation)
    // ---------------------------------------------------------------

    /** Display name of the typist (e.g. "THUNDERKEYS"). */
    private String name;

    /** Unicode symbol shown on the race track (e.g. '★', '♦', '●'). */
    private char symbol;

    /**
     * Number of characters successfully typed so far.
     * Always >= 0; slideBack() enforces this lower bound.
     */
    private int progress;

    /**
     * Accuracy rating in the range [0.0, 1.0].
     * Higher accuracy = more consistent forward movement.
     * Clamped by setAccuracy() to stay within valid bounds.
     */
    private double accuracy;

    /** True while this typist is in a burnout state and cannot type. */
    private boolean burntOut;

    /**
     * Countdown of turns remaining in the current burnout episode.
     * Zero when the typist is not burnt out.
     */
    private int burnoutTurnsRemaining;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    /**
     * Constructs a new Typist with the given identity and accuracy.
     *
     * @param typistSymbol   Unicode character shown on the track
     * @param typistName     Display name for announcements
     * @param typistAccuracy Accuracy in [0.0, 1.0]; clamped if out of range
     */
    public Typist(char typistSymbol, String typistName, double typistAccuracy) {
        this.symbol   = typistSymbol;
        this.name     = typistName;
        this.progress = 0;
        this.burntOut = false;
        this.burnoutTurnsRemaining = 0;
        setAccuracy(typistAccuracy); // use setter for clamping logic
    }

    // ---------------------------------------------------------------
    // Mutators (setters / state-changers)
    // ---------------------------------------------------------------

    /**
     * Puts this typist into a burnt-out state for the specified number of turns.
     * Ignored if turns is zero or negative.
     *
     * @param turns how many turns the burnout lasts
     */
    public void burnOut(int turns) {
        if (turns <= 0) return;
        burntOut = true;
        burnoutTurnsRemaining = turns;
    }

    /**
     * Decrements the burnout counter by one turn.
     * When the counter reaches zero the typist fully recovers.
     * Calling this on a non-burnt-out typist has no effect.
     */
    public void recoverFromBurnout() {
        if (!burntOut) return;
        burnoutTurnsRemaining--;
        if (burnoutTurnsRemaining <= 0) {
            burntOut = false;
            burnoutTurnsRemaining = 0;
        }
    }

    /**
     * Moves the typist backwards by the given number of characters.
     * Progress is clamped to zero — it can never go negative.
     *
     * Validation: if the slide amount would push progress below zero,
     * progress is set to zero instead. This is critical because a negative
     * position has no meaning in a typing race.
     *
     * @param amount number of characters to slide back (must be positive)
     */
    public void slideBack(int amount) {
        progress -= amount;
        if (progress < 0) {
            progress = 0;
        }
    }

    /**
     * Advances the typist forward by exactly one character,
     * but only if they are not currently burnt out.
     */
    public void typeCharacter() {
        if (!burntOut) {
            progress++;
        }
    }

    /**
     * Resets the typist to their starting state, ready for a new race.
     * Clears progress, burnout flag, and burnout counter.
     */
    public void resetToStart() {
        progress               = 0;
        burntOut               = false;
        burnoutTurnsRemaining  = 0;
    }

    /**
     * Updates the accuracy rating.
     *
     * Validation: values below 0.0 are clamped to 0.0, values above 1.0
     * are clamped to 1.0. This prevents the race engine from receiving
     * impossible probabilities (e.g. negative accuracy or >100%).
     *
     * @param newAccuracy desired accuracy value
     */
    public void setAccuracy(double newAccuracy) {
        if (newAccuracy < 0.0) {
            accuracy = 0.0;
        } else if (newAccuracy > 1.0) {
            accuracy = 1.0;
        } else {
            accuracy = newAccuracy;
        }
    }

    /**
     * Replaces the typist's track symbol.
     *
     * @param newSymbol the new Unicode character to display
     */
    public void setSymbol(char newSymbol) {
        this.symbol = newSymbol;
    }

    // ---------------------------------------------------------------
    // Accessors (getters)
    // ---------------------------------------------------------------

    /** @return the typist's display name */
    public String getName() {
        return name;
    }

    /** @return the Unicode symbol used on the race track */
    public char getSymbol() {
        return symbol;
    }

    /** @return current progress (characters typed correctly) */
    public int getProgress() {
        return progress;
    }

    /** @return accuracy rating in [0.0, 1.0] */
    public double getAccuracy() {
        return accuracy;
    }

    /** @return true if this typist is currently burnt out */
    public boolean isBurntOut() {
        return burntOut;
    }

    /** @return turns of burnout remaining; 0 if not burnt out */
    public int getBurnoutTurnsRemaining() {
        return burnoutTurnsRemaining;
    }
}
