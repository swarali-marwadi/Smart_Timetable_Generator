# Smart Timetable Generator

A constraint-based academic timetable scheduling engine developed in Java using **Backtracking**, **Most Constrained Variable (MCV) Heuristic**, and **Forward Checking**.

The system automatically generates optimized weekly timetables while satisfying faculty workload requirements, subject constraints, laboratory scheduling rules, and shared resource limitations.

---

## Problem Statement

Creating academic timetables manually is time-consuming and error-prone due to multiple scheduling constraints such as:

- Faculty workload limits
- Laboratory sessions requiring consecutive slots
- Subject distribution across the week
- Shared laboratory availability
- Avoiding timetable conflicts
- Efficient utilization of available time slots

This project automates the scheduling process using constraint satisfaction techniques and search-based optimization. The timetable generation process is modeled as a Constraint Satisfaction Problem (CSP), where subjects, faculty, time slots, and laboratory resources must satisfy a set of hard and soft constraints.

---

## Features

### Automated Timetable Generation
- Generates complete weekly timetables automatically.
- Supports multiple divisions.

### Constraint-Based Scheduling
- No scheduling during break periods.
- Prevents duplicate occurrence of a subject on the same day.
- Ensures faculty workload requirements are satisfied.
- Handles laboratory sessions occupying consecutive slots.

### Optimization Heuristics
- Backtracking search.
- Most Constrained Variable (MCV) heuristic.
- Forward checking for early dead-end detection.
- Load-balanced day selection.
- Compact slot placement.
- Laboratory distribution heuristic.

### Shared Resource & Room Management
- Shared laboratory occupancy tracking.
- Classroom and laboratory allocation during scheduling.
- Prevents simultaneous allocation of the same room.
- Global faculty availability tracking across divisions.
- Prevents a faculty member from being scheduled in multiple divisions simultaneously.
- Generates room-wise allocation alongside the timetable.

### Faculty Tracking
- Tracks required and scheduled teaching load.
- Generates faculty-wise scheduling summary.

### Room Allocation
- Automatically assigns classrooms and laboratories.
- Supports multiple classrooms and shared labs.
- Prevents room conflicts across divisions.
- Ensures laboratory sessions are assigned only to lab rooms.

---

## Algorithms Used

### 1. Backtracking

The scheduler incrementally assigns sessions to timetable slots.

If a placement violates any constraint, the algorithm backtracks and tries an alternative placement.

### 2. Most Constrained Variable (MCV)

Subjects are scheduled in an order that reduces the search space and improves convergence.

### 3. Forward Checking

After every placement, the scheduler verifies that all remaining sessions still have valid placement possibilities.

Branches that cannot lead to a valid solution are pruned immediately.

### 4. Load Balancing Heuristic

Days with lower current occupancy are prioritized to achieve a more balanced timetable.

### 5. Lab Distribution Heuristic

Laboratory sessions are spread across the week to avoid clustering.

---

## Why This Is a CSP

The timetable generation problem is modeled as a Constraint Satisfaction Problem (CSP), where:

- Variables → Timetable sessions
- Domains → Available time slots
- Constraints → Faculty workload, laboratory availability, subject distribution, and break periods

The scheduler uses Backtracking, Most Constrained Variable (MCV), and Forward Checking to efficiently explore and prune the search space.

---

## Scheduling Strategy

The timetable generator combines Constraint Satisfaction Problem (CSP) techniques with scheduling heuristics to produce balanced and conflict-free timetables while efficiently utilizing available resources.

### Session Placement Strategy

- Subjects are expanded into individual weekly sessions based on their required credits.
- Sessions are scheduled incrementally using recursive backtracking.
- Each placement is validated against all hard constraints before being accepted.
- Forward checking is performed after every placement to eliminate infeasible branches early.

### Resource Allocation Strategy

- Classrooms and laboratories are allocated dynamically during scheduling.
- Room occupancy is tracked globally across all divisions.
- Laboratory sessions are restricted to laboratory rooms only.
- Faculty availability is monitored globally to prevent cross-division scheduling conflicts.
- Every scheduled session must be assigned a valid and available room.

### Timetable Optimization Strategy

- Least-loaded days are prioritized to achieve a balanced weekly workload distribution.
- Sessions are placed in the next available slot to maintain timetable compactness and reduce idle gaps.
- Laboratory sessions are distributed across the week whenever possible to avoid clustering.
- Resource conflicts are resolved dynamically during the search process through backtracking and constraint validation.

---

## Resource Conflict Resolution

The scheduler manages shared resources globally across all divisions to ensure conflict-free timetable generation.

### Room Conflict Resolution

