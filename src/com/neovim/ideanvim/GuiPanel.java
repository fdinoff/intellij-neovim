package com.neovim.ideanvim;

import com.intellij.ui.JBColor;
import com.neovim.Dispatcher;
import com.neovim.Neovim;
import com.neovim.NeovimHandler;
import org.msgpack.value.Value;
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
import java.util.Arrays;
import java.util.List;

public class GuiPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedNeovimEditorProvider.class);
    private Color foreground = JBColor.CYAN;
    private Color background = JBColor.BLACK;
    private Image image;
    private TextBuffer textBuffer = new TextBuffer();

    public GuiPanel(Neovim neovim) {
        super(new FlowLayout());
        neovim.register(textBuffer);
        neovim.call(String.class, "ui_attach", 80, 24, true).join();
        neovim.sendInput("ihello world");
        setFont(new Font("Monospaced", Font.PLAIN, 12));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        super.paintComponent(g);
        //log.warn(String.format("size %s %s", getWidth(), getHeight()));
        if (image == null)
            image = createImage(getHeight(), getWidth());
        //for (int j = 0; j < 4; j++) {
        //    for (int i = 0; i < 4; i++) {
        //        drawChar((char) ('a' + i), j, i);
        //    }
        //}
        g.drawImage(image, 0, 0, null);
        //printGridSize();
        //setForeground(foreground);
        //setBackground(background);

        //TextLayout layout = new TextLayout("Hello world\nthis is some text", getFont(), g2.getFontRenderContext());
        //layout.draw(g2, 1, 1);
    }

    protected void drawImage(Graphics2D g, BufferedImage image, int x, int y, ImageObserver observer) {
        g.drawImage(image, x, y, image.getWidth(), image.getHeight(), observer);
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
        image.getGraphics().drawString(Character.toString(c), width * x, height * (y + 1));
    }

    public class TextBuffer {
        char[][] data;
        int width;
        int height;
        int col;
        int row;
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
            log.warn("resize");
            this.width = width;
            this.height = height;
            data = new char[height][width];
            for (int i = 0; i < data.length; i++) {
                Arrays.fill(data[i], ' ');
                data[i][0] = (char)('0' + i);
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
            char c1 = decode.get();
            drawChar(c1, row, col);
            //data[row][col] = c1;
            col++;
            if (col % width == 0) {
                col = 0;
                row++;
                if (row % height == 0) {
                    row = 0;
                }
            }
        }

        @NeovimHandler("redraw")
        public void redraw(List<Value> values) {
            log.warn(values.toString());
        }
    }
}

