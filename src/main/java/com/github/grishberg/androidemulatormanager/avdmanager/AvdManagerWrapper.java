package com.github.grishberg.androidemulatormanager.avdmanager;

import com.github.grishberg.androidemulatormanager.EmulatorConfig;
import com.github.grishberg.androidemulatormanager.PreferenceContext;
import com.github.grishberg.androidemulatormanager.utils.SysUtils;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for avdmanager.
 */
public abstract class AvdManagerWrapper {
    private final String pathToAvdManager;
    private final HardwareManager hardwareManager;
    private final Logger logger;

    AvdManagerWrapper(PreferenceContext context,
                      String pathToAvdManager,
                      HardwareManager hardwareManager,
                      Logger logger) {
        this.hardwareManager = hardwareManager;
        this.logger = logger;
        this.pathToAvdManager = context.getAndroidSdkPath() + pathToAvdManager;
    }

    public boolean createAvd(EmulatorConfig arg) throws InterruptedException, AvdManagerException {
        boolean isAvdCreated = false;
        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(buildCreateEmulatorCommand(arg));
            process = pb.start();
        } catch (IOException e) {
            throw new AvdManagerException("exception while creating emulator", e);
        }

        // Command may prompt us whether we want to further customise the AVD.
        // Just "press" Enter to continue with the selected target's defaults.
        try (PushbackInputStream in = new PushbackInputStream(process.getInputStream(), 10)) {
            boolean processAlive = true;

            // Block until the command outputs something (or process ends)
            int len = in.read();
            if (len == -1) {
                // Check whether the process has exited badly, as sometimes no output is valid.
                // e.g. When creating an AVD with Google APIs, no user input is requested.
                if (process.waitFor() != 0) {

                    throw new AvdManagerException(SysUtils.readStringFromInputString(process.getErrorStream()));
                }
                processAlive = false;
            }
            in.unread(len);

            // Write CRLF, if required
            if (processAlive) {
                final OutputStream stream = process.getOutputStream();
                stream.write('\r');
                stream.write('\n');
                stream.flush();
                stream.close();
            }

            // Wait for happy ending
            if (process.waitFor() == 0) {
                // Do a sanity check to ensure the AVD was really created
                isAvdCreated = SysUtils.getAvdConfig(arg.getName()).exists();
            }
        } catch (IOException e) {
            // read any errors from the attempted command
            String errorString = SysUtils.readStringFromInputString(process.getErrorStream());
            logger.error(errorString);
            throw new AvdManagerException("Exception while creating avd:" + errorString, e);
        } finally {
            process.destroy();
        }
        if (isAvdCreated) {
            hardwareManager.writeHardwareFile(arg);
        }
        return isAvdCreated;
    }

    public void deleteAvd(EmulatorConfig arg) throws AvdManagerException {
        try {
            SysUtils.executeWithArgsAndReturnOutput(logger, buildDeleteAvdCommand(arg));
        } catch (IOException e) {
            throw new AvdManagerException("exception while deleting avd", e);
        }
    }

    private List<String> buildCreateEmulatorCommand(EmulatorConfig arg) {
        ArrayList<String> params = new ArrayList<>();
        params.add(pathToAvdManager);
        params.add("-s");
        params.add("create");
        params.add("avd");
        params.add("-n");
        params.add(arg.getName());
        params.add("-k");
        params.add(buildSdkId(arg));
        return params;
    }

    private String buildSdkId(EmulatorConfig arg) {
        StringBuilder sb = new StringBuilder();
        sb.append("system-images;");
        sb.append("android-");
        sb.append(arg.getApiLevel());
        sb.append(";");
        sb.append("google_apis");
        if (arg.isWithPlaystore()) {
            sb.append("_playstore");
        }
        sb.append(";x86");
        return sb.toString();
    }

    private String[] buildDeleteAvdCommand(EmulatorConfig arg) {
        ArrayList<String> params = new ArrayList<>();
        params.add(pathToAvdManager);
        params.add("delete");
        params.add("avd");
        params.add("-n");
        params.add(arg.getName());
        return params.toArray(new String[params.size()]);
    }
}
