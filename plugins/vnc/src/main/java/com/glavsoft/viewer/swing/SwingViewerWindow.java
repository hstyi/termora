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

import com.glavsoft.core.SettingsChangedEvent;
import com.glavsoft.rfb.IChangeSettingsListener;
import com.glavsoft.rfb.IRepaintController;
import com.glavsoft.rfb.client.KeyEventMessage;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.rfb.protocol.tunnel.TunnelType;
import com.glavsoft.utils.Keymap;
import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.settings.UiSettings;
import com.glavsoft.viewer.swing.gui.OptionsDialog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SwingViewerWindow implements IChangeSettingsListener, MouseEnteredListener {
	public static final int FS_SCROLLING_ACTIVE_BORDER = 20;
	private JToggleButton zoomFitButton;
	private JToggleButton zoomFullScreenButton;
	private JButton zoomInButton;
	private JButton zoomOutButton;
	private JButton zoomAsIsButton;
	private JScrollPane scroller;
	private JFrame frame;
	private boolean forceResizable = true;
	private ButtonsBar buttonsBar;
	private Surface surface;
	private boolean isSeparateFrame;
    private ViewerEventsListener viewerEventsListener;
	private final String appName;
	private String connectionString;
    private ConnectionPresenter presenter;
    private Rectangle oldContainerBounds;
	private volatile boolean isFullScreen;
	private Border oldScrollerBorder;
	private JLayeredPane lpane;
	private EmptyButtonsBarMouseAdapter buttonsBarMouseAdapter;
    private String remoteDesktopName;
    private ProtocolSettings rfbSettings;
    private UiSettings uiSettings;
    private Protocol workingProtocol;

    private boolean isZoomToFitSelected;
    private List<JComponent> kbdButtons;
    private Container container;
    private static Logger logger = Logger.getLogger(SwingViewerWindow.class.getName());

    public SwingViewerWindow(Protocol workingProtocol, ProtocolSettings rfbSettings, UiSettings uiSettings, Surface surface,
                             boolean isSeparateFrame, boolean isApplet, ViewerEventsListener viewerEventsListener,
							 String appName, String connectionString,
                             ConnectionPresenter presenter, Container externalContainer) {
        this.workingProtocol = workingProtocol;
        this.rfbSettings = rfbSettings;
        this.uiSettings = uiSettings;
        this.surface = surface;
        this.isSeparateFrame = isSeparateFrame;
        this.viewerEventsListener = viewerEventsListener;
		this.appName = appName;
		this.connectionString = connectionString;
        this.presenter = presenter;
        createContainer(surface, externalContainer);

        if (uiSettings.showControls) {
            createButtonsPanel(workingProtocol, isSeparateFrame? frame: externalContainer, isApplet);
            if (isSeparateFrame) registerResizeListener(frame);
            updateZoomButtonsState();
        }
        if (uiSettings.isFullScreen()) {
            switchOnFullscreenMode();
        }
        setSurfaceToHandleKbdFocus();
	}

    private void createContainer(final Surface surface, Container externalContainer) {
		lpane = new JLayeredPane() {
			@Override
			public Dimension getSize() {
				return surface.getPreferredSize();
			}
			@Override
			public Dimension getPreferredSize() {
				return surface.getPreferredSize();
			}
		};
		lpane.setPreferredSize(surface.getPreferredSize());
        lpane.add(surface, JLayeredPane.DEFAULT_LAYER, 0);
        scroller = new JScrollPane();
        scroller.getViewport().setBackground(Color.DARK_GRAY);
        scroller.setViewportView(lpane);

		if (isSeparateFrame) {
			frame = new JFrame();
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent windowEvent) {
                    super.windowClosing(windowEvent);
                    fireCloseApp();
                }
            });
            frame.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
			Utils.setApplicationIconsForWindow(frame);
            frame.setLayout(new BorderLayout(0, 0));
            frame.add(scroller, BorderLayout.CENTER);

