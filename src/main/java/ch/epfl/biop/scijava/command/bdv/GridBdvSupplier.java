
package ch.epfl.biop.scijava.command.bdv;

import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvStackSource;
import bdv.viewer.InteractiveDisplayCanvas;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import net.imglib2.RealPoint;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.RayCastPositionerSliderAdder;
import sc.fiji.bdvpg.bdv.navigate.SourceNavigatorSliderAdder;
import sc.fiji.bdvpg.bdv.navigate.TimepointAdapterAdder;
import sc.fiji.bdvpg.bdv.overlay.SourceNameOverlayAdder;
import sc.fiji.bdvpg.bdv.supplier.BdvSupplierHelper;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.services.ui.swingdnd.BdvTransferHandler;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static bdv.viewer.ViewerStateChange.NUM_SOURCES_CHANGED;

public class GridBdvSupplier implements IBdvSupplier {
	public final BiopSerializableBdvOptions sOptions;

	public GridBdvSupplier(BiopSerializableBdvOptions sOptions) {
		this.sOptions = sOptions;
	}

	public GridBdvSupplier() {
		this.sOptions = BiopSerializableBdvOptions.options();
	}

	@Override
	public BdvHandle get() {

		/*BdvHandle bdvh = super.get();

		//BdvSupplierHelper.addSourcesDragAndDrop(bdvh);
		//bdvh.getViewerPanel().setTransferHandler();
		bdvh.getViewerPanel().setTransferHandler(null);
		System.out.println("New Suuplirt!");
		bdvh.getViewerPanel().setTransferHandler(new GridTransferHandler(bdvh));*/
		BdvOptions options = this.sOptions.getBdvOptions();
		ArrayImg<ByteType, ByteArray> dummyImg = ArrayImgs.bytes(new long[]{2L, 2L, 2L});
		options = options.sourceTransform(new AffineTransform3D());
		BdvStackSource<ByteType> bss = BdvFunctions.show(dummyImg, "dummy", options);
		BdvHandle bdvh = bss.getBdvHandle();
		if (this.sOptions.interpolate) {
			bdvh.getViewerPanel().setInterpolation(Interpolation.NLINEAR);
		}

		bdvh.getViewerPanel().state().removeSource(bdvh.getViewerPanel().state().getCurrentSource());
		bdvh.getViewerPanel().setNumTimepoints(this.sOptions.numTimePoints);
		//BdvSupplierHelper.addSourcesDragAndDrop(bdvh);
		SourceSelectorBehaviour ssb = BdvSupplierHelper.addEditorMode(bdvh, "");
		GridBdv gBdv = new GridBdv(bdvh,ssb,1.0,1.0);

		bdvh.getViewerPanel().setTransferHandler(new GridTransferHandler(bdvh, gBdv));


		bdvh.getSplitPanel().setCollapsed(false);
		JPanel editorModeToggle = new JPanel();
		JButton editorToggle = new JButton("Editor Mode");
		editorToggle.addActionListener((e) -> {
			if (ssb.isEnabled()) {
				ssb.disable();
				editorToggle.setText("Editor Mode 'E'");
			} else {
				ssb.enable();
				editorToggle.setText("Navigation Mode 'E'");
			}

		});
		editorModeToggle.add(editorToggle);
		JButton nameToggle = new JButton("Display sources name");
		AtomicBoolean nameOverlayEnabled = new AtomicBoolean();
		nameOverlayEnabled.set(true);
		SourceNameOverlayAdder nameOverlayAdder = new SourceNameOverlayAdder(bdvh, new Font(this.sOptions.font, 0, this.sOptions.fontSize));
		nameToggle.addActionListener((e) -> {
			if (nameOverlayEnabled.get()) {
				nameOverlayEnabled.set(false);
				nameToggle.setText("Display sources names");
				nameOverlayAdder.removeFromBdv();
			} else {
				nameOverlayEnabled.set(true);
				nameToggle.setText("Hide sources name");
				nameOverlayAdder.addToBdv();
			}

		});
		editorModeToggle.add(nameToggle);
		SwingUtilities.invokeLater(() -> {
			nameOverlayAdder.run();
			BdvHandleHelper.addCenterCross(bdvh);
			(new RayCastPositionerSliderAdder(bdvh)).run();
			(new SourceNavigatorSliderAdder(bdvh)).run();
			(new TimepointAdapterAdder(bdvh)).run();
		});
		BdvHandleHelper.addCard(bdvh, "Mode", editorModeToggle, true);
		return bdvh;
	}

