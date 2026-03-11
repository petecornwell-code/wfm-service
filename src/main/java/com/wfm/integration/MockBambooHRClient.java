package com.wfm.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Active when bamboohr.mock=true (default).
 */
@Component
@ConditionalOnProperty(name = "bamboohr.mock", havingValue = "true", matchIfMissing = true)
public class MockBambooHRClient implements BambooHRClient {

    private static final String[] FIRST_NAMES = {
        "Olivia", "Liam", "Emma", "Noah", "Ava", "Elijah", "Sophia", "James",
        "Isabella", "William", "Mia", "Benjamin", "Charlotte", "Lucas", "Amelia",
        "Henry", "Harper", "Alexander", "Evelyn", "Sebastian", "Luna", "Jack",
        "Ella", "Daniel", "Scarlett", "Michael", "Grace", "Owen", "Chloe", "Samuel",
        "Penelope", "David", "Layla", "Joseph", "Riley", "Carter", "Zoey", "Wyatt",
        "Nora", "John", "Lily", "Luke", "Eleanor", "Gabriel", "Hannah", "Anthony",
        "Lillian", "Isaac", "Addison", "Dylan", "Aubrey", "Leo", "Ellie", "Lincoln",
        "Stella", "Jaxon", "Natalie", "Asher", "Zoe", "Christopher", "Leah", "Josiah",
        "Hazel", "Andrew", "Violet", "Thomas", "Aurora", "Joshua", "Savannah", "Ezra",
        "Audrey", "Adrian", "Brooklyn", "Charles", "Bella", "Caleb", "Claire", "Ryan",
        "Skylar", "Nathan", "Lucy", "Eli", "Paisley", "Matthew", "Everly", "Connor",
        "Anna", "Aaron", "Caroline", "Landon", "Nova", "Jonathan", "Genesis", "Nolan",
        "Emilia", "Hunter", "Kennedy", "Cameron", "Samantha", "Miles", "Maya"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
        "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
        "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson",
        "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen",
        "Hill", "Flores", "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera",
        "Campbell", "Mitchell", "Carter", "Roberts"
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
        // Return between 85 and 100 employees (varying roster size)
        int size = 85 + (Math.abs(seed) % 16);
        return List.copyOf(pool.subList(0, Math.min(size, pool.size())));
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
            employees.add(new BambooEmployee(
                String.valueOf(idx + 1),
                displayName,
                email,
                rng.nextBoolean() ? "Support" : "Sales",
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
