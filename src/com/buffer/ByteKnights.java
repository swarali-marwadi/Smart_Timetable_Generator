// ByteKnights — Multi-Division Timetable Scheduler v3.1
//
/*
 * Smart Timetable Generator
 * ------------------------------------------------------------
 * Generates conflict-free academic timetables for multiple
 * divisions using Constraint Satisfaction Problem (CSP)
 * techniques.
 *
 * Algorithms Used:
 *  - Backtracking Search
 *  - Most Constrained Variable (MCV) Heuristic
 *  - Forward Checking
 *  - Load Balanced Day Selection
 *  - Compact Slot Placement
 *
 * Constraints Handled:
 *  - Faculty availability
 *  - Room availability
 *  - Laboratory allocation
 *  - Subject repetition control
 *  - Break slot enforcement
 *  - Cross-division faculty conflicts
 *
 * Features:
 *  - Multi-division scheduling
 *  - Shared resource tracking
 *  - Automatic room allocation
 *  - Faculty workload management
 *  - Balanced timetable generation
 */

package com.buffer;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import java.util.*;

// ── Enums ─────────────────────────────────────────────────────

enum SessionType { L, T, P }

// ── Domain model ──────────────────────────────────────────────

class Subject {
    final String      code, name;
    final SessionType sessionType;
    final int         creditsPerWeek;

    Subject(String code, String name, SessionType sessionType, int creditsPerWeek) {
        this.code = code; this.name = name;
        this.sessionType = sessionType; this.creditsPerWeek = creditsPerWeek;
    }
}

class Faculty {
    final String name, subject, division;
    final int    totalClasses;
    int          scheduledClasses = 0;

    Faculty(String name, String subject, String division, int totalClasses) {
        this.name = name; this.subject = subject;
        this.division = division; this.totalClasses = totalClasses;
    }

    boolean canSchedule()          { return scheduledClasses < totalClasses; }
    boolean canSchedule(int n)     { return scheduledClasses + n <= totalClasses; }
    void    schedule()             { scheduledClasses++; }
    void    unschedule()           { if (scheduledClasses > 0) scheduledClasses--; }
}

class Room {

    String name;
    String type; // CLASSROOM or LAB

    Room(String name, String type) {
        this.name = name;
        this.type = type;
    }

    boolean isLab() {
        return "LAB".equals(type);
    }
}

// ── Input loader ──────────────────────────────────────────────

class InputLoader {

    static JSONObject load(String path) throws IOException {
        return new JSONObject(new String(Files.readAllBytes(Paths.get(path))));
    }

    static Map<String, Subject> parseSubjects(JSONObject root) {
        Map<String, Subject> map = new LinkedHashMap<>();
        for (Object o : root.getJSONArray("subjects")) {
            JSONObject j = (JSONObject) o;
            String code  = j.getString("code");
            map.put(code, new Subject(code, j.getString("name"),
                    SessionType.valueOf(j.getString("sessionType")),
                    j.getInt("creditsPerWeek")));
        }
        return map;
    }

    static List<Faculty> parseFaculty(JSONObject root, Map<String, Subject> subjects) {
        List<Faculty> list = new ArrayList<>();
        for (Object o : root.getJSONArray("faculty")) {
            JSONObject j    = (JSONObject) o;
            String name     = j.getString("name");
            String subjCode = j.getString("subject");
            String div      = j.getString("division");
            Subject subj    = subjects.get(subjCode);
            // Labs: each credit = 1 consecutive pair = 2 schedule() calls
            int total = (subj != null ? subj.creditsPerWeek : 1)
                    * (subj != null && subj.sessionType == SessionType.P ? 2 : 1);
            list.add(new Faculty(name, subjCode, div, total));
        }
        return list;
    }

    static List<Room> parseRooms(JSONObject root) {
        List<Room> rooms = new ArrayList<>();

        JSONArray arr = root.getJSONArray("rooms");

        for (Object o : arr) {
            JSONObject r = (JSONObject) o;

            rooms.add(new Room(
                    r.getString("name"),
                    r.getString("type")
            ));
        }

        return rooms;
    }
}

