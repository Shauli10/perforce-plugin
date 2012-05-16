/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.util.StreamTaskListener;
import java.io.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author rpetti
 */
public class QuickCleaner {

    private Launcher hudsonLauncher;
    private String[] env;
    private FilePath filePath;
    private String p4exe;
    private FileFilter filter;

    QuickCleaner(String p4exe, Launcher hudsonLauncher, Depot depot, FilePath filePath, FileFilter filter) {
        this.hudsonLauncher = hudsonLauncher;
        this.env = getEnvFromDepot(depot, filePath.getRemote());
        this.filePath = filePath;
        this.p4exe = p4exe;
        this.filter = filter;
    }

    public void exec() throws PerforceException {
        try {
            // ensure we actually have a valid hudson launcher
            if (null == hudsonLauncher) {
                hudsonLauncher = Hudson.getInstance().createLauncher(new StreamTaskListener(System.out));
            }
            TaskListener listener = hudsonLauncher.getListener();

            // hudsonOut->p4in->reader
            FastPipedOutputStream hudsonOut = new FastPipedOutputStream();
            FastPipedInputStream p4in = new FastPipedInputStream(hudsonOut);
            //input = p4in;

            final OutputStream out = hudsonOut == null ? null : new RemoteOutputStream(hudsonOut);

            QuickCleanerCall remoteCall = new QuickCleanerCall(p4exe, env, out, filePath.getRemote(), listener, filter);
            LogPrinter logPrinter = new LogPrinter(listener.getLogger(), p4in);
            logPrinter.start();
            filePath.act(remoteCall);
            logPrinter.join();

        } catch (Exception e) {
            throw new PerforceException("Could not run quick clean.", e);
        }
    }

    private class LogPrinter extends Thread {

        private PrintStream log;
        private InputStream input;

        LogPrinter(PrintStream log, InputStream input) {
            this.log = log;
            this.input = input;
        }

        @Override
        public void run() {
            try {
                IOUtils.copy(input, log);
            } catch (IOException ex) {
                Logger.getLogger(QuickCleaner.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static String[] getEnvFromDepot(Depot depot, String workDir) {
        String[] keys = {
            "P4USER",
            "P4PASSWD",
            "P4PORT",
            "P4COMMANDCHARSET",
            "P4CHARSET",
            "P4CLIENT",
            "PATH",
            "SystemDrive",
            "SystemRoot"
        };

        ArrayList<String> result = new ArrayList<String>();
        for (int i = 0; i < keys.length; i++) {
            String value = depot.getProperty(keys[i]);
            if (value != null && !value.trim().isEmpty()) {
                result.add(keys[i] + "=" + value);
            }
        }
        try {
            result.add("PWD=" + new File(workDir).getCanonicalPath());
        } catch (IOException ex) {
            Logger.getLogger(QuickCleaner.class.getName()).log(Level.SEVERE, null, ex);
        }
        result.add("P4CONFIG=");
        return result.toArray(new String[result.size()]);
    }
}
