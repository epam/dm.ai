// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

public interface IHistoryItem {

    String getField();

    String getFromAsString();

    String getToAsString();

    class NewTicketCreation implements IHistoryItem {

        @Override
        public String getField() {
            return "NewTicketCreation";
        }

        @Override
        public String getFromAsString() {
            return "";
        }

        @Override
        public String getToAsString() {
            return "";
        }

    }
    class Impl implements IHistoryItem {
        private String field;
        private String from;
        private String to;

        public Impl(String field, String from, String to) {
            this.field = field;
            this.from = from;
            this.to = to;
        }

        @Override
        public String getField() {
            return field;
        }

        @Override
        public String getFromAsString() {
            return from;
        }

        @Override
        public String getToAsString() {
            return to;
        }
    }
}