	/**
	 * TransferHandler class :
	 * Controls drag and drop actions in the multislice positioner
	 */
	static class GridTransferHandler extends BdvTransferHandler {


		class InnerOverlay extends BdvOverlay {
			//int drawCounter = 0;
			final Color color = new Color(128,112,50,200);
			final Stroke stroke = new BasicStroke(4);
			@Override
			protected void draw(Graphics2D g) {
				// Gets a copy of the slices to avoid concurrent exception

				// Gets current bdv view position
				AffineTransform3D bdvAt3D = new AffineTransform3D();
				bdvh.getViewerPanel().state().getViewerTransform(bdvAt3D);

				drawDragAndDropRectangle(g, bdvAt3D); // honestly it's not used!

				//int w = bdvh.getViewerPanel().getWidth();
				//int h = bdvh.getViewerPanel().getHeight();

				g.setColor(color);
				g.setStroke(stroke);
			}
		}


		final BdvHandle bdvh;
		final GridBdv gBdv;

		int iX, iY;

		public GridTransferHandler(BdvHandle bdvh, GridBdv gBdv) {
			this.bdvh = bdvh;
			this.gBdv = gBdv;
			BdvFunctions.showOverlay(new InnerOverlay(), "DnD Grid Overlay", BdvOptions.options().addTo(bdvh));
		}

		@Override
		public void updateDropLocation(TransferSupport support, DropLocation dl) {
			// Gets the point in real coordinates
			RealPoint pt3d = new RealPoint(3);
			InteractiveDisplayCanvas display = bdvh.getViewerPanel().getDisplay();
			if (display!=null) {
				Point pt = display.getMousePosition();
				if (pt!=null) {
					bdvh.getViewerPanel().displayToGlobalCoordinates(
							bdvh.getViewerPanel().getDisplay().getMousePosition().getX(),
							bdvh.getViewerPanel().getDisplay().getMousePosition().getY(),
							pt3d);

					iX = (int) Math.floor((((pt3d.getDoublePosition(0)+0.5) / gBdv.getGridX())));
					iY = (int) Math.floor((((pt3d.getDoublePosition(1)+0.5) / gBdv.getGridY())));
					bdvh.getViewerPanel().getDisplay().repaint();
				}
			}
		}

		/**
		 * When the user drops the data -> import the slices
		 *
		 * @param support weird stuff for swing drag and drop TODO : link proper documentation
		 * @param sacs list of source and converter to import
		 */
		@Override
		public void importSourcesAndConverters(TransferSupport support, List<SourceAndConverter<?>> sacs) {
			Optional<BdvHandle> bdvh_local = getBdvHandleFromViewerPanel(((bdv.viewer.ViewerPanel) support.getComponent()));
			if (bdvh_local.isPresent()) {
				//double slicingAxisPosition = iSliceNoStep * msp.sizePixX * (int) msp.getReslicedAtlas().getStep();
				//msp.createSlice(sacs.toArray(new SourceAndConverter[0]), slicingAxisPosition, msp.getAtlas().getMap().getAtlasPrecisionInMillimeter(), Tile.class, new Tile(-1));
				//System.out.println("DROP!!");
				gBdv.addSources(sacs, iX, iY);
			}
		}

