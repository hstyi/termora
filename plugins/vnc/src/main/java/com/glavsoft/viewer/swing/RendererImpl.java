// Copyright (C) 2010 - 2014 GlavSoft LLC.
// All rights reserved.
//
// -----------------------------------------------------------------------
// This file is part of the TightVNC software.  Please visit our Web site:
//
//                       http://www.tightvnc.com/
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
// -----------------------------------------------------------------------
//
package com.glavsoft.viewer.swing;

import com.glavsoft.drawing.Renderer;
import com.glavsoft.rfb.encoding.PixelFormat;
import com.glavsoft.rfb.encoding.decoder.FramebufferUpdateRectangle;
import com.glavsoft.transport.Transport;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RendererImpl extends Renderer implements ImageObserver {
    CyclicBarrier barrier = new CyclicBarrier(2);
    private final Image offscreenImage;

    public RendererImpl(Transport transport, int width, int height, PixelFormat pixelFormat) {
        if (0 == width) width = 1;
        if (0 == height) height = 1;
        init(width, height, pixelFormat);
        ColorModel colorModel = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
        SampleModel sampleModel = colorModel.createCompatibleSampleModel(width,
                height);

        DataBuffer dataBuffer = new DataBufferInt(pixels, width * height);
        WritableRaster raster = Raster.createWritableRaster(sampleModel,
                dataBuffer, null);
        offscreenImage = new BufferedImage(colorModel, raster, false, null);
        cursor = new SoftCursorImpl(0, 0, 0, 0);
    }

    /**
     * Draw jpeg image data
     *
     * @param bytes            jpeg image data array
     * @param offset           start offset at data array
     * @param jpegBufferLength jpeg image data array length
     * @param rect             image location and dimensions
     */
    @Override
    public void drawJpegImage(byte[] bytes, int offset, int jpegBufferLength,
                              FramebufferUpdateRectangle rect) {
        Image jpegImage = Toolkit.getDefaultToolkit().createImage(bytes,
                offset, jpegBufferLength);
        Toolkit.getDefaultToolkit().prepareImage(jpegImage, -1, -1, this);
        try {
            barrier.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // nop
        } catch (BrokenBarrierException e) {
            // nop
        } catch (TimeoutException e) {
            // nop
        }
        Graphics graphics = offscreenImage.getGraphics();
        graphics.drawImage(jpegImage, rect.x, rect.y, rect.width, rect.height, this);
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y,
                               int width, int height) {
        boolean isReady = (infoflags & (ALLBITS | ABORT)) != 0;
        if (isReady) {
            try {
                barrier.await();
            } catch (InterruptedException e) {
                // nop
            } catch (BrokenBarrierException e) {
                // nop
            }
        }
        return !isReady;
    }

    /* Swing specific interface */
    public Image getOffscreenImage() {
        return offscreenImage;
    }

    public void paintImageOn(Graphics g, int width, int height, double scaleFactor) {
        lock.lock();
        try {
            g.drawImage(offscreenImage, 0, 0, null);
        } finally {
            lock.unlock();
        }
    }

    public void paintCursorOn(Graphics g, boolean force) {
        synchronized (cursor.getLock()) {
            Image cursorImage = ((SoftCursorImpl) cursor).getImage();
            if (cursorImage != null && (force ||
                    g.getClipBounds().intersects(cursor.rX, cursor.rY, cursor.width, cursor.height))) {
                g.drawImage(cursorImage, cursor.rX, cursor.rY, null);
            }
        }
    }

    public SoftCursorImpl getCursor() {
        return (SoftCursorImpl) cursor;
    }

}