// ── Division scheduler ────────────────────────────────────────
// One instance per division. Divisions are solved independently.

class DivisionScheduler {

    static final int      DAYS      = 5;
    static final int      SLOTS     = 7;
    static final int      BREAK     = 3;
    static final String[] TIME      = {
            "9-10am","10-11am","11am-12pm","12-1pm(Break)","1-2pm","2-3pm","3-4pm"
    };
    static final String[] DAY_LABEL = {"Mon","Tue","Wed","Thu","Fri"};

    private final String      division;
    private final String[][]  grid     = new String[DAYS][SLOTS];
    private final boolean[][] occupied = new boolean[DAYS][SLOTS];
    private final String[][] roomGrid = new String[DAYS][SLOTS];

    // subject code → Faculty for this division
    private final Map<String, Faculty> facultyMap = new HashMap<>();
    // ordered list of subjects to schedule
    private final List<Subject> agenda;

    // subjectCode → friendly short name for display
    private final Map<String, String> subjectNames = new HashMap<>();

    // ── Constraint 1: shared lab room occupancy ──────────────
    // subjectCode → Set<"day-slot"> booked across ALL divisions.
    // Same map instance shared by every DivisionScheduler so Div E
    // cannot book DSL in the same slot Div D already occupies.
    // facultyName → Set<"day-slot"> occupied across ALL divisions
    private final Map<String, Set<String>> facultyOccupancy;

    private final List<Room> rooms;

    private final Map<String, Set<String>> roomOccupancy;

    DivisionScheduler(String division,
                      Map<String, Subject> subjects,
                      List<Faculty> allFaculty,
                      List<Room> rooms,
                      Map<String, Set<String>> facultyOccupancy,
                      Map<String, Set<String>> roomOccupancy) {
        this.division = division;
        this.facultyOccupancy = facultyOccupancy;
        this.rooms = rooms;
        this.roomOccupancy = roomOccupancy;

        // Collect faculty relevant to this division
        for (Faculty f : allFaculty)
            if (f.division.equals(division)) facultyMap.put(f.subject, f);

        // Build agenda: subjects this division has, sorted MCV (fewest credits first)
        List<Subject> relevant = new ArrayList<>();
        for (Subject s : subjects.values()) {
            subjectNames.put(s.code, s.name);           // populate display names
            if (facultyMap.containsKey(s.code)) relevant.add(s);
        }
        relevant.sort(Comparator.comparingInt(s -> s.creditsPerWeek));
        this.agenda = relevant;

        // Mark break slots
        for (int d = 0; d < DAYS; d++) {
            grid[d][BREAK]     = "Break";
            occupied[d][BREAK] = true;
        }
    }

    // ── Entry point ───────────────────────────────────────────

    public boolean schedule() {
        // Expand agenda into flat session list (one entry per credit)
        List<String> sessions = new ArrayList<>();
        for (Subject s : agenda)
            for (int i = 0; i < s.creditsPerWeek; i++)
                sessions.add(s.code);

        boolean ok = backtrack(sessions, 0);
        fillEmpty();
        return ok;
    }

    // ── Backtracking + forward checking ──────────────────────