//			frame.pack();
            lpane.setSize(surface.getPreferredSize());

            internalPack(null);
            container = frame;
            fireContainerCompleted();
		} else {
            if (null == externalContainer) throw new IllegalArgumentException("External Container is null"); // TODO catch it somewhere
            externalContainer.setLayout(new BorderLayout(0, 0));
            externalContainer.add(scroller, BorderLayout.CENTER);
            container = externalContainer;
            fireContainerCompleted();
		}
	}

    private void fireContainerCompleted() {
        if (viewerEventsListener != null) {
            viewerEventsListener.onViewerComponentContainerBuilt(this);
        }
    }

    public void pack() {
		final Dimension oldSize = lpane.getSize();
		lpane.setSize(surface.getPreferredSize());
		if (isSeparateFrame && ! isZoomToFitSelected()) {
			internalPack(oldSize);
		}
        if (buttonsBar != null) {
            updateZoomButtonsState();
        }
        updateWindowTitle();
	}

    public boolean isZoomToFitSelected() {
        return isZoomToFitSelected;
    }

    public void setZoomToFitSelected(boolean zoomToFitSelected) {
        isZoomToFitSelected = zoomToFitSelected;
    }

    public void setRemoteDesktopName(String name) {
        remoteDesktopName = name;
        updateWindowTitle();
    }

    private void updateWindowTitle() {
        if (isSeparateFrame) {
			frame.setTitle(remoteDesktopName + " [zoom: " + uiSettings.getScalePercentFormatted() + "%]");
		}
    }

	private void internalPack(Dimension outerPanelOldSize) {
		final Rectangle workareaRectangle = getWorkareaRectangle();
		if (workareaRectangle.equals(frame.getBounds())) {
			forceResizable = true;
		}
		final boolean isHScrollBar = scroller.getHorizontalScrollBar().isShowing() && ! forceResizable;
		final boolean isVScrollBar = scroller.getVerticalScrollBar().isShowing() && ! forceResizable;

		boolean isWidthChangeable = true;
		boolean isHeightChangeable = true;
		if (outerPanelOldSize != null && surface.oldSize != null) {
			isWidthChangeable = forceResizable ||
					(outerPanelOldSize.width == surface.oldSize.width && ! isHScrollBar);
			isHeightChangeable = forceResizable ||
					(outerPanelOldSize.height == surface.oldSize.height && ! isVScrollBar);
		}
		forceResizable = false;
		frame.validate();

		final Insets containerInsets = frame.getInsets();
		Dimension preferredSize = frame.getPreferredSize();
		Rectangle preferredRectangle = new Rectangle(frame.getLocation(), preferredSize);

		if (null == outerPanelOldSize && workareaRectangle.contains(preferredRectangle)) {
			frame.pack();
		} else {
			Dimension minDimension = new Dimension(
					containerInsets.left + containerInsets.right, containerInsets.top + containerInsets.bottom);
			if (buttonsBar != null && buttonsBar.isVisible) {
				minDimension.width += buttonsBar.getWidth();
				minDimension.height += buttonsBar.getHeight();
			}
			Dimension dim = new Dimension(preferredSize);
			Point location = frame.getLocation();
			if ( ! isWidthChangeable) {
				dim.width = frame.getWidth();
			} else {
				if (isVScrollBar) dim.width += scroller.getVerticalScrollBar().getWidth();
				if (dim.width < minDimension.width) dim.width = minDimension.width;

				int dx = location.x - workareaRectangle.x;
				if (dx < 0) {
					dx = 0;
					location.x = workareaRectangle.x;
				}
				int w = workareaRectangle.width - dx;
				if (w < dim.width) {
					int dw = dim.width - w;
					if (dw < dx) {
						location.x -= dw;
					} else {
						dim.width = workareaRectangle.width;
						location.x = workareaRectangle.x;
					}
				}
			}
			if ( ! isHeightChangeable) {
				dim.height = frame.getHeight();
			} else {

				if (isHScrollBar) dim.height += scroller.getHorizontalScrollBar().getHeight();
				if (dim.height < minDimension.height) dim.height = minDimension.height;

				int dy = location.y - workareaRectangle.y;
				if (dy < 0) {
					dy = 0;
					location.y = workareaRectangle.y;
				}
				int h = workareaRectangle.height - dy;
				if (h < dim.height) {
					int dh = dim.height - h;
					if (dh < dy) {
						location.y -= dh;
					} else {
						dim.height = workareaRectangle.height;
						location.y = workareaRectangle.y;
					}
				}
			}
			if ( ! location.equals(frame.getLocation())) {
				frame.setLocation(location);
			}
			if ( ! isFullScreen ) {
				frame.setSize(dim);
			}
		}
		scroller.revalidate();
	}

	private Rectangle getWorkareaRectangle() {
		final GraphicsConfiguration graphicsConfiguration = frame.getGraphicsConfiguration();
		final Rectangle screenBounds = graphicsConfiguration.getBounds();
		final Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);

		screenBounds.x += screenInsets.left;
		screenBounds.y += screenInsets.top;
		screenBounds.width -= screenInsets.left + screenInsets.right;
		screenBounds.height -= screenInsets.top + screenInsets.bottom;
		return screenBounds;
	}

	void addZoomButtons() {
		buttonsBar.createStrut();
		zoomOutButton = buttonsBar.createButton("zoom-out", "Zoom Out", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				zoomFitButton.setSelected(false);
                uiSettings.zoomOut();
			}
		});
		zoomInButton = buttonsBar.createButton("zoom-in", "Zoom In", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				zoomFitButton.setSelected(false);
                uiSettings.zoomIn();
			}
		});
		zoomAsIsButton = buttonsBar.createButton("zoom-100", "Zoom 100%", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				zoomFitButton.setSelected(false);
				forceResizable = false;
                uiSettings.zoomAsIs();
			}
		});

		zoomFitButton = buttonsBar.createToggleButton("zoom-fit", "Zoom to Fit Window",
				new ItemListener() {
					@Override
					public void itemStateChanged(ItemEvent e) {
						if (e.getStateChange() == ItemEvent.SELECTED) {
							setZoomToFitSelected(true);
							forceResizable = true;
							zoomToFit();
							updateZoomButtonsState();
						} else {
							setZoomToFitSelected(false);
						}
						setSurfaceToHandleKbdFocus();
					}
				});

		zoomFullScreenButton = buttonsBar.createToggleButton("zoom-fullscreen", "Full Screen",
			new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent e) {
					updateZoomButtonsState();
					if (e.getStateChange() == ItemEvent.SELECTED) {
                        uiSettings.setFullScreen(switchOnFullscreenMode());
					} else {
						switchOffFullscreenMode();
                        uiSettings.setFullScreen(false);
					}
					setSurfaceToHandleKbdFocus();
				}
			});
			if ( ! isSeparateFrame) {
				zoomFullScreenButton.setEnabled(false);
				zoomFitButton.setEnabled(false);
			}
		}

    protected void setSurfaceToHandleKbdFocus() {
        if (surface != null && ! surface.requestFocusInWindow()) {
            surface.requestFocus();
        }
    }

    boolean switchOnFullscreenMode() {
		zoomFullScreenButton.setSelected(true);
		oldContainerBounds = frame.getBounds();
        buttonsBar.setNoFullScreenGroupVisible(false);
        setButtonsBarVisible(false);
		forceResizable = true;
		frame.dispose(); // ?
		frame.setUndecorated(true);
		frame.setResizable(false);
		frame.setVisible(true); // ?
		try {
			frame.getGraphicsConfiguration().getDevice().setFullScreenWindow(frame);
			isFullScreen = true;
			scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
			scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			oldScrollerBorder = scroller.getBorder();
			scroller.setBorder(new EmptyBorder(0, 0, 0, 0));
			new FullscreenBorderDetectionThread(frame).start();
		} catch (Exception ex) {
            Logger.getLogger(this.getClass().getName()).info("Cannot switch into FullScreen mode: " + ex.getMessage());
			return false;
		}
        return true;
	}

	private void switchOffFullscreenMode() {
		if (isFullScreen) {
			zoomFullScreenButton.setSelected(false);
			isFullScreen = false;
            buttonsBar.setNoFullScreenGroupVisible(true);
			setButtonsBarVisible(true);
			try {
				frame.dispose();
				frame.setUndecorated(false);
				frame.setResizable(true);
				frame.getGraphicsConfiguration().getDevice().setFullScreenWindow(null);
			} catch (Exception ignore) {
				// nop
			}
			scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
			scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scroller.setBorder(oldScrollerBorder);
			this.frame.setBounds(oldContainerBounds);
			frame.setVisible(true);
			pack();
		}
	}

	private void zoomToFit() {
		Dimension scrollerSize = scroller.getSize();
		Insets scrollerInsets = scroller.getInsets();
        uiSettings.zoomToFit(scrollerSize.width - scrollerInsets.left - scrollerInsets.right,
                scrollerSize.height - scrollerInsets.top - scrollerInsets.bottom +
                        (isFullScreen ? buttonsBar.getHeight() : 0),
                workingProtocol.getFbWidth(), workingProtocol.getFbHeight());
	}

	void registerResizeListener(Container container) {
		container.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (isZoomToFitSelected()) {
                    zoomToFit();
                    updateZoomButtonsState();
                    updateWindowTitle();
                    setSurfaceToHandleKbdFocus();
                }
            }
        });
	}

	void updateZoomButtonsState() {
		zoomOutButton.setEnabled(uiSettings.getScalePercent() > UiSettings.MIN_SCALE_PERCENT);
		zoomInButton.setEnabled(uiSettings.getScalePercent() < UiSettings.MAX_SCALE_PERCENT);
		zoomAsIsButton.setEnabled(uiSettings.getScalePercent() != 100);
	}

	public ButtonsBar createButtonsBar() {
		buttonsBar = new ButtonsBar();
		return buttonsBar;
	}

    public void setButtonsBarVisible(boolean isVisible) {
        setButtonsBarVisible(isVisible, frame);
    }

	private void setButtonsBarVisible(boolean isVisible, Container container) {
		buttonsBar.setVisible(isVisible);
		if (isVisible) {
			buttonsBar.borderOff();
			container.add(buttonsBar.bar, BorderLayout.NORTH);
            container.validate();
		} else {
			container.remove(buttonsBar.bar);
			buttonsBar.borderOn();
		}
	}

	public void setButtonsBarVisibleFS(boolean isVisible) {
		if (isVisible) {
			if ( ! buttonsBar.isVisible) {
                lpane.add(buttonsBar.bar, JLayeredPane.POPUP_LAYER, 0);
                final int bbWidth = buttonsBar.bar.getPreferredSize().width;
				buttonsBar.bar.setBounds(
						scroller.getViewport().getViewPosition().x + (scroller.getWidth() - bbWidth)/2, 0,
						bbWidth, buttonsBar.bar.getPreferredSize().height);

				// prevent mouse events to through down to Surface
				if (null == buttonsBarMouseAdapter) buttonsBarMouseAdapter = new EmptyButtonsBarMouseAdapter();
				buttonsBar.bar.addMouseListener(buttonsBarMouseAdapter);
			}
		} else {
			buttonsBar.bar.removeMouseListener(buttonsBarMouseAdapter);
			lpane.remove(buttonsBar.bar);
			lpane.repaint(buttonsBar.bar.getBounds());
		}
		buttonsBar.setVisible(isVisible);
        lpane.repaint();
        lpane.validate();
        buttonsBar.bar.validate();
    }

    public IRepaintController getRepaintController() {
        return surface;
    }

    void close() {
        if (isSeparateFrame && frame != null) {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    @Override
    public void mouseEnteredEvent(MouseEvent mouseEvent) {
        setSurfaceToHandleKbdFocus();
    }

    public void addMouseListener(MouseListener mouseListener) {
        surface.addMouseListener(mouseListener);
    }

    public JFrame getFrame() {
        return frame;
    }

    public void setVisible() {
        container.setVisible(true);
    }
    public void validate() {
        container.validate();
    }

    public static class ButtonsBar {
		private static final Insets BUTTONS_MARGIN = new Insets(2, 2, 2, 2);
		private JPanel bar;
		private boolean isVisible;
        private ArrayList<Component> noFullScreenGroup = new ArrayList<Component>();

        public ButtonsBar() {
			bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
		}

		public JButton createButton(String iconId, String tooltipText, ActionListener actionListener) {
			JButton button = new JButton(Utils.getButtonIcon(iconId));
			button.setToolTipText(tooltipText);
			button.setMargin(BUTTONS_MARGIN);
			bar.add(button);
			button.addActionListener(actionListener);
			return button;
		}

		public Component createStrut() {
			return bar.add(Box.createHorizontalStrut(10));
		}

		public JToggleButton createToggleButton(String iconId, String tooltipText, ItemListener itemListener) {
			JToggleButton button = new JToggleButton(Utils.getButtonIcon(iconId));
			button.setToolTipText(tooltipText);
			button.setMargin(BUTTONS_MARGIN);
			bar.add(button);
			button.addItemListener(itemListener);
			return button;
		}

		public void setVisible(boolean isVisible) {
			this.isVisible = isVisible;
            if (isVisible) bar.revalidate();
		}

		public int getWidth() {
			return bar.getMinimumSize().width;
		}
		public int getHeight() {
			return bar.getMinimumSize().height;
		}

		public void borderOn() {
			bar.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		}

		public void borderOff() {
			bar.setBorder(BorderFactory.createEmptyBorder());
		}

        public void addToNoFullScreenGroup(Component component) {
            noFullScreenGroup.add(component);
        }

        public void setNoFullScreenGroupVisible(boolean isVisible) {
            for (Component c : noFullScreenGroup) {
                c.setVisible(isVisible);
            }
        }
    }

	private static class EmptyButtonsBarMouseAdapter extends MouseAdapter {
		// empty
	}

	private class FullscreenBorderDetectionThread extends Thread {
		public static final int SHOW_HIDE_BUTTONS_BAR_DELAY_IN_MILLS = 700;
		private final JFrame frame;
		private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		private ScheduledFuture<?> futureForShow;
		private ScheduledFuture<?> futureForHide;
		private Point mousePoint, oldMousePoint;
		private Point viewPosition;

		public FullscreenBorderDetectionThread(JFrame frame) {
			super("FS border detector");
			this.frame = frame;
		}

		public void run() {
			setPriority(Thread.MIN_PRIORITY);
			while(isFullScreen) {
				mousePoint = MouseInfo.getPointerInfo().getLocation();
				if (null == oldMousePoint) oldMousePoint = mousePoint;
				SwingUtilities.convertPointFromScreen(mousePoint, frame);
                viewPosition = scroller.getViewport().getViewPosition();
				processButtonsBarVisibility();

				boolean needScrolling = processVScroll() || processHScroll();
				oldMousePoint = mousePoint;
				if (needScrolling) {
					cancelShowExecutor();
					setButtonsBarVisibleFS(false);
					makeScrolling(viewPosition);
				}
				try {
                    Thread.sleep(100);
                } catch (Exception e) {
					// nop
				}
			}
		}

		private boolean processHScroll() {
			if (mousePoint.x < FS_SCROLLING_ACTIVE_BORDER) {
				if (viewPosition.x > 0) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - mousePoint.x;
					if (mousePoint.y != oldMousePoint.y) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.x -= delta;
					if (viewPosition.x < 0) viewPosition.x = 0;
					return true;
				}
			} else if (mousePoint.x > (frame.getWidth() - FS_SCROLLING_ACTIVE_BORDER)) {
				final Rectangle viewRect = scroller.getViewport().getViewRect();
				final int right = viewRect.width + viewRect.x;
				if (right < lpane.getSize().width) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - (frame.getWidth() - mousePoint.x);
					if (mousePoint.y != oldMousePoint.y) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.x += delta;
					if (viewPosition.x + viewRect.width > lpane.getSize().width) viewPosition.x =
                            lpane.getSize().width - viewRect.width;
					return true;
				}
			}
			return false;
		}

		private boolean processVScroll() {
			if (mousePoint.y < FS_SCROLLING_ACTIVE_BORDER) {
				if (viewPosition.y > 0) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - mousePoint.y;
					if (mousePoint.x != oldMousePoint.x) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.y -= delta;
					if (viewPosition.y < 0) viewPosition.y = 0;
					return true;
				}
			} else if (mousePoint.y > (frame.getHeight() - FS_SCROLLING_ACTIVE_BORDER)) {
				final Rectangle viewRect = scroller.getViewport().getViewRect();
				final int bottom = viewRect.height + viewRect.y;
				if (bottom < lpane.getSize().height) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - (frame.getHeight() - mousePoint.y);
					if (mousePoint.x != oldMousePoint.x) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.y += delta;
					if (viewPosition.y + viewRect.height > lpane.getSize().height) viewPosition.y =
                            lpane.getSize().height - viewRect.height;
					return true;
				}
			}
			return false;
		}

		private void processButtonsBarVisibility() {
			if (mousePoint.y < 1) {
				cancelHideExecutor();
				// show buttons bar after delay
				if (! buttonsBar.isVisible && (null == futureForShow || futureForShow.isDone())) {
					futureForShow = scheduler.schedule(new Runnable() {
						@Override
						public void run() {
                            showButtonsBar();
						}
					}, SHOW_HIDE_BUTTONS_BAR_DELAY_IN_MILLS, TimeUnit.MILLISECONDS);
				}
			} else {
				cancelShowExecutor();
			}
			if (buttonsBar.isVisible && mousePoint.y <= buttonsBar.getHeight()) {
				cancelHideExecutor();
			}
			if (buttonsBar.isVisible && mousePoint.y > buttonsBar.getHeight()) {
				// hide buttons bar after delay
				if (null == futureForHide || futureForHide.isDone()) {
					futureForHide = scheduler.schedule(new Runnable() {
						@Override
						public void run() {
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									setButtonsBarVisibleFS(false);
									SwingViewerWindow.this.frame.validate();
								}
							});
						}
					}, SHOW_HIDE_BUTTONS_BAR_DELAY_IN_MILLS, TimeUnit.MILLISECONDS);
				}
			}
		}

		private void cancelHideExecutor() {
			cancelExecutor(futureForHide);
		}
		private void cancelShowExecutor() {
			cancelExecutor(futureForShow);
		}

		private void cancelExecutor(ScheduledFuture<?> future) {
			if (future != null && ! future.isDone()) {
				future.cancel(true);
			}
		}

		private void makeScrolling(final Point viewPosition) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					scroller.getViewport().setViewPosition(viewPosition);
					final Point mousePosition = surface.getMousePosition();
					if (mousePosition != null) {
						final MouseEvent mouseEvent = new MouseEvent(frame, 0, 0, 0,
								mousePosition.x, mousePosition.y, 0, false);
						for (MouseMotionListener mml : surface.getMouseMotionListeners()) {
							mml.mouseMoved(mouseEvent);
						}
					}
				}
			});
		}

		private void showButtonsBar() {
            SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
                    setButtonsBarVisibleFS(true);
				}
			});
		}
	}

    protected void createButtonsPanel(final Protocol protocol, Container container, boolean isApplet) {
        final SwingViewerWindow.ButtonsBar buttonsBar = createButtonsBar();

        buttonsBar.addToNoFullScreenGroup(
                buttonsBar.createButton("options", "Set Options", new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        showOptionsDialog();
                        setSurfaceToHandleKbdFocus();
                    }
                }));

        buttonsBar.addToNoFullScreenGroup(
                buttonsBar.createButton("info", "Show connection info", new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        showConnectionInfoMessage();
                        setSurfaceToHandleKbdFocus();
                    }
                }));

        buttonsBar.addToNoFullScreenGroup(
                buttonsBar.createStrut());

        buttonsBar.createButton("refresh", "Refresh screen", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                protocol.sendRefreshMessage();
                setSurfaceToHandleKbdFocus();
            }
        });

        addZoomButtons();

        kbdButtons = new LinkedList<JComponent>();

        buttonsBar.createStrut();

        JButton ctrlAltDelButton = buttonsBar.createButton("ctrl-alt-del", "Send 'Ctrl-Alt-Del'", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendCtrlAltDel(protocol);
                setSurfaceToHandleKbdFocus();
            }
        });
        kbdButtons.add(ctrlAltDelButton);

        JButton winButton = buttonsBar.createButton("win", "Send 'Win' key as 'Ctrl-Esc'", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendWinKey(protocol);
                setSurfaceToHandleKbdFocus();
            }
        });
        kbdButtons.add(winButton);

        JToggleButton ctrlButton = buttonsBar.createToggleButton("ctrl", "Ctrl Lock",
                new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            protocol.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, true));
                        } else {
                            protocol.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, false));
                        }
                        setSurfaceToHandleKbdFocus();
                    }
                });
        kbdButtons.add(ctrlButton);

        JToggleButton altButton = buttonsBar.createToggleButton("alt", "Alt Lock",
                new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            protocol.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, true));
                        } else {
                            protocol.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, false));
                        }
                        setSurfaceToHandleKbdFocus();
                    }
                });
        kbdButtons.add(altButton);

        ModifierButtonEventListener modifierButtonListener = new ModifierButtonEventListener();
        modifierButtonListener.addButton(KeyEvent.VK_CONTROL, ctrlButton);
        modifierButtonListener.addButton(KeyEvent.VK_ALT, altButton);
        surface.addModifierListener(modifierButtonListener);

