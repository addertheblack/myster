package com.myster.net.stream.client.msdownload;

import java.awt.Color;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.general.thread.Cancellable;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.progress.ui.FileProgressWindow;

public class MSDownloadHandler implements MSDownloadListener {
    private final FileProgressWindow progress;
    private final List<Integer> freeBars;
    private final Map<SegmentDownloader, SegmentDownloaderHandler> segmentListeners;
    private final ProgressBannerManager progressBannerManager;
    
    private int maxBarCounter;
    private int segmentCounter = 0;
    private boolean done;
    
    public MSDownloadHandler(FileProgressWindow progress, Cancellable cancellable) {
        this.progress = progress;

        progress.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (!done&& !MultiSourceUtilities.confirmCancel(progress))
                    return;

                cancellable.cancel();

                progress.setVisible(false);
            }
        });

        maxBarCounter = 1; // the first bar is used for overall progress
        freeBars = new ArrayList<Integer>();
        segmentListeners = new HashMap<>();

        this.progressBannerManager = new ProgressBannerManager(progress);
    }
    
    @Override
    public Frame getFrame() {
        return progress;
    }

    public void startDownload(MultiSourceEvent event) {
        progress.setText("Looking for first source...");
        progress.startBlock(0, 0, event.getLength());
        progress.setPreviouslyDownloaded(event.getInitialOffset(), FileProgressWindow.BAR_1);
        progress.setValue(event.getInitialOffset());
    }

    private int counter = 0;

    public void progress(MultiSourceEvent event) {
        progress.setValue(event.getProgress());

        if (--counter % 10 == 0)
            progress.setText("Transfered: "
                    + com.general.util.Util.getStringFromBytes(event.getProgress()));
    }

    public void startSegmentDownloader(MSSegmentEvent event) {
        if (segmentCounter == 0)
            progress.setText("Trying a new source...");

        ++segmentCounter;

        SegmentDownloaderHandler handler = new SegmentDownloaderHandler(progressBannerManager,
                progress, getAppropriateBarNumber());

        segmentListeners.put(event.getSegmentDownloader(), handler);

        event.getSegmentDownloader().addListener(handler);
    }

    public void endSegmentDownloader(MSSegmentEvent event) {
        --segmentCounter;

        if (segmentCounter == 0)
            progress.setText("Looking for new sources...");

        SegmentDownloaderHandler handler = segmentListeners.remove(event.getSegmentDownloader());

        if (handler == null)
            throw new RuntimeException(
                    "Could not find a segment downloader to match a segment download that has ended");

        returnBarNumber(handler.getBarNumber());
    }

    public void endDownload(MultiSourceEvent event) {
        progress.setText("Download Stopped");
    }

    public void doneDownload(MultiSourceEvent event) {
        progress.setText("Download Finished");
        progress.setValue(progress.getMax());
        progress.done();
        progress.setProgressBarNumber(1);
        
        done = true;
    }

    /**
     * This routine returns the number of the bar that should be used Next.
     * 
     * It also demonstrates the worst about Java syntax and API calls.
     */
    private int getAppropriateBarNumber() {
        if (freeBars.size() == 0) {
            progress.setProgressBarNumber(maxBarCounter + 1);

//            final int MAX_STEP = 5;
//            int i = macBarCounter - 1;
//
//            i = ((i / MAX_STEP) % 2 == 0 ? i % MAX_STEP : MAX_STEP - (i % MAX_STEP) - 1);
//
//            progress.setBarColor(new java.awt.Color(0, (MAX_STEP - i) * (255 / MAX_STEP), 150),
//                    macBarCounter);

            return maxBarCounter++;
        }

        //this blob of code figures out which of the freebars to re-use
        int minimum = freeBars.get(0).intValue(); //no
        // templates
        int min_index = 0;
        for (int i = 0; i < freeBars.size(); i++) {
            int temp_int = freeBars.get(i).intValue(); //no autoboxing

            if (temp_int < minimum) {
                minimum = temp_int;
                min_index = i;
            }
        }

        // remove at index
        return freeBars.remove(min_index); 
    }

    /**
     * returns a "bar" number to the re-distribution heap
     */
    private void returnBarNumber(int barNumber) {
        freeBars.add(barNumber);
    }
}

class SegmentDownloaderHandler implements SegmentDownloaderListener {
    private final int bar;

    private final FileProgressWindow progress;

    private final ProgressBannerManager progressBannerManager;

    private static final int MAX_SPEED = 1 * 1024 * 1024 * 100;

    public SegmentDownloaderHandler(ProgressBannerManager progressBannerManager,
            FileProgressWindow progress, int bar) {
        this.bar = bar;
        this.progress = progress;

        this.progressBannerManager = progressBannerManager;

        progress.setText("Connecting...", bar);
    }

    public void connected(SegmentDownloaderEvent e) {
        progress.setText("Negotiating...", bar);
        progress.setValue(-1, bar);
    }

    public void queued(SegmentDownloaderEvent e) {
        progress.setValue(-1, bar);
        progress.setText("You are in queue position " + e.getQueuePosition(), bar);
    }