    private boolean backtrack(List<String> sessions, int index) {
        if (index == sessions.size()) return true;

        String  code    = sessions.get(index);
        Subject subject = getSubject(code);
        Faculty faculty = facultyMap.get(code);

        // ── Constraint 3: try least-loaded days first (even distribution) ──
        // Count sessions already placed per day, sort ascending.
        // Backtracking still explores all days if needed — this is a
        // soft ordering bias, not a hard filter. Correctness is preserved.
        for (int d : daysByLoad(subject.sessionType)) {
            if (subjectOnDay(code, d)) continue;               // constraint ③

            if (subject.sessionType == SessionType.P) {
                // ── Compactness: labs must start at the next contiguous slot ──
                // Find what the next free contiguous slot on this day is.
                // Only allow the lab to start there — no jumping ahead.
                // This guarantees no idle gaps between sessions on the same day.
                int nextSlot = nextFreeSlot(d);
                if (nextSlot == -1 || nextSlot + 1 >= SLOTS) continue;
                // Lab pair must not straddle the break
                if (nextSlot == BREAK || nextSlot + 1 == BREAK) continue;
                // Try starting at nextSlot only (compact) — if it fails,
                // backtracking will revisit this day with a different prior placement
                if (!canPlaceLab(faculty, d, nextSlot)) continue;

                placeLab(faculty, d, nextSlot, code);
                if (forwardCheck(sessions, index + 1)) {
                    if (backtrack(sessions, index + 1)) return true;
                }
                undoLab(faculty, d, nextSlot);

            } else {
                // ── Compactness: lectures/tutorials fill the next free slot ──
                int nextSlot = nextFreeSlot(d);
                if (nextSlot == -1) continue;
                if (!canPlace(faculty, subject, d, nextSlot)) continue;

                place(faculty, subject, d, nextSlot, code);
                if (forwardCheck(sessions, index + 1)) {
                    if (backtrack(sessions, index + 1)) return true;
                }
                undo(faculty, d, nextSlot);
            }
        }
        return false;
    }

    /**
     * Returns day indices sorted by effective load ascending — least loaded first.
     *
     * @param sessionType the type of session about to be placed.
     *
     * When placing a LAB (P), days that already have a lab session get a
     * LAB_PENALTY added to their load score. This makes the sorter strongly
     * prefer lab-free days for new lab placements (soft constraint: spread
     * labs across the week). It never makes a day impossible — if every day
     * already has a lab (e.g. 6 labs across 5 days), the penalty applies
     * equally and the least-loaded day is still chosen. Correctness preserved.
     */
    private static final int LAB_PENALTY = 10; // >> max slots/day, so penalised
    // days always sort after clean days

    private int[] daysByLoad(SessionType sessionType) {
        Integer[] days = new Integer[DAYS];
        for (int i = 0; i < DAYS; i++) days[i] = i;
        Arrays.sort(days, (a, b) -> {
            int loadA = dayLoad(a, sessionType);
            int loadB = dayLoad(b, sessionType);
            return loadA != loadB ? loadA - loadB : a - b;
        });
        return Arrays.stream(days).mapToInt(Integer::intValue).toArray();
    }

    /**
     * Base load = number of occupied non-Break slots.
     * If placing a lab and this day already has a lab session,
     * add LAB_PENALTY so this day sorts after lab-free days.
     */
    private int dayLoad(int d, SessionType sessionType) {
        int count   = 0;
        boolean hasLab = false;
        for (String cell : grid[d]) {
            if (cell == null || cell.equals("Break") || cell.equals("-")) continue;
            count++;
            // A cell placed by placeLab occupies 2 consecutive identical cells.
            // We detect a lab day by checking if any two adjacent cells match.
        }
        // Detect lab presence: any two adjacent non-null equal non-Break cells
        for (int s = 0; s < SLOTS - 1; s++) {
            String c1 = grid[d][s], c2 = grid[d][s + 1];
            if (c1 != null && c1.equals(c2) && !c1.equals("Break")) {
                hasLab = true;
                break;
            }
        }
        if (sessionType == SessionType.P && hasLab) count += LAB_PENALTY;
        return count;
    }

    // ── Forward checking ─────────────────────────────────────
    //
    //  After a placement, scan remaining sessions. For each
    //  remaining subject, count how many valid (day, slot) pairs
    //  still exist. If any subject has ZERO valid placements
    //  remaining, this branch is a dead end — prune immediately.

