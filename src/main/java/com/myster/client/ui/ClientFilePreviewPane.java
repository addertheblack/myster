package com.myster.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.myster.thumbnail.ui.ThumbnailUiUtils;

/** Details-pane composite that displays a passive thumbnail above file statistics. */
public final class ClientFilePreviewPane extends JPanel {
    private final PreviewCanvas previewCanvas = new PreviewCanvas();
    private final JScrollPane statisticsScroll;
    private Runnable geometryListener = () -> {};
    private int logicalSide;

    public ClientFilePreviewPane(JTextArea statistics) {
        super(new BorderLayout());
        Objects.requireNonNull(statistics, "statistics");
        statisticsScroll = new JScrollPane(statistics);
        statisticsScroll.setBorder(BorderFactory.createEmptyBorder());
        add(previewCanvas, BorderLayout.NORTH);
        add(statisticsScroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        previewCanvas.setVisible(false);
    }

    public void setGeometryListener(Runnable listener) {
        geometryListener = Objects.requireNonNull(listener);
    }

    public void setThumbnail(BufferedImage image) {
        if (previewCanvas.image == image) return;
        previewCanvas.setImage(image);
        previewCanvas.setVisible(image != null);
        revalidate();
        repaint();
    }

    public void clearThumbnail() {
        setThumbnail(null);
    }

    public int getThumbnailLogicalSide() {
        if (!isShowing() || getWidth() <= 0 || getHeight() <= 0) {
            return 0;
        }
        int lineHeight = statisticsScroll.getFontMetrics(statisticsScroll.getFont()).getHeight();
        int minimumStatsHeight = Math.max(lineHeight * 3, 1);
        int availableHeight = getHeight() - minimumStatsHeight - 10;
        int availableWidth = getWidth() - 10;
        return Math.max(0, Math.min(availableWidth, availableHeight));
    }

    @Override
    public void doLayout() {
        int side = getThumbnailLogicalSide();
        previewCanvas.setPreferredSize(new Dimension(side, side));
        super.doLayout();
        if (side != logicalSide) {
            logicalSide = side;
            geometryListener.run();
        }
    }

    private static final class PreviewCanvas extends JPanel {
        private BufferedImage image;

        private PreviewCanvas() {
            setOpaque(false);
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) {
                return;
            }
            Rectangle target = ThumbnailUiUtils.aspectFit(image,
                    new Rectangle(0, 0, getWidth(), getHeight()));
            graphics.drawImage(image, target.x, target.y, target.width, target.height, null);
        }
    }
}
