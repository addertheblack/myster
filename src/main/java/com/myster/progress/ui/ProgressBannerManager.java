package com.myster.progress.ui;

import java.util.ArrayList;

import com.general.util.LinkedList;
import com.general.util.Timer;

/**
 * Manages banner images and URLs for the ProgressManagerWindow's AdPanel.
 * Rotates through banners received from download servers.
 */
public class ProgressBannerManager implements Runnable {
    public final static int TIME_TO_WAIT = 1000 * 30; // 30 seconds between banner rotations

    private final ProgressManagerWindow window;
    private final LinkedList<Banner> queue;
    private final RotatingVector oldBanners;
    
    private boolean isEndFlag = false;
    private boolean isInit;

    public ProgressBannerManager(ProgressManagerWindow window) {
        this.window = window;
        this.queue = new LinkedList<>();
        this.oldBanners = new RotatingVector();
    }

    public synchronized void addNewBannerToQueue(Banner banner) {
        if (!queue.contains(banner)) { // NOTE THAT WE DO NOT CHECK IN OLDBANNERS! THIS IS ON PURPOSE!
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
            window.getAdPanel().addImage(window.getToolkit().createImage(banner.image));
        if (banner.url != null)
            setURL(banner.url);
    }
    
    private void setURL(String url) {
        // TODO: Implement URL handling for AdPanel click events if needed
        // For now, just display the image
    }

    public synchronized void run() {
        Banner banner = getNextBannerInQueue();

        if (banner != null) {
            if (!oldBanners.contains(banner))
                oldBanners.add(banner); // if unique image then add to banners
        } else {
            banner = oldBanners.getNextBanner();
        }

        if (banner != null) {
            setBanner(banner);
        }

        scheduleTimer();
    }

    public void scheduleTimer() {
        if ((isEndFlag) || (!window.isVisible()))
            return;

        new Timer(this, TIME_TO_WAIT);
    }

    public void end() {
        // isEndFlag = true; // leave for now...
    }
    
    /**
     * Represents a banner with an image and optional URL.
     */
    public static class Banner {
        public final byte[] image;
        public final String url;

        public Banner(byte[] image, String url) {
            this.image = image;
            this.url = url;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Banner banner))
                return false;

            if (image == null && banner.image == null)
                return true;

            if (image == null || banner.image == null)
                return false;

            if (image.length != banner.image.length)
                return false;

            for (int i = 0; i < image.length; i++) {
                if (image[i] != banner.image[i])
                    return false;
            }

            return true;
        }
    }
    
    /**
     * Helper class to rotate through banners.
     */
    private static class RotatingVector extends ArrayList<Banner> {
        int currentBanner = 0;

        public Banner getNextBanner() {
            if (size() < 1)
                return null;

            if (currentBanner >= size()) {
                currentBanner = 0;
            }

            return get(currentBanner++);
        }
    }
}
