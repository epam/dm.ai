package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.sm.Change;

import java.util.List;

public class ScrumDailyPrompt {

    private final String userName;
    private List<Change> listOfChanges;

    public ScrumDailyPrompt(String userName, List<Change> listOfChanges) {
        this.userName = userName;
        this.listOfChanges = listOfChanges;
    }

    public String getUserName() {
        return userName;
    }

    public List<Change> getListOfChanges() {
        return listOfChanges;
    }
}