- Tracks occupancy of classrooms and laboratories across divisions.
- Prevents a room from being allocated to multiple divisions simultaneously.
- Dynamically searches for alternative available rooms when conflicts occur.

### Faculty Conflict Resolution

- Maintains a global faculty availability map across all divisions.
- Prevents a faculty member from being scheduled in multiple divisions simultaneously.
- Automatically searches for alternative valid placements when conflicts occur.

### Global Constraint Enforcement

Both room and faculty conflicts are treated as hard constraints and are validated during every scheduling decision.

---

## Scheduling Constraints

### Hard Constraints

- Break slot cannot be used.
- Slot must be free in the division timetable.
- A subject cannot be scheduled more than once on the same day.
- Faculty workload must not exceed assigned quota.
- Laboratory sessions must occupy two consecutive slots.
- Shared room resources cannot be double-booked.
- A faculty member cannot be scheduled in multiple divisions simultaneously.
- Laboratories can only be assigned to laboratory rooms.
- Every scheduled session must receive a valid room allocation.

### Soft Constraints

- Prefer less-loaded days.
- Prefer compact timetables.
- Distribute labs across different days whenever possible.

---

## Project Structure

```text
SmartTimetableGenerator
│
├── ByteKnights.java
├── input.json
│
├── Subject
├── Faculty
├── InputLoader
├── DivisionScheduler
│
└── Output
```

---

## Input Format

Example:

```json
{
  "classroom": "25",
  "divisions": ["D", "E"],

  "subjects": [
    {
      "code": "23PCIT301",
      "name": "DS",
      "sessionType": "L",
      "creditsPerWeek": 3
    }
  ],

  "faculty": [
    {
      "name": "Mrs. Kulkarni",
      "subject": "23PCIT301",
      "division": "D"
    }
  ]
}
```

---

## Sample Output

### Timetable + Room Allocation

![Combined Timetable](assets/timetable-room-allocation.png)

### Faculty Summary

![Faculty Summary 1](assets/faculty-summary-1.png)
![Faculty Summary 2](assets/faculty-summary-2.png)

---

## System Architecture

```text
Input JSON
     │
     ▼
Input Loader
     │
     ▼
Subject & Faculty Models
     │
     ▼
Session Expansion
     │
     ▼
Backtracking + MCV
     │
     ▼
Forward Checking
     │
     ▼
Global Resource Validation
     │
     ├── Room Availability
     │
     └── Faculty Availability
     │
     ▼
Room Allocation
     │
     ▼
Timetable Generation
     │
     ▼
Faculty Summary
```
---

## Complexity Analysis

Let:
- S = Number of sessions
- D = Number of days
- T = Number of available slots per day

### Worst Case

O((D × T)^S)
The scheduling problem is a Constraint Satisfaction Problem (CSP) and is NP-hard in the general case.

### Optimizations Used

To reduce practical runtime:

- Most Constrained Variable (MCV)
- Forward Checking
- Day Load Heuristics
- Compact Slot Selection
- Early Constraint Pruning

---

## Technologies Used

- Java
- JSON (org.json)
- Backtracking Algorithms
- Constraint Satisfaction Techniques (CSP)
- Heuristic Search
- Scheduling Optimization
- Resource Allocation
- Constraint Propagation

---

## Key Learning Outcomes

- Constraint Satisfaction Problems (CSP)
- Recursive Backtracking
- Search Space Optimization
- Forward Checking
- Heuristic-Based Scheduling
- Resource Allocation Systems

---

## Version History

### v1.0
- Basic timetable generation
- JSON input parsing
- Subject and faculty allocation

### v2.0
- Recursive backtracking scheduler
- Multi-division support
- Faculty workload tracking

### v3.0
- Most Constrained Variable (MCV) heuristic
- Forward checking
- Shared lab resource management
- Load-balanced scheduling
- Compact timetable generation
- Faculty schedule summary

### v3.1
- Cross-division faculty conflict detection
- Global faculty availability tracking
- Enhanced resource constraint handling

### v3.2
- Dynamic room allocation system
- Classroom and laboratory conflict detection
- Shared room occupancy tracking
- Combined timetable and room allocation display
- Laboratory distribution heuristic
- Compact slot placement heuristic

---

## Future Enhancements

- Room capacity constraints
- Faculty preference-based scheduling
- Graph-coloring based scheduling model
- Timetable quality scoring metrics
- PDF/Excel export
- Interactive GUI using JavaFX

---

## Achievement

Developed as part of a Loop-CCOEW's Buffer 6.0 - DSA Project Competition in the Next-Gen Academic Solutions domain and secured **3rd Place**.
