// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.scopestatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptEngineManager;
import java.text.DecimalFormat;
import java.util.List;

public class ScopePageSummary {
    private static final Logger logger = LogManager.getLogger(ScopePageSummary.class);
    private String name;

    private List<SummaryItem> summaryItems;

    public ScopePageSummary(String name, List<SummaryItem> summaryItems) {
        this.name = name;
        this.summaryItems = summaryItems;
    }

    public String getName() {
        return name;
    }

    public List<SummaryItem> getSummaryItems() {
        return summaryItems;
    }

    public void addCustomMetric(String name, String formula) {
        ScriptEngineManager mgr = new ScriptEngineManager();
        javax.script.ScriptEngine engine = mgr.getEngineByName("graal.js");
        //
        for (int i = 0; i < this.summaryItems.size(); i++) {
            SummaryItem summaryItem = this.summaryItems.get(i);
            formula = formula.replaceAll("\\$\\{" + summaryItem.getLabel() + "}", "" + summaryItem.getNumericValue());
        }

        try {
            Object eval = engine.eval(formula);
            if (eval instanceof Integer) {
                if ((Integer) eval < 0) {
                    eval = 0;
                }
                this.summaryItems.add(new SummaryItem(name, new DecimalFormat("#.##").format(eval)));
                return;
            }
            if ((Double) eval < 0) {
                eval = 0;
            }

            this.summaryItems.add(new SummaryItem(name, new DecimalFormat("#.##").format(((Double) eval))));
        } catch (Exception e) {
            logger.error(formula);
            logger.error(e);
            this.summaryItems.add(new SummaryItem(name, new DecimalFormat("#.##").format(0)));
        }
    }
}
