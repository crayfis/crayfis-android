package edu.uci.crayfis.camera;

import edu.uci.crayfis.camera.SntpClient;

/**
 * Created by danielwhiteson on 3/18/15.
 */
public class SntpUpdateThread extends Thread {

    // true if a request has been made to stop the thread
    volatile boolean stopRequested = false;
    // true if the thread is running and can process more data
    volatile boolean running = true;

    private SntpClient mInstance = SntpClient.getInstance();

    /**
     * Blocks until the thread has stopped
     */
    public void stopThread() {
        stopRequested = true;
        while (running) {
            interrupt();
            Thread.yield();
        }
    }

    @Override
    public void run()
    {
        while (!stopRequested) {
            try {
                // Grab a frame buffer from the queue, blocking if none
                // is available.
                mInstance.requestTime( "pool.ntp.org",100);
            }
            catch (Exception ex) {
                // Interrupted, possibly by app shutdown?
            }
        }
        running = false;

    }

}
