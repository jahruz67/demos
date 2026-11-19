/*
 * GeoGebra - Dynamic Mathematics for Everyone
 * Copyright (c) GeoGebra GmbH, Altenbergerstr. 69, 4040 Linz, Austria
 * https://www.geogebra.org
 *
 * This file is licensed by GeoGebra GmbH under the EUPL 1.2 licence and
 * may be used under the EUPL 1.2 in compatible projects (see Article 5
 * and the Appendix of EUPL 1.2 for details).
 * You may obtain a copy of the licence at:
 * https://interoperable-europe.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Note: The overall GeoGebra software package is free to use for
 * non-commercial purposes only.
 * See https://www.geogebra.org/license for full licensing details
 */

package dev.demos.graphing.geogebra;

import org.geogebra.common.awt.GBufferedImage;
import org.geogebra.common.awt.AwtFactory;
import org.geogebra.common.awt.GDimension;
import org.geogebra.common.awt.GFont;
import org.geogebra.common.awt.GGraphics2D;
import org.geogebra.common.euclidian.CoordSystemAnimation;
import org.geogebra.common.euclidian.EuclidianStyleBar;
import org.geogebra.common.geogebra3D.euclidian3D.EuclidianController3D;
import org.geogebra.common.geogebra3D.euclidian3D.EuclidianView3D;
import org.geogebra.common.geogebra3D.euclidian3D.draw.DrawableTexture3D;
import org.geogebra.common.geogebra3D.euclidian3D.openGL.Renderer;
import org.geogebra.common.main.settings.EuclidianSettings;

final class NativeView extends EuclidianView3D {
    private boolean focused;
    void setNativeFocus(boolean value){focused=value;}
    private int viewportWidth=1,viewportHeight=1;
    void resize(int width,int height){viewportWidth=width;viewportHeight=height;renderer.setView(0,0,width,height);setViewChanged();setWaitForUpdate();}
    @Override protected void logInited(){}


	/**
	 * Euclidian 3D view for tests.
	 */
	public NativeView(EuclidianController3D ec,
			EuclidianSettings settings) {
		super(ec, settings);
		settings.addListener(this);
	}

	@Override
	public void repaint() {

	}

	@Override
	public void setToolTipText(String plainTooltip) {

	}

	@Override
	public boolean hasFocus() {
		return focused;
	}

	@Override
	public void requestFocus() {

	}

	@Override
	public int getWidth() {
		return viewportWidth;
	}

	@Override
	public int getHeight() {
		return viewportHeight;
	}

	@Override
	public boolean suggestRepaint() {
		return false;
	}

	@Override
	public boolean isShowing() {
		return true;
	}

	@Override
	protected void createPanel() {

	}

	@Override
    protected Renderer createRenderer() { return new NativeRenderer(this); }

	@Override
	protected void setTransparentCursor() {

	}

	@Override
	protected boolean getShiftDown() {
		return false;
	}

	@Override
	protected void setDefault2DCursor() {

	}

	@Override
	public GGraphics2D getTempGraphics2D(GFont fontForGraphics) {
		GGraphics2D graphics=AwtFactory.getPrototype().newBufferedImage(8,8,1).createGraphics();
        if(fontForGraphics!=null)graphics.setFont(fontForGraphics);
        return graphics;
	}

	@Override
	public GFont getFont() {
		return AwtFactory.getPrototype().newFont("sans-serif",0,16);
	}

	@Override
	protected void setStyleBarMode(int mode) {

	}

	@Override
	protected void updateSizeKeepDrawables() {

	}

	@Override
	public boolean requestFocusInWindow() {
		return true;
	}

	@Override
	public void setPreferredSize(GDimension preferredSize) {

	}

	@Override
	protected CoordSystemAnimation newZoomer() {
		return null;
	}

	@Override
	protected EuclidianStyleBar newEuclidianStyleBar() {
		return null;
	}

	@Override
	protected EuclidianStyleBar newDynamicStyleBar() {
		return null;
	}

	@Override
	protected void addDynamicStylebarToEV(EuclidianStyleBar dynamicStylebar) {

	}

}
