// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.vacation;

import com.github.istin.dmtools.common.model.JSONModel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class Vacations {

    private JSONArray vacations;

    private static Vacations instance;


    private Vacations() {

    }

    public static synchronized Vacations getInstance() {
        if (instance == null) {
            instance = new Vacations();
        }
        return instance;
    }

    public List<Vacation> getVacations(List<String> people) {
        List<Vacation> vacationList = parseVacationsFromXLSX();
        if (people != null) {
            ListIterator<Vacation> iter = vacationList.listIterator();
            while(iter.hasNext()){
                Vacation vacation = iter.next();
                boolean found = false;
                for (String employee : people) {
                    if (vacation.getName().equalsIgnoreCase(employee)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    iter.remove();
                }
            }
        }
        return vacationList;
    }

    @NotNull
    public List<Vacation> parseVacationsFromJSON() {
        if (vacations == null) {
            InputStream input = null;
            try {
                input = getClass().getResourceAsStream("/vacations.json");
                if (input != null) {
                    String source = convertInputStreamToString(input);
                    vacations = new JSONArray(source);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Property file not found");
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return JSONModel.convertToModels(Vacation.class, vacations);
    }

    @NotNull
    public List<Vacation> parseVacationsFromXLSX() {
        if (vacations == null) {
            vacations = new JSONArray();
            parseVacations();
            parseGlobalCalendar();
        }
        List<Vacation> vacationList = null;
        vacationList = JSONModel.convertToModels(Vacation.class, vacations);
        return vacationList;
    }

    private void parseVacations() {
        InputStream input = null;
        try {
            input = getClass().getResourceAsStream("/vacations.xlsx");
            if (input != null) {
                XSSFWorkbook workbook = new XSSFWorkbook(input);
                XSSFSheet vacationsSheet = workbook.getSheetAt(0);
                Iterator<Row> rowIterator = vacationsSheet.iterator();
                int employeeIndex = -1;
                int startDateIndex = -1;
                int endDateIndex = -1;
                int durationIndex = -1;
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    if (employeeIndex == -1) {
                        Iterator<Cell> cellIterator = row.cellIterator();
                        int index = 0;
                        while (cellIterator.hasNext()) {
                            Cell cell = cellIterator.next();
                            switch (cell.getCellType()) {
                                case STRING:
                                    String stringCellValue = cell.getStringCellValue();
                                    if (stringCellValue.equalsIgnoreCase(Vacation.EMPLOYEE)) {
                                        employeeIndex = index;
                                    } else if (stringCellValue.equalsIgnoreCase(Vacation.START_DATE)) {
                                        startDateIndex = index;
                                    } else if (stringCellValue.equalsIgnoreCase(Vacation.END_DATE)) {
                                        endDateIndex = index;
                                    } else if (stringCellValue.equalsIgnoreCase(Vacation.DURATION)) {
                                        durationIndex = index;
                                    }
                                    break;
                                default:
                            }
                            index++;
                        }
                    } else {
                        String employeeName = row.getCell(employeeIndex).getStringCellValue();
                        if (employeeName != null && employeeName.length() > 0) {
                            Vacation vacation = new Vacation();
                            vacation.set(Vacation.EMPLOYEE, employeeName);
                            vacation.set(Vacation.START_DATE, Vacation.DEFAULT_FORMATTER.format(row.getCell(startDateIndex).getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()));
                            vacation.set(Vacation.END_DATE, Vacation.DEFAULT_FORMATTER.format(row.getCell(endDateIndex).getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()));
                            vacation.set(Vacation.DURATION, String.valueOf(row.getCell(durationIndex).getNumericCellValue()));
                            vacations.put(vacation.getJSONObject());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Property file not found");
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseGlobalCalendar() {
        InputStream input = null;
        try {
            input = getClass().getResourceAsStream("/global_calendar.xlsx");
            if (input != null) {

                XSSFWorkbook workbook = new XSSFWorkbook(input);
                XSSFSheet vacationsSheet = workbook.getSheetAt(0);
                Iterator<Row> rowIterator = vacationsSheet.iterator();
                int rowIndex = 0;
                int countOfCells = -1;
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    if (rowIndex < 2) {
                        if (countOfCells == -1) {
                            countOfCells = row.getPhysicalNumberOfCells();
                        }
                        rowIndex++;
                        continue;
                    }
                    String employeeName = null;
//                    int physicalNumberOfCells = row.getPhysicalNumberOfCells();
//                    int physicalNumberOfCells = 244;
                    for (int i = 0; i < countOfCells; i++ ){
                        Cell cell = row.getCell(i);
                        if (cell == null) {
                            continue;
                        }

                        if (employeeName == null) {
                            employeeName = cell.getStringCellValue();
                        } else {
                            String cellValue = cell.getStringCellValue();
                            if (!cellValue.equalsIgnoreCase("VAC") && !cellValue.equalsIgnoreCase("Vacation")) {
                                CellStyle cellStyle = cell.getCellStyle();

                                short fillForegroundColor = cellStyle.getFillForegroundColor();
                                short fillBackgroundColor = cellStyle.getFillBackgroundColor();
                                Color fillBackgroundColorColor = cellStyle.getFillBackgroundColorColor();
                                Color fillForegroundColorColor = cellStyle.getFillForegroundColorColor();
//                                XSSFColor fillBackgroundXSSFColor = cellStyle.getFillBackgroundXSSFColor();
//                                XSSFColor fillForegroundXSSFColor = cellStyle.getFillForegroundXSSFColor();
                                String cellInfo = printCellStyle(cell, cellStyle);
                                XSSFCell dayCell = vacationsSheet.getRow(1).getCell(i);
                                String stringCellValue = dayCell.getStringCellValue();
                                if (fillForegroundColor == 0 && !stringCellValue.equalsIgnoreCase("sat") && !stringCellValue.equalsIgnoreCase("sun")) {
                                    String startEndDate = Vacation.DEFAULT_FORMATTER.format(vacationsSheet.getRow(0).getCell(i).getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                                    Vacation vacation = new Vacation();
                                    vacation.set(Vacation.EMPLOYEE, employeeName);
                                    vacation.set(Vacation.START_DATE, startEndDate);
                                    vacation.set(Vacation.END_DATE, startEndDate);
                                    vacation.set(Vacation.DURATION, String.valueOf(1));
                                    vacations.put(vacation.getJSONObject());
                                }
                            }
                        }

                    }
                    rowIndex++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Property file not found");
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String printCellStyle(Cell cell, CellStyle cellStyle) {
        return cell.getCellType()
                + " " + cellStyle.getFillPattern()
                + " " + cellStyle.getFillBackgroundColor()
                + " " + cellStyle.getAlignment()
                + " " + cellStyle.getFillBackgroundColorColor()
                + " " + cellStyle.getBorderBottom()
                + " " + cellStyle.getFillForegroundColor()
                + " " + cellStyle.getFillForegroundColorColor()
                + " " + cellStyle.getFontIndex()
                + " " + cellStyle.getIndention()
                + " " + cellStyle.getBottomBorderColor()
                ;
    }

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    protected static String convertInputStreamToString(InputStream is) throws IOException {

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }


        return result.toString("UTF-8");


    }
}
