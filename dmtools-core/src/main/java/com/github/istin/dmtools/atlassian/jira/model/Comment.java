package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;

public class Comment extends JSONModel implements IComment, ToText {

    private static final String ID = "id";
    private static final String AUTHOR = "author";
    public static final String BODY = "body";


    public Comment() {
    }

    public Comment(String json) throws JSONException {
        super(json);
    }

    public Comment(JSONObject json) {
        super(json);
    }


    @Override
    public String getId() {
        return getString(ID);
    }

    @Override
    public Date getCreated() {
        return DateUtils.parseJiraDate2(getString("created"));
    }

    @Override
    public String getBody() {
        return getString(BODY);
    }

    @Override
    public IUser getAuthor() {
        return getModel(Assignee.class, AUTHOR);
    }


    @Override
    public String toText() throws IOException {
        String fullName = getAuthor().getFullName();
        String emailAddress = getAuthor().getEmailAddress();
        return "author: " + fullName + " " + emailAddress + "\nCreated:" + getString("created") +"\n-\n"+getBody()+"\n-\n";
    }
}