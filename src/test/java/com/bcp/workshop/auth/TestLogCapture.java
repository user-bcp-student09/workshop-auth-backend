package com.bcp.workshop.auth;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

class TestLogCapture implements AutoCloseable {
    private final StringBuffer messages = new StringBuffer();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private final PrintStream previousError = System.err;
    private final PrintStream capturedError = new PrintStream(stderr, true, StandardCharsets.UTF_8);
    private final Logger root = Logger.getLogger("");
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            messages.append(new SimpleFormatter().formatMessage(record)).append('\n');
            if (record.getThrown() != null) {
                StringWriter trace = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(trace));
                messages.append(trace);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    TestLogCapture() {
        root.addHandler(handler);
        System.setErr(capturedError);
    }

    String text() {
        capturedError.flush();
        return messages + stderr.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        root.removeHandler(handler);
        System.setErr(previousError);
        capturedError.close();
    }
}