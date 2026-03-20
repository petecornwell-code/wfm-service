package com.wfm.integration;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Incremented on each listEmployees call so each refresh produces a different roster. */
    private final AtomicInteger callCount = new AtomicInteger(0);

    /**
     * Build a shuffled roster of Vinted agents. The seed controls which employees
     * appear and in what order, so each refresh call returns a visibly different list.
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
                "Support",
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
        int seed = callCount.getAndIncrement();

        // Case-insensitive desk name matching
        if ("Vinted".equalsIgnoreCase(project)) {
            return buildVintedAgents(wfmTenantId, seed);
        }

        // For non-Vinted desks, generate a varying roster from the name pools
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
            String department = rng.nextBoolean() ? "Support" : "Sales";
            if (project != null && !project.isBlank() && !project.equalsIgnoreCase(department)) {
                continue;
            }
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

        // Generate test day-off data for the first 5 mock employees within the requested window.
        // Employee "1" (Jane Smith / Olivia Smith): one mandatory holiday per month
        // Employee "2" (John Doe / Liam Johnson): PTO every other Friday
        // Employee "3" (Alice Brown / Emma Williams): one week PTO block starting 2 weeks from 'from'
        // Employees "4" and "5": scattered mandatory holidays

        // Employee 1: one mandatory holiday on the first Monday of each month in range
        LocalDate date = from;
        while (!date.isAfter(to)) {
            if (date.getDayOfMonth() <= 7 && date.getDayOfWeek() == DayOfWeek.MONDAY) {
                timeOffs.add(new BambooTimeOff("1", date, "holiday"));
            }
            date = date.plusDays(1);
        }

        // Employee 2: PTO every other Friday
        date = from;
        int fridayCount = 0;
        while (!date.isAfter(to)) {
            if (date.getDayOfWeek() == DayOfWeek.FRIDAY) {
                fridayCount++;
                if (fridayCount % 2 == 0) {
                    timeOffs.add(new BambooTimeOff("2", date, "pto"));
                }
            }
            date = date.plusDays(1);
        }

        // Employee 3: one week PTO block starting 2 weeks from 'from'
        LocalDate ptoStart = from.plusWeeks(2);
        for (int i = 0; i < 5 && !ptoStart.plusDays(i).isAfter(to); i++) {
            LocalDate ptoDate = ptoStart.plusDays(i);
            if (ptoDate.getDayOfWeek() != DayOfWeek.SATURDAY && ptoDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
                timeOffs.add(new BambooTimeOff("3", ptoDate, "pto"));
            }
        }

        // Employee 4: mandatory holiday on the 15th of each month (if weekday)
        date = from;
        while (!date.isAfter(to)) {
            if (date.getDayOfMonth() == 15
                    && date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                timeOffs.add(new BambooTimeOff("4", date, "mandatory"));
            }
            date = date.plusDays(1);
        }

        // Employee 5: PTO on the last Friday of each month
        date = from.withDayOfMonth(1);
        while (!date.isAfter(to)) {
            LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
            while (lastDay.getDayOfWeek() != DayOfWeek.FRIDAY) {
                lastDay = lastDay.minusDays(1);
            }
            if (!lastDay.isBefore(from) && !lastDay.isAfter(to)) {
                timeOffs.add(new BambooTimeOff("5", lastDay, "pto"));
            }
            date = date.plusMonths(1);
        }

        return timeOffs;
    }
}
