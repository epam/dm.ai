// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import com.github.istin.dmtools.vacation.Vacation;
import com.github.istin.dmtools.vacation.Vacations;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class VacationsMetricSource extends CommonSourceCollector {

    private List<String> peopleToFilterOut;

    private IEmployees employees;
    private boolean isDays;

    public VacationsMetricSource(IEmployees employees, List<String> peopleToFilterOut, boolean isDays) {
        super(employees);
        this.peopleToFilterOut = peopleToFilterOut;
        this.employees = employees;
        this.isDays = isDays;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        List<Vacation> vacationList = Vacations.getInstance().getVacations(peopleToFilterOut);
        int i = 0;
        for (Vacation vacation : vacationList) {
            String name = employees.transformName(vacation.getName());
            if (employees != null) {
                if (!employees.contains(name)) {
                    continue;
                }
            }
            Calendar startCalendar = vacation.getStartDateAsCalendar();
            if (startCalendar == null) {
                continue;
            }
            KeyTime keyTime = new KeyTime("" + i, startCalendar, isPersonalized ? name : metricName);
            int vacationHours = (int) (vacation.getDuration() * 8);

            if (isDays) {
                keyTime.setWeight(vacation.getDuration());
            } else {
                keyTime.setWeight(convertVacationHoursToSPs(vacationHours, name));
            }
            //in story points

            data.add(keyTime);
        }

        return data;
    }

    protected double convertVacationHoursToSPs(int vacationHours, String name) throws Exception {
        return vacationHours;
    }
}
