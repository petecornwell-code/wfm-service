package com.wfm.integration;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * In-memory mock BambooHR client for development.
 * Used by BambooRefreshService (desk agents) and as fallback when
 * BambooHR credentials are not configured.
 */
@Component("mockBambooHRClient")
public class MockBambooHRClient implements BambooHRClient {

    private static final String[] FIRST_NAMES = {
        "Aaron", "Addison", "Adrian", "Alexander", "Amelia", "Anna", "Anthony", "Asher",
        "Aubrey", "Audrey", "Aurora", "Ava", "Bella", "Benjamin", "Brooklyn", "Caleb",
        "Cameron", "Caroline", "Carter", "Charles", "Charlotte", "Chloe", "Christopher",
        "Claire", "Connor", "Daniel", "David", "Dylan", "Eleanor", "Eli", "Elijah",
        "Ella", "Ellie", "Emilia", "Emma", "Evelyn", "Everly", "Ezra", "Gabriel",
        "Genesis", "Grace", "Hannah", "Harper", "Hazel", "Henry", "Hunter", "Isaac",
        "Isabella", "Jack", "James", "Jaxon", "John", "Jonathan", "Joseph", "Joshua",
        "Josiah", "Kennedy", "Landon", "Layla", "Leah", "Leo", "Liam", "Lillian",
        "Lily", "Lincoln", "Lucas", "Lucy", "Luke", "Luna", "Matthew", "Maya",
        "Mia", "Michael", "Miles", "Natalie", "Nathan", "Noah", "Nolan", "Nora",
        "Nova", "Olivia", "Owen", "Paisley", "Penelope", "Riley", "Ryan", "Samantha",
        "Samuel", "Savannah", "Scarlett", "Sebastian", "Skylar", "Sophia", "Stella",
        "Thomas", "Violet", "William", "Wyatt", "Zoe", "Zoey",
        "Alice", "Brian", "Catherine", "Derek", "Elena", "Felix", "Georgia", "Hugo",
        "Iris", "Julian", "Katherine", "Lorenzo", "Madeline", "Nico", "Ophelia",
        "Patrick", "Quinn", "Regina", "Sienna", "Tobias", "Ursula", "Victor"
    };

    private static final String[] LAST_NAMES = {
        "Adams", "Allen", "Anderson", "Baker", "Brown", "Campbell", "Carter", "Clark",
        "Davis", "Flores", "Garcia", "Gonzalez", "Green", "Hall", "Harris", "Hernandez",
        "Hill", "Jackson", "Johnson", "Jones", "King", "Lee", "Lewis", "Lopez",
        "Martin", "Martinez", "Miller", "Mitchell", "Moore", "Nelson", "Nguyen", "Perez",
        "Ramirez", "Rivera", "Roberts", "Robinson", "Rodriguez", "Sanchez", "Scott",
        "Smith", "Taylor", "Thomas", "Thompson", "Torres", "Walker", "White", "Williams",
        "Wilson", "Wright", "Young"
    };

    /**
     * Build a stable roster of Vinted agents. The seed is derived from the desk name
     * so the same employees are always returned for a given desk (preventing spurious soft-deletes).
     */
    private static List<BambooEmployee> buildVintedAgents(String wfmTenantId, int seed) {
        // Build the full pool of possible employees
        List<BambooEmployee> pool = new ArrayList<>(FIRST_NAMES.length);
        for (int i = 0; i < FIRST_NAMES.length; i++) {
            String firstName = FIRST_NAMES[i];
            String lastName = LAST_NAMES[i % LAST_NAMES.length];
            String displayName = firstName + " " + lastName;
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + (i + 1) + "@vinted.example.com";
            pool.add(new BambooEmployee(
                String.valueOf(i + 1),
                displayName,
                email,
                "Vinted",
                "Agent",
                "Active",
                wfmTenantId,
                "Vinted"
            ));
        }
        // Shuffle with a seed derived from the call count so each refresh is different
        Collections.shuffle(pool, new Random(seed));
        // Return exactly 120 employees
        return List.copyOf(pool.subList(0, Math.min(120, pool.size())));
    }

