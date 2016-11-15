package org.broadinstitute.hellbender.utils.runtime;


import org.broadinstitute.hellbender.utils.Utils;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

public final class ProcessSettings {
    private String[] command;
    private Map<String, String> environment;
    private File directory;
    private boolean redirectErrorStream;
    private final InputStreamSettings stdinSettings;
    private final OutputStreamSettings stdoutSettings;
    private final OutputStreamSettings stderrSettings;

    /**
     * @param command Command line to run.
     */
    public ProcessSettings(final String[] command) {
        this(command, false, null, null, null, null, null);
    }

    /**
     * @param command             Command line to run.
     * @param redirectErrorStream true if stderr should be sent to stdout.
     * @param environment         Environment settings to override System.getEnv, or null to use System.getEnv.
     * @param directory           The directory to run the command in, or null to run in the current directory.
     * @param stdinSettings       Settings for writing to the process stdin.
     * @param stdoutSettings      Settings for capturing the process stdout.
     * @param stderrSettings      Setting for capturing the process stderr.
     */
    public ProcessSettings(final String[] command, final boolean redirectErrorStream, final File directory, final Map<String, String> environment,
                           final InputStreamSettings stdinSettings, final OutputStreamSettings stdoutSettings, final OutputStreamSettings stderrSettings) {
        this.command = checkCommand(command);
        this.redirectErrorStream = redirectErrorStream;
        this.directory = directory;
        this.environment = environment;
        this.stdinSettings = checkSettings(stdinSettings);
        this.stdoutSettings = checkSettings(stdoutSettings);
        this.stderrSettings = checkSettings(stderrSettings);
    }

    public String[] getCommand() {
        return command;
    }

    public void setCommand(final String[] command) {
        this.command = checkCommand(command);
    }

    public boolean isRedirectErrorStream() {
        return redirectErrorStream;
    }

    public void setRedirectErrorStream(final boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
    }

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(final File directory) {
        this.directory = directory;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(final Map<String, String> environment) {
        this.environment = environment;
    }

    public InputStreamSettings getStdinSettings() {
        return stdinSettings;
    }

    public OutputStreamSettings getStdoutSettings() {
        return stdoutSettings;
    }

    public OutputStreamSettings getStderrSettings() {
        return stderrSettings;
    }

    protected String[] checkCommand(final String[] command) {
        Utils.nonNull(command);
        Utils.containsNoNull(Arrays.asList(command), "Command is not allowed to contain nulls");
        return command;
    }

    protected InputStreamSettings checkSettings(final InputStreamSettings settings) {
        return settings == null ? new InputStreamSettings() : settings;
    }

    protected OutputStreamSettings checkSettings(final OutputStreamSettings settings) {
        return settings == null ? new OutputStreamSettings() : settings;
    }
}
