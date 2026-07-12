// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.vacation;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VacationsCoverageTest {

    @Test
    public void testGetInstanceReturnsSameSingleton() {
        assertSame(Vacations.getInstance(), Vacations.getInstance());
    }

    @Test
    public void testConvertInputStreamToStringSmallContent() throws Exception {
        String content = "hello vacations";
        String result = Vacations.convertInputStreamToString(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertEquals(content, result);
    }

    @Test
    public void testConvertInputStreamToStringLargerThanBuffer() throws Exception {
        byte[] data = new byte[Vacations.DEFAULT_BUFFER_SIZE * 2 + 100];
        Arrays.fill(data, (byte) 'a');
        String result = Vacations.convertInputStreamToString(new ByteArrayInputStream(data));
        assertEquals(data.length, result.length());
        assertTrue(result.startsWith("aaa"));
    }

    @Test
    public void testParseVacationsFromXLSXWithoutResourcesReturnsEmpty() throws Exception {
        Object saved = setVacationsField(null);
        try {
            List<Vacation> result = Vacations.getInstance().parseVacationsFromXLSX();
            assertTrue(result.isEmpty());
        } finally {
            setVacationsField(saved);
        }
    }

    @Test
    public void testParseVacationsFromJSONWithoutResourcesReturnsEmpty() throws Exception {
        Object saved = setVacationsField(null);
        try {
            List<Vacation> result = Vacations.getInstance().parseVacationsFromJSON();
            assertTrue(result.isEmpty());
        } finally {
            setVacationsField(saved);
        }
    }

    @Test
    public void testGetVacationsFilteringWithInjectedData() throws Exception {
        JSONArray injected = new JSONArray();
        injected.put(vacationJson("John Doe"));
        injected.put(vacationJson("Jane Smith"));
        injected.put(vacationJson("Bob Brown"));
        Object saved = setVacationsField(injected);
        try {
            Vacations vacations = Vacations.getInstance();

            List<Vacation> all = vacations.getVacations(null);
            assertEquals(3, all.size());

            List<Vacation> filtered = vacations.getVacations(Arrays.asList("john doe", "JANE SMITH"));
            assertEquals(2, filtered.size());
            assertEquals("John Doe", filtered.get(0).getName());
            assertEquals("Jane Smith", filtered.get(1).getName());

            List<Vacation> none = vacations.getVacations(Collections.singletonList("Unknown Person"));
            assertTrue(none.isEmpty());
        } finally {
            setVacationsField(saved);
        }
    }

    @Test
    public void testParseVacationsFromXLSXWithGeneratedWorkbooks() throws Exception {
        Path dir = Files.createTempDirectory("vacations-xlsx");
        writeVacationsXlsx(dir.resolve("vacations.xlsx"));
        writeGlobalCalendarXlsx(dir.resolve("global_calendar.xlsx"));

        try (URLClassLoader loader = newChildClassLoader(dir)) {
            Object instance = getIsolatedInstance(loader);

            List<?> vacations = (List<?>) invoke(instance, "parseVacationsFromXLSX");
            assertEquals(2, vacations.size());
            List<String> names = new ArrayList<>();
            for (Object v : vacations) {
                names.add((String) invoke(v, "getName"));
            }
            assertTrue(names.contains("John Doe"));
            assertTrue(names.contains("Jane Smith"));

            // second call uses the cached JSONArray (vacations != null branch)
            List<?> cached = (List<?>) invoke(instance, "parseVacationsFromXLSX");
            assertEquals(2, cached.size());

            // filtering on parsed data: people == null keeps everything
            List<?> all = (List<?>) invoke(instance, "getVacations", new Class[]{List.class}, (Object) null);
            assertEquals(2, all.size());

            // case-insensitive match keeps only John Doe
            List<?> filtered = (List<?>) invoke(instance, "getVacations", new Class[]{List.class},
                    Collections.singletonList("john doe"));
            assertEquals(1, filtered.size());
            assertEquals("John Doe", invoke(filtered.get(0), "getName"));

            // no match removes every entry
            List<?> none = (List<?>) invoke(instance, "getVacations", new Class[]{List.class},
                    Collections.singletonList("Nobody"));
            assertTrue(none.isEmpty());
        }
    }

    @Test
    public void testParseVacationsFromJSONWithGeneratedResource() throws Exception {
        Path dir = Files.createTempDirectory("vacations-json");
        Files.write(dir.resolve("vacations.json"),
                "[{\"EMPLOYEE\":\"Json Person\",\"START DATE\":\"01/01/24\",\"END DATE\":\"01/05/24\",\"DURATION\":\"4.0\"}]"
                        .getBytes(StandardCharsets.UTF_8));

        try (URLClassLoader loader = newChildClassLoader(dir)) {
            Object instance = getIsolatedInstance(loader);
            List<?> vacations = (List<?>) invoke(instance, "parseVacationsFromJSON");
            assertEquals(1, vacations.size());
            assertEquals("Json Person", invoke(vacations.get(0), "getName"));
            assertEquals("01/01/24", invoke(vacations.get(0), "getStartDate"));
            assertEquals(4.0, (Double) invoke(vacations.get(0), "getDuration"), 0.0);

            // second call uses the cached JSONArray
            List<?> cached = (List<?>) invoke(instance, "parseVacationsFromJSON");
            assertEquals(1, cached.size());
        }
    }

    private static JSONObject vacationJson(String name) {
        JSONObject json = new JSONObject();
        json.put(Vacation.EMPLOYEE, name);
        json.put(Vacation.START_DATE, "01/01/24");
        json.put(Vacation.END_DATE, "01/02/24");
        json.put(Vacation.DURATION, "1.0");
        return json;
    }

    private static Object setVacationsField(Object value) throws Exception {
        Field field = Vacations.class.getDeclaredField("vacations");
        field.setAccessible(true);
        Object saved = field.get(Vacations.getInstance());
        field.set(Vacations.getInstance(), value);
        return saved;
    }

    private static void writeVacationsXlsx(Path path) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("vacations");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue(Vacation.EMPLOYEE);
            header.createCell(1).setCellValue(Vacation.START_DATE);
            header.createCell(2).setCellValue(Vacation.END_DATE);
            header.createCell(3).setCellValue(Vacation.DURATION);
            header.createCell(4).setCellValue(42.0);   // non-string cell -> default switch branch
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("John Doe");
            row.createCell(1).setCellValue(new Date());
            row.createCell(2).setCellValue(new Date());
            row.createCell(3).setCellValue(5.0);
            Row emptyName = sheet.createRow(2);
            emptyName.createCell(0).setCellValue("");  // blank employee -> row skipped
            try (OutputStream os = Files.newOutputStream(path)) {
                workbook.write(os);
            }
        }
    }

    private static void writeGlobalCalendarXlsx(Path path) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("calendar");
            Row dates = sheet.createRow(0);
            dates.createCell(0).setCellValue("Name");
            for (int i = 1; i <= 6; i++) {
                dates.createCell(i).setCellValue(new Date());
            }
            Row days = sheet.createRow(1);
            days.createCell(0).setCellValue("Day");
            days.createCell(1).setCellValue("Mon");
            days.createCell(2).setCellValue("Sat");
            days.createCell(3).setCellValue("Sun");
            days.createCell(4).setCellValue("Tue");
            days.createCell(5).setCellValue("Wed");
            days.createCell(6).setCellValue("Sat");
            CellStyle noHolidayStyle = workbook.createCellStyle();
            noHolidayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            noHolidayStyle.setFillForegroundColor((short) 0);
            Row person = sheet.createRow(2);
            person.createCell(0).setCellValue("Jane Smith");
            Cell holiday = person.createCell(1);
            holiday.setCellValue("X");                 // weekday, zero fill -> public holiday
            holiday.setCellStyle(noHolidayStyle);
            person.createCell(2).setCellValue("VAC");  // vacation marker -> skipped
            Cell weekend = person.createCell(3);
            weekend.setCellValue("Y");                 // weekend -> skipped
            weekend.setCellStyle(noHolidayStyle);
            Cell filled = person.createCell(4);
            filled.setCellValue("Z");                  // filled cell -> not a holiday
            CellStyle style = workbook.createCellStyle();
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setFillForegroundColor((short) 9);
            filled.setCellStyle(style);
            person.createCell(5).setCellValue("Vacation"); // long vacation marker -> skipped
            Cell saturday = person.createCell(6);
            saturday.setCellValue("W");                // Saturday -> skipped
            saturday.setCellStyle(noHolidayStyle);
            sheet.createRow(3);                        // empty row -> null cells skipped
            try (OutputStream os = Files.newOutputStream(path)) {
                workbook.write(os);
            }
        }
    }

    private static URLClassLoader newChildClassLoader(Path resourceDir) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(resourceDir.toUri().toURL());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        // platform class loader as parent so Vacations and its dependencies
        // are defined by this loader and pick up the generated resources
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    private static Object getIsolatedInstance(URLClassLoader loader) throws Exception {
        Class<?> clazz = loader.loadClass("com.github.istin.dmtools.vacation.Vacations");
        return clazz.getMethod("getInstance").invoke(null);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        return invoke(target, methodName, new Class[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }
}