    private boolean forwardCheck(List<String> sessions, int fromIndex) {
        // Track remaining quota per subject in this lookahead
        Map<String, Integer> remainingNeeded = new HashMap<>();
        for (int i = fromIndex; i < sessions.size(); i++) {
            String c = sessions.get(i);
            remainingNeeded.merge(c, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : remainingNeeded.entrySet()) {
            String  code    = entry.getKey();
            int     needed  = entry.getValue();
            Subject subject = getSubject(code);
            Faculty faculty = facultyMap.get(code);

            int validSlots = countValidSlots(subject, faculty, code);
            if (validSlots < needed) return false;  // prune
        }
        return true;
    }

    private int countValidSlots(Subject subject, Faculty faculty, String code) {
        int count = 0;
        for (int d = 0; d < DAYS; d++) {
            if (subjectOnDay(code, d)) continue;
            int nextSlot = nextFreeSlot(d);
            if (nextSlot == -1) continue;                 // day is full
            if (subject.sessionType == SessionType.P) {
                // Lab needs nextSlot and nextSlot+1, neither can be break
                if (nextSlot + 1 >= SLOTS) continue;
                if (nextSlot == BREAK || nextSlot + 1 == BREAK) continue;
                if (canPlaceLab(faculty, d, nextSlot)) count++;
            } else {
                if (canPlace(faculty, subject, d, nextSlot)) count++;
            }
        }
        return count;
    }

    private Room findRoom(Subject subject, int d, int s) {

        boolean lab = subject.sessionType == SessionType.P;

        for (Room room : rooms) {

            if (lab && !room.isLab())
                continue;

            if (!lab && room.isLab())
                continue;

            if (!roomAvailable(room, d, s))
                continue;

            if (lab && !roomAvailable(room, d, s + 1))
                continue;

            return room;
        }

        return null;
    }

    // ── Compactness helper ───────────────────────────────────

    /**
     * Returns the index of the next free (non-occupied, non-Break) slot on
     * day d, scanning from slot 0 upward. Returns -1 if the day is full.
     *
     * "Next free" means the lowest-indexed unoccupied slot — enforcing that
     * new sessions always attach directly to the existing block, never jump
     * ahead and create a gap.
     *
     * Example: if Mon has [DS, CN, -, Break, -, -, -], nextFreeSlot(Mon) = 2
     * (slot index 2, which is 11am-12pm). A new session placed at 2 keeps
     * the block solid: [DS, CN, NEW, Break, ...]. Placing at slot 4 instead
     * would leave a gap at 2 — that is now impossible.
     */
    private int nextFreeSlot(int d) {
        for (int s = 0; s < SLOTS; s++) {
            if (s == BREAK) continue;
            if (!occupied[d][s]) return s;
        }
        return -1;  // day is completely full
    }

    // ── Constraint checks ─────────────────────────────────────

    private boolean canPlace(Faculty f, Subject subject, int d, int s) {
        if (s == BREAK) return false;
        if (occupied[d][s]) return false;
        if (!f.canSchedule()) return false;

        // Cross-division faculty conflict
        if (!facultyAvailable(f.name, d, s))
            return false;

        Room room = findRoom(subject, d, s);

        if (room == null)
            return false;

        return true;
    }

    private boolean canPlaceLab(Faculty f, int d, int s) {
        if (s == BREAK || s + 1 == BREAK) return false;
        if (s + 1 >= SLOTS) return false;
        if (occupied[d][s] || occupied[d][s+1]) return false;
        if (!f.canSchedule(2)) return false;
        if (!facultyAvailable(f.name, d, s))
            return false;
        if (!facultyAvailable(f.name, d, s + 1))
            return false;
        // ── Constraint 1: lab room conflict check ──
        // Check if this subject's physical lab is already in use by
        // another division in these two slots.

        Subject subject = getSubject(f.subject);

        Room room = findRoom(subject, d, s);

        if (room == null)
            return false;

        return true;
    }

    // ── Place / undo ──────────────────────────────────────────

    private void place(Faculty f, Subject subject, int d, int s, String code) {

        Room room = findRoom(subject, d, s);

        if (room == null)
            throw new IllegalStateException(
                    "No room available");

        grid[d][s] = subjectNames.getOrDefault(code, code);

        roomGrid[d][s] = room.name;

        roomOccupancy
                .computeIfAbsent(room.name, k -> new HashSet<>())
                .add(d + "-" + s);

        occupied[d][s] = true;

        f.schedule();

        facultyOccupancy
                .computeIfAbsent(f.name, k -> new HashSet<>())
                .add(d + "-" + s);
    }

    private void undo(Faculty f, int d, int s) {
        grid[d][s] = null;
        occupied[d][s] = false;
        f.unschedule();

        String roomName =
                roomGrid[d][s];

        roomGrid[d][s] = null;

        if (roomName != null) {

            Set<String> booked =
                    roomOccupancy.get(roomName);

            if (booked != null) {

                booked.remove(d + "-" + s);

                if (booked.isEmpty())
                    roomOccupancy.remove(roomName);
            }
        }

        Set<String> booked = facultyOccupancy.get(f.name);

        if (booked != null) {
            booked.remove(d + "-" + s);

            if (booked.isEmpty())
                facultyOccupancy.remove(f.name);
        }
    }
    private void placeLab(Faculty f, int d, int s, String code) {
        Subject subject = getSubject(code);
        Room room = findRoom(subject, d, s);
        String lbl = subjectNames.get(code) != null ? subjectNames.get(code) : code;
        grid[d][s] = lbl;
        grid[d][s+1] = lbl;
        roomGrid[d][s] = room.name;
        roomGrid[d][s+1] = room.name;
        occupied[d][s] = true; occupied[d][s+1] = true;
        f.schedule(); f.schedule();
        facultyOccupancy
                .computeIfAbsent(f.name, k -> new HashSet<>())
                .add(d + "-" + s);
        facultyOccupancy
                .get(f.name)
                .add(d + "-" + (s + 1));

        roomOccupancy
                .computeIfAbsent(room.name, k -> new HashSet<>())
                .add(d + "-" + s);

        roomOccupancy
                .get(room.name)
                .add(d + "-" + (s + 1));

    }
    private void undoLab(Faculty f, int d, int s) {
        grid[d][s] = null;
        grid[d][s+1] = null;

        occupied[d][s] = false;
        occupied[d][s+1] = false;

        f.unschedule();
        f.unschedule();

        Set<String> booked = facultyOccupancy.get(f.name);

        if (booked != null) {
            booked.remove(d + "-" + s);
            booked.remove(d + "-" + (s + 1));

            if (booked.isEmpty())
                facultyOccupancy.remove(f.name);
        }

        String roomName = roomGrid[d][s];

        roomGrid[d][s] = null;
        roomGrid[d][s+1] = null;

        if (roomName != null) {

            booked = roomOccupancy.get(roomName);

            if (booked != null) {

                booked.remove(d + "-" + s);
                booked.remove(d + "-" + (s + 1));

                if (booked.isEmpty())
                    roomOccupancy.remove(roomName);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private Subject getSubject(String code) {
        for (Subject s : agenda)
            if (s.code.equals(code))
                return s;

        throw new IllegalStateException(
                "Unknown subject code encountered during scheduling: " + code);
    }

    private boolean subjectOnDay(String code, int day) {
        String name = subjectNames.getOrDefault(code, code);
        for (String cell : grid[day])
            if (cell != null && cell.equals(name)) return true;
        return false;
    }

    private void fillEmpty() {
        for (int d = 0; d < DAYS; d++)
            for (int s = 0; s < SLOTS; s++)
                if (grid[d][s] == null) grid[d][s] = "-";
    }

    private boolean facultyAvailable(String facultyName, int day, int slot) {
        Set<String> booked =
                facultyOccupancy.get(facultyName);

        return booked == null ||
                !booked.contains(day + "-" + slot);
    }

    private boolean roomAvailable(Room room, int d, int s) {
        Set<String> booked =
                roomOccupancy.get(room.name);

        return booked == null ||
                !booked.contains(d + "-" + s);
    }

    // ── Display ───────────────────────────────────────────────

    public void displayCombined() {

        System.out.println(
                "\n╔════════════════════════════════════════ Division " + division +
                        " — Timetable + Room Allocation ════════════════════════════════════════╗\n");

        System.out.printf("%-10s", "");

        for (String t : TIME)
            System.out.printf("%-15s", t);

        System.out.println();

        System.out.println("─".repeat(123));

        for (int d = 0; d < DAYS; d++) {

            // Subject row
            System.out.printf("%-10s", DAY_LABEL[d]);

            for (int s = 0; s < SLOTS; s++) {

                String subject = grid[d][s];

                System.out.printf(
                        "%-15s",
                        subject == null ? "-" : subject
                );
            }

            System.out.println();

            // Room row
            System.out.printf("%-10s", "");

            for (int s = 0; s < SLOTS; s++) {

                String room = roomGrid[d][s];

                if ("Break".equals(grid[d][s])) {
                    System.out.printf("%-15s", "");
                    continue;
                }

                System.out.printf(
                        "%-15s",
                        room == null ? "" : "[" + room + "]"
                );
            }

            System.out.println();
            System.out.println();
        }

        System.out.println("═".repeat(123));
    }

    public void displayFacultySummary() {
        System.out.println("\n  Division " + division + " faculty:");
        System.out.printf("    %-20s %-14s %-9s %-9s %s%n",
                "Name","Subject","Required","Placed","Status");
        System.out.println("    " + "─".repeat(58));
        for (Faculty f : facultyMap.values()) {
            String st = f.scheduledClasses == f.totalClasses ? "✓ Complete" : "✗ Partial";
            String subjectName = subjectNames.getOrDefault(f.subject, f.subject);

            System.out.printf("    %-20s %-14s %-9d %-9d %s%n",
                    f.name,
                    subjectName,
                    f.totalClasses,
                    f.scheduledClasses,
                    st);
        }
    }

    public String getDivision() { return division; }
}

// ── Main ──────────────────────────────────────────────────────

public class ByteKnights {

    public static void main(String[] args) {
        String inputPath = args.length > 0 ? args[0] : "input.json";

        JSONObject root;
        try { root = InputLoader.load(inputPath); }
        catch (IOException e) {
            System.err.println("ERROR: Cannot read " + inputPath + " — " + e.getMessage());
            return;
        }

        List<String>         divisions = new ArrayList<>();
        for (Object d : root.getJSONArray("divisions")) divisions.add((String) d);
        Map<String, Subject> subjects  = InputLoader.parseSubjects(root);
        List<Faculty>        faculty   = InputLoader.parseFaculty(root, subjects);

        System.out.println("Loaded    : " + inputPath);
        System.out.println("Classroom : " + root.getString("classroom"));
        System.out.println("Divisions : " + divisions);
        System.out.println("Subjects  : " + subjects.size());

        int slotsPerDiv = DivisionScheduler.DAYS * (DivisionScheduler.SLOTS - 1);
        for (String div : divisions) {
            int sessions = 0;
            for (Faculty f : faculty)
                if (f.division.equals(div)) {
                    Subject s = subjects.get(f.subject);
                    if (s != null) sessions += s.creditsPerWeek;
                }
            System.out.printf("Sessions  : %d for Division %s | %d slots available%n",
                    sessions, div, slotsPerDiv);
        }

        // Schedule each division independently
        // Constraint 1: one shared lab room map across all divisions
        Map<String, Set<String>> facultyOccupancy = new HashMap<>();
        List<Room> rooms = InputLoader.parseRooms(root);
        Map<String, Set<String>> roomOccupancy = new HashMap<>();

        boolean allOk = true;
        List<DivisionScheduler> schedulers = new ArrayList<>();
        for (String div : divisions) {
            System.out.println("\nScheduling Division " + div + "...");
            DivisionScheduler ds =
                    new DivisionScheduler(
                            div,
                            subjects,
                            faculty,
                            rooms,
                            facultyOccupancy,
                            roomOccupancy
                    );
            if (!ds.schedule()) {
                System.err.println("  FAILED: No valid timetable for Division " + div);
                allOk = false;
            } else {
                System.out.println("  ✓ Done.");
                schedulers.add(ds);
            }
        }

        if (!allOk) {
            System.err.println("\nCould not generate complete schedule. " +
                    "Reduce session counts or add more time slots.");
            return;
        }

        System.out.println("\n✓ All divisions scheduled via backtracking + MCV + forward checking.");

        for (DivisionScheduler ds : schedulers) {
            ds.displayCombined();
        }

        System.out.println("\n╔══ Faculty Schedule Summary ══╗");
        for (DivisionScheduler ds : schedulers) ds.displayFacultySummary();
        System.out.println("═".repeat(64));
    }
}