		private void drawDragAndDropRectangle(Graphics2D g, AffineTransform3D bdvAt3D) {
			int colorCode = ARGBType.rgba(120,250,50,128);

			Color color = new Color(ARGBType.red(colorCode), ARGBType.green(colorCode), ARGBType.blue(colorCode), ARGBType.alpha(colorCode));

			g.setColor(color);

			RealPoint[][] ptRectWorld = new RealPoint[2][2];

			Point[][] ptRectScreen = new Point[2][2];

			double sX = gBdv.getGridX();
			double sY = gBdv.getGridY();

			for (int xp = 0; xp < 2; xp++) {
				for (int yp = 0; yp < 2; yp++) {
					ptRectWorld[xp][yp] = new RealPoint(3);
					RealPoint pt = ptRectWorld[xp][yp];
					pt.setPosition((sX * (iX - 0.5 + xp)), 0);
					pt.setPosition((sY * (iY - 0.5 + yp)), 1);
					pt.setPosition(0, 2);
					bdvAt3D.apply(pt, pt);
					ptRectScreen[xp][yp] = new Point(
							(int) pt.getDoublePosition(0),
							(int) pt.getDoublePosition(1));
				}
			}

			g.drawLine(ptRectScreen[0][0].x, ptRectScreen[0][0].y, ptRectScreen[1][0].x, ptRectScreen[1][0].y);
			g.drawLine(ptRectScreen[1][0].x, ptRectScreen[1][0].y, ptRectScreen[1][1].x, ptRectScreen[1][1].y);
			g.drawLine(ptRectScreen[1][1].x, ptRectScreen[1][1].y, ptRectScreen[0][1].x, ptRectScreen[0][1].y);
			g.drawLine(ptRectScreen[0][1].x, ptRectScreen[0][1].y, ptRectScreen[0][0].x, ptRectScreen[0][0].y);

			g.setColor(color);
		}
	}

	public static class GridBdv implements ViewerStateChangeListener {

		public static class CenterAndGridPosition {
			RealPoint center;
			double px, py;
			public CenterAndGridPosition(RealPoint center, int px, int py) {
				this.center = center;
				this.px = px;
				this.py = py;
			}
		}

		final BdvHandle bdvh;

		double gridSizeX, gridSizeY;

		Map<SourceAndConverter<?>, CenterAndGridPosition> sourceToCenter = new ConcurrentHashMap<>();

		final SourceSelectorBehaviour ssb;

		public GridBdv(final BdvHandle bdvh, SourceSelectorBehaviour ssb, double gridSizeX, double gridSizeY) {
			this.bdvh = bdvh;
			this.ssb = ssb;
			setGridSize(gridSizeX, gridSizeY);
			bdvh.getViewerPanel().state().changeListeners()
					.add(this);
			BdvHandleHelper.addCard(bdvh, "Grid Size", getGridSizeSelector(gridSizeX,gridSizeY), true);
		}

		private JComponent getGridSizeSelector(double iniX, double iniY) {
			JTextField widthField;
			JTextField heightField;
			JButton increaseButton;
			JButton decreaseButton;
			// Create the text fields for the width and height
			widthField = new JTextField(Double.toString(iniX), 5);
			heightField = new JTextField(Double.toString(iniY), 5);
			widthField.addActionListener(e -> {
				double width = Double.parseDouble(widthField.getText());
				double height = Double.parseDouble(heightField.getText());
				setGridSize(width, height);
			});
			heightField.addActionListener(e -> {
				double width = Double.parseDouble(widthField.getText());
				double height = Double.parseDouble(heightField.getText());
				setGridSize(width, height);
			});

			// Create the buttons for increasing and decreasing the size
			increaseButton = new JButton("+");
			decreaseButton = new JButton("-");

			DecimalFormat df = new DecimalFormat("0.000");

			// Set the action listeners for the buttons
			increaseButton.addActionListener(e -> {
				double width = Double.parseDouble(widthField.getText());
				double height = Double.parseDouble(heightField.getText());
				width *= 1.1;
				height *= 1.1;
				widthField.setText(df.format(width));
				heightField.setText(df.format(height));
				setGridSize(width, height);
			});

			decreaseButton.addActionListener(e -> {
				double width = Double.parseDouble(widthField.getText());
				double height = Double.parseDouble(heightField.getText());
				width /= 1.1;
				height /= 1.1;
				widthField.setText(df.format(width));
				heightField.setText(df.format(height));
				setGridSize(width, height);
			});

			// Create the labels for the fields
			JLabel widthLabel = new JLabel("Width:");
			JLabel heightLabel = new JLabel("Height:");

			// Create the panel for the fields
			JPanel fieldPanel = new JPanel(new GridLayout(2, 2));
			fieldPanel.add(widthLabel);
			fieldPanel.add(widthField);
			fieldPanel.add(heightLabel);
			fieldPanel.add(heightField);

			// Create the panel for the buttons
			JPanel buttonPanel = new JPanel(new FlowLayout());
			buttonPanel.add(increaseButton);
			buttonPanel.add(decreaseButton);

			// Create the main panel
			JPanel mainPanel = new JPanel(new BorderLayout());
			mainPanel.add(fieldPanel, BorderLayout.CENTER);
			mainPanel.add(buttonPanel, BorderLayout.SOUTH);

			JPanel moveButton = new JPanel();
			JButton moveUp = new JButton("/\\");
			moveUp.addActionListener(e -> moveSelectedSources(0,-1));
			moveButton.add(moveUp);
			JButton moveDown = new JButton("\\/");
			moveDown.addActionListener(e -> moveSelectedSources(0,1));
			moveButton.add(moveDown);
			JButton moveRight = new JButton(">");
			moveRight.addActionListener(e -> moveSelectedSources(1,0));
			moveButton.add(moveRight);
			JButton moveLeft = new JButton("<");
			moveLeft.addActionListener(e -> moveSelectedSources(-1,0));
			moveButton.add(moveLeft);

			mainPanel.add(moveButton, BorderLayout.NORTH);


			// Add the main panel to the frame
			return mainPanel;
		}

