# Legacy Coursework Maintenance Playbook

**Date:** 15 September 2025  \\
**Authors:** Adapted for *The Legend of Esran – Escape Unemployment* by the HackNotts 2025 team

This playbook distils the expectations from the COMP2013 "Maintain and Extend a Legacy Software" brief
into concrete steps for our dungeon crawler. Use it as the single source of truth while iterating on the
project so that every contributor follows the same cadence, documentation style, and delivery checkpoints.

---

## 1. Milestone Timeline

| Task | Focus | Expected Deliverables |
|------|-------|-----------------------|
| **Task 1 – Stabilise V1** | Bring the codebase to a clean, bootable state on a dedicated `dev` branch. | *Fix compile/runtime regressions; refresh `README.md`; start `Changelog.md`; capture a build screenshot; tag commit `v1`.* |
| **Task 2 – Analyse & Plan V2** | Understand the legacy structure and prepare refactoring. | *500-word write-up in `SoftwareDesign.md` (current vs. planned), two embedded UML diagrams, Javadoc-ready inline comments, link from README, tag `task2`.* |
| **Task 3 – Regression Tests** | Lock in behaviour before refactoring. | *JUnit coverage for V1, documented rationale in `Testing.md`, forward-looking stubs for redesigned systems, tag `task3`.* |
| **Task 4 – Refactor to V2** | Apply SOLID, DRY, KISS, and CUPID principles; adopt JavaFX/MVC where required. | *Modular packages, design-pattern usage notes in `SoftwareDesign.md` (500 words + UML), refreshed tests with table in `Testing.md`, tag `v2`.* |
| **Task 5 – Plan V3 Features** | Pitch three evolutions (e.g., accessibility start screen, scoring, new enemies). | *Lo-fi prototypes and/or UML in `SoftwareDesign.md` (~500 words), updated future test plan in `Testing.md`, tag `task5`.* |
| **Task 6 – CI/CD Pipeline** | Automate builds and tests through GitLab CI. | *.gitlab-ci.yml tuned to run unit/integration suites, 500-word explanation in `Testing.md`, tag `task6`.* |
| **Task 7 – Build V3** | Implement planned features with clean architecture. | *Production-ready code with Javadoc, TDD evidence in `Testing.md`, tag `v3`.* |
| **Task 8 – Final Video** | Justify the engineering journey. | *≤6 minute .mp4 with face cam, architecture walkthrough, coding principles, top-three refactors, posted to Moodle and the final issue thread.* |

> 🔁 **Bonus:** Shipping the tagged `v3` build before 12 December unlocks the 5% early-delivery bonus—no commits after the deadline if you want it to stand.

---

## 2. Repository Etiquette

- **Branching:** Work from `dev`, fork task-specific feature branches, and merge back with descriptive PRs.
- **Commits:** Small, well-scoped commits using active-voice messages ("Refactor shop manager"), cross-referenced in documentation.
- **Issues & Boards:** Move issues across the board as you progress; document blockers or design debates in-line.
- **Tags:** Apply the required semantic tags (`v1`, `task2`, …) on the commits that satisfy each milestone and push them upstream.
- **Changelog Discipline:** Append dated entries to `Changelog.md` for every meaningful change-set and link it from the README.

---

## 3. README & Documentation Standards

1. **README structure**
   - Project title and author roster
   - Concise pitch / elevator summary
   - Setup + run instructions for Gradle and IntelliJ/VS Code
   - How to play (controls, objectives, fail states)
   - Credits and acknowledgements for third-party assets
   - Quick links to `Changelog.md`, `SoftwareDesign.md`, `Testing.md`, and this playbook
2. **SoftwareDesign.md**
   - Versioned sections (V1/V2/V3) with UML snippets describing architecture shifts
   - Callouts to specific commits when referencing refactors or design decisions
3. **Testing.md**
   - Regression tables (test name, purpose, pass/fail status)
   - Coverage targets (>80%) and CI links once Task 6 is complete
4. **Code Comments**
   - Prefer Javadoc blocks on public classes/methods, including preconditions and side effects
   - Avoid duplicating what the code already states; focus on intent and rationale

---

## 4. Quality Bar for Refactoring & Evolution

- **Architecture:** Move progressively towards clear MVC boundaries; isolate UI (JavaFX), game logic, and persistence.
- **Design Patterns:** Evaluate Strategy for enemy behaviour, Factory/Builder for rooms, Observer for UI events, and State for game phases.
- **Code Smells to Eliminate:** long methods, God objects, duplicated conditionals, dead code, and primitive obsession. Replace nested conditionals with polymorphism where practical.
- **Data Structures:** Use collections that reduce line counts and clarify intent (e.g., `EnumSet` for directional flags, `Map<Point, Room>` for spatial lookup).
- **Testing:** Combine fast unit suites with integration tests that drive core loops (movement, combat, shop interactions). Document fixtures and mock strategies.

---

## 5. Delivery Checklist Before Tagging

- [ ] README and markdown artefacts updated with cross-links and diagrams
- [ ] Changelog entry created with ISO date stamp
- [ ] Gradle build (`./gradlew build`) green locally and in CI
- [ ] Screenshots or videos attached to relevant issues
- [ ] Tags pushed and verified via `git tag -l`
- [ ] PR description summarises scope, testing, and any follow-up tasks

---

## 6. Video Submission Guidance (Task 8)

1. Record a Zoom/Meet session with screen share and face cam.
2. Demonstrate gameplay (shop access, boss fights, new features) and highlight the corresponding code.
3. Explain how the architecture satisfies OO/SOLID/DRY/CUPID principles.
4. Deep dive into the three most impactful refactors (problem → solution → alternatives considered).
5. Export as .mp4 (HD recommended), compress with Handbrake if necessary, upload to OneDrive, set sharing to "People in The University of Nottingham", and paste the link into both Moodle and the closing issue comment.

Staying aligned with this guide ensures our coursework submission mirrors industry-grade maintenance practice while keeping the dungeon crawler delightful and stable.
