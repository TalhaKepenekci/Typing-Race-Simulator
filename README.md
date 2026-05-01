# ⌨ Keyboard Sprint Championship — Typing Race Simulator

A Java-based typing race simulation built for the ECS414U Object Oriented Programming module.

---

## Project Structure

```
TypingRaceSimulator/
├── Part1/
│   ├── Typist.java          # Competitor entity (fields, encapsulation, mechanics)
│   └── TypingRace.java      # Simulation engine (console-animated race)
├── Part2/
│   └── TypingRaceGUI.java   # Full Swing GUI (setup, animated track, results)
└── Report.pdf               # Full project report (Part I & II documentation)
```

---

## Prerequisites

- **Java JDK 11 or later**
- No external libraries required — standard Java SE only.

---

## Compilation

Open a terminal in the `TypingRaceSimulator/` root folder.

### Part 1 (Textual)
```bash
javac Part1/Typist.java
javac -cp Part1 Part1/TypingRace.java
```

### Part 2 (GUI)
```bash
javac -cp Part1 Part2/TypingRaceGUI.java
```

---

## Running

### Part 1 — Console Race
```bash
cd Part1
java -cp . TypingRace
```

### Part 2 — Graphical Interface
```bash
java -cp Part1:Part2 TypingRaceGUI
# Windows: java -cp Part1;Part2 TypingRaceGUI
```

---

## How to Use the GUI

1. **Setup Tab** — Choose a passage length, set the number of typists (2–6), and configure each competitor:
   - Typing style (Touch Typist, Hunt & Peck, Phone Thumbs, Voice-to-Text)
   - Keyboard type (Mechanical, Membrane, Touchscreen, Stenography)
   - Accuracy slider and accessory bonus
2. Apply **Difficulty Modifiers** (Autocorrect, Caffeine Mode, Night Shift) as desired.
3. Click **▶ Launch Race** to start.
4. Switch to the **Race Tab** to watch the animated track. Use ⏸ Pause / ▶ Resume as needed.
5. After the race, the **Results Tab** shows WPM, accuracy, burnout count, points, and the running leaderboard.

---

## Difficulty Modifiers

| Modifier        | Effect                                                       |
|-----------------|--------------------------------------------------------------|
| Autocorrect ON  | Slide-back on mistype reduced from 2 to 1 character          |
| Caffeine Mode   | First 10 ticks: +0.08 accuracy. After tick 10: 2× burnout risk |
| Night Shift     | All typists receive −0.05 accuracy (everyone is tired)       |

---

## Typing Style Effects

| Style          | Accuracy Modifier |
|----------------|-------------------|
| Touch Typist   | +0.05             |
| Hunt & Peck    | −0.10             |
| Phone Thumbs   | −0.05             |
| Voice-to-Text  | 0.00              |

---

## Git Branching

- `main` — stable, contains Part 1 complete code
- `gui-development` — all Part 2 development; merged back to `main` on completion