//		JButton fileTransferButton = new JButton(Utils.getButtonIcon("file-transfer"));
//		fileTransferButton.setMargin(buttonsMargin);
//		buttonBar.add(fileTransferButton);
//        buttonsBar.createStrut();
//
//        final JToggleButton viewOnlyButton = buttonsBar.createToggleButton("viewonly", "View Only",
//                new ItemListener() {
//                    @Override
//                    public void itemStateChanged(ItemEvent e) {
//                        if (e.getStateChange() == ItemEvent.SELECTED) {
//                            rfbSettings.setViewOnly(true);
//                            rfbSettings.fireListeners();
//                        } else {
//                            rfbSettings.setViewOnly(false);
//                            rfbSettings.fireListeners();
//                        }
//                        setSurfaceToHandleKbdFocus();
//                    }
//                });
//        viewOnlyButton.setSelected(rfbSettings.isViewOnly());
//        rfbSettings.addListener(new IChangeSettingsListener() {
//            @Override
//            public void settingsChanged(SettingsChangedEvent event) {
//                if (ProtocolSettings.isRfbSettingsChangedFired(event)) {
//                    ProtocolSettings settings = (ProtocolSettings) event.getSource();
//                    viewOnlyButton.setSelected(settings.isViewOnly());
//                }
//            }
//        });
//        kbdButtons.add(viewOnlyButton);

        buttonsBar.createStrut();

        buttonsBar.createButton("close", isApplet ? "Disconnect" : "Close", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close();
                presenter.setNeedReconnection(false);
                presenter.cancelConnection();
                fireCloseApp();
            }
        }).setAlignmentX(JComponent.RIGHT_ALIGNMENT);

        setButtonsBarVisible(true, container);
    }

    private void fireCloseApp() {
		if (viewerEventsListener != null) {
			viewerEventsListener.onViewerComponentClosing();
		}
    }

    private void sendCtrlAltDel(Protocol protocol) {
        protocol.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, true));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, true));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_DELETE, true));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_DELETE, false));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, false));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, false));
    }

    private void sendWinKey(Protocol protocol) {
        protocol.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, true));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_ESCAPE, true));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_ESCAPE, false));
        protocol.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, false));
    }

    @Override
    public void settingsChanged(SettingsChangedEvent e) {
        if (ProtocolSettings.isRfbSettingsChangedFired(e)) {
            ProtocolSettings settings = (ProtocolSettings) e.getSource();
            setEnabledKbdButtons( ! settings.isViewOnly());
        }
    }

    private void setEnabledKbdButtons(boolean enabled) {
        if (kbdButtons != null) {
            for (JComponent b : kbdButtons) {
                b.setEnabled(enabled);
            }
        }
    }

    private void showOptionsDialog() {
        OptionsDialog optionsDialog = new OptionsDialog(frame);
        optionsDialog.initControlsFromSettings(rfbSettings, uiSettings, false);
        optionsDialog.setVisible(true);
        presenter.saveHistory();
    }

    private void showConnectionInfoMessage() {
        StringBuilder message = new StringBuilder();
		if ( ! Strings.isTrimmedEmpty(appName)) {
			message.append(appName).append("\n\n");
		}
        message.append("Connected to: ").append(remoteDesktopName).append("\n");
        message.append("Host: ").append(connectionString).append("\n\n");

        message.append("Desktop geometry: ")
                .append(String.valueOf(surface.getWidth()))
                .append(" \u00D7 ") // multiplication sign
                .append(String.valueOf(surface.getHeight())).append("\n");
        message.append("Color format: ")
                .append(String.valueOf(Math.round(Math.pow(2, workingProtocol.getPixelFormat().depth))))
                .append(" colors (")
                .append(String.valueOf(workingProtocol.getPixelFormat().depth))
                .append(" bits)\n");
        message.append("Current protocol version: ")
                .append(workingProtocol.getProtocolVersion());
        if (workingProtocol.isTight()) {
            message.append(" tight");
            if (workingProtocol.getTunnelType() != null && workingProtocol.getTunnelType() != TunnelType.NOTUNNEL) {
                message.append(" using ").append(workingProtocol.getTunnelType().hrName).append(" tunneling");
            }
        }
        message.append("\n");

        JOptionPane infoPane = new JOptionPane(message.toString(), JOptionPane.INFORMATION_MESSAGE);
        final JDialog infoDialog = infoPane.createDialog(frame, "VNC connection info");
        infoDialog.setModalityType(Dialog.ModalityType.MODELESS);
        infoDialog.setVisible(true);
    }
}