    public void startSegment(SegmentDownloaderEvent e) {
        progress.startBlock(bar, e.getOffset(), e.getOffset() + e.getLength());
        //progress.setPreviouslyDownloaded(e.getOffset(), bar);
        progress.setValue(e.getOffset(), bar);
        updateBarColor();
        progress.setText("Downloading from " + e.getMysterFileStub().getMysterAddress(), bar);
    }

    private void updateBarColor() {
        long rate = Math.min(MAX_SPEED, progress.getRate(bar));
        if (rate<=0)
            return;
        int offset = (int) (512 * (Math.log(rate) / Math.log(MAX_SPEED))) - 256;
        offset = Math.min(255,offset);
        if (offset > 0) {
            progress.setBarColor(new Color(Math.min(255, 511 - offset*2), Math.min(255,offset*2), 0), bar);
        }
    }

    public void downloadedBlock(SegmentDownloaderEvent e) {
        progress.setValue(e.getProgress() + e.getOffset(), bar);
        updateBarColor();
    }

    public void endSegment(SegmentDownloaderEvent e) {
        progress.setValue(progress.getMax(bar), bar);
    }

    public void endConnection(SegmentDownloaderEvent e) {
        progress.setMax(1, bar);
        progress.setMin(0, bar);
        progress.setValue(0, bar);
        progress.setAdditionalText("", bar);
        progress.setText("This spot is Idle..", bar);
    }

    public int getBarNumber() {
        return bar;
    }

    ////////Meta Data Managers
    byte[] image;

    String url;

    public void downloadedMetaData(SegmentMetaDataEvent e) {
        switch (e.getType()) {
        case 'i':
            flushBanner();

            image = e.getCopyOfData();

            break;
        case 'u': //URLs are UTF-8 but java's UTF decoder needs the length in
            // the first two bytes
            byte[] temp_buffer = e.getCopyOfData();

            if (temp_buffer.length > (0xFFFF))
                break; //error URL is insanely long

            byte[] final_buffer = new byte[temp_buffer.length + 2];

            final_buffer[0] = (byte) ((temp_buffer.length >> 8) & 0xFF);
            final_buffer[1] = (byte) ((temp_buffer.length) & 0xFF);

            for (int i = 0; i < temp_buffer.length; i++) {
                final_buffer[i + 2] = temp_buffer[i];
            }

            final var in = new MysterDataInputStream(
                    new java.io.ByteArrayInputStream(final_buffer));

            try {
                url = in.readUTF();
            } catch (java.io.IOException ex) {
                //nothing
                //means UTF was corrupt
            }

            flushBanner();
            break;
        default:
            //do nothing
            break;
        }
    }

    private void flushBanner() {
        if (image == null)
            return;

        progressBannerManager.addNewBannerToQueue(new Banner(image, url));

        image = null;
        url = null;
    }
}

class ProgressBannerManager implements Runnable {
    public final static int TIME_TO_WAIT = 1000 * 30;

    FileProgressWindow progress;

    com.general.util.LinkedList<Banner> queue;

    RotatingVector oldBanners;

    boolean isEndFlag = false;

    boolean isInit;

    public ProgressBannerManager(FileProgressWindow progress) {
        this.progress = progress;
        this.queue = new com.general.util.LinkedList<Banner>();
        this.oldBanners = new RotatingVector();
    }

    public synchronized void addNewBannerToQueue(Banner banner) {
        if (!queue.contains(banner)) { //NOTE THAT WE DO NOT CHECK IN
            // OLDBANNERS! THIS IS ON PURPOSE!
            queue.addToTail(banner);
        }

        if (!isInit) {
            isInit = true;
            run();
        }
    }

    private Banner getNextBannerInQueue() {
        return queue.removeFromHead();
    }

    private void setBanner(Banner banner) {
        if (banner.image != null)
            progress.makeImage(banner.image); //also sets image
        if (banner.url != null)
            progress.setURL(banner.url);
    }

    public synchronized void run() {
        Banner banner = getNextBannerInQueue();

        if (banner != null) {
            if (!oldBanners.contains(banner))
                oldBanners.add(banner); //if unique image then add to
            // banners
        } else {
            banner = oldBanners.getNextBanner();
        }

        if (banner != null) {
            setBanner(banner);
        }

        shedualTimer();
    }

    public void shedualTimer() {
        if ((isEndFlag) || (!progress.isVisible()))
            return;

        com.general.util.Timer timer = new com.general.util.Timer(this, TIME_TO_WAIT);
    }

    public void end() {
        //isEndFlag = true; //oops.. leave for now...
    }
}

class RotatingVector extends ArrayList<Banner> {
    int currentBanner = 0;

    public Banner getNextBanner() {
        if (size() < 1)
            return null;

        if (currentBanner >= size()) {
            currentBanner = 0;
        }

        return get(currentBanner++); // xxx++ is very handy
    }
}

class Banner {
    public final byte[] image;

    public final String url;

    protected Banner(byte[] image, String url) {
        this.image = image;
        this.url = url;
    }

    public boolean equals(Object o) {
        Banner banner;

        try {
            banner = (Banner) o;
        } catch (ClassCastException ex) {
            return false;
        }

        if (image.length != banner.image.length)
            return false;

        for (int i = 0; i < image.length; i++) {
            if (image[i] != banner.image[i])
                return false;
        }

        return true;
    }

    public int hashCode() {
        return image.length;
    }
}