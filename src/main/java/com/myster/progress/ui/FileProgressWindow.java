package com.myster.progress.ui;

import java.awt.Cursor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.general.thread.Cancellable;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.hash.FileHash;
import com.myster.net.stream.client.msdownload.DownloadInitiator.DownloadInitiatorListener;
import com.myster.net.stream.client.msdownload.ObsoleteHandler;
import com.myster.net.stream.client.msdownload.MSDownloadParams;
import com.myster.net.stream.client.msdownload.MSPartialFile;
import com.myster.net.web.WebLinkManager;
import com.myster.search.MysterFileStub;
import com.myster.ui.MysterFrameContext;

public class FileProgressWindow extends ProgressWindow {
    public static final int BAR_1 = 0;

    public static final int BAR_2 = 1;

    private RateTimer rateTimer;

    private final List<Long> barStartTime = new ArrayList<>();

    private final List<Long> previouslyDownloaded = new ArrayList<>();

    private boolean overFlag = false;

    private String url;
    
    public FileProgressWindow(MysterFrameContext c) {
        this(c, "");
    }
    
    public FileProgressWindow(MysterFrameContext c, String title) {
        super(c, title);

        resizeVectorWithLongs(barStartTime, getProgressBarNumber());
        resizeVectorWithLongs(previouslyDownloaded, getProgressBarNumber());

        addComponentListener(new ComponentAdapter() {
            public synchronized void componentShown(ComponentEvent e) {
                rateTimer = new RateTimer();
            }

            public synchronized void componentHidden(ComponentEvent e) {
                if (rateTimer != null)
                    rateTimer.end();
            }
        });

        addAdClickListener(new AdClickHandler());

        resize();
    }

    public synchronized void setProgressBarNumber(int numberOfBars) {
        resizeVectorWithLongs(barStartTime, numberOfBars);
        resizeVectorWithLongs(previouslyDownloaded, numberOfBars);

        super.setProgressBarNumber(numberOfBars);
    }
    
    private static void resizeVectorWithLongs(List<Long> list, int newSize) {
        int oldSize = list.size();
        
        if (newSize > oldSize) {
            for (int i = oldSize; i < newSize; i++) {
                list.add( 0L);
            }
        }
    }

    public synchronized void setPreviouslyDownloaded(long someValue, int bar) {
        previouslyDownloaded.set( bar, someValue);
    }

    public synchronized void startBlock(int bar, long min, long max) {
        barStartTime.set( bar, System.currentTimeMillis());

        super.startBlock(bar, min, max);
    }

    public synchronized void done() {
        overFlag = true;
    }

    public synchronized int getRate(int bar) {
        if (getValue(bar) < getMin(bar) || getValue(bar) > getMax(bar))
            return -1;

        long startTime = barStartTime.get(bar);
        long timeDelta = (System.currentTimeMillis() - startTime);
        if (timeDelta < 1 * 1000)
            return -1;
        long value = getValue(bar) - previouslyDownloaded.get(bar) - getMin(bar);
        long int_temp = (timeDelta / 1000);

        return (int) (int_temp <= 0 ? -1 : value / int_temp);
    }

    private String calculateRate(int bar) {
        return formatRate(getRate(bar));

    }

    public synchronized void setURL(String urlString) {
        url = (urlString.equals("") ? null : urlString);

        if (url != null) {
            adPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        } else {
            adPanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private static String formatRate(long value) {
        if (value <= 0) {
            return "";
        }

        return Util.getStringFromBytes(value) + "/s";
    }

    private class RateTimer implements Runnable {
        public static final int UPDATE_TIME = 100;

        Timer timer;

        private boolean endFlag = false;

        public RateTimer() {
            newTimer();
        }

        public void run() {
            synchronized (FileProgressWindow.this) {
                if (endFlag)
                    return;
                if (overFlag)
                    return;

                for (int i = 0; i < getProgressBarNumber(); i++) {
                    setAdditionalText(calculateRate(i), i);
                }
            }

            newTimer();
        }

        private void newTimer() {
            timer = new Timer(this, UPDATE_TIME);
        }

        public synchronized void end() {
            if (timer != null)
                timer.cancelTimer();
            endFlag = true;
        }
    }

    private class AdClickHandler extends MouseAdapter {
        public long lastMouseReleaseTime = 0;

        public synchronized void mouseEntered(MouseEvent e) {
            if (url != null)
                adPanel.setLabelText(url);
        }

        public synchronized void mouseExited(MouseEvent e) {
            adPanel.setLabelText("");
        }

        public synchronized void mouseReleased(MouseEvent e) {
            if ((e.getX() > 0 && e.getX() < X_SIZE) && (e.getY() > 0 && e.getY() < AD_HEIGHT)) {
                if ((System.currentTimeMillis() - lastMouseReleaseTime > 500) && url != null
                        && (!url.equals(""))) {
                    try {
                        WebLinkManager.openURL(new URI(url).toURL());
                    } catch (MalformedURLException ex) {
                        ex.printStackTrace();
                    } catch (URISyntaxException ex) {
                        ex.printStackTrace();
                    }
                }

                /*
                 * so that double or triple clicks by spastic people don't
                 * generate many browser windows
                 */
                lastMouseReleaseTime = System.currentTimeMillis();
            }
        }
    }
    
    public static DownloadInitiatorListener bindToFileProgressGui(MysterFrameContext context, MSDownloadParams params) {
        return new DownloadInitiatorListener() {
            EdtFileProgressWindow w = null;
            
            private void init() {
                if (w == null) {
                    w = new EdtFileProgressWindow(context, params);
                }
            }
            
            @Override
            public void setCancellable(Cancellable cancellable) {
                Util.invokeLater(()-> {
                    init();
                    
                    w.setCancellable(cancellable);
                });
            }

            @Override
            public void setTitle(String title) {
                Util.invokeLater(()-> {
                    init();
                    
                    w.setTitle(title);
                });
            }

            @Override
            public void setText(String text) {
                Util.invokeLater(()-> {
                    init();
                    
                    w.setText(text);
                });
            }
            
            @Override
            public ObsoleteHandler getMsDownloadListener() {
                return Util.callAndWaitNoThrows(() -> {
                    init();

                    return w.getMsDownloadListener();
                });
            }

            @Override
            public File getFileToDownloadTo(MysterFileStub stub) {
                return Util.callAndWaitNoThrows(()-> {
                    init();
                    
                    return w.getFileToDownloadTo(stub);
                });
            }

            @Override
            public MSPartialFile createMSPartialFile(MysterFileStub stub,
                                                     File fileToDownloadTo,
                                                     long estimatedFileLength,
                                                     FileHash[] hashes)
                    throws IOException {
                return Util.callAndWaitNoThrows(() -> {
                    init();

                    return w.createMSPartialFile(stub,
                                                 fileToDownloadTo,
                                                 estimatedFileLength,
                                                 hashes);
                });
            }

            @Override
            public void moveFileToFinalDestination(File sourceFile) {
                Util.invokeLater(() -> {
                    init();

                    w.moveFileToFinalDestination(sourceFile);
                });
            }
        };
    }
}