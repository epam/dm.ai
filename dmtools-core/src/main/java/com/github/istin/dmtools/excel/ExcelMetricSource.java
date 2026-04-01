// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.excel;

import com.github.istin.dmtools.metrics.source.CommonSourceCollector;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.Employees;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExcelMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(ExcelMetricSource.class);

    private final String fileName;
    private final String whoColumn;
    private final String whenColumn;
    private final String weightColumn;
    private final double weightMultiplier;

    public ExcelMetricSource(IEmployees employees, String fileName, String whoColumn, String whenColumn, String weightColumn, double weightMultiplier) {
        super(employees);
        this.fileName = fileName;
        this.whoColumn = whoColumn;
        this.whenColumn = whenColumn;
        this.weightColumn = weightColumn;
        this.weightMultiplier = weightMultiplier;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
            List<KeyTime> keyTimes = new ArrayList<>();
            InputStream input = null;
            try {
                input = getClass().getResourceAsStream(fileName);
                if (input != null) {
                    XSSFWorkbook workbook = new XSSFWorkbook(input);
                    XSSFSheet vacationsSheet = workbook.getSheetAt(0);
                    Iterator<Row> rowIterator = vacationsSheet.iterator();
                    int employeeIndex = -1;
                    int whenDateIndex = -1;
                    int weightIndex = -1;
                    if (rowIterator.hasNext()) {
                        Row headerRow = rowIterator.next();
                        Iterator<Cell> cellIterator = headerRow.cellIterator();
                        int index = 0;
                        while (cellIterator.hasNext()) {
                            Cell cell = cellIterator.next();
                            if (cell.getCellType() == CellType.STRING) {
                                String cellValue = cell.getStringCellValue();
                                if (cellValue.equalsIgnoreCase(whoColumn)) {
                                    employeeIndex = index;
                                } else if (cellValue.equalsIgnoreCase(whenColumn)) {
                                    whenDateIndex = index;
                                } else if (cellValue.equalsIgnoreCase(weightColumn)) {
                                    weightIndex = index;
                                }
                            }
                            index++;
                        }
                    }

                    if (employeeIndex != -1 && whenDateIndex != -1 && weightIndex != -1) {
                        int index = 0;
                        while (rowIterator.hasNext()) {
                            Row row = rowIterator.next();
                            Cell employeeCell = row.getCell(employeeIndex);
                            if (employeeCell != null && employeeCell.getCellType() == CellType.STRING) {
                                String employeeName;
                                if (isPersonalized) {
                                    employeeName = employeeCell.getStringCellValue();
                                    employeeName = getEmployees().transformName(employeeName);
                                    if (getEmployees() != null) {
                                        if (!getEmployees().contains(employeeName)) {
                                            employeeName = Employees.UNKNOWN;
                                        }
                                    }
                                } else {
                                    employeeName = metricName;
                                }

                                Date startDate = row.getCell(whenDateIndex).getDateCellValue();
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(startDate);
                                Cell cell = row.getCell(weightIndex);

                                if (cell != null) {
                                    double weight = 0.0; // Default value in case cell type is not NUMERIC or BOOLEAN
                                    switch (cell.getCellType()) {
                                        case NUMERIC:
                                            weight = cell.getNumericCellValue();
                                            break;
                                        case BOOLEAN:
                                            weight = cell.getBooleanCellValue() ? 1.0 : 0.0;
                                            break;
                                        default:
                                            // Handle other types, if necessary, or log a warning
                                            logger.info("Warning: Cell type is not NUMERIC or BOOLEAN. Defaulting to weight 0.0.");
                                            break;
                                    }

                                    KeyTime keyTime = new KeyTime(fileName + "_" + index, cal, employeeName);
                                    keyTime.setWeight(weight * weightMultiplier);
                                    keyTimes.add(keyTime);
                                } else {
                                    // Handle null cell, if necessary
                                    logger.debug("Warning: Cell is null. Skipping weight assignment.");
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Excel file not found or bad format", e);
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return keyTimes;
        }

}