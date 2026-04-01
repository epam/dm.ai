// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class RallyIssueTest {

    private RallyIssue rallyIssue;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() {
        mockJsonObject = mock(JSONObject.class);
        rallyIssue = new RallyIssue(mockJsonObject);
    }

    @Test
    public void testGetLastStatusesUpdate() {
        rallyIssue.setLastStatusesUpdate("2023-10-01");
        assertEquals("2023-10-01", rallyIssue.getLastStatusesUpdate());
    }

    @Test
    public void testGetType() {
        when(mockJsonObject.getString(RallyFields._TYPE)).thenReturn("Defect");
        assertEquals("Defect", rallyIssue.getType());
    }

    @Test
    public void testGetRef() {
        when(mockJsonObject.getString(RallyFields._REF)).thenReturn("ref123");
        assertEquals("ref123", rallyIssue.getRef());
    }

    @Test
    public void testGetFormattedId() {
        when(mockJsonObject.getString(RallyFields.FORMATTED_ID)).thenReturn("US123");
        assertEquals("US123", rallyIssue.getFormattedId());
    }

    @Test
    public void testGetStatus() throws IOException {
        Status mockStatus = mock(Status.class);
        when(mockStatus.getName()).thenReturn("In Progress");
        RallyIssue spyRallyIssue = spy(rallyIssue);
        doReturn(mockStatus).when(spyRallyIssue).getStatusModel();
        assertEquals("In Progress", spyRallyIssue.getStatus());
    }

    @Test
    public void testGetIterationName() {
        Iteration mockIteration = mock(Iteration.class);
        when(mockIteration.getName()).thenReturn("Iteration 1");
        RallyIssue spyRallyIssue = spy(rallyIssue);
        doReturn(mockIteration).when(spyRallyIssue).getIteration();
        assertEquals("Iteration 1", spyRallyIssue.getIterationName());
    }


    @Test
    public void testGetPriorityAsEnum() throws IOException {
        RallyIssue spyRallyIssue = spy(rallyIssue);
        doReturn("High").when(spyRallyIssue).getPriority();
        assertEquals(ITicket.TicketPriority.High, spyRallyIssue.getPriorityAsEnum());
    }

    @Test
    public void testToText() {
        when(mockJsonObject.toString()).thenReturn("{\"key\":\"value\"}");
        assertEquals("{\"key\":\"value\"}", rallyIssue.toText());
    }

    @Test
    public void testGetTicketTitle() throws IOException {
        when(mockJsonObject.getString(RallyFields._REF_OBJECT_NAME)).thenReturn("Title");
        assertEquals("Title", rallyIssue.getTicketTitle());
    }

    @Test
    public void testGetTicketDescription() {
        when(mockJsonObject.getString(RallyFields.DESCRIPTION)).thenReturn("Description");
        assertEquals("Description", rallyIssue.getTicketDescription());
    }

    @Test
    public void testSetTicketDescription() {
        rallyIssue.setTicketDescription("New Description");
        verify(mockJsonObject).put(RallyFields.DESCRIPTION, "New Description");
    }

    @Test
    public void testGetCreated() {
        when(mockJsonObject.getString(RallyFields.CREATION_DATE)).thenReturn("2023-10-01T00:00:00.000Z");
        Date createdDate = rallyIssue.getCreated();
        assertNotNull(createdDate);
    }

    @Test
    public void testGetCreatedAsCalendar() {
        RallyIssue spyRallyIssue = spy(rallyIssue);
        Date mockDate = new Date();
        doReturn(mockDate).when(spyRallyIssue).getCreated();
        Calendar calendar = spyRallyIssue.getCreatedAsCalendar();
        assertEquals(mockDate.getTime(), calendar.getTimeInMillis());
    }

    @Test
    public void testGetUpdatedAsMillis() {
        when(mockJsonObject.getString(RallyFields.LAST_UPDATE_DATE)).thenReturn("2023-10-01T00:00:00.000Z");
        Long updatedMillis = rallyIssue.getUpdatedAsMillis();
        assertNotNull(updatedMillis);
    }

    @Test
    public void testGetTicketLabels() {
        JSONObject tagsObject = new JSONObject();
        JSONArray tagsArray = new JSONArray();
        tagsArray.put(new JSONObject().put("Name", "Label1"));
        tagsObject.put("_tagsNameArray", tagsArray);
        when(mockJsonObject.getJSONObject(RallyFields.TAGS)).thenReturn(tagsObject);
        JSONArray labels = rallyIssue.getTicketLabels();
        assertEquals(1, labels.length());
        assertEquals("Label1", labels.getString(0));
    }

    @Test
    public void testGetTagsRefs() {
        JSONObject tagsObject = new JSONObject();
        JSONArray tagsArray = new JSONArray();
        tagsArray.put(new JSONObject().put("Name", "Tag1"));
        tagsObject.put("_tagsNameArray", tagsArray);
        when(mockJsonObject.getJSONObject(RallyFields.TAGS)).thenReturn(tagsObject);
        JSONArray tagsRefs = rallyIssue.getTagsRefs();
        assertEquals(1, tagsRefs.length());
    }

    @Test
    public void testGetTagsRefsWithoutTag() {
        JSONObject tagsObject = new JSONObject();
        JSONArray tagsArray = new JSONArray();
        tagsArray.put(new JSONObject().put("Name", "Tag1"));
        tagsArray.put(new JSONObject().put("Name", "Tag2"));
        tagsObject.put("_tagsNameArray", tagsArray);
        when(mockJsonObject.getJSONObject(RallyFields.TAGS)).thenReturn(tagsObject);
        JSONArray tagsRefs = rallyIssue.getTagsRefsWithoutTag("Tag1");
        assertEquals(1, tagsRefs.length());
    }

    @Test
    public void testGetAttachmentsRefs() {
        JSONObject attachmentsObject = new JSONObject();
        attachmentsObject.put(RallyFields._REF, "ref123");
        when(mockJsonObject.getJSONObject("Attachments")).thenReturn(attachmentsObject);
        assertEquals("ref123", rallyIssue.getAttachmentsRefs());
    }

    @Test
    public void testGetWeight() {
        when(mockJsonObject.getDouble(RallyFields.PLAN_ESTIMATE)).thenReturn(5.0);
        assertEquals(5.0, rallyIssue.getWeight(), 0.0);
    }

    @Test
    public void testGetProjectName() {
        JSONObject projectObject = new JSONObject();
        projectObject.put("Name", "Project1");
        when(mockJsonObject.getJSONObject(RallyFields.PROJECT)).thenReturn(projectObject);
        assertEquals("Project1", rallyIssue.getProjectName());
    }
}