		private void moveSelectedSources(int dx, int dy) {
			Set<SourceAndConverter<?>> selectedSources = ssb.getSelectedSources();
			sourceToCenter.forEach((source, cpxy) -> {
				if (selectedSources.contains(source)) {
					cpxy.px+=dx;
					cpxy.py+=dy;
				}
			});
			updatePositions();
			bdvh.getViewerPanel().requestRepaint();
		}

		private void updatePositions() {
			sourceToCenter.forEach((source, cpxy) -> {
				AffineTransform3D transform3D = new AffineTransform3D();
				//RealPoint center = SourceAndConverterHelper.getSourceAndConverterCenterPoint(source,0);
				double[] coords =  cpxy.center.positionAsDoubleArray();
				//double[] coords =  center.positionAsDoubleArray();
				transform3D.translate(-coords[0], -coords[1], -coords[2]);
				transform3D.translate(cpxy.px*gridSizeX, cpxy.py*gridSizeY, 0);
				((TransformedSource)source.getSpimSource()).setFixedTransform(transform3D);
			});
			bdvh.getViewerPanel().requestRepaint();
		}

		public synchronized void setGridSize(double gridSizeX, double gridSizeY) {
			if ((gridSizeX!=this.gridSizeX)||(gridSizeY!=gridSizeY)) {
				this.gridSizeX = gridSizeX;
				this.gridSizeY = gridSizeY;
				updatePositions();
			}
		}

		public double getGridX() {
			return gridSizeX;
		}
		public double getGridY() {
			return gridSizeY;
		}

		public synchronized void addSources(List<SourceAndConverter<?>> sacs, int px, int py) {
			RealPoint center = SourceAndConverterHelper.getSourceAndConverterCenterPoint(sacs.get(0),0);
			AffineTransform3D transform3D = new AffineTransform3D();
			double[] coords =  center.positionAsDoubleArray();
			transform3D.translate(-coords[0], -coords[1], -coords[2]);
			transform3D.translate(px*gridSizeX, py*gridSizeY, 0);
			SourceAffineTransformer sat = new SourceAffineTransformer(null, transform3D);
			for (SourceAndConverter<?> source: sacs) {
				SourceAndConverter<?> transformed = sat.apply(source);
				sourceToCenter.put(transformed, new CenterAndGridPosition(center, px, py));
				SourceAndConverterServices
						.getBdvDisplayService()
						.show(bdvh, transformed);
			}
		}
		@Override
		public void viewerStateChanged(ViewerStateChange change) {
			switch (change) {
				case NUM_SOURCES_CHANGED:

					break;
			}
		}
	}




}
