package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.HtmlCleaner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TextInputPrompt {

    private final String basePath;
    private ToText input;
    public List<ITicket> getExtraTickets() {
        return extraTickets;
    }

    public void setExtraTickets(List<ITicket> extraTickets) {
        this.extraTickets = extraTickets;
    }

    private List<ITicket> extraTickets = new ArrayList<>();

    public TextInputPrompt(String basePath, ToText input) {
        this.basePath = basePath;
        this.input = new TextWrapper(input);
    }

    public ToText getInput() {
        return input;
    }

    public void setInput(ToText input) {
        this.input = new TextWrapper(input);
    }

    public class TextWrapper implements ToText {

        private final ToText input;

        public TextWrapper(ToText toText) {
            this.input = toText;
        }

        @Override
        public String toText() throws IOException {
            String text = input.toText();
            return HtmlCleaner.cleanAllHtmlTags(basePath, text);
        }
    }
}
