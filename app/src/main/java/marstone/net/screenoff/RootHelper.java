package marstone.net.screenoff;


import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeoutException;


/**
 * Created by marstone on 2018/8/14.
 */
public class RootHelper {


    private static final String TAG = "RootHelper";

    private static final String ECHO_EXIT_MAGIC = "-BoeFFla_X7+3";

    private String su = "su";
    private boolean running = false;
    private int state = 0;
    private int maxWait = 20000;
    private Process process = null;
    private DataOutputStream dataOutputStream = null;
    private InputStreamWorker inputStreamWorker = null;
    private ErrorStreamWorker errorStreamWorker = null;
    private StringBuilder output = new StringBuilder();


    boolean isRooted() {
        return isRooted(60000);
    }

    boolean isRooted(int timeout) {
        if (!waitDone()) {
            return false;
        }
        while (!execSuperUser()) {
            return false;
        }
        return sudo("id", timeout).contains("uid=0");
    }


    private String execAndExit(String command, int timeout) {
        if (!execSuperUser()) {
            this.state = 2;
            return "";
        }
        if (command.length() == 0) {
            return "";
        }
        String cmd = command;
        if (command.charAt(command.length() - 1) != ';') {
            cmd = command + ";";
        }
        this.running = true;
        this.state = 0;
        this.output.setLength(0);
        try {
            this.dataOutputStream.write((cmd + "\necho " + ECHO_EXIT_MAGIC + "\n").getBytes("UTF-8"));
            this.dataOutputStream.flush();
            long then = System.currentTimeMillis();
            do {
                boolean bool = this.running;
                if (!bool) {
                    return this.output.toString();
                }
                if (System.currentTimeMillis() > then + timeout) {
                    this.state = 1;
                    exit();
                    this.running = false;
                    return "";
                }
            } while (isExited());
            this.state = 2;
            this.running = false;
            return "";
        } catch (Exception ex) {
            ex.printStackTrace();
            this.state = 2;
            this.running = false;
        }
        return "";
    }

    private void sleep(int interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException ignored) {
        }
    }


    private String sudo(String command, int timeout) {
        if (!execSuperUser()) {
            this.state = 2;
            return "";
        }
        this.state = 0;
        this.output.setLength(0);
        this.running = true;
        try {
            this.dataOutputStream.write((command + "\n").getBytes("UTF-8"));
            this.dataOutputStream.flush();
            long then = System.currentTimeMillis();
            do {
                int len = this.output.length();
                if (len != 0) {
                    this.running = false;
                    return this.output.toString();
                }
                if (System.currentTimeMillis() > then + timeout) {
                    this.state = 1;
                    exit();
                    this.running = false;
                    return "";
                }
            } while (isExited());
            this.state = 2;
            this.running = false;
            return "";
        } catch (Exception ex) {
            ex.printStackTrace();
            this.state = 2;
            this.running = false;
        }
        return "";
    }

    private boolean execSuperUser() {
        if (isExited()) {
            return true;
        }
        try {
            this.process = Runtime.getRuntime().exec(this.su);
            this.dataOutputStream = new DataOutputStream(this.process.getOutputStream());
            this.inputStreamWorker = new InputStreamWorker(this.process.getInputStream());
            this.errorStreamWorker = new ErrorStreamWorker(this.process.getErrorStream());
            this.inputStreamWorker.start();
            this.errorStreamWorker.start();
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            this.process = null;
            this.dataOutputStream = null;
            this.inputStreamWorker = null;
            this.errorStreamWorker = null;
        }
        return false;
    }

    private boolean isExited() {
        if (this.process == null) {
            return false;
        }
        try {
            int result = this.process.exitValue();
            return false;
        } catch (IllegalThreadStateException ignored) {
            Log.d(TAG, "waiting process exiting...");
        }
        return true;
    }

    private void exit() {
        try {
            this.dataOutputStream.write("exit\n".getBytes("UTF-8"));
            this.dataOutputStream.flush();
            this.process.getOutputStream().close();
            this.process.getInputStream().close();
            this.process.getErrorStream().close();
            this.errorStreamWorker = null;
            this.inputStreamWorker = null;
            this.process = null;
            this.dataOutputStream = null;
            Runtime.getRuntime().gc();
        } catch (Exception localException) {
            localException.printStackTrace();
        }
    }

    private boolean waitDone() {
        if (this.running) {
            int j = 0;
            while (this.running) {
                sleep(10);
                j = j + 10;
                if (j > this.maxWait + 1000) {
                    return false;
                }
            }
        }
        return true;
    }

    String sudoAndExit(String command, int timeout) throws TimeoutException, IOException {
        if (!waitDone()) {
            this.state = 1;
            command = "";
        }
        do {
            String result = execAndExit(command, timeout);
            if (this.state == 1) {
                throw new TimeoutException("Timeout error");
            }
            return result;
        } while (this.state != 2);
        // throw new IOException("Shell error");
    }

    void tryExitAndClean() {
        try {
            if (isExited()) {
                this.dataOutputStream.write("exit\n".getBytes("UTF-8"));
                this.dataOutputStream.flush();
                this.process.destroy();
            }
            this.process = null;
            this.dataOutputStream = null;
            this.inputStreamWorker = null;
            this.errorStreamWorker = null;
            Runtime.getRuntime().gc();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private class ErrorStreamWorker extends Thread {

        private BufferedReader reader = null;

        ErrorStreamWorker(InputStream paramInputStream) {
            this.reader = new BufferedReader(new InputStreamReader(paramInputStream));
        }

        public void run() {
            try {
                for (; ; ) {
                    String str = this.reader.readLine();
                    if (str == null) {
                        break;
                    }
                    if (!str.contains("unsupported flags DT_FLAGS")) {
                        output.append(str).append("\n");
                    }
                    Log.w(TAG, str);
                }
            } catch (Exception ex) {
                Log.e(TAG, ex.toString());
            } finally {
                try {
                    this.reader.close();
                } catch (Exception ignored) {
                }
            }

        }
    }

    private class InputStreamWorker extends Thread {

        private BufferedReader reader = null;

        InputStreamWorker(InputStream paramInputStream) {
            this.reader = new BufferedReader(new InputStreamReader(paramInputStream));
        }

        public void run() {
            try {
                while(true) {
                    String str = this.reader.readLine();
                    if (str == null) {
                        break;
                    }
                    if (str.contains(ECHO_EXIT_MAGIC)) {
                        output.append(str.replace(ECHO_EXIT_MAGIC, "")).append("\n");
                        running = false;
                        break;
                    }
                    Log.d(TAG, str);
                    output.append(str).append("\n");
                }
            } catch (Exception ex) {
                output.append(ex).append("\n");
                Log.e(TAG, ex.toString());
            } finally {
                try {
                    this.reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

}

