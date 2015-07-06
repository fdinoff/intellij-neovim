package com.neovim.ideanvim;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.ui.JBColor;
import com.neovim.Dispatcher;
import com.neovim.DispatcherHelper;
import com.neovim.Neovim;
import com.neovim.NeovimHandler;
import com.neovim.msgpack.JsonNodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.neovim.msgpack.JsonNodeUtil.formatJsonNode;

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

    private void drawChar(char c, int x, int y) {
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int width = fontMetrics.charWidth(c);
        int height = fontMetrics.getHeight();
        if (image != null) {
            Graphics graphics = image.getGraphics();
            graphics.setFont(getFont());
            graphics.drawString(
                    Character.toString(c),
                    width * x,
                    height * (y + 1) - fontMetrics.getLeading() - 2);
        }
    }

    private void drawCursor(int row, int col) {
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int width = fontMetrics.charWidth('W');
        int height = fontMetrics.getHeight();
        image.getGraphics().drawRect(
                width * col,
                height * row,
                width, height);
    }

    // highlight_set
    // clear
    // set_scroll_region [0, 42, 0, 95]
    // scroll[-8]
    // bell
    public class TextBuffer implements DispatcherHelper {
        private Logger log = LoggerFactory.getLogger(TextBuffer.class);
        char[][] data;
        int width;
        int height;
        int col;
        int row;
        private class ScrollRegion {
            int top, bottom, left, right;
        }

        ScrollRegion scrollRegion = new ScrollRegion();

        private Dispatcher dispatcher;

        public TextBuffer() {
        }

        @NeovimHandler("update_fg")
        public void updateForeground(int rgb) {
            foreground = new Color(rgb);
        }

        @NeovimHandler("update_bg")
        public void updateBackground(int rgb) {
            background = new Color(rgb);
        }

        @NeovimHandler("resize")
        public void resize(int width, int height) {
            log.warn(String.format("resize(%s, %s)", width, height));
            this.width = width;
            this.height = height;
            scrollRegion.top = 0;
            scrollRegion.bottom = height;
            data = new char[height + 1][width + 1];
            clear();
        }

        @NeovimHandler("clear")
        public void clear() {
            for (char[] d : data) {
                Arrays.fill(d, ' ');
            }
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
                log.warn(String.format("(%sx%s) data.length = %s, System.arraycopy(data, %s, data, %s, %s)",
                        width, height, scrollRegion.bottom, count, 0, length - count));
                System.arraycopy(
                        data, scrollRegion.top + count,
                        data, scrollRegion.top,
                        length - count);
                for (int i = scrollRegion.bottom - 1; i > scrollRegion.bottom - 1 - count; i--) {
                    char[] d = new char[width];
                    Arrays.fill(d, ' ');
                    data[i] = d;
                }
            } else if (count < 0) {
                count = -count;
                log.warn(String.format("(%sx%s) data.length = %s, System.arraycopy(data, %s, data, %s, %s)",
                        width, height, scrollRegion.bottom, scrollRegion.top, scrollRegion.top + count, length - count));
                System.arraycopy(data, scrollRegion.top, data, scrollRegion.top + count, length - count);
                for (int i = scrollRegion.top; i < scrollRegion.top + count; i++) {
                    char[] d = new char[width];
                    Arrays.fill(d, ' ');
                    data[i] = d;
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
            System.out.println(Arrays.toString(c));
            CharBuffer decode = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(c));
            if (decode.length() != 1) {
                log.error("put not one character " + decode);
                return;
            }
            char c1 = decode.get();
            //drawChar(c1, row, col);
            if (row > height || col > width) {
                log.error(String.format("bad size %s, %s > %s, %s", row, col, height, width));
                return;
            }
            data[row][col] = c1;
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
                data[row][i] = ' ';
            }
        }

        @NeovimHandler("redraw")
        public void redraw(JsonNode values) {
            checkState(dispatcher != null);
            System.out.println(String.format("size %s: %s", values.size(), formatJsonNode(values)));

            for (JsonNode v : values) {
                String name = JsonNodeUtil.getText(v.get(0));
                for (int i = 1; i < v.size(); i++) {
                    //System.out.println(name + "(" + formatJsonNode(v.get(i)) + ")");
                    dispatcher.dispatchMethod(name, v.get(i));
                }
            }
            SwingUtilities.invokeLater(GuiPanel.this::repaint);
        }

        public void drawImage() {
            if (image == null) {
                return;
            }
            drawCursor(row, col);
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < data[i].length; j++) {
                    drawChar(data[i][j], j, i);
                }
            }
        }

        @Override
        public void setDispatcher(Dispatcher dispatcher) {
            this.dispatcher = checkNotNull(dispatcher);
        }
    }
}

