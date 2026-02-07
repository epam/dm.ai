package com.github.istin.dmtools.reporting.model;

import com.github.istin.dmtools.team.IEmployees;

import java.util.*;

/**
 * Lightweight IEmployees implementation configured from report JSON config.
 * Supports inline employee list and aliases without external JSON files.
 */
public class ReportEmployees implements IEmployees {

    private final Set<String> employeeNames = new HashSet<>();
    private final Map<String, List<String>> aliases = new HashMap<>();
    // Reverse map: alias -> primary name (for fast lookup)
    private final Map<String, String> aliasToName = new HashMap<>();

    public ReportEmployees(List<String> employees, Map<String, List<String>> aliases) {
        if (employees != null) {
            for (String name : employees) {
                employeeNames.add(name.toLowerCase());
            }
        }
        if (aliases != null) {
            for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
                String primaryName = entry.getKey();
                List<String> aliasList = entry.getValue();
                this.aliases.put(primaryName, aliasList);
                employeeNames.add(primaryName.toLowerCase());
                for (String alias : aliasList) {
                    aliasToName.put(alias.toLowerCase(), primaryName);
                    employeeNames.add(alias.toLowerCase());
                }
            }
        }
    }

    @Override
    public boolean contains(String fullName) {
        if (fullName == null) return false;
        if (employeeNames.isEmpty()) return true;
        return employeeNames.contains(fullName.toLowerCase());
    }

    @Override
    public String transformName(String sourceFullName) {
        if (sourceFullName == null) return null;
        String primary = aliasToName.get(sourceFullName.toLowerCase());
        return primary != null ? primary : sourceFullName;
    }

    @Override
    public boolean isBot(String sourceFullName) {
        return sourceFullName != null &&
            (sourceFullName.equalsIgnoreCase("DM_scripts") ||
             sourceFullName.equalsIgnoreCase("DM_scripts_token"));
    }
}