    @Override
    public List<BambooEmployee> listEmployees(String wfmTenantId, String project) {
        // Use a stable seed derived from the desk name so the same employees are
        // always returned for a given desk — prevents spurious soft-deletes on refresh.
        int seed = (project != null ? project.toLowerCase() : "").hashCode();

        // Case-insensitive desk name matching
        if ("Vinted".equalsIgnoreCase(project)) {
            return buildVintedAgents(wfmTenantId, seed);
        }

        // For non-Vinted desks, generate a stable roster from the name pools
        Random rng = new Random(seed);
        int count = 3 + rng.nextInt(6); // 3-8 employees
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < FIRST_NAMES.length; i++) indices.add(i);
        Collections.shuffle(indices, rng);

        List<BambooEmployee> employees = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = indices.get(i);
            String firstName = FIRST_NAMES[idx];
            String lastName = LAST_NAMES[idx % LAST_NAMES.length];
            String displayName = firstName + " " + lastName;
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@example.com";
            String department = project != null ? project : (rng.nextBoolean() ? "Support" : "Sales");
            employees.add(new BambooEmployee(
                String.valueOf(idx + 1),
                displayName,
                email,
                department,
                "Agent",
                "Active",
                wfmTenantId,
                project
            ));
        }
        return List.copyOf(employees);
    }

    @Override
    public BambooEmployee getEmployee(String bamboohrId) {
        return new BambooEmployee(bamboohrId, "Mock Employee", "mock@example.com", "Support", "Agent", "Active", "1", "Default");
    }

    @Override
    public List<BambooTimeOff> listTimeOff(String wfmTenantId, LocalDate from, LocalDate to) {
        List<BambooTimeOff> timeOffs = new ArrayList<>();

        // Generate time-off data for all mock employee IDs (1..FIRST_NAMES.length)
        // so that PTO shows regardless of which employees are assigned to the desk.
        // Use a deterministic pattern based on employee ID so results are stable.
        for (int empIdx = 0; empIdx < FIRST_NAMES.length; empIdx++) {
            String employeeId = String.valueOf(empIdx + 1);
            int pattern = empIdx % 5;

            switch (pattern) {
                case 0 -> {
                    // Mandatory holiday on the first Monday of each month in range
                    LocalDate date = from;
                    while (!date.isAfter(to)) {
                        if (date.getDayOfMonth() <= 7 && date.getDayOfWeek() == DayOfWeek.MONDAY) {
                            timeOffs.add(new BambooTimeOff(employeeId, date, "holiday"));
                        }
                        date = date.plusDays(1);
                    }
                }
                case 1 -> {
                    // PTO every other Friday
                    LocalDate date = from;
                    int fridayCount = 0;
                    while (!date.isAfter(to)) {
                        if (date.getDayOfWeek() == DayOfWeek.FRIDAY) {
                            fridayCount++;
                            if (fridayCount % 2 == 0) {
                                timeOffs.add(new BambooTimeOff(employeeId, date, "pto"));
                            }
                        }
                        date = date.plusDays(1);
                    }
                }
                case 2 -> {
                    // One week PTO block starting 2 weeks from 'from'
                    LocalDate ptoStart = from.plusWeeks(2);
                    for (int i = 0; i < 5 && !ptoStart.plusDays(i).isAfter(to); i++) {
                        LocalDate ptoDate = ptoStart.plusDays(i);
                        if (ptoDate.getDayOfWeek() != DayOfWeek.SATURDAY && ptoDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
                            timeOffs.add(new BambooTimeOff(employeeId, ptoDate, "pto"));
                        }
                    }
                }
                case 3 -> {
                    // Mandatory holiday on the 15th of each month (if weekday)
                    LocalDate date = from;
                    while (!date.isAfter(to)) {
                        if (date.getDayOfMonth() == 15
                                && date.getDayOfWeek() != DayOfWeek.SATURDAY
                                && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                            timeOffs.add(new BambooTimeOff(employeeId, date, "mandatory"));
                        }
                        date = date.plusDays(1);
                    }
                }
                case 4 -> {
                    // PTO on the last Friday of each month
                    LocalDate date = from.withDayOfMonth(1);
                    while (!date.isAfter(to)) {
                        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
                        while (lastDay.getDayOfWeek() != DayOfWeek.FRIDAY) {
                            lastDay = lastDay.minusDays(1);
                        }
                        if (!lastDay.isBefore(from) && !lastDay.isAfter(to)) {
                            timeOffs.add(new BambooTimeOff(employeeId, lastDay, "pto"));
                        }
                        date = date.plusMonths(1);
                    }
                }
            }
        }

        return timeOffs;
    }
}
