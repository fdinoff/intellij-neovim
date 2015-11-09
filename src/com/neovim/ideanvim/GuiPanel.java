package com.neovim.ideanvim;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.neovim.Dispatcher;
import com.neovim.DispatcherHelper;
import com.neovim.Neovim;
import com.neovim.NeovimHandler;
import com.neovim.msgpack.JsonNodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class GuiPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedNeovimEditorProvider.class);
    private final TextBuffer textBuffer;
    private final Neovim neovim;

    private Color foreground = JBColor.CYAN;
    private Color background = JBColor.BLACK;
    private Image image;

    public GuiPanel(Neovim neovim) {
        super(new FlowLayout());
        this.neovim = checkNotNull(neovim);
        textBuffer = new TextBuffer();
        neovim.register(textBuffer);
        neovim.call(String.class, "ui_attach", 80, 24, true).join();
        setFont(new Font("Monaco", Font.PLAIN, 12));
    }

    public boolean isMonospace() {
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int width = fontMetrics.charWidth('W');
        int width1 = fontMetrics.charWidth('m');
        int width2 = fontMetrics.charWidth('i');
        return width == width1 && width == width2;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        super.paintComponent(g);
        //log.warn(String.format("size %s %s", getWidth(), getHeight()));
        image = createImage(getWidth(), getHeight());
        textBuffer.drawImage();
        //for (int j = 0; j < 4; j++) {
        //    for (int i = 0; i < 4; i++) {
        //        drawChar((char) ('a' + i), j, i);
        //    }
        //}
        //printGridSize();
        setForeground(foreground);
        setBackground(background);
        g.drawImage(image, 0, 0, null);

        //TextLayout layout = new TextLayout("Hello world\nthis is some text", getFont(), g2.getFontRenderContext());
        //layout.draw(g2, 1, 1);
    }

    protected void drawImage(Graphics2D g, BufferedImage image, int x, int y, ImageObserver observer) {
        g.drawImage(image, x, y, image.getWidth(), image.getHeight(), observer);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int width = fontMetrics.charWidth('W');
        int height = fontMetrics.getHeight();
        neovim.call(String.class,
                "ui_try_resize",
                Math.max(1, getWidth() / width),
                Math.max(1, getHeight() / height)).join();
        // The redrawing code doesn't handle resizes to the left or right properly
        // redraw the whole screen instead
        neovim.sendVimCommand("redraw!");
    }

    private void printGridSize() {
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int width = fontMetrics.charWidth('W');
        int height = fontMetrics.getHeight();
        log.warn(String.format("size %s %s", getWidth() / width, getHeight() / height));
    }

    private void drawCursor(int row, int col) {
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int width = fontMetrics.charWidth('W');
        int height = fontMetrics.getHeight();
        Graphics graphics = image.getGraphics();
        graphics.setColor(foreground.brighter());
        graphics.drawRect(
                width * col,
                height * row,
                width, height);
    }

    // highlight_set
    // clear
    // bell
    // normal_mode
    public class TextBuffer implements DispatcherHelper {
        private Logger log = LoggerFactory.getLogger(TextBuffer.class);
        Box[][] data;
        int width;
        int height;
        int col;
        int row;
        HighlightAttributes currentHighlightAttributes = new HighlightAttributes();
        private class Box {
            final String value;
            final HighlightAttributes highlightAttributes;

            public Box(String value, HighlightAttributes highlightAttributes) {
                this.value = checkNotNull(value);
                this.highlightAttributes = checkNotNull(highlightAttributes);
            }
        }
        private class ScrollRegion {
            int top, bottom, left, right;
        }

        private class HighlightAttributes {
            boolean bold;
            boolean underline;
            boolean undercurl;
            boolean italic;
            boolean reverse;
            Color foreground;
            Color background;
        }

        ScrollRegion scrollRegion = new ScrollRegion();

        private Dispatcher dispatcher;

        public TextBuffer() {
        }

        private Box[] initBoxes(int width, String s) {
            Box[] boxes = new Box[width];
            for (int i = 0; i < boxes.length; i++) {
                boxes[i] = new Box(" ", currentHighlightAttributes);
            }
            return boxes;
        }

        @NeovimHandler("update_fg")
        public void updateForeground(int rgb) {
            if (rgb == -1) {
                foreground = JBColor.BLACK;
            } else {
                foreground = new Color(rgb);
            }
        }

        @NeovimHandler("update_bg")
        public void updateBackground(int rgb) {
            if (rgb == -1) {
                background = JBColor.WHITE;
            } else {
                background = new Color(rgb);
            }
        }

        @NeovimHandler("resize")
        public void resize(int width, int height) {
            log.warn(String.format("resize(%s, %s)", width, height));
            this.width = width;
            this.height = height;
            scrollRegion.top = 0;
            scrollRegion.bottom = height;
            col = 0;
            row = 0;
            data = new Box[height + 1][width + 1];
            for (int i = 0 ; i < data.length; i++) {
                for (int j = 0; j < data.length; j++) {
                    data[i][j] = new Box(" ", currentHighlightAttributes);
                }
            }
        }

        @NeovimHandler("clear")
        public void clear() {
            for (Box[] d : data) {
                for (int i = 0; i < d.length; i++) {
                    d[i] = new Box(" ", currentHighlightAttributes);
                }
            }
        }

        @NeovimHandler("highlight_set")
        public void setHighlightAttributes(Map<String, Object> attributes) {
            HighlightAttributes attrs = new HighlightAttributes();
            if (attributes.containsKey("background")) {
                attrs.background = new Color((Integer) attributes.get("background"));
            } else {
                attrs.background = background;
            }
            if (attributes.containsKey("foreground")) {
                attrs.foreground = new Color((Integer) attributes.get("foreground"));
            } else {
                attrs.foreground = foreground;
            }
            currentHighlightAttributes = attrs;
        }

        @NeovimHandler("set_scroll_region")
        public void setScrollRegion(int top, int bottom, int left, int right) {
            scrollRegion.top = top;
            scrollRegion.bottom = bottom;
            scrollRegion.left = left;
            scrollRegion.right = right;
        }

        /**
         * scroll the region by count lines up.
         */
        @NeovimHandler("scroll")
        public void scroll(int count) {
            if (count == 0) {
                return;
            }
            int length = scrollRegion.bottom - scrollRegion.top + 1;
            if (count > 0) {
                System.arraycopy(
                        data, scrollRegion.top + count,
                        data, scrollRegion.top,
                        length - count);
                for (int i = scrollRegion.bottom - 1; i > scrollRegion.bottom - 1 - count; i--) {
                    data[i] = initBoxes(width, " ");
                }
            } else if (count < 0) {
                count = -count;
                System.arraycopy(data, scrollRegion.top, data, scrollRegion.top + count, length - count);
                for (int i = scrollRegion.top; i < scrollRegion.top + count; i++) {
                    data[i] = initBoxes(width, " ");
                }
            }
        }

        @NeovimHandler("cursor_goto")
        public void setCursor(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @NeovimHandler("put")
        public void put(byte[] c) {
            CharBuffer decode = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(c));
            if (decode.length() != 1) {
                log.error("put not one character " + decode);
                return;
            }
            if (row > height || col > width) {
                log.error(String.format("bad size %s, %s > %s, %s", row, col, height, width));
                return;
            }
            data[row][col] = new Box(decode.toString(), currentHighlightAttributes);
            col++;
            if (col % width == 0) {
                col = 0;
                row++;
                if (row % height == 0) {
                    row = 0;
                }
            }
        }

        @NeovimHandler("eol_clear")
        public void clearToEnd() {
            for (int i = col; i < width; i++) {
                data[row][i] = new Box(" ", currentHighlightAttributes);
            }
        }

        @NeovimHandler("redraw")
        public void redraw(JsonNode values) {
            checkState(dispatcher != null);
            ApplicationManager.getApplication().invokeLater(() -> {
                        //System.out.println(String.format("size %s: %s", values.size(), formatJsonNode(values)));

                        for (JsonNode v : values) {
                            String name = JsonNodeUtil.getText(v.get(0));
                            for (int i = 1; i < v.size(); i++) {
                                //System.out.println(name + "(" + formatJsonNode(v.get(i)) + ")");
                                dispatcher.dispatchMethod(name, v.get(i));
                            }
                        }
                        GuiPanel.this.repaint();
                    }
            );
        }

        public void drawImage() {
            if (image == null || data == null) {
                return;
            }
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < data[i].length; j++) {
                    drawBox(data[i][j], j, i);
                }
            }
            drawCursor(row, col);
        }

        @Override
        public void setDispatcher(Dispatcher dispatcher) {
            this.dispatcher = checkNotNull(dispatcher);
        }

        private void drawBox(Box box, int x, int y) {
            FontMetrics fontMetrics = getFontMetrics(getFont());
            int width = fontMetrics.charWidth('W');
            int height = fontMetrics.getHeight();
            Graphics2D graphics = (Graphics2D) image.getGraphics();
            graphics.setFont(getFont());
            graphics.setColor(box.highlightAttributes.background);
            graphics.fillRect(width * x, height * y, width, height);
            graphics.setColor(box.highlightAttributes.foreground);
            //graphics.setBackground(box.highlightAttributes.background);
            //graphics.setColor(box.highlightAttributes.foreground);
            graphics.drawString(
                    box.value,
                    width * x,
                    height * (y + 1) - fontMetrics.getLeading() - 2);
        }

    }
}

