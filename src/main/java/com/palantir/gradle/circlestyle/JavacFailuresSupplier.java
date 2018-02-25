package com.palantir.gradle.circlestyle;

import static java.lang.Integer.parseInt;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.compile.JavaCompile;
import org.inferred.freebuilder.shaded.com.google.common.collect.ImmutableList;

import com.google.common.annotations.VisibleForTesting;

class JavacFailuresSupplier implements FailuresSupplier {

    public static JavacFailuresSupplier create(final JavaCompile javac) {
        // Capture standard output
        final StringBuilder errorStream = new StringBuilder();
        StandardOutputListener listener = new StandardOutputListener() {
            @Override
            public void onOutput(CharSequence output) {
                errorStream.append(output);
            }
        };
        javac.getLogging().addStandardErrorListener(listener);

        // Configure the finalizer task
        return new JavacFailuresSupplier(errorStream);
    }

    private static final Pattern ERROR_LINE = Pattern.compile("([^ ].*):(\\d+): error: (.*)");

    private final StringBuilder errorStream;

    @VisibleForTesting JavacFailuresSupplier(StringBuilder errorStream) {
        this.errorStream = errorStream;
    }

    @Override
    public List<Failure> getFailures() throws IOException {
        ImmutableList.Builder<Failure> failures = ImmutableList.builder();
        Failure.Builder failureBuilder = null;
        StringBuilder details = null;
        for (String line : errorStream.toString().split("\n")) {
            if (failureBuilder != null) {
                if (line.startsWith(" ")) {
                    details.append("\n").append(line);
                    continue;
                } else {
                    failures.add(failureBuilder.details(details.toString()).build());
                    failureBuilder = null;
                    details = null;
                }
            }
            Matcher matcher = ERROR_LINE.matcher(line);
            if (matcher.matches()) {
                failureBuilder = new Failure.Builder()
                        .file(new File(matcher.group(1)))
                        .line(parseInt(matcher.group(2)))
                        .severity("ERROR")
                        .message(matcher.group(3));
                details = new StringBuilder();
            }
        }
        if (failureBuilder != null) {
            failures.add(failureBuilder.details(details.toString()).build());
        }
        return failures.build();
    }
}