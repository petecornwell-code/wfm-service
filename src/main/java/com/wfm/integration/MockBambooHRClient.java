package com.wfm.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    private static List<BambooEmployee> buildVintedAgents(String wfmTenantId) {
        List<BambooEmployee> agents = new ArrayList<>(150);
        int id = 1;
        for (int i = 0; i < FIRST_NAMES.length && agents.size() < 150; i++) {
            for (int j = 0; j < LAST_NAMES.length && agents.size() < 150; j++) {
                String firstName = FIRST_NAMES[i];
                String lastName = LAST_NAMES[j];
                String displayName = firstName + " " + lastName;
                String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@vinted.example.com";
                agents.add(new BambooEmployee(
                    String.valueOf(id),
                    displayName,
                    email,
                    "Support",
                    "Agent",
                    "Active",
                    wfmTenantId,
                    "Vinted"
                ));
                id++;
            }
        }
        return List.copyOf(agents);
    }

    @Override
    public List<BambooEmployee> listEmployees(String wfmTenantId, String project) {
        if ("Vinted".equals(project)) {
            return buildVintedAgents(wfmTenantId);
        }
        return List.of(
            new BambooEmployee("1", "Jane Smith", "jane@example.com", "Support", "Senior Agent", "Active", wfmTenantId, project),
            new BambooEmployee("2", "John Doe", "john@example.com", "Support", "Agent", "Active", wfmTenantId, project),
            new BambooEmployee("3", "Alice Brown", "alice@example.com", "Sales", "Agent", "Active", wfmTenantId, project)
        );
    }

    @Override
    public BambooEmployee getEmployee(String bamboohrId) {
        return new BambooEmployee(bamboohrId, "Mock Employee", "mock@example.com", "Support", "Agent", "Active", "1", "Default");
    }

    @Override
    public List<BambooTimeOff> listTimeOff(String wfmTenantId, LocalDate from, LocalDate to) {
        return List.of();
    }
}
